package com.kartproduction.kames

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private class ChungiBall { var x = 0f; var y = 0f; var vx = 0f; var vy = 0f; var a = 0f; var spin = 0f; var dec = false }

private val BAND_COLORS = listOf(Color(0xFFE8553D), Color(0xFFF2B134), Color(0xFF3DA5D9), Color(0xFF6BBF59), Color(0xFFC65BCF))
private const val ROUNDS = 3

@Composable
internal fun ChungiPill(text: String, on: Boolean, ink: Color, surface: Color, line: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (on) ink else Color.Transparent)
            .border(1.5.dp, if (on) ink else line, RoundedCornerShape(50))
            .clickable { onClick() }.padding(horizontal = 18.dp, vertical = 10.dp)
    ) { Text(text, color = if (on) surface else ink, fontSize = 18.sp) }
}

// Chungi: the Nepali rubber-band ball. Tap below the dashed line to kick it; tap left of the ball to send it right.
// 1 player: endless practice with a saved best ("chungiBest"). 2+ players (up to 4 people, plus an optional computer):
// turns on one device, ROUNDS turns each, most kicks in total wins.
@Composable
fun Chungi(ink: Color, mute: Color, surface: Color, line: Color, onClose: () -> Unit) {
    val sp = scorePrefs(LocalContext.current)
    val ball = remember { ChungiBall() }
    var best by remember { mutableIntStateOf(sp.getInt("chungiBest", 0)) }
    var score by remember { mutableIntStateOf(0) }
    var state by remember { mutableIntStateOf(0) }          // 0 ready, 1 playing, 2 over
    var tick by remember { mutableLongStateOf(0L) }
    var overAt by remember { mutableLongStateOf(0L) }
    var w by remember { mutableFloatStateOf(0f) }
    var h by remember { mutableFloatStateOf(0f) }
    var inSetup by remember { mutableStateOf(true) }
    var humans by remember { mutableIntStateOf(1) }
    var vsCpu by remember { mutableStateOf(false) }
    var turn by remember { mutableIntStateOf(0) }
    val totals = remember { mutableStateListOf<Int>() }

    fun cnt() = humans + (if (vsCpu) 1 else 0)
    fun solo() = cnt() == 1
    fun isCpu(i: Int) = vsCpu && i == cnt() - 1
    fun nameOf(i: Int) = if (solo()) "You" else if (isCpu(i)) "Computer" else "Player ${i + 1}"
    fun lastTurn() = turn + 1 >= cnt() * ROUNDS
    fun total(i: Int) = if (i < totals.size) totals[i] else 0

    fun newBall() {
        score = 0
        ball.x = w / 2; ball.y = h * .18f; ball.vx = 0f; ball.vy = 0f; ball.a = 0f; ball.spin = 0f; ball.dec = false
        state = if (!inSetup && isCpu(turn % cnt())) 1 else 0
    }
    fun startMatch() {
        totals.clear(); repeat(cnt()) { totals.add(0) }
        turn = 0; inSetup = false; newBall()
    }

    fun kick(px: Float) {
        if (ball.y < h * .42f) return                       // too high to reach
        val g = h * 1.9f
        ball.vy = -sqrt(2f * g * h * (.36f + Math.random().toFloat() * .05f))
        ball.vx = (ball.vx * .25f + (ball.x - px) / w * w * 3.2f).coerceIn(-w * .9f, w * .9f)
        ball.spin = (Math.random().toFloat() - .5f) * 14f
        score++
    }

    LaunchedEffect(w, h) { if (w > 0f && h > 0f) newBall() }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                val dt = if (last == 0L) 0f else min((t - last) / 1_000_000_000f, 0.033f)
                last = t
                if (w > 0f && h > 0f) {
                    val g = h * 1.9f
                    val r = min(w, h) * .07f
                    if (state == 1) {
                        // computer: decide once per fall; it misses more often the longer its turn runs
                        if (!inSetup && isCpu(turn % cnt()) && ball.vy > 0f && ball.y > h * .6f && !ball.dec) {
                            ball.dec = true
                            if (Math.random() > min(.04 + score * .012, .4))
                                kick(ball.x + (ball.x - w / 2) * .4f + (Math.random().toFloat() - .5f) * 30f)
                        }
                        if (ball.vy < 0f) ball.dec = false
                        ball.vy += g * dt
                        ball.vx *= 1f - .35f * dt
                        ball.x += ball.vx * dt; ball.y += ball.vy * dt
                        ball.a += ball.spin * dt; ball.spin *= 1f - .8f * dt
                        if (ball.x < r) { ball.x = r; ball.vx = Math.abs(ball.vx) * .6f }
                        if (ball.x > w - r) { ball.x = w - r; ball.vx = -Math.abs(ball.vx) * .6f }
                        if (ball.y < r) { ball.y = r; ball.vy = Math.abs(ball.vy) * .4f }
                        if (ball.y > h * .9f - r) {
                            ball.y = h * .9f - r
                            state = 2; overAt = System.currentTimeMillis()
                            if (solo()) {
                                if (score > best) { best = score; sp.edit().putInt("chungiBest", best).apply() }
                            } else if (!inSetup && totals.size == cnt()) {
                                val i = turn % cnt(); totals[i] = totals[i] + score
                            }
                        }
                    } else if (state == 0) {
                        ball.y = h * .18f + sin(t / 5e8).toFloat() * 6f
                        ball.a = sin(t / 7e8).toFloat() * .3f
                    }
                }
                tick = t
            }
        }
    }

    val head = when {
        solo() -> if (best > 0) "Best: $best kicks" else "Keep it in the air. Tap to kick."
        inSetup -> "Choose players"
        else -> "Round ${turn / cnt() + 1} of $ROUNDS · " + (0 until cnt()).joinToString(" · ") { "${nameOf(it)} ${total(it)}" }
    }

    Box(
        Modifier.fillMaxSize().background(surface).pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Chungi", color = ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Text(head, color = mute, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .border(1.5.dp, line, RoundedCornerShape(20.dp))
                    .onSizeChanged { w = it.width.toFloat(); h = it.height.toFloat() }
                    .pointerInput(Unit) {
                        detectTapGestures { o ->
                            if (inSetup) return@detectTapGestures
                            when (state) {
                                0 -> state = 1
                                1 -> if (!isCpu(turn % cnt())) kick(o.x)
                                else -> if (System.currentTimeMillis() - overAt > 450) {
                                    if (solo()) newBall()
                                    else if (lastTurn()) inSetup = true
                                    else { turn++; newBall() }
                                }
                            }
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    @Suppress("UNUSED_VARIABLE") val frame = tick   // reading it redraws every frame
                    val zoneY = size.height * .42f
                    val groundY = size.height * .9f
                    val pad = 14.dp.toPx()
                    drawLine(line, Offset(pad, zoneY), Offset(size.width - pad, zoneY), strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 16f)))
                    drawLine(mute, Offset(0f, groundY), Offset(size.width, groundY), strokeWidth = 1.5.dp.toPx())
                    val r = min(size.width, size.height) * .07f
                    val near = (1f - (groundY - ball.y) / size.height).coerceIn(0f, 1f)
                    val sw = r * (.5f + near * .7f)
                    drawOval(line, Offset(ball.x - sw, groundY + 6f - r * .18f), Size(sw * 2f, r * .36f))
                    rotate(degrees = ball.a * 57.29578f, pivot = Offset(ball.x, ball.y)) {
                        for (i in 0 until 14) {
                            val t = i / 14f * 2f * PI.toFloat()
                            val len = r * (.85f + .15f * sin(i * 2.3f))
                            val p = Path().apply {
                                moveTo(ball.x, ball.y)
                                quadraticBezierTo(ball.x + cos(t + .5f) * len * .9f, ball.y + sin(t + .5f) * len * .9f,
                                    ball.x + cos(t) * len, ball.y + sin(t) * len)
                                quadraticBezierTo(ball.x + cos(t - .45f) * len * .7f, ball.y + sin(t - .45f) * len * .7f,
                                    ball.x, ball.y)
                            }
                            drawPath(p, BAND_COLORS[i % 5], style = Stroke(width = r * .16f, cap = StrokeCap.Round))
                        }
                    }
                    drawCircle(ink, r * .16f, Offset(ball.x, ball.y))
                }
                if (!inSetup && state != 0) Text("$score", color = ink, fontSize = 56.sp, fontWeight = FontWeight.Light,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 36.dp))
                if (!inSetup) Column(Modifier.align(Alignment.Center).padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (state) {
                        0 -> {
                            Text(if (solo()) "Tap to drop the chungi" else "${nameOf(turn % cnt())}, tap to drop", color = mute, fontSize = 16.sp, textAlign = TextAlign.Center)
                            Text("Then tap below the line to kick", color = mute, fontSize = 16.sp, textAlign = TextAlign.Center)
                        }
                        2 -> if (solo()) {
                            Text("Dropped at $score", color = ink, fontSize = 24.sp, fontWeight = FontWeight.Light)
                            Text(if (score > 0 && score >= best) "New best!" else "Best: $best", color = mute, fontSize = 16.sp)
                            Text("Tap to play again", color = mute, fontSize = 16.sp)
                        } else {
                            Text("${nameOf(turn % cnt())} dropped at $score", color = ink, fontSize = 22.sp, fontWeight = FontWeight.Light)
                            val order = (0 until cnt()).sortedByDescending { total(it) }
                            val top = total(order[0])
                            if (lastTurn()) Text(
                                if (order.count { total(it) == top } > 1) "It's a tie!" else "${nameOf(order[0])} wins!",
                                color = ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            order.forEach { Text("${nameOf(it)}: ${total(it)}", color = mute, fontSize = 16.sp) }
                            Text(if (lastTurn()) "Tap to play again" else "Tap for ${nameOf((turn + 1) % cnt())}", color = mute, fontSize = 16.sp)
                        }
                        else -> {}
                    }
                }
                if (inSetup) Column(
                    Modifier.fillMaxSize().background(surface).pointerInput(Unit) { detectTapGestures { } }.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Players", color = mute, fontSize = 15.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        (1..4).forEach { n -> ChungiPill("$n", humans == n, ink, surface, line) { humans = n } }
                    }
                    ChungiPill(if (vsCpu) "✓ Play against the computer" else "Play against the computer", vsCpu, ink, surface, line) { vsCpu = !vsCpu }
                    Text(
                        if (solo()) "Practice: one chungi, keep your best score."
                        else "${cnt()} players, $ROUNDS turns each. Most kicks in total wins.",
                        color = mute, fontSize = 15.sp, textAlign = TextAlign.Center
                    )
                    ChungiPill("Start", true, ink, surface, line) { startMatch() }
                }
            }
            Spacer(Modifier.height(12.dp))
            ChungiPill("Close", false, ink, surface, line) { onClose() }
        }
    }
}
