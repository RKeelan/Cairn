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
    fun anAmountTheDatabaseWouldRefuseCannotBeMade() {
        assertThrows(IllegalArgumentException::class.java) { Amount(0.0) }
        assertThrows(IllegalArgumentException::class.java) { Amount(Amount.MAX_QUANTITY) }
        assertThrows(IllegalArgumentException::class.java) { Amount(unit = " ") }
        assertThrows(IllegalArgumentException::class.java) { Amount(unit = "x".repeat(21)) }
    }
}
