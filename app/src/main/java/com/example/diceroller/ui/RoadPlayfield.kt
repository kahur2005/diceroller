package com.example.diceroller.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.diceroller.game.formatMultiplier
import com.example.diceroller.ui.theme.Beak
import com.example.diceroller.ui.theme.Comb
import com.example.diceroller.ui.theme.Headlight
import com.example.diceroller.ui.theme.NightDeep
import com.example.diceroller.ui.theme.Pill
import com.example.diceroller.ui.theme.Road
import com.example.diceroller.ui.theme.RoadAlt
import com.example.diceroller.ui.theme.Sidewalk
import kotlin.math.PI
import kotlin.math.sin

private const val VisibleLanes = 5

private val CarPalette = listOf(
    Color(0xFF2F8F4E),
    Color(0xFF5B4ED6),
    Color(0xFF248A62),
    Color(0xFF3C6FDB),
    Color(0xFF2F8F4E),
    Color(0xFF6A45C8)
)

private val Dash = Color.White.copy(alpha = 0.42f)
private val ChickenWhite = Color(0xFFFFFDF8)
private val ChickenShade = Color(0xFFE4DDD2)

@Composable
private fun rememberWorldTime(): Float {
    val time by rememberInfiniteTransition(label = "world").animateFloat(
        initialValue = 0f,
        targetValue = 1_000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_000_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "seconds"
    )
    return time
}

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
    val time = rememberWorldTime()
    val visualLane = chickenLane + hopProgress
    val maxCamera = (maxSteps + 2 - VisibleLanes).coerceAtLeast(0).toFloat()
    val cameraTarget = cameraTarget(visualLane, maxCamera)
    val camera by animateFloatAsState(
        targetValue = cameraTarget,
        animationSpec = spring(
            dampingRatio = 0.86f,
            stiffness = Spring.StiffnessLow
        ),
        label = "camera"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val laneW = size.width / VisibleLanes
        val walkY = size.height * 0.48f

        drawRect(Road)

        for (lane in 0..(maxSteps + 1)) {
            val left = (lane - camera) * laneW
            if (left > size.width + 8f || left + laneW < -8f) continue
            if (lane == 0 || lane == maxSteps + 1) {
                drawSidewalk(lane, left, laneW, walkY, measurer)
            } else {
                drawTrafficLane(
                    lane = lane,
                    multipliers = multipliers,
                    left = left,
                    width = laneW,
                    measurer = measurer,
                    time = time,
                    hitLane = hitLane,
                    carHitProgress = carHitProgress,
                    chickenY = walkY
                )
            }
        }

        val chickenSize = laneW * 0.78f
        val chickenX = (visualLane - camera) * laneW + laneW * 0.50f
        val lift = hopLift(hopProgress) * laneW * 0.38f
        val idle = if (hopProgress == 0f && squash == 0f) sin(time * 2.2f) * 2.2f else 0f
        drawChicken(
            center = Offset(chickenX, walkY - lift + idle),
            hopTilt = hopProgress,
            squash = if (squash > 0f) squash else hopSquash(hopProgress),
            sizePx = chickenSize,
            time = time
        )
    }
}

private fun cameraTarget(visualLane: Float, maxCamera: Float): Float {
    val start = 1.1f
    val raw = visualLane - start
    if (raw <= 0f) return 0f
    val easeIn = (raw / 0.55f).coerceIn(0f, 1f)
    val smooth = easeIn * easeIn * (3f - 2f * easeIn)
    return (raw * smooth).coerceIn(0f, maxCamera)
}

private fun hopLift(progress: Float): Float {
    if (progress <= 0f) return 0f
    if (progress < 0.14f) {
        return -sin((progress / 0.14f) * PI.toFloat()) * 0.14f
    }
    val t = ((progress - 0.14f) / 0.86f).coerceIn(0f, 1f)
    val hang = 1f - (2f * t - 1f).let { it * it * it * it }
    return hang
}

private fun hopSquash(progress: Float): Float {
    if (progress <= 0f) return 0f
    return when {
        progress < 0.14f -> (progress / 0.14f) * 0.32f
        progress < 0.30f -> {
            val t = (progress - 0.14f) / 0.16f
            0.32f + t * (-0.22f - 0.32f)
        }
        progress < 0.82f -> -0.12f
        else -> {
            val t = (progress - 0.82f) / 0.18f
            sin(t * PI.toFloat()) * 0.38f
        }
    }
}

private fun DrawScope.drawTrafficLane(
    lane: Int,
    multipliers: List<Double>,
    left: Float,
    width: Float,
    measurer: TextMeasurer,
    time: Float,
    hitLane: Int?,
    carHitProgress: Float,
    chickenY: Float
) {
    drawRect(
        if (lane % 2 == 0) RoadAlt else Road,
        Offset(left, 0f),
        Size(width, size.height)
    )
    drawRect(
        Brush.horizontalGradient(
            0f to Color.Black.copy(alpha = 0.16f),
            0.18f to Color.Transparent,
            0.82f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.10f)
        ),
        Offset(left, 0f),
        Size(width, size.height)
    )
    drawVerticalDashes(left)

    val down = lane % 2 == 0
    val length = width * 1.22f
    val thickness = width * 0.62f
    val speed = 0.14f + (lane % 4) * 0.035f
    val phase = ((time * speed + lane * 0.17f) % 1f + 1f) % 1f
    val travel = size.height + length + 120f
    var cy = if (down) -length + phase * travel else size.height + length - phase * travel
    val cx = left + width * 0.5f
    if (hitLane == lane) {
        cy += (chickenY - cy) * carHitProgress
    }
    drawCar(
        center = Offset(cx, cy),
        color = CarPalette[lane % CarPalette.size],
        facingDown = down,
        length = length,
        thickness = thickness,
        time = time
    )

    val multiplier = multipliers.getOrNull(lane - 1) ?: return
    val centerX = left + width * 0.5f
    if (centerX < 8f || centerX > size.width - 8f) return
    drawManhole(Offset(centerX, size.height * 0.58f), width * 0.52f)
    drawMultiplierPill(
        center = Offset(centerX, size.height * 0.72f),
        label = formatMultiplier(multiplier),
        measurer = measurer
    )
}

private fun DrawScope.drawSidewalk(
    lane: Int,
    left: Float,
    width: Float,
    walkY: Float,
    measurer: TextMeasurer
) {
    drawRect(Sidewalk, Offset(left, 0f), Size(width, size.height))
    var y = 10f
    while (y < size.height) {
        drawRect(
            Color(0xFF2A3648),
            Offset(left + 7f, y),
            Size(width - 14f, 3f)
        )
        y += 22f
    }
    val curbX = if (lane == 0) left + width - 5f else left
    drawRect(Color(0xFF3E4E62), Offset(curbX, 0f), Size(5f, size.height))

    if (lane != 0) {
        val layout = measurer.measure(
            "Finish",
            TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        )
        drawText(
            layout,
            topLeft = Offset(
                left + (width - layout.size.width) / 2f,
                walkY - layout.size.height / 2f
            )
        )
    }
}

private fun DrawScope.drawVerticalDashes(x: Float) {
    val dash = 16f
    val gap = 13f
    var y = 4f
    while (y < size.height) {
        drawRoundRect(
            Dash,
            Offset(x - 1.5f, y),
            Size(3f, dash),
            CornerRadius(1.5f, 1.5f)
        )
        y += dash + gap
    }
}

private fun DrawScope.drawManhole(center: Offset, w: Float) {
    val h = w * 0.62f
    val topLeft = Offset(center.x - w / 2f, center.y - h / 2f)
    drawOval(Color.Black.copy(alpha = 0.28f), topLeft + Offset(0f, 3f), Size(w, h))
    drawOval(Color(0xFF243140), topLeft, Size(w, h))
    drawOval(Color(0xFF607086), topLeft, Size(w, h), style = Stroke(width = 2.4f))
    val insetX = w * 0.14f
    val insetY = h * 0.16f
    drawOval(
        Color(0xFF161E2A),
        Offset(topLeft.x + insetX, topLeft.y + insetY),
        Size(w - insetX * 2f, h - insetY * 2f)
    )
    val slotW = w * 0.07f
    val slotH = h * 0.42f
    val slotY = center.y - slotH / 2f
    for (i in -1..1) {
        drawRoundRect(
            Color(0xFF0E141C),
            Offset(center.x + i * w * 0.16f - slotW / 2f, slotY),
            Size(slotW, slotH),
            CornerRadius(2f, 2f)
        )
    }
}

private fun DrawScope.drawMultiplierPill(
    center: Offset,
    label: String,
    measurer: TextMeasurer
) {
    val layout = measurer.measure(
        label,
        TextStyle(
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    )
    val padX = 12f
    val padY = 7f
    val box = Size(layout.size.width + padX * 2f, layout.size.height + padY * 2f)
    val topLeft = Offset(center.x - box.width / 2f, center.y - box.height / 2f)
    drawRoundRect(Pill, topLeft, box, CornerRadius(box.height / 2f, box.height / 2f))
    drawText(layout, topLeft = Offset(topLeft.x + padX, topLeft.y + padY))
}

private fun DrawScope.drawCar(
    center: Offset,
    color: Color,
    facingDown: Boolean,
    length: Float,
    thickness: Float,
    time: Float
) {
    val bob = sin(time * 14f + center.x * 0.02f) * 0.6f
    translate(center.x, center.y + bob) {
        rotate(if (facingDown) 0f else 180f) {
            val body = Size(thickness, length)
            val topLeft = Offset(-body.width / 2f, -body.height / 2f)
            drawOval(
                Color.Black.copy(alpha = 0.28f),
                Offset(topLeft.x + 2f, topLeft.y + body.height * 0.72f),
                Size(body.width - 4f, body.height * 0.16f)
            )
            drawRoundRect(color, topLeft, body, CornerRadius(body.width * 0.28f, body.width * 0.28f))
            drawRoundRect(
                Color.White.copy(alpha = 0.14f),
                Offset(topLeft.x + body.width * 0.18f, topLeft.y + body.height * 0.08f),
                Size(body.width * 0.64f, body.height * 0.16f),
                CornerRadius(6f, 6f)
            )
            drawRoundRect(
                Color(0xFF102033).copy(alpha = 0.82f),
                Offset(topLeft.x + body.width * 0.16f, topLeft.y + body.height * 0.30f),
                Size(body.width * 0.68f, body.height * 0.22f),
                CornerRadius(5f, 5f)
            )
            val lightY = topLeft.y + body.height * 0.78f
            val lightR = body.width * 0.09f
            drawCircle(Headlight, lightR, Offset(topLeft.x + body.width * 0.28f, lightY))
            drawCircle(Headlight, lightR, Offset(topLeft.x + body.width * 0.72f, lightY))
            drawRoundRect(
                Color(0xFFB42318).copy(alpha = 0.9f),
                Offset(topLeft.x + body.width * 0.22f, topLeft.y + 4f),
                Size(body.width * 0.56f, 4f),
                CornerRadius(2f, 2f)
            )
            val wr = thickness * 0.13f
            drawCircle(Color(0xFF141414), wr, Offset(topLeft.x - 1f, -length * 0.22f))
            drawCircle(Color(0xFF141414), wr, Offset(topLeft.x + body.width + 1f, -length * 0.22f))
            drawCircle(Color(0xFF141414), wr, Offset(topLeft.x - 1f, length * 0.22f))
            drawCircle(Color(0xFF141414), wr, Offset(topLeft.x + body.width + 1f, length * 0.22f))
        }
    }
}

private fun DrawScope.drawChicken(
    center: Offset,
    hopTilt: Float,
    squash: Float,
    sizePx: Float,
    time: Float
) {
    val flatten = squash.coerceAtLeast(0f)
    val stretch = (-squash).coerceAtLeast(0f)
    val sx = 1f + flatten * 0.48f - stretch * 0.18f
    val sy = (1f - flatten * 0.72f + stretch * 0.32f).coerceAtLeast(0.22f)
    val shadowScale = (1f - hopLift(hopTilt) * 0.55f).coerceIn(0.35f, 1f)

    translate(center.x, center.y) {
        drawOval(
            Color.Black.copy(alpha = 0.22f * shadowScale),
            Offset(-sizePx * 0.34f * shadowScale, sizePx * 0.22f),
            Size(sizePx * 0.68f * shadowScale, sizePx * 0.16f * shadowScale)
        )
        rotate(-5f + hopTilt * 16f) {
            scale(sx, sy) {
                val w = sizePx * 0.86f
                val h = sizePx * 0.70f
                val kick = sin(hopTilt * PI.toFloat()) * 18f

                rotate(12f + kick, Offset(-w * 0.12f, h * 0.32f)) {
                    drawRoundRect(
                        Color(0xFFE08A3C),
                        Offset(-w * 0.18f, h * 0.22f),
                        Size(8f, h * 0.28f),
                        CornerRadius(3f, 3f)
                    )
                }
                rotate(-10f - kick, Offset(w * 0.08f, h * 0.32f)) {
                    drawRoundRect(
                        Color(0xFFE08A3C),
                        Offset(w * 0.02f, h * 0.22f),
                        Size(8f, h * 0.26f),
                        CornerRadius(3f, 3f)
                    )
                }

                val tail = Path().apply {
                    moveTo(-w * 0.46f, 0f)
                    lineTo(-w * 0.78f, -h * 0.18f)
                    lineTo(-w * 0.62f, 4f)
                    lineTo(-w * 0.76f, h * 0.16f)
                    close()
                }
                drawPath(tail, ChickenShade)

                drawOval(ChickenShade, Offset(-w * 0.42f, -h * 0.28f), Size(w * 0.92f, h * 0.86f))
                drawOval(ChickenWhite, Offset(-w * 0.46f, -h * 0.34f), Size(w * 0.88f, h * 0.78f))
                drawOval(
                    Color(0xFFD5D0C8),
                    Offset(-w * 0.46f, -h * 0.34f),
                    Size(w * 0.88f, h * 0.78f),
                    style = Stroke(width = 2.2f)
                )

                val wingPhase = if (hopTilt > 0f) sin(hopTilt * PI.toFloat()) * 22f else sin(time * 3.1f) * 4f
                rotate(wingPhase, Offset(-w * 0.02f, h * 0.02f)) {
                    drawOval(
                        ChickenShade,
                        Offset(-w * 0.22f, -h * 0.02f),
                        Size(w * 0.46f, h * 0.36f)
                    )
                }

                drawCircle(ChickenWhite, sizePx * 0.20f, Offset(w * 0.22f, -h * 0.28f))
                drawRoundRect(Comb, Offset(w * 0.12f, -h * 0.68f), Size(w * 0.13f, h * 0.26f), CornerRadius(5f, 5f))
                drawRoundRect(Comb, Offset(w * 0.22f, -h * 0.74f), Size(w * 0.12f, h * 0.24f), CornerRadius(5f, 5f))
                drawRoundRect(Comb, Offset(w * 0.30f, -h * 0.60f), Size(w * 0.10f, h * 0.18f), CornerRadius(4f, 4f))

                val beak = Path().apply {
                    moveTo(w * 0.38f, -h * 0.28f)
                    lineTo(w * 0.38f + sizePx * 0.24f, -h * 0.20f)
                    lineTo(w * 0.38f, -h * 0.12f)
                    close()
                }
                drawPath(beak, Beak)
                drawCircle(Color.White, sizePx * 0.055f, Offset(w * 0.26f, -h * 0.32f))
                drawCircle(NightDeep, sizePx * 0.028f, Offset(w * 0.28f, -h * 0.32f))
                drawCircle(Comb.copy(alpha = 0.85f), sizePx * 0.045f, Offset(w * 0.24f, -h * 0.08f))
            }
        }
    }
}
