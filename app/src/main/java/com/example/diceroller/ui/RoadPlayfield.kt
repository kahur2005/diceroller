package com.example.diceroller.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.diceroller.game.formatMultiplier
import com.example.diceroller.ui.theme.Asphalt
import com.example.diceroller.ui.theme.AsphaltDark
import com.example.diceroller.ui.theme.Beak
import com.example.diceroller.ui.theme.ChickenShade
import com.example.diceroller.ui.theme.ChickenYellow
import com.example.diceroller.ui.theme.Comb
import com.example.diceroller.ui.theme.Crosswalk
import com.example.diceroller.ui.theme.Curb
import com.example.diceroller.ui.theme.GrassDark
import com.example.diceroller.ui.theme.GrassLight
import com.example.diceroller.ui.theme.LanePaint
import com.example.diceroller.ui.theme.SkyHorizon
import com.example.diceroller.ui.theme.SkyTop
import com.example.diceroller.ui.theme.TreeDark
import com.example.diceroller.ui.theme.TreeLight
import kotlin.math.PI
import kotlin.math.sin

private const val VisibleLanes = 6

private val CarPalette = listOf(
    Color(0xFFE85D4C),
    Color(0xFF3D8BFF),
    Color(0xFFFFC53D),
    Color(0xFF7A5CFF),
    Color(0xFFFF7A18),
    Color(0xFF2EC4B6)
)

@Composable
fun RoadPlayfield(
    modifier: Modifier = Modifier,
    maxSteps: Int,
    multipliers: List<Double>,
    chickenLane: Int,
    hopProgress: Float,
    hitLane: Int?,
    carHitProgress: Float,
    squash: Float
) {
    val measurer = rememberTextMeasurer()
    val traffic by rememberInfiniteTransition(label = "traffic").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cars"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val gutter = 8f
        val laneW = (size.width - gutter) / VisibleLanes
        val visualLane = chickenLane + hopProgress
        val maxCamera = (maxSteps + 2 - VisibleLanes).coerceAtLeast(0).toFloat()
        val camera = when {
            visualLane < 1.2f -> 0f
            else -> (visualLane - 1.2f).coerceIn(0f, maxCamera)
        }
        val walkY = size.height * 0.55f

        drawRect(
            Brush.verticalGradient(
                0f to SkyTop,
                0.42f to SkyHorizon,
                1f to GrassLight
            )
        )

        for (lane in 0..(maxSteps + 1)) {
            val left = gutter + (lane - camera) * laneW
            if (left > size.width || left + laneW < -4f) continue
            drawVerticalLane(
                lane = lane,
                maxSteps = maxSteps,
                multipliers = multipliers,
                left = left,
                width = laneW,
                measurer = measurer,
                traffic = traffic,
                hitLane = hitLane,
                carHitProgress = carHitProgress,
                chickenY = walkY,
                occupied = lane == chickenLane || (hopProgress > 0f && lane == chickenLane + 1),
                next = lane == chickenLane + 1 && hopProgress == 0f
            )
        }

        val chickenSize = laneW * 0.5f
        val chickenX = (gutter + (visualLane - camera) * laneW + laneW * 0.72f)
            .coerceAtLeast(chickenSize * 0.65f)
        val hopLift = sin(hopProgress * PI.toFloat()) * laneW * 0.3f
        drawChicken(
            center = Offset(chickenX, walkY - hopLift),
            hopTilt = hopProgress,
            squash = squash,
            sizePx = chickenSize
        )
    }
}

private fun DrawScope.drawVerticalLane(
    lane: Int,
    maxSteps: Int,
    multipliers: List<Double>,
    left: Float,
    width: Float,
    measurer: TextMeasurer,
    traffic: Float,
    hitLane: Int?,
    carHitProgress: Float,
    chickenY: Float,
    occupied: Boolean,
    next: Boolean
) {
    val isRoad = lane in 1..maxSteps
    if (!isRoad) {
        drawRect(
            if (lane == 0) GrassLight else GrassDark,
            Offset(left, 0f),
            Size(width, size.height)
        )
        drawRect(
            Curb,
            Offset(left + width * 0.08f, size.height * 0.48f),
            Size(width * 0.84f, size.height * 0.22f)
        )
        val tx = left + width * 0.5f
        val ty = size.height * 0.16f
        drawRoundRect(
            Color(0xFF6B3E1D),
            Offset(tx - 5f, ty + 10f),
            Size(10f, 16f),
            CornerRadius(2f, 2f)
        )
        drawCircle(TreeDark, width * 0.2f, Offset(tx, ty))
        drawCircle(TreeLight, width * 0.13f, Offset(tx - 5f, ty - 6f))
        val caption = if (lane == 0) "Start" else "Finish"
        val captionLayout = measurer.measure(
            caption,
            TextStyle(color = Color(0xFF14301C), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        )
        drawText(
            captionLayout,
            topLeft = Offset(
                left + (width - captionLayout.size.width) / 2f,
                size.height * 0.86f
            )
        )
        return
    }

    drawRect(
        if (lane % 2 == 0) Asphalt else AsphaltDark,
        Offset(left, 0f),
        Size(width, size.height)
    )
    if (next) {
        drawRect(LanePaint.copy(alpha = 0.16f), Offset(left, 0f), Size(width, size.height))
    }
    if (occupied) {
        drawRect(Color.White.copy(alpha = 0.08f), Offset(left, 0f), Size(width, size.height))
    }

    val dashH = 26f
    val gap = 18f
    var y = 10f
    val midX = left + width * 0.5f
    while (y < size.height) {
        drawRoundRect(
            LanePaint.copy(alpha = 0.9f),
            Offset(midX - 4f, y),
            Size(8f, dashH),
            CornerRadius(3f, 3f)
        )
        y += dashH + gap
    }

    val walkTop = chickenY - width * 0.42f
    val walkH = width * 0.84f
    var stripeX = left + 6f
    while (stripeX < left + width - 8f) {
        drawRect(
            Crosswalk.copy(alpha = 0.92f),
            Offset(stripeX, walkTop),
            Size(7f, walkH)
        )
        stripeX += 14f
    }

    val carCount = 1 + (lane % 2)
    for (c in 0 until carCount) {
        val speed = 0.38f + (lane % 4) * 0.12f
        val down = lane % 2 == 0
        val phase = (traffic * speed + c * 0.41f + lane * 0.17f) % 1f
        val travel = size.height + 160f
        var cy = if (down) phase * travel - 80f else size.height + 80f - phase * travel
        val cx = left + width * (0.5f + if (c == 0) -0.08f else 0.1f)
        if (hitLane == lane && c == 0) {
            cy += (chickenY - cy) * carHitProgress
        }
        drawCar(
            center = Offset(cx, cy),
            color = CarPalette[(lane + c) % CarPalette.size],
            facingDown = down,
            length = width * 0.92f,
            thickness = width * 0.38f
        )
    }

    val multiplier = multipliers.getOrNull(lane - 1)
    val plaqueFits = left > 8f && left + width < size.width - 8f
    if (multiplier != null && plaqueFits) {
        val label = formatMultiplier(multiplier)
        val layout = measurer.measure(
            label,
            TextStyle(
                color = Color(0xFF241C04),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
        )
        val pad = 10f
        val box = Size(layout.size.width + pad * 2, layout.size.height + 10f)
        val boxLeft = left + (width - box.width) / 2f
        drawRoundRect(
            LanePaint,
            Offset(boxLeft, 16f),
            box,
            CornerRadius(10f, 10f)
        )
        drawText(layout, topLeft = Offset(boxLeft + pad, 21f))
    }
}

private fun DrawScope.drawCar(
    center: Offset,
    color: Color,
    facingDown: Boolean,
    length: Float,
    thickness: Float
) {
    val body = Size(thickness, length)
    val topLeft = Offset(center.x - body.width / 2f, center.y - body.height / 2f)
    drawRoundRect(color, topLeft, body, CornerRadius(12f, 12f))
    val cabinY = if (facingDown) topLeft.y + body.height * 0.46f else topLeft.y + 10f
    drawRoundRect(
        Color(0xFFD7ECF8).copy(alpha = 0.9f),
        Offset(topLeft.x + 6f, cabinY),
        Size(body.width - 12f, body.height * 0.28f),
        CornerRadius(6f, 6f)
    )
    val lightY = if (facingDown) topLeft.y + body.height - 12f else topLeft.y + 4f
    drawRoundRect(
        Color(0xFFFFF3C0),
        Offset(topLeft.x + 5f, lightY),
        Size(body.width - 10f, 8f),
        CornerRadius(3f, 3f)
    )
    val wheelOffset = if (facingDown) 1f else -1f
    drawCircle(Color(0xFF151515), thickness * 0.16f, Offset(topLeft.x - 2f, center.y - length * 0.18f * wheelOffset))
    drawCircle(Color(0xFF151515), thickness * 0.16f, Offset(topLeft.x + body.width + 2f, center.y - length * 0.18f * wheelOffset))
    drawCircle(Color(0xFF151515), thickness * 0.16f, Offset(topLeft.x - 2f, center.y + length * 0.18f * wheelOffset))
    drawCircle(Color(0xFF151515), thickness * 0.16f, Offset(topLeft.x + body.width + 2f, center.y + length * 0.18f * wheelOffset))
}

private fun DrawScope.drawChicken(
    center: Offset,
    hopTilt: Float,
    squash: Float,
    sizePx: Float
) {
    translate(center.x, center.y) {
        rotate(-6f + hopTilt * 18f) {
            val sy = (1f - squash * 0.75f).coerceAtLeast(0.2f)
            val sx = 1f + squash * 0.5f
            val w = sizePx * 0.9f * sx
            val h = sizePx * 0.72f * sy
            drawRoundRect(
                Color(0xFF1A1A1A).copy(alpha = 0.18f),
                Offset(-w * 0.42f, h * 0.28f),
                Size(w * 0.9f, h * 0.22f),
                CornerRadius(12f, 12f)
            )
            drawRoundRect(
                ChickenShade,
                Offset(-w / 2f + 3f, -h / 2f + 5f),
                Size(w, h),
                CornerRadius(16f, 16f)
            )
            drawRoundRect(
                ChickenYellow,
                Offset(-w / 2f, -h / 2f),
                Size(w, h),
                CornerRadius(16f, 16f)
            )
            drawRoundRect(
                Color(0xFF2A2208),
                Offset(-w / 2f, -h / 2f),
                Size(w, h),
                CornerRadius(16f, 16f),
                style = Stroke(width = 3.5f)
            )
            drawRoundRect(Comb, Offset(w * 0.02f, -h * 0.72f), Size(w * 0.16f, h * 0.28f), CornerRadius(6f, 6f))
            drawRoundRect(Comb, Offset(-w * 0.08f, -h * 0.66f), Size(w * 0.14f, h * 0.22f), CornerRadius(5f, 5f))
            val beak = Path().apply {
                moveTo(w * 0.42f, -6f)
                lineTo(w * 0.42f + sizePx * 0.28f, 2f)
                lineTo(w * 0.42f, 12f)
                close()
            }
            drawPath(beak, Beak)
            drawCircle(Color.White, sizePx * 0.1f, Offset(w * 0.18f, -h * 0.12f))
            drawCircle(Color(0xFF1A1A1A), sizePx * 0.045f, Offset(w * 0.22f, -h * 0.12f))
            drawCircle(Color(0xFFFFF4B0), sizePx * 0.1f, Offset(-w * 0.18f, h * 0.04f))
        }
    }
}
