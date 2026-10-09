package com.example.diceroller.game

fun formatChips(amount: Int): String = "$amount"

fun formatMultiplier(value: Double): String {
    return when {
        value >= 100_000 -> "${value.toInt()}×"
        value >= 1_000 -> String.format("%.1fk×", value / 1_000.0)
        value >= 100 -> String.format("%.0f×", value)
        else -> String.format("%.2f×", value)
    }
}

fun payoutFor(bet: Int, multiplier: Double): Int =
    kotlin.math.round(bet * multiplier).toInt()
