package com.example.diceroller.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diceroller.game.Difficulty
import com.example.diceroller.game.GameSession
import com.example.diceroller.game.HopResult
import com.example.diceroller.game.Round
import com.example.diceroller.game.RoundStatus
import com.example.diceroller.game.Wallet
import com.example.diceroller.game.formatChips
import com.example.diceroller.game.formatMultiplier
import com.example.diceroller.game.multiplierTable
import com.example.diceroller.game.payoutFor
import com.example.diceroller.ui.theme.Booth
import com.example.diceroller.ui.theme.CashAmber
import com.example.diceroller.ui.theme.ChickenYellow
import com.example.diceroller.ui.theme.FeltDeep
import com.example.diceroller.ui.theme.GoGreen
import com.example.diceroller.ui.theme.Ink
import com.example.diceroller.ui.theme.Ticket
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun ChickenRoadScreen(modifier: Modifier = Modifier) {
    val session = remember { GameSession(Wallet(100)) }
    var tick by remember { mutableIntStateOf(0) }
    var bet by remember { mutableIntStateOf(10) }
    var difficulty by remember { mutableStateOf(Difficulty.EASY) }
    var round by remember { mutableStateOf<Round?>(null) }
    var message by remember { mutableStateOf("Add chips, lock a bet, then send the chicken right.") }
    var busy by remember { mutableStateOf(false) }
    var chickenLane by remember { mutableIntStateOf(0) }
    var hopProgress by remember { mutableStateOf(0f) }
    var hitLane by remember { mutableStateOf<Int?>(null) }
    var carHitProgress by remember { mutableStateOf(0f) }
    var squash by remember { mutableStateOf(0f) }
    val hop = remember { Animatable(0f) }
    val slam = remember { Animatable(0f) }
    val flatten = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    tick
    val active = round
    val table = active?.multipliers ?: multiplierTable(difficulty)
    val inRound = active?.status == RoundStatus.ACTIVE
    val roundOver = active != null && active.status != RoundStatus.ACTIVE
    val liveMultiplier = when {
        active?.status == RoundStatus.ACTIVE && active.stepsCleared > 0 -> active.currentMultiplier
        active?.status == RoundStatus.ACTIVE -> active.nextLaneMultiplier() ?: table.first()
        else -> table.first()
    }

    fun bump() {
        tick++
    }

    fun resetBoard() {
        chickenLane = 0
        hopProgress = 0f
        hitLane = null
        carHitProgress = 0f
        squash = 0f
        scope.launch {
            hop.snapTo(0f)
            slam.snapTo(0f)
            flatten.snapTo(0f)
        }
    }

    fun placeOrReset() {
        if (busy) return
        if (session.wallet.balance < bet) {
            message = "Not enough chips. Add more to bet ${formatChips(bet)}."
            bump()
            return
        }
        resetBoard()
        round = session.placeBet(bet, difficulty, Random.Default)
        message = "Bet locked. Press Go — the chicken hops right."
        bump()
    }

    fun go() {
        val current = round ?: return
        if (busy || current.status != RoundStatus.ACTIVE) return
        busy = true
        val result = current.hop()
        val targetLane = if (result is HopResult.Safe) current.stepsCleared else chickenLane + 1
        scope.launch {
            hop.snapTo(0f)
            hop.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) {
                hopProgress = value
            }
            if (result is HopResult.Hit) {
                hitLane = targetLane
                message = "Hit by a car. Bet lost."
                slam.snapTo(0f)
                slam.animateTo(1f, tween(240)) { carHitProgress = value }
                flatten.snapTo(0f)
                flatten.animateTo(1f, tween(180)) { squash = value }
                hopProgress = 1f
            } else {
                chickenLane = current.stepsCleared
                hopProgress = 0f
                hop.snapTo(0f)
                message = when (current.status) {
                    RoundStatus.CLEARED -> "Made it across. Paid ${formatChips(current.payout)} chips."
                    else -> "Safe at ${formatMultiplier(current.currentMultiplier)}. Cash out or keep going."
                }
            }
            busy = false
            bump()
        }
    }

    fun cashOut() {
        val current = round ?: return
        if (busy || current.status != RoundStatus.ACTIVE || current.stepsCleared == 0) return
        val paid = current.cashOut()
        message = "Cashed out ${formatChips(paid)} chips."
        bump()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF14241A), FeltDeep))
            )
    ) {
        TopBar(
            balance = session.wallet.balance,
            onDeposit = { amount ->
                session.wallet.deposit(amount)
                message = "Deposited ${formatChips(amount)} chips."
                bump()
            }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .shadow(12.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .border(2.dp, Color(0xFF3A5A44), RoundedCornerShape(18.dp))
        ) {
            RoadPlayfield(
                maxSteps = difficulty.maxSteps,
                multipliers = table,
                chickenLane = chickenLane,
                hopProgress = hopProgress,
                hitLane = hitLane,
                carHitProgress = carHitProgress,
                squash = squash
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                color = Ticket,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .weight(1f)
                    .testTag("status")
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(ChickenYellow)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = formatMultiplier(liveMultiplier),
                    color = Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        ControlTray(
            bet = bet,
            difficulty = difficulty,
            round = active,
            inRound = inRound,
            roundOver = roundOver,
            busy = busy,
            canGo = inRound && !busy,
            canCash = inRound && !busy && (active?.stepsCleared ?: 0) > 0,
            onBetChange = { bet = it.coerceAtLeast(1) },
            onDifficulty = {
                if (!inRound) {
                    difficulty = it
                    resetBoard()
                    round = null
                    bump()
                }
            },
            onBet = { placeOrReset() },
            onGo = { go() },
            onCashOut = { cashOut() }
        )
    }
}

@Composable
private fun TopBar(balance: Int, onDeposit: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Crossing",
                color = ChickenYellow,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Serif
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Ticket)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${formatChips(balance)} chips",
                    color = Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("balance")
                )
            }
        }
        Row {
            DepositChip(label = "+50", tag = "deposit_50") { onDeposit(50) }
            Spacer(Modifier.width(8.dp))
            DepositChip(label = "+100", tag = "deposit_100") { onDeposit(100) }
        }
    }
}

@Composable
private fun DepositChip(label: String, tag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .testTag(tag)
            .clip(RoundedCornerShape(14.dp))
            .background(Booth)
            .border(1.dp, Color(0xFF4A6A56), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = Ticket, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun ControlTray(
    bet: Int,
    difficulty: Difficulty,
    round: Round?,
    inRound: Boolean,
    roundOver: Boolean,
    busy: Boolean,
    canGo: Boolean,
    canCash: Boolean,
    onBetChange: (Int) -> Unit,
    onDifficulty: (Difficulty) -> Unit,
    onBet: () -> Unit,
    onGo: () -> Unit,
    onCashOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Booth)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Difficulty.entries.forEach { level ->
                val selected = difficulty == level
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) ChickenYellow else Color(0xFF1A2C22))
                        .clickable(enabled = !inRound && !busy) { onDifficulty(level) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = level.label,
                        color = if (selected) Ink else Ticket,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Stepper(enabled = !inRound && !busy, onClick = { onBetChange(bet / 2) }, label = "½")
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Bet", color = Ticket.copy(alpha = 0.7f), fontSize = 11.sp)
                Text(formatChips(bet), color = Ticket, fontWeight = FontWeight.Black, fontSize = 22.sp)
            }
            Spacer(Modifier.width(8.dp))
            Stepper(enabled = !inRound && !busy, onClick = { onBetChange(bet * 2) }, label = "2×")
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onCashOut,
                enabled = canCash,
                modifier = Modifier.testTag("cash_out"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CashAmber,
                    contentColor = Ink,
                    disabledContainerColor = Color(0xFF3A4A32),
                    disabledContentColor = Color(0xFF8A9A82)
                )
            ) {
                val cashLabel = if (canCash && round != null) {
                    "Cash ${formatChips(payoutFor(round.bet, round.currentMultiplier))}"
                } else {
                    "Cash out"
                }
                Text(cashLabel, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(10.dp))

        val primaryLabel = when {
            roundOver -> "New bet"
            inRound -> "Go"
            else -> "Bet"
        }
        Button(
            onClick = { if (inRound) onGo() else onBet() },
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(
                containerColor = GoGreen,
                contentColor = Ink
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("primary_action"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(primaryLabel, fontWeight = FontWeight.Black, fontSize = 22.sp)
        }
    }
}

@Composable
private fun Stepper(enabled: Boolean, onClick: () -> Unit, label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1A2C22))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, color = Ticket, fontWeight = FontWeight.Bold)
    }
}
