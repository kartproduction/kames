package com.kartproduction.kames

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

// Bagh-chal: 4 tigers against 20 goats on a 5x5 board. Goats are placed one at a time, then move.
// Tigers win by capturing 5 goats. Goats win by trapping every tiger.
private class BS(val b: IntArray, val gl: Int, val cp: Int)   // b: 0 empty, 1 goat, 2 tiger
private class BM(val f: Int, val t: Int, val x: Int = -1)      // f = -1 means a new goat; x = captured goat

private val D4 = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
private val D8 = D4 + listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
private fun dirs(i: Int) = if ((i / 5 + i % 5) % 2 == 0) D8 else D4   // diagonal lines only run through some points
private fun inb(r: Int, c: Int) = r in 0..4 && c in 0..4

private fun bgen(S: BS, goat: Boolean): List<BM> {
    val m = ArrayList<BM>(); val b = S.b
    if (goat) {
        if (S.gl > 0) { for (i in 0 until 25) if (b[i] == 0) m.add(BM(-1, i)) }
        else for (i in 0 until 25) if (b[i] == 1) for ((dr, dc) in dirs(i)) {
            val r = i / 5 + dr; val c = i % 5 + dc
            if (inb(r, c) && b[r * 5 + c] == 0) m.add(BM(i, r * 5 + c))
        }
    } else for (i in 0 until 25) if (b[i] == 2) for ((dr, dc) in dirs(i)) {
        val r = i / 5 + dr; val c = i % 5 + dc
        if (!inb(r, c)) continue
        val t = r * 5 + c
        if (b[t] == 0) m.add(BM(i, t))
        else if (b[t] == 1) { val r2 = r + dr; val c2 = c + dc; if (inb(r2, c2) && b[r2 * 5 + c2] == 0) m.add(BM(i, r2 * 5 + c2, t)) }
    }
    return m
}

private fun bapp(S: BS, m: BM, goat: Boolean): BS {
    val b = S.b.copyOf(); var gl = S.gl; var cp = S.cp
    if (goat) { if (m.f < 0) gl-- else b[m.f] = 0; b[m.t] = 1 }
    else { b[m.f] = 0; b[m.t] = 2; if (m.x >= 0) { b[m.x] = 0; cp++ } }
    return BS(b, gl, cp)
}

private fun bai(S: BS, goat: Boolean, hard: Boolean): BM? {
    val ms = bgen(S, goat)
    if (ms.isEmpty()) return null
    if (!hard) { val caps = ms.filter { it.x >= 0 }; return (if (caps.isNotEmpty()) caps else ms).random() }
    var best = ms[0]; var bs = Double.NEGATIVE_INFINITY
    for (m in ms) {
        val s2 = bapp(S, m, goat); val tm = bgen(s2, false)
        val sc = (if (goat) -(tm.count { it.x >= 0 } * 20 + tm.size) else (if (m.x >= 0) 100 else 0) + tm.size) + Math.random()
        if (sc > bs) { bs = sc; best = m }
    }
    return best
}

private fun startBS(): BS { val b = IntArray(25); for (i in listOf(0, 4, 20, 24)) b[i] = 2; return BS(b, 20, 0) }

@Composable
fun BaghChal(ink: Color, mute: Color, surface: Color, line: Color, accent: Color, onClose: () -> Unit) {
    var s by remember { mutableStateOf(startBS()) }
    var goatTurn by remember { mutableStateOf(true) }
    var sel by remember { mutableIntStateOf(-1) }
    var over by remember { mutableStateOf("") }
    var started by remember { mutableStateOf(false) }
    var vsComputer by remember { mutableStateOf(true) }
    var humanGoat by remember { mutableStateOf(true) }
    var hard by remember { mutableStateOf(true) }

    val moves = remember(s, goatTurn) { bgen(s, goatTurn) }
    val cpuTurn = started && vsComputer && over.isEmpty() && goatTurn != humanGoat

    fun reset() { s = startBS(); goatTurn = true; sel = -1; over = "" }
    val sp = scorePrefs(LocalContext.current)
    var scoreRev by remember { mutableIntStateOf(0) }
    LaunchedEffect(over) {
        if (over.isNotEmpty() && started && vsComputer) { addScore(sp, "bagh", if (over.startsWith("Goats") == humanGoat) 0 else 1); scoreRev++ }
    }

    fun play(m: BM) {
        val ns = bapp(s, m, goatTurn); val nt = !goatTurn
        s = ns; goatTurn = nt; sel = -1
        over = when {
            ns.cp >= 5 -> "Tigers win! Five goats were captured."
            bgen(ns, nt).isEmpty() -> if (nt) "Tigers win! The goats cannot move." else "Goats win! Every tiger is trapped."
            else -> ""
        }
    }

    LaunchedEffect(s, goatTurn, started, vsComputer, humanGoat) {
        if (cpuTurn) { delay(500); bai(s, goatTurn, hard)?.let { play(it) } }
    }

    fun tap(i: Int) {
        if (!started || over.isNotEmpty() || cpuTurn) return
        if (goatTurn && s.gl > 0) { moves.firstOrNull { it.t == i }?.let { play(it) }; return }
        val mv = moves.firstOrNull { it.f == sel && it.t == i }
        if (mv != null) { play(mv); return }
        sel = if (s.b[i] == (if (goatTurn) 1 else 2) && moves.any { it.f == i }) i else -1
    }

    @Composable
    fun Pill(label: String, filled: Boolean, onClick: () -> Unit) {
        Box(
            Modifier.clip(RoundedCornerShape(50))
                .then(if (filled) Modifier.background(ink) else Modifier.border(1.5.dp, line, RoundedCornerShape(50)))
                .clickable { onClick() }.padding(horizontal = 16.dp, vertical = 10.dp)
        ) { Text(label, color = if (filled) surface else ink, fontSize = 15.sp) }
    }

    Box(
        Modifier.fillMaxSize().background(surface).pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Bagh-chal", color = ink, fontSize = 28.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
                Text("Close", color = accent, fontSize = 16.sp, modifier = Modifier.clickable { onClose() }.padding(8.dp))
            }
            val sTxt = remember(scoreRev, started) { scoreText(sp, "bagh") }
            if (sTxt.isNotEmpty()) Text(sTxt, color = mute, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            if (!started) {
                Text("Goats: place 20 goats, then trap every tiger. Tigers: jump over goats to capture 5.", color = mute, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("2 players", !vsComputer) { vsComputer = false }
                    Pill("vs Computer", vsComputer) { vsComputer = true }
                }
                if (vsComputer) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("I play Goats", humanGoat) { humanGoat = true }
                        Pill("I play Tigers", !humanGoat) { humanGoat = false }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Easy", !hard) { hard = false }
                        Pill("Hard", hard) { hard = true }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Pill("Start", true) { reset(); started = true }
            } else {
                Text(
                    if (over.isNotEmpty()) over else (if (goatTurn) "Goats" else "Tigers") +
                        (if (cpuTurn) " (computer) are thinking…" else if (goatTurn && s.gl > 0) ": place a goat" else ": move a piece"),
                    color = ink, fontSize = 17.sp, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Canvas(
                    Modifier.widthIn(max = 380.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFF7F5FB))
                        .pointerInput(Unit) {
                            detectTapGestures { o ->
                                val cell = size.width / 5f
                                tap((o.y / cell).toInt().coerceIn(0, 4) * 5 + (o.x / cell).toInt().coerceIn(0, 4))
                            }
                        }
                ) {
                    val cell = size.width / 5f
                    fun pt(i: Int) = Offset((i % 5 + .5f) * cell, (i / 5 + .5f) * cell)
                    val lc = Color(0xFFB9B2D0)
                    for (k in 0 until 5) {
                        drawLine(lc, pt(k * 5), pt(k * 5 + 4), 3f); drawLine(lc, pt(k), pt(20 + k), 3f)
                    }
                    drawLine(lc, pt(0), pt(24), 3f); drawLine(lc, pt(4), pt(20), 3f)
                    drawLine(lc, pt(10), pt(2), 3f); drawLine(lc, pt(2), pt(14), 3f); drawLine(lc, pt(14), pt(22), 3f); drawLine(lc, pt(22), pt(10), 3f)
                    for (i in 0 until 25) {
                        val c = pt(i)
                        when (s.b[i]) {
                            0 -> drawCircle(lc, cell * .07f, c)
                            1 -> { drawCircle(Color(0xFFFFFFFF), cell * .3f, c); drawCircle(Color(0xFF201A33), cell * .3f, c, style = Stroke(4f)) }
                            else -> { drawCircle(Color(0xFFE0861F), cell * .3f, c); drawCircle(Color(0xFF201A33), cell * .3f, c, style = Stroke(4f)) }
                        }
                        if (i == sel) drawCircle(Color(0xFF4A35FF), cell * .38f, c, style = Stroke(6f))
                        if (sel >= 0 && moves.any { it.f == sel && it.t == i }) drawCircle(Color(0xAA4A35FF), cell * .14f, c)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("White = goats, orange = tigers", color = mute, fontSize = 12.sp)
                Text("Goats left to place: ${s.gl} · Goats captured: ${s.cp} of 5", color = mute, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("New game", true) { reset() }
                    Pill("Mode", false) { reset(); started = false }
                }
            }
        }
    }
}
