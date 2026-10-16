package com.example.diceroller.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChickenEngineTest {

    @Test
    fun depositAddsToBalance() {
        val wallet = Wallet(startingBalance = 50)
        wallet.deposit(100)
        assertEquals(150, wallet.balance)
    }

    @Test
    fun easyFirstMultiplierIsAbout103() {
        val table = multiplierTable(Difficulty.EASY)
        assertEquals(1.03, table[0], 0.01)
    }

    @Test
    fun mediumFirstMultiplierIsAbout115() {
        val table = multiplierTable(Difficulty.MEDIUM)
        assertEquals(1.15, table[0], 0.01)
    }

    @Test
    fun expertHasTenLanesAndHugeFinalMultiplier() {
        val table = multiplierTable(Difficulty.EXPERT)
        assertEquals(10, table.size)
        assertTrue(table.last() > 100_000.0)
    }

    @Test
    fun laterLanesAlwaysPayMore() {
        multiplierTable(Difficulty.HARD).zipWithNext().forEach { (a, b) ->
            assertTrue(b > a)
        }
    }

    @Test
    fun placingBetDeductsBalance() {
        val session = GameSession(Wallet(100))
        val round = session.placeBet(10, Difficulty.EASY, Random(1))
        assertEquals(90, session.wallet.balance)
        assertEquals(10, round.bet)
        assertEquals(RoundStatus.ACTIVE, round.status)
        assertEquals(0, round.stepsCleared)
    }

    @Test
    fun cannotBetMoreThanBalance() {
        val session = GameSession(Wallet(5))
        val error = runCatching { session.placeBet(10, Difficulty.EASY, Random(1)) }
        assertTrue(error.isFailure)
        assertEquals(5, session.wallet.balance)
    }

    @Test
    fun hopOntoHazardLosesTheBet() {
        val session = GameSession(Wallet(100))
        val round = session.placeBet(10, Difficulty.EXPERT, Random(0))
        var hit = false
        while (round.status == RoundStatus.ACTIVE) {
            if (round.hop() is HopResult.Hit) hit = true
        }
        if (hit) {
            assertEquals(RoundStatus.HIT, round.status)
            assertEquals(90, session.wallet.balance)
            assertEquals(0, round.payout)
        }
    }

    @Test
    fun cashOutCreditsBetTimesCurrentMultiplier() {
        val session = GameSession(Wallet(100))
        val hazards = BooleanArray(20) { false }
        hazards[5] = true
        val round = session.startRound(bet = 10, difficulty = Difficulty.EASY, hazards = hazards)
        repeat(3) {
            val result = round.hop()
            assertTrue(result is HopResult.Safe)
        }
        val paid = round.cashOut()
        val expected = kotlin.math.round(10 * multiplierTable(Difficulty.EASY)[2]).toInt()
        assertEquals(expected, paid)
        assertEquals(90 + expected, session.wallet.balance)
        assertEquals(RoundStatus.CASHED_OUT, round.status)
    }

    @Test
    fun cannotCashOutBeforeFirstHop() {
        val session = GameSession(Wallet(100))
        val round = session.placeBet(10, Difficulty.EASY, Random(1))
        val error = runCatching { round.cashOut() }
        assertTrue(error.isFailure)
        assertEquals(90, session.wallet.balance)
    }

    @Test
    fun clearingEverySafeLanePaysTheFinalMultiplier() {
        val session = GameSession(Wallet(100))
        val hazards = BooleanArray(20) { it >= 19 }
        val round = session.startRound(bet = 10, difficulty = Difficulty.EASY, hazards = hazards)
        while (round.status == RoundStatus.ACTIVE) {
            assertFalse(round.hop() is HopResult.Hit)
        }
        assertEquals(RoundStatus.CLEARED, round.status)
        val expected = kotlin.math.round(10 * multiplierTable(Difficulty.EASY).last()).toInt()
        assertEquals(expected, round.payout)
        assertEquals(90 + expected, session.wallet.balance)
    }

    @Test
    fun successiveHopsRaiseMultiplierUntilCashOut() {
        val session = GameSession(Wallet(50))
        val hazards = BooleanArray(20) { it == 19 }
        val round = session.startRound(bet = 10, difficulty = Difficulty.EASY, hazards = hazards)
        repeat(4) { assertTrue(round.hop() is HopResult.Safe) }
        assertEquals(4, round.stepsCleared)
        val first = multiplierTable(Difficulty.EASY)[0]
        val fourth = multiplierTable(Difficulty.EASY)[3]
        assertTrue(fourth > first)
        val paid = round.cashOut()
        assertEquals(kotlin.math.round(10 * fourth).toInt(), paid)
    }

    @Test
    fun firstHopHitsWhenFirstTileIsHazard() {
        val session = GameSession(Wallet(40))
        val hazards = BooleanArray(20) { it == 0 || it == 10 || it == 19 }
        val round = session.startRound(bet = 10, difficulty = Difficulty.MEDIUM, hazards = hazards)
        assertEquals(HopResult.Hit, round.hop())
        assertEquals(RoundStatus.HIT, round.status)
        assertEquals(30, session.wallet.balance)
    }

    @Test
    fun shuffleHazardsPlacesExactDeathCountForEveryDifficulty() {
        repeat(250) { seed ->
            Difficulty.entries.forEach { difficulty ->
                val hazards = shuffleHazards(difficulty.deaths, Random(seed.toLong()))
                assertEquals(TILE_COUNT, hazards.size)
                assertEquals(difficulty.deaths, hazards.count { it })
            }
        }
    }

    @Test
    fun previewPayoutMatchesCashOutWithoutChangingState() {
        val session = GameSession(Wallet(100))
        val hazards = BooleanArray(20) { it >= 19 }
        val round = session.startRound(bet = 10, difficulty = Difficulty.EASY, hazards = hazards)
        repeat(2) { assertTrue(round.hop() is HopResult.Safe) }
        val preview = round.previewPayout()
        assertEquals(RoundStatus.ACTIVE, round.status)
        assertEquals(90, session.wallet.balance)
        val paid = round.cashOut()
        assertEquals(paid, preview)
        assertEquals(kotlin.math.round(10 * multiplierTable(Difficulty.EASY)[1]).toInt(), preview)
    }
}
