package com.example.diceroller.game

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormatTest {

    @Test
    fun multiplierFormattingUsesDecimalPointRegardlessOfDeviceLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)

            assertEquals("1.15x", formatMultiplier(1.15))
            assertEquals("1.2kx", formatMultiplier(1_200.0))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
