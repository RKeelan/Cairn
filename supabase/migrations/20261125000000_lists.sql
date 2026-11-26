-- Lists, their members and their items, and the functions the apps call.
--
-- The apps never write to a table. They find their lists with my_lists(), exchange changes with sync(), and change who a list
-- is shared with through add_member() and remove_member(). Row-level security lets a member read a list's rows, which is also
-- what lets Realtime tell a member that the list has changed.

create schema private;
grant usage on schema private to authenticated;

-- Who may start a list of their own. The project's owner keeps this table, and the API cannot reach it. Someone added to a list
-- by one of its members needs no row here.
create table public.invited (
    email text primary key check (email = lower(email))
);
alter table public.invited enable row level security;

create table public.lists (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    -- Counts the changes made to the list. Each item records the revision it last changed at, so a device asks only for what
    -- changed after the revision it last saw.
    revision bigint not null default 0,
    created_at timestamptz not null default now()
);
alter table public.lists enable row level security;

-- A list's members, by the email address each signs in with, so that someone can be added before they first sign in.
create table public.members (
    list_id uuid not null references public.lists (id) on delete cascade,
    email text not null check (email = lower(email)),
    added_at timestamptz not null default now(),
    primary key (list_id, email)
);
create index members_by_email on public.members (email);
alter table public.members enable row level security;

create table public.items (
    list_id uuid not null references public.lists (id) on delete cascade,
    -- The item's name without regard to case or spacing, which identifies the item within its list. Whatever first adds the item
    -- works the key out.
    key text not null check (key <> '' and char_length(key) <= 200),
    name text not null check (name <> '' and char_length(name) <= 100),
    -- How much to buy, as a number and a unit, either of which may be left out: 2 loaves, 500 g, 3, or a box.
    quantity numeric check (quantity > 0 and quantity < 100000),
    unit text check (unit <> '' and char_length(unit) <= 20),
    -- An item taken off the list keeps its row, as bought or removed. That is how its going reaches the other devices, and the
    -- apps suggest bought items when a name is typed.
    status text not null check (status in ('needed', 'inBasket', 'bought', 'removed')),
    -- Milliseconds since the epoch, by the clock of whatever made the change. Where two changes to an item meet, the one with
    -- the later changed_at wins.
    added_at bigint not null,
    changed_at bigint not null,
    revision bigint not null,
    primary key (list_id, key)
);
create index items_by_revision on public.items (list_id, revision);
alter table public.items enable row level security;

revoke all on public.invited, public.lists, public.members, public.items from anon, authenticated;
grant select on public.lists, public.members, public.items to authenticated;

-- The email address the caller signed in with.
create function private.caller() returns text
language sql stable
set search_path = ''
as $$
    select lower(auth.jwt() ->> 'email')
$$;

-- Whether the caller is a member of a list. It runs as its owner, since a policy on members that read members itself would
-- never finish.
create function private.is_member(list uuid) returns boolean
language sql stable security definer
set search_path = ''
as $$
    select exists (select 1 from public.members m where m.list_id = list and m.email = private.caller())
$$;

create policy "Members read their lists" on public.lists
    for select to authenticated using (private.is_member(id));
create policy "Members read their list's members" on public.members
    for select to authenticated using (private.is_member(list_id));
create policy "Members read their list's items" on public.items
    for select to authenticated using (private.is_member(list_id));

-- Realtime tells each member's devices when a list's revision changes.
alter publication supabase_realtime add table public.lists;

-- The caller's lists and who each is shared with. The apps show the first: the list with the most members, and the oldest of
-- those. A caller who belongs to no list is given a new one, if invited; otherwise the call fails with CA001.
create function public.my_lists()
returns table (id uuid, name text, members text[])
language plpgsql security definer
set search_path = ''
as $$
declare
    me text := private.caller();
    created uuid;
begin
    -- Two devices signing in at once would otherwise each make the account a list.
    perform pg_advisory_xact_lock(hashtextextended(coalesce(me, ''), 0));

    if not exists (select 1 from public.members m where m.email = me) then
        if not exists (select 1 from public.invited i where i.email = me) then
            raise exception 'This account has not been invited' using errcode = 'CA001';
        end if;
        insert into public.lists as l (name) values ('Shopping list') returning l.id into created;
        insert into public.members (list_id, email) values (created, me);
    end if;

    return query
        select l.id, l.name, array(select m.email from public.members m where m.list_id = l.id order by m.added_at, m.email)
        from public.lists l
        where exists (select 1 from public.members m where m.list_id = l.id and m.email = me)
        order by (select count(*) from public.members m where m.list_id = l.id) desc, l.created_at, l.id;
end;
$$;

-- Takes a device's changes to a list and returns the list's revision, its members, and every item changed after the revision the
-- device last saw. A change is taken only if it is later than the item the list already has. Fails with CA002 if the caller is
-- not a member of the list.
--
-- changes is an array of {key, name, quantity, unit, status, addedAt, changedAt}, and the items returned have the same form.
-- quantity and unit are null, or left out of a change, when there is none.
create function public.sync(list uuid, since bigint, changes jsonb)
returns jsonb
language plpgsql security definer
set search_path = ''
as $$
declare
    latest bigint;
begin
    if not private.is_member(list) then
        raise exception 'Not a member of this list' using errcode = 'CA002';
    end if;

    if jsonb_array_length(changes) > 0 then
        -- Locks the list's row, so that changes from two devices are taken one after the other.
        update public.lists l set revision = l.revision + 1 where l.id = list returning l.revision into latest;

        insert into public.items as held (list_id, key, name, quantity, unit, status, added_at, changed_at, revision)
        select distinct on (c.key) list, c.key, c.name, c.quantity, c.unit, c.status, c."addedAt", c."changedAt", latest
        from jsonb_to_recordset(changes)
            as c (key text, name text, quantity numeric, unit text, status text, "addedAt" bigint, "changedAt" bigint)
        order by c.key, c."changedAt" desc
        on conflict (list_id, key) do update
            set name = excluded.name,
                quantity = excluded.quantity,
                unit = excluded.unit,
                status = excluded.status,
                added_at = excluded.added_at,
                changed_at = excluded.changed_at,
                revision = excluded.revision
            where excluded.changed_at > held.changed_at;
    else
        select l.revision into latest from public.lists l where l.id = list;
    end if;

    return jsonb_build_object(
        'revision', latest,
        'members', (
            select coalesce(jsonb_agg(m.email order by m.added_at, m.email), '[]'::jsonb)
            from public.members m
            where m.list_id = list
        ),
        'items', (
            select coalesce(
                jsonb_agg(jsonb_build_object(
                    'key', i.key,
                    'name', i.name,
                    'quantity', i.quantity,
                    'unit', i.unit,
                    'status', i.status,
                    'addedAt', i.added_at,
                    'changedAt', i.changed_at
                )),
                '[]'::jsonb
            )
            from public.items i
            where i.list_id = list and i.revision > since
        )
    );
end;
$$;

-- Shares a list with whoever signs in with an email address. Fails with CA002 if the caller is not a member of the list, and
-- with CA003 if the address is not one.
create function public.add_member(list uuid, member text)
returns void
language plpgsql security definer
set search_path = ''
as $$
declare
    address text := lower(btrim(member));
begin
    if not private.is_member(list) then
        raise exception 'Not a member of this list' using errcode = 'CA002';
    end if;
    if address !~ '^[^@[:space:]]+@[^@[:space:]]+$' or char_length(address) > 254 then
        raise exception 'Not an email address' using errcode = 'CA003';
    end if;

    insert into public.members (list_id, email) values (list, address) on conflict do nothing;
    -- The other members' devices hear of the change as they hear of any other.
    update public.lists l set revision = l.revision + 1 where l.id = list;
end;
$$;

-- Stops sharing a list with a member, who may be the caller. A list left with no members is deleted. Fails with CA002 if the
-- caller is not a member of the list.
create function public.remove_member(list uuid, member text)
returns void
language plpgsql security definer
set search_path = ''
as $$
begin
    if not private.is_member(list) then
        raise exception 'Not a member of this list' using errcode = 'CA002';
    end if;

    delete from public.members m where m.list_id = list and m.email = lower(btrim(member));
    if exists (select 1 from public.members m where m.list_id = list) then
        update public.lists l set revision = l.revision + 1 where l.id = list;
    else
        delete from public.lists l where l.id = list;
    end if;
end;
$$;

revoke execute on function public.my_lists(), public.sync(uuid, bigint, jsonb), public.add_member(uuid, text), public.remove_member(uuid, text)
    from public, anon;
grant execute on function public.my_lists(), public.sync(uuid, bigint, jsonb), public.add_member(uuid, text), public.remove_member(uuid, text)
    to authenticated;
