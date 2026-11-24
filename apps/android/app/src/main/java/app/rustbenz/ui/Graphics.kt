package app.rustbenz.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** 270° battery gauge with a charge-limit tick and a slow shimmer while charging. */
@Composable
fun BatteryRing(
    progress: Float?,
    limit: Float?,
    charging: Boolean,
    modifier: Modifier = Modifier,
    ringSize: Dp = 260.dp,
    center: @Composable BoxScope.() -> Unit,
) {
    val animated by animateFloatAsState(progress ?: 0f, tween(1200), label = "soc")
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val phase by shimmer.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Box(modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2 + 6.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(Lux.SurfaceHighest, 135f, 270f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (progress != null) {
                val brush = Brush.sweepGradient(
                    0f to Lux.ChampagneDeep, 0.375f to Lux.ChampagneDeep, 0.75f to Lux.ChampagneBright, 1f to Lux.ChampagneDeep,
                    center = this.center,
                )
                drawArc(brush, 135f, 270f * animated, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                if (charging) {
                    val head = 135f + 270f * animated * phase
                    drawArc(Lux.ChampagneBright.copy(alpha = 0.55f * (1 - phase)), head - 6f, 12f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            if (limit != null) {
                val angle = Math.toRadians((135f + 270f * limit).toDouble())
                val r1 = arcSize.width / 2 - stroke
                val r2 = arcSize.width / 2 + stroke
                val c = this.center
                drawLine(
                    Lux.Text.copy(alpha = 0.8f),
                    Offset(c.x + (r1 * cos(angle)).toFloat(), c.y + (r1 * sin(angle)).toFloat()),
                    Offset(c.x + (r2 * cos(angle)).toFloat(), c.y + (r2 * sin(angle)).toFloat()),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        center()
    }
}

/** Smooth area chart (e.g. charging power curve, consumption trend). */
@Composable
fun AreaChart(values: List<Float>, modifier: Modifier = Modifier, color: Color = Lux.Champagne, description: String) {
    Canvas(modifier.semantics { contentDescription = description }) {
        if (values.size < 2) return@Canvas
        val max = values.max().coerceAtLeast(1f)
        val min = (values.min() * 0.9f).coerceAtMost(max * 0.5f)
        val stepX = size.width / (values.size - 1)
        fun pt(i: Int) = Offset(i * stepX, size.height - (values[i] - min) / (max - min) * size.height * 0.9f)
        val line = Path().apply {
            moveTo(pt(0).x, pt(0).y)
            for (i in 1 until values.size) {
                val p0 = pt(i - 1)
                val p1 = pt(i)
                val mid = (p0.x + p1.x) / 2
                cubicTo(mid, p0.y, mid, p1.y, p1.x, p1.y)
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
        drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(Lux.ChampagneBright, 4.dp.toPx(), pt(values.lastIndex))
    }
}

/** Vertical bars with labels; the last bar (today) is highlighted. */
@Composable
fun BarChart(values: List<Pair<String, Float>>, modifier: Modifier = Modifier, description: String) {
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val max = values.maxOf { it.second }.coerceAtLeast(1f)
            val slot = size.width / values.size
            val barW = slot * 0.42f
            values.forEachIndexed { i, (_, v) ->
                val h = (v / max) * size.height
                val brush = if (i == values.lastIndex) Lux.AccentBrush else Brush.verticalGradient(listOf(Lux.ChampagneDeep.copy(alpha = 0.55f), Lux.ChampagneDeep.copy(alpha = 0.2f)))
                drawRoundRect(
                    brush,
                    topLeft = Offset(i * slot + (slot - barW) / 2, size.height - h),
                    size = Size(barW, h.coerceAtLeast(barW / 2)),
                    cornerRadius = CornerRadius(barW / 2),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            values.forEach { (label, _) ->
                Text(label, style = LuxType.BodySmall, color = Lux.TextFaint, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Draggable temperature arc (cool → warm), snapping to 0.5 °C. Accessible via setProgress. */
@Composable
fun TemperatureDial(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 16f..28f,
    center: @Composable BoxScope.() -> Unit,
) {
    val current by rememberUpdatedState(value)
    val update by rememberUpdatedState(onChange)
    fun fromAngle(x: Float, y: Float, w: Float, h: Float) {
        var deg = Math.toDegrees(atan2((y - h / 2).toDouble(), (x - w / 2).toDouble())).toFloat()
        if (deg < 0) deg += 360f
        val rel = ((deg - 150f + 360f) % 360f).coerceIn(0f, 240f)
        val raw = range.start + rel / 240f * (range.endInclusive - range.start)
        update(((raw * 2).roundToInt() / 2f).coerceIn(range))
    }
    Box(
        modifier
            .aspectRatio(1f)
            .semantics {
                contentDescription = "Target temperature"
                progressBarRangeInfo = ProgressBarRangeInfo(current, range, steps = ((range.endInclusive - range.start) * 2).toInt() - 1)
                setProgress { target -> update(((target * 2).roundToInt() / 2f).coerceIn(range)); true }
            }
            .pointerInput(Unit) {
                detectTapGestures { fromAngle(it.x, it.y, size.width.toFloat(), size.height.toFloat()) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> fromAngle(change.position.x, change.position.y, size.width.toFloat(), size.height.toFloat()) }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2 + 12.dp.toPx()
            val arc = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(Lux.SurfaceHighest, 150f, 240f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            val frac = (current - range.start) / (range.endInclusive - range.start)
            val brush = Brush.sweepGradient(0f to Lux.Cool, 0.42f to Lux.Cool, 0.6f to Lux.Champagne, 0.9f to Lux.Warm, 1f to Lux.Cool, center = this.center)
            withTransform({ rotate(0f) }) {
                drawArc(brush, 150f, 240f * frac, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            val a = Math.toRadians((150f + 240f * frac).toDouble())
            val r = arc.width / 2
            val knob = Offset(this.center.x + (r * cos(a)).toFloat(), this.center.y + (r * sin(a)).toFloat())
            drawCircle(Lux.Background, stroke * 0.95f, knob)
            drawCircle(Lux.Text, stroke * 0.7f, knob)
        }
        center()
    }
}

/** Stylised side-profile line art of a compact electric SUV (no brand marks), facing right. */
@Composable
fun CarIllustration(modifier: Modifier = Modifier, plugged: Boolean = false, charging: Boolean = false) {
    val glow = rememberInfiniteTransition(label = "port")
    val pulse by glow.animateFloat(0.35f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "pulse")
    Canvas(modifier.aspectRatio(2.3f).semantics { contentDescription = "Illustration of your car" }) {
        val sx = size.width / 400f
        val sy = size.height / 174f
        fun p(x: Float, y: Float) = Offset(x * sx, y * sy)
        val stroke = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round)
        val edge = Brush.horizontalGradient(listOf(Lux.ChampagneDeep.copy(alpha = 0.7f), Lux.ChampagneBright, Lux.Champagne.copy(alpha = 0.85f)))

        // Floor reflection
        drawOval(
            Brush.radialGradient(listOf(Lux.Champagne.copy(alpha = 0.16f), Color.Transparent), center = p(205f, 160f), radius = 190f * sx),
            topLeft = p(20f, 148f),
            size = Size(370f * sx, 24f * sy),
        )

        // Compact-SUV proportions: tall flat roof, steep tailgate, short bonnet, big wheels.
        val body = Path().apply {
            moveTo(p(30f, 128f)); lineToP(p(26f, 100f))
            cubicToP(p(25f, 82f), p(30f, 66f), p(40f, 56f))
            cubicToP(p(50f, 46f), p(62f, 40f), p(80f, 38f))
            cubicToP(p(150f, 33f), p(220f, 33f), p(250f, 36f))
            cubicToP(p(272f, 40f), p(290f, 52f), p(306f, 64f))
            cubicToP(p(330f, 70f), p(360f, 74f), p(374f, 80f))
            cubicToP(p(384f, 85f), p(388f, 94f), p(388f, 104f))
            lineToP(p(386f, 124f)); lineToP(p(356f, 128f))
            archAround(p(320f, 128f), 36f * sx, 36f * sy)
            lineToP(p(132f, 128f))
            archAround(p(96f, 128f), 36f * sx, 36f * sy)
            lineToP(p(30f, 128f))
        }
        drawPath(body, Brush.verticalGradient(listOf(Lux.SurfaceHigh.copy(alpha = 0.9f), Lux.Background)))
        drawPath(body, edge, style = stroke)

        val glass = Path().apply {
            moveTo(p(46f, 64f)); lineToP(p(66f, 48f))
            cubicToP(p(120f, 41f), p(200f, 40f), p(244f, 43f))
            lineToP(p(294f, 66f)); lineToP(p(52f, 72f)); close()
        }
        drawPath(glass, Brush.linearGradient(listOf(Lux.SurfaceHighest, Lux.Background), start = p(46f, 40f), end = p(294f, 72f)))
        drawPath(glass, Lux.ChampagneDeep.copy(alpha = 0.6f), style = Stroke(1.dp.toPx()))
        drawLine(Lux.ChampagneDeep.copy(alpha = 0.5f), p(176f, 41f), p(174f, 70f), 1.dp.toPx())
        drawLine(Lux.ChampagneDeep.copy(alpha = 0.35f), p(84f, 46f), p(78f, 71f), 1.dp.toPx())

        // Roof rails, shoulder line and door seam
        drawLine(Lux.Champagne.copy(alpha = 0.45f), p(70f, 35f), p(236f, 32f), 1.2.dp.toPx(), StrokeCap.Round)
        drawLine(Lux.Champagne.copy(alpha = 0.35f), p(32f, 94f), p(382f, 96f), 1.dp.toPx())
        drawLine(Lux.Hairline, p(176f, 76f), p(176f, 124f), 1.dp.toPx())

        // Full-width light bars
        drawLine(Lux.Text, p(368f, 84f), p(388f, 90f), 2.5.dp.toPx(), StrokeCap.Round)
        drawLine(Lux.Critical.copy(alpha = 0.85f), p(27f, 86f), p(40f, 78f), 2.5.dp.toPx(), StrokeCap.Round)

        listOf(96f, 320f).forEach { x -> wheel(p(x, 130f), 27f * sx) }

        // Charge port (rear quarter)
        val port = p(54f, 90f)
        drawCircle(Lux.SurfaceHighest, 4.dp.toPx(), port)
        if (plugged) {
            val c = if (charging) Lux.Positive else Lux.Champagne
            drawCircle(c.copy(alpha = 0.25f * pulse), 12.dp.toPx(), port)
            drawCircle(c, 3.dp.toPx(), port)
        }
    }
}

private fun DrawScope.wheel(c: Offset, r: Float) {
    drawCircle(Lux.Background, r, c)
    drawCircle(Lux.Champagne.copy(alpha = 0.75f), r, c, style = Stroke(1.5.dp.toPx()))
    drawCircle(Lux.SurfaceHighest, r * 0.62f, c)
    drawCircle(Lux.ChampagneDeep, r * 0.62f, c, style = Stroke(1.dp.toPx()))
    for (i in 0 until 5) {
        rotate(i * 72f, c) { drawLine(Lux.ChampagneDeep, Offset(c.x, c.y - r * 0.18f), Offset(c.x, c.y - r * 0.58f), 1.5.dp.toPx(), StrokeCap.Round) }
    }
    drawCircle(Lux.Champagne, r * 0.12f, c)
}

private fun Path.moveTo(o: Offset) = moveTo(o.x, o.y)
private fun Path.lineToP(o: Offset) = lineTo(o.x, o.y)
private fun Path.cubicToP(a: Offset, b: Offset, c: Offset) = cubicTo(a.x, a.y, b.x, b.y, c.x, c.y)

/** Wheel arch: from the right edge of the circle around [center], over the top, to its left edge. */
private fun Path.archAround(center: Offset, rx: Float, ry: Float) {
    arcTo(
        rect = androidx.compose.ui.geometry.Rect(center.x - rx, center.y - ry, center.x + rx, center.y + ry),
        startAngleDegrees = 0f,
        sweepAngleDegrees = -180f,
        forceMoveTo = false,
    )
}

/** Top-down car outline with four tire pressures (bar). */
@Composable
fun TireDiagram(pressures: List<Float>, modifier: Modifier = Modifier, low: Float = 2.4f) {
    Box(modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(width = 110.dp, height = 190.dp)) {
            val bodyColor = Lux.ChampagneDeep
            drawRoundRect(Lux.Surface, topLeft = Offset(14.dp.toPx(), 0f), size = Size(size.width - 28.dp.toPx(), size.height), cornerRadius = CornerRadius(34.dp.toPx()))
            drawRoundRect(bodyColor, topLeft = Offset(14.dp.toPx(), 0f), size = Size(size.width - 28.dp.toPx(), size.height), cornerRadius = CornerRadius(34.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            drawRoundRect(Lux.SurfaceHighest, topLeft = Offset(26.dp.toPx(), 46.dp.toPx()), size = Size(size.width - 52.dp.toPx(), 92.dp.toPx()), cornerRadius = CornerRadius(14.dp.toPx()))
            val tw = 10.dp.toPx()
            val th = 30.dp.toPx()
            listOf(
                Offset(2.dp.toPx(), 24.dp.toPx()), Offset(size.width - 2.dp.toPx() - tw, 24.dp.toPx()),
                Offset(2.dp.toPx(), size.height - 24.dp.toPx() - th), Offset(size.width - 2.dp.toPx() - tw, size.height - 24.dp.toPx() - th),
            ).forEachIndexed { i, o ->
                val warn = pressures.getOrNull(i)?.let { it < low } == true
                drawRoundRect(if (warn) Lux.Caution else Lux.Champagne, topLeft = o, size = Size(tw, th), cornerRadius = CornerRadius(tw / 2))
            }
        }
        val labels = listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd)
        labels.forEachIndexed { i, align ->
            val v = pressures.getOrNull(i)
            Column(
                Modifier.align(align).padding(horizontal = 8.dp, vertical = 22.dp),
                horizontalAlignment = if (i % 2 == 0) Alignment.Start else Alignment.End,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(v?.let { "%.1f".format(it) } ?: "—", style = LuxType.MetricSmall, color = if (v != null && v < low) Lux.Caution else Lux.Text)
                Text(listOf("Front left", "Front right", "Rear left", "Rear right")[i] + " · bar", style = LuxType.BodySmall, color = Lux.TextFaint)
            }
        }
    }
}
