package com.example.diceroller.game

import java.util.Locale

fun formatChips(amount: Int): String = "$amount"

fun formatMultiplier(value: Double): String {
    return when {
        value >= 100_000 -> "${value.toInt()}x"
        value >= 1_000 -> String.format(Locale.US, "%.1fkx", value / 1_000.0)
        value >= 100 -> String.format(Locale.US, "%.0fx", value)
        else -> String.format(Locale.US, "%.2fx", value)
    }
}

fun payoutFor(bet: Int, multiplier: Double): Int =
    kotlin.math.round(bet * multiplier).toInt()
