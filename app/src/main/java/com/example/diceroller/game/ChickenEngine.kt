package com.example.diceroller.game

import kotlin.math.round
import kotlin.random.Random

const val TILE_COUNT = 20
const val RTP = 0.98

enum class Difficulty(val deaths: Int) {
    EASY(1),
    MEDIUM(3),
    HARD(5),
    EXPERT(10);

    val maxSteps: Int get() = TILE_COUNT - deaths

    val label: String
        get() = when (this) {
            EASY -> "Easy"
            MEDIUM -> "Medium"
            HARD -> "Hard"
            EXPERT -> "Expert"
        }
}

enum class RoundStatus {
    ACTIVE,
    HIT,
    CASHED_OUT,
    CLEARED
}

sealed class HopResult {
    data class Safe(val multiplier: Double, val stepsCleared: Int) : HopResult()
    data object Hit : HopResult()
}

class Wallet(startingBalance: Int = 0) {
    var balance: Int = startingBalance
        private set

    fun deposit(amount: Int) {
        require(amount > 0) { "Deposit must be positive" }
        balance += amount
    }

    internal fun debit(amount: Int) {
        require(amount in 1..balance) { "Not enough chips" }
        balance -= amount
    }

    internal fun credit(amount: Int) {
        require(amount >= 0)
        balance += amount
    }
}

fun multiplierTable(difficulty: Difficulty): List<Double> {
    val deaths = difficulty.deaths
    val table = ArrayList<Double>(difficulty.maxSteps)
    var survival = 1.0
    for (k in 1..difficulty.maxSteps) {
        val remaining = TILE_COUNT - (k - 1)
        val safeRemaining = remaining - deaths
        survival *= safeRemaining.toDouble() / remaining
        table += RTP / survival
    }
    return table
}

fun shuffleHazards(deaths: Int, random: Random): BooleanArray {
    val tiles = (0 until TILE_COUNT).toMutableList()
    for (i in tiles.lastIndex downTo 1) {
        val j = random.nextInt(i + 1)
        val tmp = tiles[i]
        tiles[i] = tiles[j]
        tiles[j] = tmp
    }
    val hazards = BooleanArray(TILE_COUNT)
    repeat(deaths) { index ->
        hazards[tiles[index]] = true
    }
    return hazards
}

class GameSession(val wallet: Wallet) {
    fun placeBet(amount: Int, difficulty: Difficulty, random: Random): Round {
        require(amount > 0) { "Bet must be positive" }
        require(amount <= wallet.balance) { "Not enough chips" }
        wallet.debit(amount)
        return Round(this, amount, difficulty, shuffleHazards(difficulty.deaths, random))
    }

    fun startRound(bet: Int, difficulty: Difficulty, hazards: BooleanArray): Round {
        require(hazards.size == TILE_COUNT)
        require(hazards.count { it } == difficulty.deaths) {
            "Hazard count must match difficulty"
        }
        require(bet > 0)
        require(bet <= wallet.balance) { "Not enough chips" }
        wallet.debit(bet)
        return Round(this, bet, difficulty, hazards.copyOf())
    }
}

class Round(
    private val session: GameSession,
    val bet: Int,
    val difficulty: Difficulty,
    private val hazards: BooleanArray
) {
    val multipliers: List<Double> = multiplierTable(difficulty)

    var stepsCleared: Int = 0
        private set
    var status: RoundStatus = RoundStatus.ACTIVE
        private set
    var payout: Int = 0
        private set

    val currentMultiplier: Double
        get() = if (stepsCleared == 0) 0.0 else multipliers[stepsCleared - 1]

    fun nextLaneMultiplier(): Double? = multipliers.getOrNull(stepsCleared)

    fun hop(): HopResult {
        check(status == RoundStatus.ACTIVE)
        if (hazards[stepsCleared]) {
            status = RoundStatus.HIT
            payout = 0
            return HopResult.Hit
        }
        stepsCleared += 1
        val multiplier = multipliers[stepsCleared - 1]
        if (stepsCleared == difficulty.maxSteps) {
            status = RoundStatus.CLEARED
            payout = round(bet * multiplier).toInt()
            session.wallet.credit(payout)
        }
        return HopResult.Safe(multiplier, stepsCleared)
    }

    fun cashOut(): Int {
        check(status == RoundStatus.ACTIVE)
        check(stepsCleared > 0) { "Need a safe hop first" }
        payout = round(bet * currentMultiplier).toInt()
        status = RoundStatus.CASHED_OUT
        session.wallet.credit(payout)
        return payout
    }
}
