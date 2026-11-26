package com.rkeelan.cairn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingListTest {
    private fun shopping(vararg names: String): ShoppingList = names.foldIndexed(ShoppingList()) { index, list, name -> list.add(name, now = 100L + index) }

    private fun ShoppingList.names(): List<String> = needed.map(Item::name)

    private fun ShoppingList.boughtNames(): List<String> = bought.map(Item::name)

    private val twoLitres = Amount(2.0, "L")

    @Test
    fun tidyTrimsAndCollapsesSpacing() {
        assertEquals("Oat milk", tidy("  Oat \t\n milk "))
        assertEquals("", tidy(" \n "))
    }

    @Test
    fun tidyCutsLongNamesWithoutSplittingACharacter() {
        assertEquals(MAX_NAME_LENGTH, tidy("x".repeat(250)).length)
        // The pair straddles the limit, so the whole character goes.
        assertEquals("x".repeat(MAX_NAME_LENGTH - 1), tidy("x".repeat(MAX_NAME_LENGTH - 1) + "🥛"))
        assertEquals("ab", tidy("ab    cd", maxLength = 3))
    }

    @Test
    fun keysIgnoreCaseAndSpacing() {
        assertEquals("oat milk", keyOf("  Oat   MILK "))
        assertEquals(keyOf("Crème fraîche"), keyOf("CRÈME  FRAÎCHE"))
    }

    @Test
    fun addedItemsAreNeededInTheOrderAdded() {
        val list = shopping("Milk", "Eggs", "Bread")

        assertEquals(listOf("Milk", "Eggs", "Bread"), list.names())
        assertEquals(setOf("milk", "eggs", "bread"), list.unsent)
        assertEquals(Item("milk", "Milk", status = Status.NEEDED, addedAt = 100, changedAt = 100), list["milk"])
    }

    @Test
    fun addingABlankNameAddsNothing() {
        val list = shopping("Milk")

        assertSame(list, list.add("  ", now = 200))
    }

    @Test
    fun addingTheSameNameTwiceIsTheSameAsAddingItOnce() {
        val list = shopping("Milk")

        assertSame(list, list.add(" MILK ", now = 200))
    }

    @Test
    fun addingWithAnAmountSetsItAndAddingWithoutOneKeepsIt() {
        val list = shopping("Milk").add("Milk", now = 200, amount = twoLitres)

        assertEquals(twoLitres, list["milk"]?.amount)
        assertSame(list, list.add("Milk", now = 300))
        assertSame(list, list.add("Milk", now = 300, amount = twoLitres))
        assertEquals(Amount(unit = "carton"), list.add("Milk", now = 300, amount = Amount(unit = "carton"))["milk"]?.amount)
    }

    @Test
    fun addingABoughtOrRemovedItemPutsItAtTheEndAsTyped() {
        val list = shopping("milk", "Eggs", "bread").remove("milk", now = 200).tick("bread", now = 201).add("Milk", now = 300).add("Bread", now = 301)

        assertEquals(listOf("Eggs", "Milk", "Bread"), list.names())
        assertEquals(emptyList<String>(), list.boughtNames())
    }

    @Test
    fun tickingBuysAnItemAndUntickingPutsItBackInItsPlace() {
        val ticked = shopping("Milk", "Eggs").tick("milk", now = 200)

        assertEquals(listOf("Eggs"), ticked.names())
        assertEquals(listOf("Milk"), ticked.boughtNames())
        assertEquals(200L, ticked["milk"]?.changedAt)
        assertSame(ticked, ticked.tick("milk", now = 300))
        assertEquals(listOf("Milk", "Eggs"), ticked.untick("milk", now = 300).names())
        assertSame(ticked, ticked.untick("eggs", now = 300))
        assertSame(ticked, ticked.tick("no such item", now = 300))
    }

    @Test
    fun boughtItemsComeTheMostRecentlyTickedFirst() {
        val list = shopping("Milk", "Eggs", "Bread").tick("eggs", now = 200).tick("milk", now = 201).tick("bread", now = 202).untick("milk", now = 203)

        assertEquals(listOf("Bread", "Eggs"), list.boughtNames())
        assertEquals(listOf("Milk", "Bread", "Eggs"), list.tick("milk", now = 204).boughtNames())
    }

    @Test
    fun aTickFromElsewhereCountsOnlyIfItIsLater() {
        val list = shopping("Milk", "Eggs").tick("eggs", now = 200).remove("milk", now = 201)
        val eggs = list["eggs"]!!

        assertEquals(Status.NEEDED, list.ticked("eggs", bought = false, at = 300)["eggs"]?.status)
        assertEquals(setOf("eggs", "milk"), list.ticked("eggs", bought = false, at = 300).unsent)
        assertEquals(300L, list.ticked("eggs", bought = false, at = 300)["eggs"]?.changedAt)
        assertSame(list, list.ticked("eggs", bought = false, at = eggs.changedAt))
        assertSame(list, list.ticked("eggs", bought = true, at = 300))
        assertSame(list, list.ticked("milk", bought = true, at = 300))
        assertSame(list, list.ticked("no such item", bought = true, at = 300))
    }

    @Test
    fun removingTakesANeededOrBoughtItemOffTheList() {
        val list = shopping("Milk", "Eggs").tick("milk", now = 200).remove("milk", now = 300).remove("eggs", now = 300)

        assertEquals(Status.REMOVED, list["milk"]?.status)
        assertEquals(Status.REMOVED, list["eggs"]?.status)
        assertTrue(list.needed.isEmpty() && list.bought.isEmpty())
        assertSame(list, list.remove("milk", now = 400))
        assertSame(list, list.tick("eggs", now = 400))
        assertSame(list, list.untick("milk", now = 400))
    }

    @Test
    fun restoringPutsItemsBackAsTheyWere() {
        val before = shopping("Milk", "Eggs", "Bread").tick("eggs", now = 200)
        val taken = listOf(before["milk"]!!, before["eggs"]!!)
        val after = before.remove("milk", now = 300).remove("eggs", now = 301).restore(taken, now = 400)

        assertEquals(listOf("Milk", "Bread"), after.names())
        assertEquals(listOf("Eggs"), after.boughtNames())
        assertEquals(400L, after["milk"]?.changedAt)
    }

    @Test
    fun restoringLeavesAloneAnItemAlreadyBackOnTheList() {
        val before = shopping("Milk")
        val readded = before.remove("milk", now = 200).add("Milk", now = 300, amount = twoLitres)

        assertSame(readded, readded.restore(listOf(before["milk"]!!), now = 400))
    }

    @Test
    fun editingChangesTheSpellingAndAmountOfTheSameItem() {
        val list = shopping("milk").edit("milk", "Milk", twoLitres, now = 200)

        assertEquals(Item("milk", "Milk", 2.0, "L", Status.NEEDED, addedAt = 100, changedAt = 200), list["milk"])
        assertSame(list, list.edit("milk", "Milk", twoLitres, now = 300))
        assertSame(list, list.edit("milk", "  ", Amount.NONE, now = 300))
        assertSame(list, list.edit("no such item", "Eggs", Amount.NONE, now = 300))
        assertEquals(Amount.NONE, list.edit("milk", "Milk", Amount.NONE, now = 300)["milk"]?.amount)
    }

    @Test
    fun onlyANeededItemCanBeEdited() {
        val list = shopping("Milk").tick("milk", now = 200)

        assertSame(list, list.edit("milk", "Oat milk", Amount.NONE, now = 300))
    }

    @Test
    fun editingTheNameReplacesTheItemInItsPlace() {
        val list = shopping("Mlik", "Eggs").edit("mlik", "Milk", Amount.NONE, now = 300)

        assertEquals(Status.REMOVED, list["mlik"]?.status)
        assertEquals(Item("milk", "Milk", status = Status.NEEDED, addedAt = 100, changedAt = 300), list["milk"])
        assertEquals(listOf("Milk", "Eggs"), list.names())
    }

    @Test
    fun editingANameIntoOneAlreadyOnTheListMakesThemOneItem() {
        val list = shopping("Milk", "Mlik").add("Milk", now = 150, amount = Amount(1.0)).edit("mlik", "milk", Amount.NONE, now = 300)

        assertEquals(listOf("Milk"), list.names())
        assertEquals(Amount(1.0), list["milk"]?.amount)
        assertEquals(Amount(3.0), shopping("Milk", "Mlik").edit("mlik", "milk", Amount(3.0), now = 300)["milk"]?.amount)
    }

    @Test
    fun suggestionsAreBoughtItemsWithAWordThatStartsWithWhatWasTyped() {
        val list = shopping("Oat milk", "Milk", "Mlik", "Eggs", "Almond milk")
            .tick("oat milk", now = 200)
            .tick("milk", now = 201)
            .tick("almond milk", now = 202)
            .remove("mlik", now = 301)

        assertEquals(listOf("Almond milk", "Milk", "Oat milk"), list.suggestions("mi"))
        assertEquals(listOf("Oat milk"), list.suggestions(" OAT "))
        assertEquals(listOf("Almond milk", "Milk"), list.suggestions("m", limit = 2))
        assertEquals(emptyList<String>(), list.suggestions("ilk"))
        assertEquals(emptyList<String>(), list.suggestions("  "))
        assertEquals(emptyList<String>(), list.suggestions("egg"))
    }

    @Test
    fun aChangeIsStampedLaterThanAnythingTheListHasSeen() {
        // The clock is behind the item's own time.
        val list = shopping("Milk").tick("milk", now = 5)

        assertEquals(101L, list["milk"]?.changedAt)
        assertEquals(102L, list.add("Eggs", now = 5)["eggs"]?.addedAt)
    }

    @Test
    fun aSyncedCopyHasNothingUnsentAndTakesWhatItReceives() {
        val list = shopping("Milk", "Eggs")
        val eggsTicked = list["eggs"]!!.copy(status = Status.BOUGHT, changedAt = 500)
        val bread = Item("bread", "Bread", status = Status.NEEDED, addedAt = 90, changedAt = 90)

        val synced = list.synced(sent = list.outgoing(), received = listOf(eggsTicked, bread), revision = 7)

        assertEquals(emptySet<String>(), synced.unsent)
        assertEquals(7L, synced.revision)
        assertEquals(listOf("Bread", "Milk"), synced.names())
        assertEquals(listOf("Eggs"), synced.boughtNames())
    }

    @Test
    fun aChangeMadeDuringASyncStaysUnsent() {
        val before = shopping("Milk")
        val sent = before.outgoing()
        val during = before.tick("milk", now = 200)

        // The server echoes what it was sent.
        val synced = during.synced(sent, received = sent, revision = 1)

        assertEquals(setOf("milk"), synced.unsent)
        assertEquals(Status.BOUGHT, synced["milk"]?.status)
    }

    @Test
    fun aChangeMadeDuringASyncGivesWayToALaterOneFromTheServer() {
        val before = shopping("Milk")
        val sent = before.outgoing()
        val during = before.tick("milk", now = 200)
        val theirs = before["milk"]!!.copy(status = Status.REMOVED, changedAt = 200)

        val synced = during.synced(sent, received = listOf(theirs), revision = 1)

        assertEquals(emptySet<String>(), synced.unsent)
        assertEquals(Status.REMOVED, synced["milk"]?.status)
    }

    @Test
    fun whatChangedSinceIsWhatStorageHasToWrite() {
        val before = shopping("Milk", "Eggs")
        val synced = before.synced(before.outgoing(), emptyList(), revision = 1)

        assertEquals(setOf("milk", "eggs"), synced.changedSince(before).map(Item::key).toSet())
        assertEquals(listOf("eggs"), synced.tick("eggs", now = 200).changedSince(synced).map(Item::key))
        assertEquals(emptyList<Item>(), synced.changedSince(synced))
    }

    @Test
    fun aCopyFromStorageIsTheCopyThatWasStored() {
        val list = shopping("Milk", "Eggs").synced(emptyList(), emptyList(), revision = 3)

        val read = ShoppingList.of(list.items, list.unsent + "gone", list.revision)

        assertEquals(list, read)
        assertEquals(list.hashCode(), read.hashCode())
        assertEquals(setOf("milk", "eggs"), read.unsent)
        assertFalse(list == list.tick("milk", now = 200))
        assertNull(read["bread"])
        assertTrue("milk" in read.toString())
    }
}
