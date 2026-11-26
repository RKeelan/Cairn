package com.rkeelan.cairn.core

import java.util.Locale

/**
 * How much of an item to buy: a [quantity], a [unit], both, or neither, as in "2 loaves", "500 g", "3" and "a box".
 *
 * A unit is a measure the list recognises, such as grams or cups, which it writes in one standard form, or any other word, which it keeps as typed.
 */
data class Amount(val quantity: Double? = null, val unit: String? = null) {
    init {
        require(quantity == null || (quantity > 0 && quantity < MAX_QUANTITY)) { "A quantity must be above zero and below $MAX_QUANTITY" }
        require(unit == null || (unit.isNotBlank() && unit.length <= MAX_UNIT_LENGTH)) { "A unit must be words, no longer than $MAX_UNIT_LENGTH" }
    }

    val isNone: Boolean get() = quantity == null && unit == null

    /** The amount as the list shows it, such as "2 cups", "0.5 kg" or "box", and empty for none. */
    override fun toString(): String {
        val number = quantity?.let(::format)
        val words = unit?.let { if (quantity != null && quantity != 1.0) plurals[it] ?: it else it }
        return listOfNotNull(number, words).joinToString(" ")
    }

    companion object {
        val NONE = Amount()

        /** The largest quantity, which the database also enforces. */
        const val MAX_QUANTITY = 100_000.0

        /** The longest unit, which the database also enforces. */
        const val MAX_UNIT_LENGTH = 20

        /**
         * The amount [text] describes: an optional number, as a whole number, a decimal or a fraction, and then an optional unit. A leading "a" or
         * "an" stands for no number, so "a box" is a box. A number too large, or not above zero, is left out.
         */
        fun parse(text: String): Amount {
            val tidied = tidy(text)
            if (tidied.isEmpty()) return NONE
            val match = pattern.matchEntire(tidied) ?: return NONE
            val quantity = match.groups["number"]?.value?.let(::number)?.takeIf { it > 0 && it < MAX_QUANTITY }
            val words = match.groups["unit"]?.value?.let { tidy(it, MAX_UNIT_LENGTH) }?.takeIf { it.isNotEmpty() }
            return Amount(quantity, words?.let(::unitOf))
        }

        /** The standard form of [typed] if it is a unit the list recognises, and otherwise [typed] itself. */
        fun unitOf(typed: String): String = units[typed.lowercase(Locale.ROOT)] ?: typed

        private val pattern = Regex(
            """(?:(?<number>\d+ \d+/\d+|\d+/\d+|\d+(?:[.,]\d+)?|[.,]\d+) ?|(?i:an?) )?(?<unit>\S.*)?""",
        )

        private fun number(text: String): Double? {
            val parts = text.split(" ")
            if (parts.size == 2) return number(parts[0])?.let { whole -> number(parts[1])?.let { whole + it } }
            val fraction = text.split("/")
            if (fraction.size == 2) {
                val denominator = fraction[1].toDouble()
                return if (denominator == 0.0) null else fraction[0].toDouble() / denominator
            }
            return text.replace(',', '.').toDoubleOrNull()
        }

        private fun format(quantity: Double): String {
            if (quantity % 1.0 == 0.0) return quantity.toLong().toString()
            return String.format(Locale.ROOT, "%.3f", quantity).trimEnd('0').trimEnd('.')
        }

        // Each recognised unit's standard form, and the ways of writing it that the list takes as that unit.
        private val spellings = mapOf(
            "g" to listOf("g", "gm", "gms", "gram", "grams", "gramme", "grammes"),
            "kg" to listOf("kg", "kgs", "kilo", "kilos", "kilogram", "kilograms", "kilogramme", "kilogrammes"),
            "mg" to listOf("mg", "milligram", "milligrams"),
            "ml" to listOf("ml", "mls", "millilitre", "millilitres", "milliliter", "milliliters"),
            "L" to listOf("l", "litre", "litres", "liter", "liters"),
            "lb" to listOf("lb", "lbs", "pound", "pounds"),
            "oz" to listOf("oz", "ounce", "ounces"),
            "cup" to listOf("cup", "cups"),
            "tbsp" to listOf("tbsp", "tbs", "tablespoon", "tablespoons"),
            "tsp" to listOf("tsp", "teaspoon", "teaspoons"),
        )

        private val units = spellings.flatMap { (unit, ways) -> ways.map { it to unit } }.toMap()

        // The recognised units that are words rather than symbols, which take a plural after any number but one.
        private val plurals = mapOf("cup" to "cups")
    }
}
