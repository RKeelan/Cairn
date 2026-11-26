package com.rkeelan.cairn.phone

import android.content.Context
import androidx.room.Room
import com.rkeelan.cairn.core.Amount
import com.rkeelan.cairn.core.Item
import com.rkeelan.cairn.core.Order
import com.rkeelan.cairn.core.Saved
import com.rkeelan.cairn.core.Settings
import com.rkeelan.cairn.core.ShoppingList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class StorageTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val database = Room.inMemoryDatabaseBuilder(context, ListDatabase::class.java).allowMainThreadQueries().build()
    private val store = RoomListStore(database.lists())

    @After
    fun close() = database.close()

    @Test
    fun nothingSavedLoadsAsAnEmptyCopyOfNoList() = runTest {
        assertEquals(Saved(null, ShoppingList()), store.load())
    }

    @Test
    fun aSavedCopyLoadsAsItWas() = runTest {
        val unsynced = ShoppingList().add("Milk", now = 100, amount = Amount(2.0, "L")).add("Eggs", now = 101).tick("eggs", now = 102)
        store.save(null, unsynced, unsynced.changedSince(ShoppingList()))
        assertEquals(Saved(null, unsynced), store.load())

        // The server took the eggs, and the milk came off the list while that was under way.
        val synced = unsynced.synced(sent = listOf(unsynced["eggs"]!!), received = emptyList(), revision = 7).remove("milk", now = 200)
        store.save("list-1", synced, synced.changedSince(unsynced))

        assertEquals(Saved("list-1", synced), store.load())
        assertEquals(setOf("milk"), store.load().list.unsent)
    }

    @Test
    fun onlyTheRowsGivenAreWritten() = runTest {
        val list = ShoppingList().add("Milk", now = 100).add("Eggs", now = 101)
        store.save("list-1", list, list.changedSince(ShoppingList()))

        // Storage is told nothing changed, so it keeps the rows it has.
        store.save("list-1", list.tick("milk", now = 200), emptyList<Item>())

        assertEquals(list.items.toSet(), store.load().list.items.toSet())
    }

    @Test
    fun aClearedStoreLoadsAsEmpty() = runTest {
        val list = ShoppingList().add("Milk", now = 100)
        store.save("list-1", list, list.changedSince(ShoppingList()))

        store.clear()

        assertEquals(Saved(null, ShoppingList()), store.load())
    }

    @Test
    fun settingsAreKeptBetweenRuns() {
        val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val settings = SettingsStore(preferences)
        assertEquals(Settings(), settings.settings.value)

        settings.set(Settings(order = Order.ALPHABETICAL))

        assertEquals(Settings(Order.ALPHABETICAL), settings.settings.value)
        assertEquals(Settings(Order.ALPHABETICAL), SettingsStore(preferences).settings.value)
    }

    @Test
    fun aSettingThisVersionLacksReadsAsTheDefault() {
        val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        preferences.edit().putString("order", "BY_AISLE").commit()

        assertEquals(Settings(Order.OLDEST_FIRST), SettingsStore(preferences).settings.value)
    }
}
