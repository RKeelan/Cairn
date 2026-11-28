package com.rkeelan.cairn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountTest {
    @Test
    fun aNumberAndAUnitAreEachOptional() {
        assertEquals(Amount(2.0, "loaves"), Amount.parse("2 loaves"))
        assertEquals(Amount(3.0), Amount.parse(" 3 "))
        assertEquals(Amount(unit = "box"), Amount.parse("box"))
        assertEquals(Amount.NONE, Amount.parse("   "))
        assertTrue(Amount.parse("").isNone)
        assertFalse(Amount(unit = "box").isNone)
    }

    @Test
    fun aOrAnStandsForNoNumber() {
        assertEquals(Amount(unit = "box"), Amount.parse("a box"))
        assertEquals(Amount(unit = "onion bag"), Amount.parse("An onion bag"))
        assertEquals(Amount(unit = "apple"), Amount.parse("apple"))
    }

    @Test
    fun numbersMayBeDecimalsOrFractions() {
        assertEquals(Amount(1.5, "L"), Amount.parse("1.5 L"))
        assertEquals(Amount(1.5, "L"), Amount.parse("1,5 l"))
        assertEquals(Amount(0.5, "kg"), Amount.parse(".5kg"))
        assertEquals(Amount(0.5, "cup"), Amount.parse("1/2 cup"))
        assertEquals(Amount(2.25, "cup"), Amount.parse("2 1/4 cups"))
    }

    @Test
    fun aNumberOutOfRangeIsLeftOut() {
        assertEquals(Amount(unit = "g"), Amount.parse("0 g"))
        assertEquals(Amount(unit = "g"), Amount.parse("1000000 g"))
        assertEquals(Amount(unit = "cup"), Amount.parse("1/0 cup"))
    }

    @Test
    fun recognisedUnitsTakeTheirStandardForm() {
        assertEquals(Amount(500.0, "g"), Amount.parse("500 grams"))
        assertEquals(Amount(500.0, "g"), Amount.parse("500g"))
        assertEquals(Amount(2.0, "lb"), Amount.parse("2 LBS"))
        assertEquals(Amount(1.0, "L"), Amount.parse("1 litre"))
        assertEquals(Amount(250.0, "ml"), Amount.parse("250 mL"))
        assertEquals("tbsp", Amount.unitOf("Tablespoons"))
        assertEquals("Loaves", Amount.unitOf("Loaves"))
    }

    @Test
    fun aLongUnitIsCut() {
        assertEquals(Amount.MAX_UNIT_LENGTH, Amount.parse("2 " + "x".repeat(50)).unit?.length)
    }

    @Test
    fun anAmountIsShownWithItsNumberAndUnit() {
        assertEquals("2 loaves", Amount(2.0, "loaves").toString())
        assertEquals("500 g", Amount(500.0, "g").toString())
        assertEquals("0.5 kg", Amount(0.5, "kg").toString())
        assertEquals("0.333", Amount(1.0 / 3).toString())
        assertEquals("3", Amount(3.0).toString())
        assertEquals("box", Amount(unit = "box").toString())
        assertEquals("", Amount.NONE.toString())
    }

    @Test
    fun aWordUnitIsPluralAfterAnyNumberButOne() {
        assertEquals("1 cup", Amount(1.0, "cup").toString())
        assertEquals("2 cups", Amount(2.0, "cup").toString())
        assertEquals("0.5 cups", Amount(0.5, "cup").toString())
        assertEquals("cup", Amount(unit = "cup").toString())
    }

    @Test
    fun anAmountTypedAfterANameIsSplitFromIt() {
        assertEquals("bread" to Amount(2.0, "loaves"), Amount.split("bread 2 loaves"))
        assertEquals("milk" to Amount(2.0, "L"), Amount.split("milk 2 L"))
        assertEquals("ground pork" to Amount(500.0, "g"), Amount.split("ground pork 500g"))
        assertEquals("flour" to Amount(2.5, "cup"), Amount.split("flour 2 1/2 cups"))
        assertEquals("eggs" to Amount(12.0), Amount.split("eggs 12"))
        assertEquals("butter" to Amount(0.5, "lb"), Amount.split("butter .5 lb"))
        assertEquals("cream" to Amount(1.5, "L"), Amount.split("cream 1,5L"))
        assertEquals("flour" to Amount(2.5, "cup"), Amount.split("flour 2 1/2cups"))
        assertEquals("oat milk" to Amount(1.0, "L"), Amount.split("  oat   milk  1 L "))
    }

    @Test
    fun textWithNoAmountAfterANameIsAllName() {
        assertEquals("7 up" to Amount.NONE, Amount.split("7 up"))
        assertEquals("2 percent milk" to Amount.NONE, Amount.split("2 percent milk"))
        assertEquals("ten percent cream" to Amount.NONE, Amount.split("ten percent cream"))
        assertEquals("12" to Amount.NONE, Amount.split("12"))
        assertEquals("chips 2 big bags" to Amount.NONE, Amount.split("chips 2 big bags"))
        assertEquals("rice a bag" to Amount.NONE, Amount.split("rice a bag"))
        assertEquals("vitamin d 100000" to Amount.NONE, Amount.split("vitamin d 100000"))
        assertEquals("eggs 0" to Amount.NONE, Amount.split("eggs 0"))
        assertEquals("tea 1/0" to Amount.NONE, Amount.split("tea 1/0"))
        assertEquals("beans 500g tin" to Amount.NONE, Amount.split("beans 500g tin"))
        assertEquals("diet 7-up" to Amount.NONE, Amount.split("diet 7-up"))
        assertEquals("x 2." to Amount.NONE, Amount.split("x 2."))
        assertEquals("x 1/2/3" to Amount.NONE, Amount.split("x 1/2/3"))
        assertEquals("" to Amount.NONE, Amount.split("  "))
    }

    @Test
    fun onlyTheLastNumberIsTakenWhenMoreThanOneEndsTheText() {
        // A known limitation: "in" is taken for part of the name, and the name can be corrected by editing the item.
        assertEquals("pantene 2 in" to Amount(1.0), Amount.split("pantene 2 in 1"))
        assertEquals("paper 2" to Amount(3.0), Amount.split("paper 2 3"))
    }

    @Test
    fun anAmountTheDatabaseWouldRefuseCannotBeMade() {
        assertThrows(IllegalArgumentException::class.java) { Amount(0.0) }
        assertThrows(IllegalArgumentException::class.java) { Amount(Amount.MAX_QUANTITY) }
        assertThrows(IllegalArgumentException::class.java) { Amount(unit = " ") }
        assertThrows(IllegalArgumentException::class.java) { Amount(unit = "x".repeat(21)) }
    }
}
