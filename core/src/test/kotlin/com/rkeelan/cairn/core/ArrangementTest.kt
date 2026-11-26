package com.rkeelan.cairn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangementTest {
    // Added in this order, then eggs and apples ticked, eggs first.
    private val list = listOf("Milk", "eggs", "Bread", "apples")
        .foldIndexed(ShoppingList()) { index, list, name -> list.add(name, now = 100L + index) }
        .tick("eggs", now = 200)
        .tick("apples", now = 201)

    private fun names(items: List<Item>) = items.map(Item::name)

    @Test
    fun byDefaultTheOldestIsFirstAndTheMostRecentlyBoughtFollow() {
        val arranged = list.arranged(Settings())

        assertEquals(listOf("Milk", "Bread"), names(arranged.needed))
        assertEquals(listOf("apples", "eggs"), names(arranged.bought))
        assertFalse(arranged.moreBought)
        assertFalse(arranged.isEmpty)
    }

    @Test
    fun theOrderAppliesToTheNeededItemsOnly() {
        val newest = list.arranged(Settings(order = Order.NEWEST_FIRST))
        val alphabetical = list.arranged(Settings(order = Order.ALPHABETICAL))

        assertEquals(listOf("Bread", "Milk"), names(newest.needed))
        assertEquals(listOf("Bread", "Milk"), names(alphabetical.needed))
        assertEquals(listOf("apples", "eggs"), names(newest.bought))
        assertEquals(listOf("apples", "eggs"), names(alphabetical.bought))
    }

    @Test
    fun boughtItemsComeAPageAtATime() {
        val many = (1..45).fold(ShoppingList()) { list, n -> list.add("Item $n", now = n.toLong()).tick("item $n", now = 100L + n) }

        val first = many.arranged(Settings())
        val all = many.arranged(Settings(), boughtShown = 3 * Arrangement.BOUGHT_PAGE)

        assertEquals(Arrangement.BOUGHT_PAGE, first.bought.size)
        assertEquals("Item 45", first.bought.first().name)
        assertTrue(first.moreBought)
        assertEquals(45, all.bought.size)
        assertFalse(all.moreBought)
    }

    @Test
    fun anArrangementWithNothingInItIsEmpty() {
        assertTrue(ShoppingList().arranged(Settings()).isEmpty)
        assertTrue(list.remove("eggs", now = 300).remove("apples", now = 301).remove("milk", now = 302).remove("bread", now = 303).arranged(Settings()).isEmpty)
    }
}
