package com.example.diceroller

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.diceroller.ui.ChickenRoadScreen
import com.example.diceroller.ui.theme.DiceRollerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CrossingUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun depositBetAndGoResolvesALane() {
        compose.setContent {
            DiceRollerTheme {
                ChickenRoadScreen()
            }
        }

        compose.onNodeWithText("Crossing").assertExists()
        compose.onNodeWithTag("deposit_100").performClick()
        compose.waitForIdle()
        assertTrue(statusText().contains("Deposited"))

        compose.onNodeWithTag("primary_action").performClick()
        compose.waitForIdle()
        assertTrue(statusText().contains("Bet locked"))

        compose.onNodeWithTag("primary_action").performClick()
        compose.waitUntil(timeoutMillis = 8_000) {
            val text = statusText()
            text.contains("Safe at") || text.contains("Hit by a car") || text.contains("Far side")
        }

        if (statusText().contains("Safe at")) {
            compose.onNodeWithTag("cash_out").performClick()
            compose.waitForIdle()
            assertTrue(statusText().contains("Cashed out"))
        }
    }

    private fun statusText(): String {
        val node = compose.onNodeWithTag("status").fetchSemanticsNode()
        return node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString()
    }
}
