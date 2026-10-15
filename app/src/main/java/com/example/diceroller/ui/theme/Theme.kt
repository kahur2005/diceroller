package com.example.diceroller.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CrossingColors = darkColorScheme(
    primary = GoGreen,
    secondary = CashAmber,
    tertiary = Sodium,
    background = Screen,
    surface = Panel,
    onPrimary = Color(0xFF062113),
    onSecondary = Color(0xFF2A1D00),
    onBackground = Ticket,
    onSurface = Ticket
)

@Composable
fun DiceRollerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CrossingColors,
        typography = Typography,
        content = content
    )
}
