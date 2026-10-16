package com.example.diceroller.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.diceroller.ui.theme.CashFace
import com.example.diceroller.ui.theme.CashInk
import com.example.diceroller.ui.theme.Field
import com.example.diceroller.ui.theme.GoBright
import com.example.diceroller.ui.theme.Ink
import com.example.diceroller.ui.theme.Muted
import com.example.diceroller.ui.theme.Panel
import com.example.diceroller.ui.theme.Paper
import com.example.diceroller.ui.theme.Road
import com.example.diceroller.ui.theme.Screen
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.random.Random

private val FieldShape = RoundedCornerShape(10.dp)
private val FieldLine = Color(0xFF2A3544)
private val ChipFace = Color(0xFF2C384A)

@Composable
fun ChickenRoadScreen(modifier: Modifier = Modifier) {
    val session = remember { GameSession(Wallet(100)) }
    var tick by remember { mutableIntStateOf(0) }
    var bet by remember { mutableIntStateOf(10) }
    var difficulty by remember { mutableStateOf(Difficulty.MEDIUM) }
    var auto by remember { mutableStateOf(false) }
    var autoRunning by remember { mutableStateOf(false) }
    var runId by remember { mutableIntStateOf(0) }
    var round by remember { mutableStateOf<Round?>(null) }
    var message by remember { mutableStateOf("Set a bet, then press Go.") }
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
    val locked = inRound || busy || autoRunning

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

    suspend fun animateHop(current: Round) {
        val result = current.hop()
        val standing = chickenLane
        hop.snapTo(0f)
        hop.animateTo(
            1f,
            keyframes {
                durationMillis = 560
                0f at 0
                0.10f at 100 using FastOutLinearInEasing
                0.62f at 300 using FastOutSlowInEasing
                1f at 560 using LinearOutSlowInEasing
            }
        ) {
            hopProgress = value
        }
        if (result is HopResult.Hit) {
            hitLane = standing + 1
            message = "Hit by a car. Bet lost."
            slam.snapTo(0f)
            flatten.snapTo(0f)
            coroutineScope {
                val slamJob = launch {
                    slam.animateTo(1f, tween(340, easing = FastOutLinearInEasing)) {
                        carHitProgress = value
                    }
                }
                delay(120)
                flatten.animateTo(
                    1f,
                    spring(dampingRatio = 0.42f, stiffness = 520f)
                ) {
                    squash = value
                }
                slamJob.join()
            }
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
    }

    fun placeOrReset() {
        if (busy || autoRunning) return
        if (session.wallet.balance < bet) {
            message = "Not enough chips. Add more to bet ${formatChips(bet)}."
            bump()
            return
        }
        val id = ++runId
        resetBoard()
        round = session.placeBet(bet, difficulty, Random.Default)
        message = if (auto) "Bet locked. Auto is running." else "Bet locked. Press Go — the chicken hops right."
        bump()
        if (!auto) return
        autoRunning = true
        scope.launch {
            delay(420)
            while (runId == id) {
                val live = round
                if (live == null || live.status != RoundStatus.ACTIVE) break
                busy = true
                animateHop(live)
                busy = false
                bump()
                if (live.status != RoundStatus.ACTIVE) break
                delay(700)
            }
            if (runId == id) autoRunning = false
        }
    }

    fun go() {
        val current = round ?: return
        if (busy || autoRunning || current.status != RoundStatus.ACTIVE) return
        busy = true
        scope.launch {
            animateHop(current)
            busy = false
            bump()
        }
    }

    fun cashOut() {
        val current = round ?: return
        if (busy || current.status != RoundStatus.ACTIVE || current.stepsCleared == 0) return
        runId++
        autoRunning = false
        val paid = current.cashOut()
        message = "Cashed out ${formatChips(paid)} chips."
        bump()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Screen)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Road)
                .border(1.dp, Color(0xFF314155), RoundedCornerShape(16.dp))
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

        Text(
            text = message,
            color = Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("status")
        )

        ControlTray(
            bet = bet,
            balance = session.wallet.balance,
            difficulty = difficulty,
            auto = auto,
            inRound = inRound,
            locked = locked,
            canGo = !busy && !autoRunning,
            canCash = inRound && !busy && (active?.stepsCleared ?: 0) > 0,
            cashPreview = active?.previewPayout() ?: 0,
            nextMultiplier = active?.nextLaneMultiplier(),
            onBetChange = { bet = it.coerceAtLeast(1) },
            onDifficulty = {
                if (!locked) {
                    difficulty = it
                    resetBoard()
                    round = null
                    bump()
                }
            },
            onAuto = { if (!locked) auto = it },
            onDeposit = { amount ->
                session.wallet.deposit(amount)
                message = "Deposited ${formatChips(amount)} chips."
                bump()
            },
            onBet = { placeOrReset() },
            onGo = { go() },
            onCashOut = { cashOut() }
        )
    }
}

@Composable
private fun ControlTray(
    bet: Int,
    balance: Int,
    difficulty: Difficulty,
    auto: Boolean,
    inRound: Boolean,
    locked: Boolean,
    canGo: Boolean,
    canCash: Boolean,
    cashPreview: Int,
    nextMultiplier: Double?,
    onBetChange: (Int) -> Unit,
    onDifficulty: (Difficulty) -> Unit,
    onAuto: (Boolean) -> Unit,
    onDeposit: (Int) -> Unit,
    onBet: () -> Unit,
    onGo: () -> Unit,
    onCashOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Bet Amount",
                color = Paper,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatBalance(balance),
                    color = Paper,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("balance")
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DepositChip(label = "+50", tag = "deposit_50") { onDeposit(50) }
                    DepositChip(label = "+100", tag = "deposit_100") { onDeposit(100) }
                }
            }
        }

        Spacer(Modifier.height(1.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            ModeChip(label = "Manual", selected = !auto, enabled = !locked) { onAuto(false) }
            Spacer(Modifier.width(8.dp))
            ModeChip(label = "Auto", selected = auto, enabled = !locked) { onAuto(true) }
        }

        Spacer(Modifier.height(1.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(FieldShape)
                    .background(Field)
                    .border(1.dp, FieldLine, FieldShape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = String.format(Locale.US, "%.2f", bet.toDouble()),
                    color = Paper,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Coin()
            }
            Spacer(Modifier.width(10.dp))
            Stepper(enabled = !locked, label = "½") { onBetChange(bet / 2) }
            Spacer(Modifier.width(8.dp))
            Stepper(enabled = !locked, label = "2×") { onBetChange(bet * 2) }
        }

        Spacer(Modifier.height(10.dp))

        DifficultyMenu(
            difficulty = difficulty,
            enabled = !locked,
            onDifficulty = onDifficulty
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { if (inRound) onGo() else onBet() },
            enabled = canGo,
            colors = ButtonDefaults.buttonColors(
                containerColor = GoBright,
                contentColor = Paper,
                disabledContainerColor = GoBright.copy(alpha = 0.66f),
                disabledContentColor = Paper.copy(alpha = 0.66f)
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                disabledElevation = 0.dp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("primary_action"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (inRound && nextMultiplier != null) {
                    "Go  ${formatMultiplier(nextMultiplier)}"
                } else {
                    "Go"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onCashOut,
            enabled = canCash,
            colors = ButtonDefaults.buttonColors(
                containerColor = Paper,
                contentColor = Ink,
                disabledContainerColor = CashFace,
                disabledContentColor = CashInk
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                disabledElevation = 0.dp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("cash_out"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (cashPreview > 0) {
                    "Cash Out  ${formatChips(cashPreview)}"
                } else {
                    "Cash Out"
                },
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun DifficultyMenu(
    difficulty: Difficulty,
    enabled: Boolean,
    onDifficulty: (Difficulty) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) {
        if (!enabled) expanded = false
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(FieldShape)
                .background(Field)
                .border(1.dp, FieldLine, FieldShape)
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${difficulty.label}  ·  ${difficulty.deaths} cars",
                color = if (enabled) Paper else Muted,
                fontSize = 15.sp
            )
            Text(text = "▾", color = Muted, fontSize = 14.sp)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Color(0xFF1C2636)
        ) {
            Difficulty.entries.forEach { level ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "${level.label}  ·  ${level.deaths} cars",
                            color = if (level == difficulty) GoBright else Paper
                        )
                    },
                    onClick = {
                        onDifficulty(level)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) ChipFace else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Paper else Muted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun DepositChip(label: String, tag: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = GoBright,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        modifier = Modifier
            .testTag(tag)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun Coin() {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(GoBright),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .border(1.5.dp, Ink.copy(alpha = 0.35f), CircleShape)
        )
    }
}

@Composable
private fun Stepper(enabled: Boolean, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) Paper else Muted,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
    }
}

private fun formatBalance(amount: Int): String =
    "Balance: $" + String.format(Locale.US, "%,.2f", amount.toDouble())
