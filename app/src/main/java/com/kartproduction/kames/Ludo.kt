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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

// Ludo for 2 to 4 players. A token's progress is -1 (at home), 0..50 on the track, 51..55 up the coloured path, 56 = finished.
private val LNAMES = listOf("Red", "Green", "Yellow", "Blue")
private val LCOL = listOf(Color(0xFFD64545), Color(0xFF2F9E5B), Color(0xFFE0A526), Color(0xFF3B6FD4))
private val LTINT = listOf(Color(0xFFF2C4C4), Color(0xFFBFE3CC), Color(0xFFF3DFA6), Color(0xFFC2D3F2))
private val LST = listOf(0, 13, 26, 39)
private val LSAFE = listOf(0, 13, 26, 39, 8, 21, 34, 47)
private val LBB = listOf(0 to 0, 9 to 0, 9 to 9, 0 to 9)
private val LSL = listOf(1.5f to 1.5f, 3.5f to 1.5f, 1.5f to 3.5f, 3.5f to 3.5f)
private val LFIN = listOf(6 to 7, 7 to 6, 8 to 7, 7 to 8)

private val LTRACK: List<Pair<Int, Int>> = buildList {
    fun seg(x: Int, y: Int, dx: Int, dy: Int, n: Int) { for (i in 0 until n) add((x + dx * i) to (y + dy * i)) }
    seg(1, 6, 1, 0, 5); seg(6, 5, 0, -1, 6); seg(7, 0, 1, 0, 2); seg(8, 1, 0, 1, 5); seg(9, 6, 1, 0, 6); seg(14, 7, 0, 1, 2)
    seg(13, 8, -1, 0, 5); seg(8, 9, 0, 1, 6); seg(7, 14, -1, 0, 2); seg(6, 13, 0, -1, 5); seg(5, 8, -1, 0, 6); seg(0, 7, 0, -1, 2)
}
private val LHOME: List<List<Pair<Int, Int>>> = listOf(
    List(5) { (1 + it) to 7 }, List(5) { 7 to (1 + it) }, List(5) { (13 - it) to 7 }, List(5) { 7 to (13 - it) }
)

private class LS {
    var p = Array(4) { intArrayOf(-1, -1, -1, -1) }
    var cur = 0; var dice = 0; var over = -1
    var act = listOf(0, 2); var hum = listOf(0); var scored = false
}

private fun lAbs(c: Int, p: Int) = (LST[c] + p) % 52
private fun lMov(s: LS, c: Int): List<Int> = (0 until 4).filter { t -> val q = s.p[c][t]; if (q < 0) s.dice == 6 else q + s.dice <= 56 }
private fun lNext(s: LS) = s.act[(s.act.indexOf(s.cur) + 1) % s.act.size]

private fun lPos(s: LS, c: Int, t: Int): Pair<Float, Float> {
    val q = s.p[c][t]
    if (q < 0) return (LBB[c].first + LSL[t].first) to (LBB[c].second + LSL[t].second)
    val cell = if (q <= 50) LTRACK[lAbs(c, q)] else if (q < 56) LHOME[c][q - 51] else LFIN[c]
    return (cell.first + .5f) to (cell.second + .5f)
}

private fun lScore(s: LS, c: Int, t: Int): Double {
    val q = s.p[c][t]; val np = if (q < 0) 0 else q + s.dice
    var sc = np.toDouble()
    if (q < 0) sc += 50; if (np == 56) sc += 80; if (q <= 50 && np > 50) sc += 40
    if (np <= 50) {
        if (lAbs(c, np) in LSAFE) sc += 15
        else for (d in s.act) if (d != c) for (u in 0 until 4) { val o = s.p[d][u]; if (o in 0..50 && lAbs(d, o) == lAbs(c, np)) sc += 100 }
    }
    return sc + Math.random()
}

private fun lDo(s: LS, c: Int, t: Int): Boolean {   // returns true when the same player rolls again
    val q = s.p[c][t]; val np = if (q < 0) 0 else q + s.dice
    var extra = s.dice == 6
    s.p[c][t] = np
    if (np <= 50 && lAbs(c, np) !in LSAFE) {
        for (d in s.act) if (d != c) for (u in 0 until 4) { val o = s.p[d][u]; if (o in 0..50 && lAbs(d, o) == lAbs(c, np)) { s.p[d][u] = -1; extra = true } }
    }
    if (np == 56) extra = true
    if (s.p[c].all { it == 56 }) { s.over = c; s.dice = 0; return false }
    s.dice = 0
    return extra
}

@Composable
fun Ludo(ink: Color, mute: Color, surface: Color, line: Color, accent: Color, onClose: () -> Unit) {
    val s = remember { LS() }
    var rev by remember { mutableIntStateOf(0) }
    var started by remember { mutableStateOf(false) }
    var n by remember { mutableIntStateOf(2) }
    var vsComputer by remember { mutableStateOf(true) }

    fun isH(c: Int) = c in s.hum
    val sp = scorePrefs(LocalContext.current)
    var scoreRev by remember { mutableIntStateOf(0) }
    LaunchedEffect(rev) {
        if (s.over >= 0 && vsComputer && !s.scored) { s.scored = true; addScore(sp, "ludo", if (s.over == 0) 0 else 1); scoreRev++ }
    }
    fun start() {
        s.act = listOf(listOf(0, 2), listOf(0, 1, 2), listOf(0, 1, 2, 3))[n - 2]
        s.hum = if (vsComputer) listOf(0) else s.act
        s.p = Array(4) { intArrayOf(-1, -1, -1, -1) }
        s.cur = s.act[0]; s.dice = 0; s.over = -1; s.scored = false
        started = true; rev++
    }
    fun roll() { if (s.over >= 0 || s.dice > 0) return; s.dice = (1..6).random(); rev++ }
    fun pass() { s.dice = 0; s.cur = lNext(s); rev++ }
    fun move(t: Int) { val again = lDo(s, s.cur, t); if (!again && s.over < 0) s.cur = lNext(s); rev++ }

    // The computer's turns, and passing when nothing can move.
    LaunchedEffect(rev, started) {
        if (!started || s.over >= 0) return@LaunchedEffect
        val m = lMov(s, s.cur)
        if (s.dice == 0) { if (!isH(s.cur)) { delay(700); roll() } }
        else if (m.isEmpty()) { delay(900); pass() }
        else if (!isH(s.cur)) { delay(800); move(m.maxByOrNull { lScore(s, s.cur, it) } ?: m[0]) }
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
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Ludo", color = ink, fontSize = 28.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
                Text("Close", color = accent, fontSize = 16.sp, modifier = Modifier.clickable { onClose() }.padding(8.dp))
            }
            val sTxt = remember(scoreRev, started) { scoreText(sp, "ludo") }
            if (sTxt.isNotEmpty()) Text(sTxt, color = mute, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            if (!started) {
                Text("Roll a 6 to leave home, go around the board and bring all four tokens to the centre first. Land on another colour to send it home.",
                    color = mute, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (k in 2..4) Pill("$k players", n == k) { n = k }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("You vs Computer", vsComputer) { vsComputer = true }
                    Pill("Friends", !vsComputer) { vsComputer = false }
                }
                Spacer(Modifier.height(18.dp))
                Pill("Start", true) { start() }
            } else {
                val r = rev
                val movable = if (s.dice > 0 && isH(s.cur) && s.over < 0) lMov(s, s.cur) else emptyList()
                Text(
                    if (s.over >= 0) "${LNAMES[s.over]} wins!" else LNAMES[s.cur] +
                        (if (isH(s.cur)) (if (s.dice > 0) ": tap a ringed token" else ": roll the dice") else " (computer) is playing…"),
                    color = ink, fontSize = 17.sp, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Canvas(
                    Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF7F5FB))
                        .pointerInput(Unit) {
                            detectTapGestures { o ->
                                if (s.over >= 0 || s.dice == 0 || !isH(s.cur)) return@detectTapGestures
                                val u = size.width / 15f
                                var best = -1; var bd = .7f
                                for (t in lMov(s, s.cur)) {
                                    val q = lPos(s, s.cur, t)
                                    val d = Math.hypot((q.first - o.x / u).toDouble(), (q.second - o.y / u).toDouble()).toFloat()
                                    if (d < bd) { bd = d; best = t }
                                }
                                if (best >= 0) move(best)
                            }
                        }
                ) {
                    val u = size.width / 15f
                    r.hashCode()
                    fun cellAt(x: Int, y: Int, f: Color) {
                        drawRect(f, Offset(x * u, y * u), Size(u, u))
                        drawRect(Color(0xFFB9B2D0), Offset(x * u, y * u), Size(u, u), style = Stroke(1f))
                    }
                    for (c in 0 until 4) {
                        drawRect(LTINT[c], Offset(LBB[c].first * u, LBB[c].second * u), Size(6 * u, 6 * u))
                        drawRect(Color(0xFFF7F5FB), Offset((LBB[c].first + 1) * u, (LBB[c].second + 1) * u), Size(4 * u, 4 * u))
                    }
                    LTRACK.forEachIndexed { i, p ->
                        val k = LST.indexOf(i)
                        cellAt(p.first, p.second, if (k >= 0) LCOL[k] else if (i in LSAFE) Color(0xFFE4DDF3) else Color.White)
                        if (k < 0 && i in LSAFE) drawCircle(Color(0xFF7A6FA3), u * .1f, Offset((p.first + .5f) * u, (p.second + .5f) * u))
                    }
                    LHOME.forEachIndexed { c, col -> col.forEach { cellAt(it.first, it.second, LCOL[c]) } }
                    fun tri(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float, col: Color) {
                        val path = Path().apply { moveTo(a * u, b * u); lineTo(c * u, d * u); lineTo(e * u, f * u); close() }
                        drawPath(path, col)
                    }
                    tri(6f, 6f, 7.5f, 7.5f, 6f, 9f, LCOL[0]); tri(6f, 6f, 9f, 6f, 7.5f, 7.5f, LCOL[1])
                    tri(9f, 6f, 9f, 9f, 7.5f, 7.5f, LCOL[2]); tri(6f, 9f, 9f, 9f, 7.5f, 7.5f, LCOL[3])
                    val seen = HashMap<String, Int>()
                    for (c in s.act) for (t in 0 until 4) {
                        val q = lPos(s, c, t)
                        val key = "${q.first},${q.second}"
                        val k = seen[key] ?: 0; seen[key] = k + 1
                        val ctr = Offset((q.first + k * .14f) * u, (q.second - k * .14f) * u)
                        drawCircle(LCOL[c], u * .34f, ctr)
                        drawCircle(Color.White, u * .34f, ctr, style = Stroke(3f))
                        if (c == s.cur && t in movable) drawCircle(Color(0xFF201A33), u * .44f, ctr, style = Stroke(5f))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (s.dice > 0) "${s.dice}" else "–", color = ink, fontSize = 40.sp, fontWeight = FontWeight.Light)
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(if (s.over < 0 && s.dice == 0 && isH(s.cur)) ink else line)
                            .clickable(enabled = s.over < 0 && s.dice == 0 && isH(s.cur)) { roll() }.padding(horizontal = 22.dp, vertical = 12.dp)
                    ) { Text("Roll the dice", color = surface, fontSize = 16.sp) }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("New game", true) { start() }
                    Pill("Mode", false) { started = false }
                }
            }
        }
    }
}
