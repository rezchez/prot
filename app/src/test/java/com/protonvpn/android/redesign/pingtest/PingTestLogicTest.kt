/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.redesign.pingtest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PingTestLogicTest {

    @Test
    fun alphabeticalSortingIsCaseInsensitiveAndDeterministic() {
        val serverNames = listOf("us-free#10", "DE#1", "ch#2", "US-FREE#2", "nl#1", "AT#5")
        val sorted = serverNames.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })

        val expected = listOf("AT#5", "ch#2", "DE#1", "nl#1", "us-free#10", "US-FREE#2")
        assertEquals(expected, sorted)
    }

    @Test
    fun attemptsValidationAcceptsValidIntegersAndRejectsInvalid() {
        fun validate(input: String): Pair<Int?, String?> {
            val trimmed = input.trim()
            val parsed = trimmed.toIntOrNull()
            val error = when {
                trimmed.isEmpty() -> "Please enter a value"
                parsed == null || parsed < 1 || parsed > 10 -> "Enter a number between 1 and 10"
                else -> null
            }
            return (if (error == null) parsed else null) to error
        }

        // Valid
        assertEquals(1, validate("1").first)
        assertNull(validate("1").second)
        assertEquals(5, validate("5").first)
        assertNull(validate("5").second)
        assertEquals(10, validate("10").first)
        assertNull(validate("10").second)

        // Invalid: zero, negative, out of bounds, decimal, text
        assertNull(validate("0").first)
        assertNotNull(validate("0").second)

        assertNull(validate("-1").first)
        assertNotNull(validate("-1").second)

        assertNull(validate("11").first)
        assertNotNull(validate("11").second)

        assertNull(validate("1.5").first)
        assertNotNull(validate("1.5").second)

        assertNull(validate("abc").first)
        assertNotNull(validate("abc").second)

        assertNull(validate("").first)
        assertNotNull(validate("").second)
    }

    @Test
    fun estimatedDurationCalculationFormat() {
        val state1 = PingTestUiState(totalCount = 10, attemptsPerServer = 1)
        // 10 * 1 * 10 = 100s -> 1m 40s
        assertEquals("1m 40s", state1.estimatedDurationText)

        val state2 = PingTestUiState(totalCount = 2, attemptsPerServer = 2)
        // 2 * 2 * 10 = 40s
        assertEquals("40s", state2.estimatedDurationText)
    }

    @Test
    fun serverNumberExtractionFromNames() {
        fun extractServerNumber(name: String): String {
            val num = name.substringAfterLast('#', "")
            return if (num.isNotEmpty()) "#$num" else ""
        }

        assertEquals("#10", extractServerNumber("US-FREE#10"))
        assertEquals("#2", extractServerNumber("CH#2"))
        assertEquals("#105", extractServerNumber("nl-free#105"))
        assertEquals("", extractServerNumber("SpecialServer"))
    }

    @Test
    fun countryFilterSummaryCalculation() {
        val countries = listOf(
            PingCountryItem("US", "United States", 10),
            PingCountryItem("NL", "Netherlands", 5),
            PingCountryItem("DE", "Germany", 8)
        )

        val allState = PingTestUiState(
            availableCountries = countries,
            selectedCountryCodes = emptySet()
        )
        assertEquals("All countries (3)", allState.selectedCountriesSummary)

        val filteredState = PingTestUiState(
            availableCountries = countries,
            selectedCountryCodes = setOf("US", "NL"),
            totalCount = 15
        )
        assertEquals("2 of 3 countries (15 servers)", filteredState.selectedCountriesSummary)
    }
}
