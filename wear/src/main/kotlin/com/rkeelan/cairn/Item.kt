package com.rkeelan.cairn

/** An item on the list. A ticked item is in the basket. */
data class Item(val name: String, val ticked: Boolean = false)

/** The items in the order the watch shows them: those still needed first, then those in the basket, each in list order. */
fun List<Item>.basketLast(): List<Item> {
    val (inBasket, needed) = partition(Item::ticked)
    return needed + inBasket
}
