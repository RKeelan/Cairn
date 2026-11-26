package com.rkeelan.cairn.core

/** The order the needed items are shown in. */
enum class Order { OLDEST_FIRST, NEWEST_FIRST, ALPHABETICAL }

/** How the list is shown. The phone keeps the settings, and the watch follows the phone's. */
data class Settings(val order: Order = Order.OLDEST_FIRST)

/**
 * The list as a screen shows it: the [needed] items, then the [bought] ones under their own heading, the most recently ticked first. [bought] holds
 * as many as were asked for, and [moreBought] says whether there are others, which the screen asks for as it is scrolled towards its end.
 */
data class Arrangement(val needed: List<Item>, val bought: List<Item>, val moreBought: Boolean) {
    val isEmpty: Boolean get() = needed.isEmpty() && bought.isEmpty()

    companion object {
        /** How many bought items a screen shows at first, and how many more it asks for each time. */
        const val BOUGHT_PAGE = 20
    }
}

/** The list as [settings] lay it out, with up to [boughtShown] bought items. */
fun ShoppingList.arranged(settings: Settings, boughtShown: Int = Arrangement.BOUGHT_PAGE): Arrangement {
    val order = when (settings.order) {
        Order.OLDEST_FIRST -> compareBy(Item::addedAt).thenBy(Item::key)
        Order.NEWEST_FIRST -> compareByDescending(Item::addedAt).thenBy(Item::key)
        Order.ALPHABETICAL -> compareBy(Item::key)
    }
    return Arrangement(needed.sortedWith(order), bought.take(boughtShown), bought.size > boughtShown)
}
