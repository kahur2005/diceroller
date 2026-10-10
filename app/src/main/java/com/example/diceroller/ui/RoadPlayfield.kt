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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.diceroller.game.formatMultiplier
import com.example.diceroller.ui.theme.Asphalt
import com.example.diceroller.ui.theme.AsphaltDark
import com.example.diceroller.ui.theme.AsphaltSheen
import com.example.diceroller.ui.theme.Beak
import com.example.diceroller.ui.theme.Comb
import com.example.diceroller.ui.theme.Crosswalk
import com.example.diceroller.ui.theme.Curb
import com.example.diceroller.ui.theme.DuskGlow
import com.example.diceroller.ui.theme.DuskHigh
import com.example.diceroller.ui.theme.DuskMid
import com.example.diceroller.ui.theme.Headlight
import com.example.diceroller.ui.theme.LanePaint
import com.example.diceroller.ui.theme.NightDeep
import com.example.diceroller.ui.theme.Porcelain
import com.example.diceroller.ui.theme.PorcelainShade
import com.example.diceroller.ui.theme.SkyTop
import com.example.diceroller.ui.theme.Sodium
import com.example.diceroller.ui.theme.Ticket
import com.example.diceroller.ui.theme.TreeDark
import com.example.diceroller.ui.theme.TreeLight
import com.example.diceroller.ui.theme.Verge
import com.example.diceroller.ui.theme.VergeDeep
import kotlin.math.PI
import kotlin.math.sin

private const val VisibleLanes = 6

private val CarPalette = listOf(
    Color(0xFFD45A4A),
    Color(0xFF3D7AD9),
    Color(0xFFE6B84C),
    Color(0xFF3FA37A),
    Color(0xFF8B5CBF),
    Color(0xFF4AA3B5)
)

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
        val gutter = 6f
        val laneW = (size.width - gutter) / VisibleLanes
        val horizon = size.height * 0.30f
        val walkY = size.height * 0.62f

        drawBackdrop(time)

        for (lane in 0..(maxSteps + 1)) {
            val left = gutter + (lane - camera) * laneW
            if (left > size.width + 8f || left + laneW < -8f) continue
            drawVerticalLane(
                lane = lane,
                maxSteps = maxSteps,
                multipliers = multipliers,
                left = left,
                width = laneW,
                horizon = horizon,
                measurer = measurer,
                time = time,
                hitLane = hitLane,
                carHitProgress = carHitProgress,
                chickenY = walkY,
                occupied = lane == chickenLane || (hopProgress > 0f && lane == chickenLane + 1),
                next = lane == chickenLane + 1 && hopProgress == 0f
            )
        }

        val chickenSize = laneW * 0.64f
        val chickenX = (gutter + (visualLane - camera) * laneW + laneW * 0.68f)
            .coerceAtLeast(chickenSize * 0.7f)
        val lift = hopLift(hopProgress) * laneW * 0.38f
        val idle = if (hopProgress == 0f && squash == 0f) sin(time * 2.2f) * 2.4f else 0f
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

private fun DrawScope.drawBackdrop(time: Float) {
    drawRect(
        Brush.verticalGradient(
            0f to SkyTop,
            0.10f to DuskHigh,
            0.20f to DuskMid,
            0.27f to DuskGlow,
            0.30f to Color(0xFFC9A07A),
            1f to VergeDeep
        )
    )

    val moon = Offset(size.width * 0.78f, size.height * 0.11f)
    drawCircle(Color.White.copy(alpha = 0.08f), 54f, moon)
    drawCircle(Color.White.copy(alpha = 0.16f), 32f, moon)
    drawCircle(Color(0xFFF4EED8), 18f, moon)
    drawCircle(Color(0xFFE4D8B8), 6f, moon + Offset(5f, -3f))

    val hillY = size.height * 0.30f
    val hillBack = Path().apply {
        moveTo(0f, hillY + 8f)
        cubicTo(
            size.width * 0.22f, hillY - 36f,
            size.width * 0.48f, hillY + 18f,
            size.width, hillY - 22f
        )
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(hillBack, Color(0xFF243044).copy(alpha = 0.85f))

    val hillFront = Path().apply {
        moveTo(0f, hillY + 22f)
        cubicTo(
            size.width * 0.28f, hillY - 8f,
            size.width * 0.62f, hillY + 28f,
            size.width, hillY + 6f
        )
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(hillFront, Color(0xFF1A2634))

    for (i in 0 until 8) {
        val twinkle = 0.30f + 0.20f * sin(time * (1.1f + i * 0.13f) + i)
        val sx = (i * 97f + 28f) % size.width
        val sy = 22f + (i * 31f) % (size.height * 0.26f)
        drawCircle(Color.White.copy(alpha = twinkle), if (i % 3 == 0) 1.8f else 1.1f, Offset(sx, sy))
    }
}

private fun DrawScope.drawVerticalLane(
    lane: Int,
    maxSteps: Int,
    multipliers: List<Double>,
    left: Float,
    width: Float,
    horizon: Float,
    measurer: TextMeasurer,
    time: Float,
    hitLane: Int?,
    carHitProgress: Float,
    chickenY: Float,
    occupied: Boolean,
    next: Boolean
) {
    val isRoad = lane in 1..maxSteps
    if (!isRoad) {
        drawSafeLane(lane, left, width, horizon, measurer)
        return
    }

    val roadH = size.height - horizon
    drawRect(
        Brush.horizontalGradient(
            0f to AsphaltDark,
            0.45f to if (lane % 2 == 0) Asphalt else AsphaltDark,
            1f to AsphaltSheen.copy(alpha = 0.55f)
        ),
        Offset(left, horizon),
        Size(width, roadH)
    )
    drawRect(
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.10f),
            0.45f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.24f)
        ),
        Offset(left, horizon),
        Size(width, roadH)
    )

    drawRect(Curb.copy(alpha = 0.55f), Offset(left, horizon), Size(4f, roadH))
    drawRect(Curb.copy(alpha = 0.35f), Offset(left + width - 4f, horizon), Size(4f, roadH))
    drawRect(Sodium.copy(alpha = 0.22f), Offset(left, horizon), Size(width, 2.5f))

    if (next) {
        drawRect(Sodium.copy(alpha = 0.10f), Offset(left, horizon), Size(width, roadH))
    }
    if (occupied) {
        drawRect(Headlight.copy(alpha = 0.05f), Offset(left, horizon), Size(width, roadH))
    }

    val lamp = Offset(left + width * 0.5f, horizon)
    drawCircle(Sodium.copy(alpha = 0.14f + 0.04f * sin(time * 3f + lane)), width * 0.55f, lamp)
    drawCircle(Sodium.copy(alpha = 0.22f), width * 0.18f, lamp)

    val dashH = 28f
    val gap = 20f
    val down = lane % 2 == 0
    val scroll = (time * 64f) % (dashH + gap)
    var y = horizon + if (down) -dashH + scroll else -dashH - scroll
    val midX = left + width * 0.5f
    while (y < size.height + dashH) {
        drawRoundRect(
            LanePaint.copy(alpha = 0.82f),
            Offset(midX - 3.5f, y),
            Size(7f, dashH),
            CornerRadius(3f, 3f)
        )
        y += dashH + gap
    }

    val walkTop = chickenY - width * 0.40f
    val walkH = width * 0.80f
    var stripeX = left + 5f
    var stripe = 0
    while (stripeX < left + width - 7f) {
        drawRect(
            Crosswalk.copy(alpha = if (stripe % 2 == 0) 0.92f else 0.55f),
            Offset(stripeX, walkTop),
            Size(6.5f, walkH)
        )
        stripeX += 13f
        stripe++
    }

    val carCount = 1 + (lane % 2)
    for (c in 0 until carCount) {
        val speed = 0.22f + (lane % 4) * 0.07f
        val phase = ((time * speed + c * 0.41f + lane * 0.17f) % 1f + 1f) % 1f
        val travel = (size.height - horizon) + 200f
        var cy = if (down) horizon - 100f + phase * travel else size.height + 100f - phase * travel
        val cx = left + width * (0.5f + if (c == 0) -0.07f else 0.09f)
        if (hitLane == lane && c == 0) {
            val aimed = chickenY
            cy += (aimed - cy) * carHitProgress
        }
        drawCar(
            center = Offset(cx, cy),
            color = CarPalette[(lane + c) % CarPalette.size],
            facingDown = down,
            length = width * 0.90f,
            thickness = width * 0.36f,
            time = time
        )
    }

    val multiplier = multipliers.getOrNull(lane - 1)
    val plaqueFits = left > 6f && left + width < size.width - 6f
    if (multiplier != null && plaqueFits) {
        drawHighwaySign(left, width, horizon, formatMultiplier(multiplier), measurer)
    }
}

private fun DrawScope.drawSafeLane(
    lane: Int,
    left: Float,
    width: Float,
    horizon: Float,
    measurer: TextMeasurer
) {
    drawRect(
        Brush.verticalGradient(
            0f to if (lane == 0) Verge else VergeDeep,
            0.55f to if (lane == 0) Color(0xFF355544) else VergeDeep,
            1f to Color(0xFF14241C)
        ),
        Offset(left, horizon),
        Size(width, size.height - horizon)
    )

    for (i in 0 until 7) {
        val gx = left + width * (0.12f + (i * 0.13f) % 0.76f)
        val gy = horizon + (size.height - horizon) * (0.18f + (i * 0.11f) % 0.62f)
        drawCircle(TreeLight.copy(alpha = 0.22f), 5f, Offset(gx, gy))
    }

    drawRect(
        Curb.copy(alpha = 0.7f),
        Offset(left + width * 0.08f, size.height * 0.52f),
        Size(width * 0.84f, size.height * 0.16f)
    )
    drawRect(
        Color(0xFF2A2620),
        Offset(left + width * 0.08f, size.height * 0.66f),
        Size(width * 0.84f, 6f)
    )

    val tx = left + width * 0.5f
    val ty = horizon - width * 0.08f
    drawRoundRect(
        Color(0xFF4A3422),
        Offset(tx - 5f, ty + 14f),
        Size(10f, 22f),
        CornerRadius(2f, 2f)
    )
    drawOval(TreeDark, Offset(tx - width * 0.22f, ty - width * 0.10f), Size(width * 0.44f, width * 0.34f))
    drawOval(TreeLight, Offset(tx - width * 0.16f, ty - width * 0.16f), Size(width * 0.28f, width * 0.24f))
    drawOval(Color(0xFF2F5A3C), Offset(tx - width * 0.08f, ty - width * 0.04f), Size(width * 0.18f, width * 0.14f))

    val caption = if (lane == 0) "Start" else "Finish"
    val captionLayout = measurer.measure(
        caption,
        TextStyle(
            color = Ticket,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Serif
        )
    )
    drawText(
        captionLayout,
        topLeft = Offset(
            left + (width - captionLayout.size.width) / 2f,
            size.height * 0.86f
        )
    )
}

private fun DrawScope.drawHighwaySign(
    left: Float,
    width: Float,
    horizon: Float,
    label: String,
    measurer: TextMeasurer
) {
    val layout = measurer.measure(
        label,
        TextStyle(
            color = Sodium,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif
        )
    )
    val pad = 9f
    val box = Size(layout.size.width + pad * 2, layout.size.height + 8f)
    val boxLeft = left + (width - box.width) / 2f
    val top = horizon + 10f
    drawRect(Color(0xFF2A2418), Offset(boxLeft + 8f, horizon - 4f), Size(3f, 16f))
    drawRect(Color(0xFF2A2418), Offset(boxLeft + box.width - 11f, horizon - 4f), Size(3f, 16f))
    drawRoundRect(
        Color(0xFF1A1C22),
        Offset(boxLeft, top),
        box,
        CornerRadius(5f, 5f)
    )
    drawRoundRect(
        Sodium.copy(alpha = 0.7f),
        Offset(boxLeft, top),
        box,
        CornerRadius(5f, 5f),
        style = Stroke(width = 1.6f)
    )
    drawText(layout, topLeft = Offset(boxLeft + pad, top + 4f))
}

private fun DrawScope.drawCar(
    center: Offset,
    color: Color,
    facingDown: Boolean,
    length: Float,
    thickness: Float,
    time: Float
) {
    val bob = sin(time * 14f + center.x * 0.02f) * 0.8f
    translate(center.x, center.y + bob) {
        rotate(if (facingDown) 0f else 180f) {
            val body = Size(thickness, length)
            val topLeft = Offset(-body.width / 2f, -body.height / 2f)
            drawOval(
                Color.Black.copy(alpha = 0.28f),
                Offset(topLeft.x - 4f, topLeft.y + body.height * 0.55f),
                Size(body.width + 8f, body.height * 0.22f)
            )
            drawRoundRect(color, topLeft, body, CornerRadius(11f, 14f))
            drawRoundRect(
                Color.White.copy(alpha = 0.10f),
                Offset(topLeft.x + 4f, topLeft.y + 6f),
                Size(body.width * 0.28f, body.height * 0.7f),
                CornerRadius(6f, 6f)
            )
            drawRoundRect(
                Color(0xFFD7ECF8).copy(alpha = 0.72f),
                Offset(topLeft.x + 5f, topLeft.y + body.height * 0.42f),
                Size(body.width - 10f, body.height * 0.22f),
                CornerRadius(5f, 5f)
            )
            val lightY = topLeft.y + body.height - 11f
            drawRoundRect(
                Headlight,
                Offset(topLeft.x + 6f, lightY),
                Size(body.width - 12f, 7f),
                CornerRadius(3f, 3f)
            )
            val cone = Path().apply {
                moveTo(-body.width * 0.28f, body.height * 0.48f)
                lineTo(-body.width * 0.72f, body.height * 0.95f)
                lineTo(body.width * 0.72f, body.height * 0.95f)
                lineTo(body.width * 0.28f, body.height * 0.48f)
                close()
            }
            drawPath(cone, Headlight.copy(alpha = 0.10f))
            drawRoundRect(
                Color(0xFFB42318).copy(alpha = 0.85f),
                Offset(topLeft.x + 7f, topLeft.y + 4f),
                Size(body.width - 14f, 5f),
                CornerRadius(2f, 2f)
            )
            val wr = thickness * 0.15f
            drawCircle(Color(0xFF141414), wr, Offset(topLeft.x - 1f, -length * 0.18f))
            drawCircle(Color(0xFF141414), wr, Offset(topLeft.x + body.width + 1f, -length * 0.18f))
            drawCircle(Color(0xFF141414), wr, Offset(topLeft.x - 1f, length * 0.18f))
            drawCircle(Color(0xFF141414), wr, Offset(topLeft.x + body.width + 1f, length * 0.18f))
            drawCircle(Color(0xFF6A6A6A), wr * 0.4f, Offset(topLeft.x - 1f, -length * 0.18f))
            drawCircle(Color(0xFF6A6A6A), wr * 0.4f, Offset(topLeft.x + body.width + 1f, -length * 0.18f))
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
                drawPath(tail, PorcelainShade)

                drawOval(PorcelainShade, Offset(-w * 0.42f, -h * 0.28f), Size(w * 0.92f, h * 0.86f))
                drawOval(Porcelain, Offset(-w * 0.46f, -h * 0.34f), Size(w * 0.88f, h * 0.78f))
                drawOval(
                    NightDeep.copy(alpha = 0.55f),
                    Offset(-w * 0.46f, -h * 0.34f),
                    Size(w * 0.88f, h * 0.78f),
                    style = Stroke(width = 2.4f)
                )

                val wingPhase = if (hopTilt > 0f) sin(hopTilt * PI.toFloat()) * 22f else sin(time * 3.1f) * 4f
                rotate(wingPhase, Offset(-w * 0.02f, h * 0.02f)) {
                    drawOval(
                        PorcelainShade,
                        Offset(-w * 0.22f, -h * 0.02f),
                        Size(w * 0.46f, h * 0.36f)
                    )
                }

                drawCircle(Porcelain, sizePx * 0.20f, Offset(w * 0.22f, -h * 0.28f))
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
                drawCircle(Color(0xFFFFF6C8), sizePx * 0.07f, Offset(w * 0.06f, -h * 0.02f))
                drawCircle(Comb.copy(alpha = 0.85f), sizePx * 0.045f, Offset(w * 0.24f, -h * 0.08f))
            }
        }
    }
}
