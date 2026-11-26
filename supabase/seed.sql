-- The local stack's invited accounts: the first of the dev flavour's accounts, and the account the integration tests start a
-- list with. The dev flavour's second account is not here, so that it can use a list only once the first shares one with it.
insert into public.invited (email) values
    ('ada@example.com'),
    ('owner@example.com');
