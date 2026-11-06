package com.kartproduction.kames

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private class KeshState {
    var p = 0f; var v = 0f
    val b = floatArrayOf(1f, 1f)
    val last = longArrayOf(0L, 0L)
    var readyAt = 0L; var fightAt = 0L; var ai = 0L; var overAt = 0L
}

private val KESH_COL = listOf(Color(0xFFE8553D), Color(0xFF3DA5D9))

private fun nowMs() = System.currentTimeMillis()

// Keshey: Bhutanese wrestling ("holding the waist"). Tap your side to push; every tap costs balance, which only
// recovers when you pause. Throw your opponent out of the ring or make them lose their balance. First to 2 rounds wins.
// Index 0 is the bottom player (you), index 1 the top player (Player 2 or the computer).
@Composable
fun Keshey(ink: Color, mute: Color, surface: Color, line: Color, onClose: () -> Unit) {
    val sp = scorePrefs(LocalContext.current)
    val st = remember { KeshState() }
    var phase by remember { mutableIntStateOf(0) }          // 0 setup, 1 ready, 2 fight, 3 round over, 4 match over
    var cpu by remember { mutableStateOf(true) }
    val wins = remember { mutableStateListOf(0, 0) }
    var msg by remember { mutableStateOf("") }
    var hold by remember { mutableStateOf(true) }
    var tick by remember { mutableLongStateOf(0L) }
    var h by remember { mutableFloatStateOf(0f) }

    fun nm(i: Int) = if (cpu) (if (i == 1) "Computer" else "You") else "Player ${i + 1}"

    fun startRound() {
        st.p = 0f; st.v = 0f; st.b[0] = 1f; st.b[1] = 1f; st.last[0] = 0L; st.last[1] = 0L
        phase = 1; st.readyAt = nowMs(); st.ai = 0L; hold = true
    }
    fun startMatch(c: Boolean) { cpu = c; wins[0] = 0; wins[1] = 0; startRound() }
    fun tap(i: Int) {
        if (phase != 2) return
        val o = 1 - i
        st.b[i] = st.b[i] - .09f; st.last[i] = nowMs()
        st.v += (if (i == 1) -1f else 1f) * .55f * (1f + (1f - st.b[o]) * 1.2f)   // a tired opponent is easier to push
    }
    fun endRound(win: Int, why: String) {
        phase = 3; st.overAt = nowMs(); wins[win] = wins[win] + 1
        msg = nm(win) + (if (cpu && win == 1 || !cpu) " throws " else " throw ") + nm(1 - win) + " ($why)"
        if (wins[win] == 2) {
            phase = 4
            msg = nm(win) + (if (cpu && win == 0) " win" else " wins") + " the match!"
            if (cpu) addScore(sp, "keshey", if (win == 0) 0 else 1)
        }
    }
    fun onDown(y: Float) {
        when (phase) {
            2 -> { val i = if (y < h / 2) 1 else 0; if (!(cpu && i == 1)) tap(i) }
            3, 4 -> if (nowMs() - st.overAt > 500) { if (phase == 4) phase = 0 else startRound() }
        }
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                val dt = if (last == 0L) 0f else min((t - last) / 1_000_000_000f, 0.033f)
                last = t
                val n = nowMs()
                if (phase == 1) {
                    hold = n - st.readyAt < 700
                    if (n - st.readyAt > 1200) { phase = 2; st.fightAt = n }
                }
                if (phase == 2) {
                    for (i in 0..1) {                          // balance returns only while resting, and wrestlers tire
                        val rg = if (n - st.last[i] > 250) .35f else .05f
                        st.b[i] = min(1f, st.b[i] + rg * max(.2f, 1f - (n - st.fightAt) / 18000f) * dt)
                    }
                    if (n - st.fightAt > 15000) { st.b[0] = st.b[0] - .12f * dt; st.b[1] = st.b[1] - .12f * dt }
                    st.p += st.v * dt; st.v *= exp(-3f * dt)
                    if (cpu && n > st.ai) {
                        if (st.b[1] > .4f) tap(1)
                        st.ai = n + (if (st.b[1] > .6f) 230 else 330) + (Math.random() * 140).toLong()
                    }
                    if (st.b[0] <= 0f) endRound(1, "lost balance")
                    else if (st.b[1] <= 0f) endRound(0, "lost balance")
                    else if (st.p >= 1f) endRound(0, "out of the ring")
                    else if (st.p <= -1f) endRound(1, "out of the ring")
                }
                tick = t
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(surface).pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Keshey", color = ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Text("Hold the waist. Throw your opponent.", color = mute, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .border(1.5.dp, line, RoundedCornerShape(20.dp))
                    .onSizeChanged { h = it.height.toFloat() }
                    .pointerInput(Unit) {
                        // raw pointer events, so two players can tap at the same moment
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent()
                                e.changes.forEach { c ->
                                    if (c.pressed && !c.previousPressed) { onDown(c.position.y); c.consume() }
                                }
                            }
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    @Suppress("UNUSED_VARIABLE") val frame = tick   // reading it redraws every frame
                    val cx = size.width / 2; val cy = size.height / 2
                    val ring = min(size.width, size.height) * .4f
                    val r = ring * .28f
                    drawCircle(line, ring, Offset(cx, cy), style = Stroke(2.dp.toPx()))                          // the ring
                    drawCircle(line, ring + 14.dp.toPx(), Offset(cx, cy),
                        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 22f)))) // spectators
                    drawLine(line, Offset(cx - ring, cy), Offset(cx + ring, cy), strokeWidth = 1.5.dp.toPx(), alpha = .4f)
                    val mid = cy - st.p * ring * .9f
                    val ms = tick / 1_000_000f
                    for (i in 0..1) {
                        val sway = if (phase == 2) sin(ms / 90f + i * 2f) * (1f - st.b[i]) * r * .7f else 0f
                        val c = Offset(cx + sway, mid + (if (i == 1) -1f else 1f) * r * .95f)
                        drawCircle(KESH_COL[i], r, c)
                        drawCircle(ink, r * .62f, c, style = Stroke(r * .22f))                                   // the kera (belt)
                        drawCircle(ink, r * .12f, Offset(c.x, c.y + (if (i == 1) r * .62f else -r * .62f)))      // its knot
                        val bw = size.width * .5f
                        val by = if (i == 1) 16.dp.toPx() else size.height - 26.dp.toPx()
                        drawRect(line, Offset(cx - bw / 2, by), Size(bw, 8.dp.toPx()))
                        drawRect(if (st.b[i] > .35f) KESH_COL[i] else Color(0xFFC0392B), Offset(cx - bw / 2, by),
                            Size(bw * max(0f, st.b[i]), 8.dp.toPx()))
                    }
                }
                if (phase != 0) {
                    Text("${nm(1)}  " + "●".repeat(wins[1]) + "○".repeat(2 - wins[1]), color = mute, fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 30.dp).then(if (cpu) Modifier else Modifier.rotate(180f)))
                    Text("${nm(0)}  " + "●".repeat(wins[0]) + "○".repeat(2 - wins[0]), color = mute, fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 38.dp))
                }
                val line1 = when (phase) { 1 -> if (hold) "Hold the waist…" else "Keshey!"; 3, 4 -> msg; else -> "" }
                val line2 = when (phase) { 3 -> "Tap to continue"; 4 -> "Tap to play again"; else -> "" }
                if (line1.isNotEmpty()) {
                    @Composable fun note(mod: Modifier) = Column(mod, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(line1, color = ink, fontSize = if (phase == 1) 30.sp else 20.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center)
                        if (line2.isNotEmpty()) Text(line2, color = mute, fontSize = 15.sp)
                    }
                    note(Modifier.align(Alignment.Center).padding(top = 90.dp, start = 16.dp, end = 16.dp))
                    if (!cpu) note(Modifier.align(Alignment.Center).padding(bottom = 90.dp, start = 16.dp, end = 16.dp).rotate(180f))
                }
                if (phase == 0) Column(
                    Modifier.fillMaxSize().background(surface).pointerInput(Unit) { detectTapGestures { } }.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Keshey is the traditional wrestling of Bhutan. Two wrestlers grab each other's belts and try to throw the other down.",
                        color = mute, fontSize = 15.sp, textAlign = TextAlign.Center)
                    Text("Tap your side to push. Tap too fast and you lose your balance and fall. First to win 2 rounds wins.",
                        color = ink, fontSize = 15.sp, textAlign = TextAlign.Center)
                    ChungiPill("Play the computer", true, ink, surface, line) { startMatch(true) }
                    ChungiPill("2 players, one phone", false, ink, surface, line) { startMatch(false) }
                }
            }
            Spacer(Modifier.height(12.dp))
            ChungiPill("Close", false, ink, surface, line) { onClose() }
        }
    }
}
