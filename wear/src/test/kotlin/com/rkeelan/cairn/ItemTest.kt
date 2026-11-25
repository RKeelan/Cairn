package com.rkeelan.cairn

import org.junit.Assert.assertEquals
import org.junit.Test

class ItemTest {
    @Test
    fun tickedItemsMoveToTheEndInListOrder() {
        val list = listOf(Item("milk", ticked = true), Item("bread"), Item("eggs", ticked = true), Item("apples"))

        assertEquals(listOf("bread", "apples", "milk", "eggs"), list.basketLast().map(Item::name))
    }
}
