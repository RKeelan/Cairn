package com.rkeelan.cairn.phone

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import com.rkeelan.cairn.core.Item
import com.rkeelan.cairn.core.ListStore
import com.rkeelan.cairn.core.Saved
import com.rkeelan.cairn.core.ShoppingList
import com.rkeelan.cairn.core.Status

/** An item in the device's copy of the list. [unsent] marks one changed here that the server has not yet taken. */
@Entity(tableName = "items")
data class ItemRow(
    @PrimaryKey val key: String,
    val name: String,
    val quantity: Double?,
    val unit: String?,
    val status: Status,
    val addedAt: Long,
    val changedAt: Long,
    val unsent: Boolean,
) {
    constructor(item: Item, unsent: Boolean) : this(item.key, item.name, item.quantity, item.unit, item.status, item.addedAt, item.changedAt, unsent)

    fun item(): Item = Item(key, name, quantity, unit, status, addedAt, changedAt)
}

/** Which of the server's lists the items are a copy of, if known yet, and the revision the copy was last synced at. There is one row. */
@Entity(tableName = "copy")
data class CopyRow(val listId: String?, val revision: Long, @PrimaryKey val id: Int = 0)

@Dao
interface ListDao {
    @Query("SELECT * FROM items")
    suspend fun items(): List<ItemRow>

    @Query("SELECT * FROM copy")
    suspend fun copy(): CopyRow?

    @Upsert
    suspend fun put(items: List<ItemRow>)

    @Upsert
    suspend fun put(copy: CopyRow)

    @Query("DELETE FROM items")
    suspend fun deleteItems()

    @Query("DELETE FROM copy")
    suspend fun deleteCopy()

    @Transaction
    suspend fun save(copy: CopyRow, items: List<ItemRow>) {
        put(copy)
        put(items)
    }

    @Transaction
    suspend fun clear() {
        deleteItems()
        deleteCopy()
    }
}

@Database(entities = [ItemRow::class, CopyRow::class], version = 1)
abstract class ListDatabase : RoomDatabase() {
    abstract fun lists(): ListDao
}

/** The device's copy of the list, kept in a Room database. */
class RoomListStore(private val dao: ListDao) : ListStore {
    override suspend fun load(): Saved {
        val copy = dao.copy()
        val rows = dao.items()
        val unsent = rows.filter(ItemRow::unsent).map(ItemRow::key).toSet()
        return Saved(copy?.listId, ShoppingList.of(rows.map(ItemRow::item), unsent, copy?.revision ?: 0))
    }

    override suspend fun save(listId: String?, list: ShoppingList, changed: Collection<Item>) {
        dao.save(CopyRow(listId, list.revision), changed.map { ItemRow(it, unsent = it.key in list.unsent) })
    }

    override suspend fun clear() = dao.clear()
}
