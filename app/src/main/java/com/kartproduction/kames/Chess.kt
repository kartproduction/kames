package com.kartproduction.kames

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.random.Random

// ---- Chess engine: full rules except draw by repetition / 50 moves. Board index 0 = a8, 63 = h1. ----
private class CS(val b: CharArray, val w: Boolean, val c: String, val e: Int)
private class Mv(val f: Int, val t: Int, val ep: Boolean = false, val cs: Boolean = false)

private fun isW(p: Char) = p.isUpperCase()
private val ROOK = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
private val BISH = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
private val KNT = listOf(1 to 2, 2 to 1, -1 to 2, -2 to 1, 1 to -2, 2 to -1, -1 to -2, -2 to -1)

private fun startPos(): CS {
    val b = CharArray(64) { ' ' }
    val back = "rnbqkbnr"
    for (i in 0 until 8) { b[i] = back[i]; b[8 + i] = 'p'; b[48 + i] = 'P'; b[56 + i] = back[i].uppercaseChar() }
    return CS(b, true, "KQkq", -1)
}

private fun att(b: CharArray, s: Int, byW: Boolean): Boolean {
    val r = s / 8; val c = s % 8
    fun at(rr: Int, cc: Int, ch: Char) = rr in 0..7 && cc in 0..7 && b[rr * 8 + cc] == ch
    val p = if (byW) "PNBRQK" else "pnbrqk"
    val pr = if (byW) r + 1 else r - 1
    if (at(pr, c - 1, p[0]) || at(pr, c + 1, p[0])) return true
    for ((dr, dc) in KNT) if (at(r + dr, c + dc, p[1])) return true
    for ((dr, dc) in ROOK + BISH) if (at(r + dr, c + dc, p[5])) return true
    for ((dr, dc) in ROOK + BISH) {
        val rook = ROOK.contains(dr to dc)
        var rr = r + dr; var cc = c + dc
        while (rr in 0..7 && cc in 0..7) {
            val q = b[rr * 8 + cc]
            if (q != ' ') { if (q == p[4] || q == (if (rook) p[3] else p[2])) return true; break }
            rr += dr; cc += dc
        }
    }
    return false
}

private fun gen(S: CS): List<Mv> {
    val b = S.b; val w = S.w; val m = ArrayList<Mv>()
    for (s in 0 until 64) {
        val p = b[s]
        if (p == ' ' || isW(p) != w) continue
        val r = s / 8; val f = s % 8
        fun add(rr: Int, ff: Int): Boolean {
            if (rr !in 0..7 || ff !in 0..7) return false
            val q = b[rr * 8 + ff]
            if (q != ' ' && isW(q) == w) return false
            m.add(Mv(s, rr * 8 + ff)); return q == ' '
        }
        when (p.uppercaseChar()) {
            'P' -> {
                val d = if (w) -1 else 1
                if (b[(r + d) * 8 + f] == ' ') {
                    m.add(Mv(s, (r + d) * 8 + f))
                    if (r == (if (w) 6 else 1) && b[(r + 2 * d) * 8 + f] == ' ') m.add(Mv(s, (r + 2 * d) * 8 + f))
                }
                for (dc in listOf(-1, 1)) {
                    val f2 = f + dc
                    if (f2 !in 0..7) continue
                    val t = (r + d) * 8 + f2; val q = b[t]
                    if (q != ' ' && isW(q) != w) m.add(Mv(s, t)) else if (t == S.e) m.add(Mv(s, t, ep = true))
                }
            }
            'N' -> for ((dr, dc) in KNT) add(r + dr, f + dc)
            'K' -> {
                for ((dr, dc) in ROOK + BISH) add(r + dr, f + dc)
                val base = if (w) 60 else 4
                if (s == base && !att(b, s, !w)) {
                    if (S.c.contains(if (w) 'K' else 'k') && b[base + 1] == ' ' && b[base + 2] == ' ' &&
                        !att(b, base + 1, !w) && !att(b, base + 2, !w)) m.add(Mv(s, base + 2, cs = true))
                    if (S.c.contains(if (w) 'Q' else 'q') && b[base - 1] == ' ' && b[base - 2] == ' ' && b[base - 3] == ' ' &&
                        !att(b, base - 1, !w) && !att(b, base - 2, !w)) m.add(Mv(s, base - 2, cs = true))
                }
            }
            else -> {
                val dirs = when (p.uppercaseChar()) { 'R' -> ROOK; 'B' -> BISH; else -> ROOK + BISH }
                for ((dr, dc) in dirs) { var rr = r + dr; var ff = f + dc; while (add(rr, ff)) { rr += dr; ff += dc } }
            }
        }
    }
    return m
}

private fun make(S: CS, mv: Mv): CS {
    val b = S.b.copyOf(); val p = b[mv.f]; var c = S.c; var e = -1
    b[mv.t] = p; b[mv.f] = ' '
    if (mv.ep) b[mv.t + (if (isW(p)) 8 else -8)] = ' '
    if (p == 'P' && mv.t < 8) b[mv.t] = 'Q'
    if (p == 'p' && mv.t > 55) b[mv.t] = 'q'
    if (mv.cs) {
        if (mv.t > mv.f) { b[mv.t - 1] = b[mv.f + 3]; b[mv.f + 3] = ' ' } else { b[mv.t + 1] = b[mv.f - 4]; b[mv.f - 4] = ' ' }
    }
    if (p.uppercaseChar() == 'P' && abs(mv.t - mv.f) == 16) e = (mv.f + mv.t) / 2
    for (s in listOf(mv.f, mv.t)) when (s) {
        60 -> c = c.replace("K", "").replace("Q", "")
        4 -> c = c.replace("k", "").replace("q", "")
        63 -> c = c.replace("K", "")
        56 -> c = c.replace("Q", "")
        7 -> c = c.replace("k", "")
        0 -> c = c.replace("q", "")
    }
    return CS(b, !S.w, c, e)
}

private fun kingSq(S: CS) = S.b.indexOf(if (S.w) 'K' else 'k')
private fun inCheck(S: CS) = att(S.b, kingSq(S), !S.w)
private fun legal(S: CS): List<Mv> = gen(S).filter { val n = make(S, it); !att(n.b, kingSq(CS(n.b, S.w, "", -1)), !S.w) }

private fun value(p: Char) = when (p.lowercaseChar()) { 'p' -> 100; 'n' -> 320; 'b' -> 330; 'r' -> 500; 'q' -> 900; else -> 0 }

private fun eval(S: CS): Int {
    var x = 0
    for (s in 0 until 64) {
        val p = S.b[s]
        if (p == ' ') continue
        val centre = if (p.lowercaseChar() == 'k') 0.0 else (6.0 - abs(3.5 - s / 8) - abs(3.5 - s % 8)) * 3
        x += (if (isW(p)) 1 else -1) * (value(p) + centre.toInt())
    }
    return x
}

private fun nm(S: CS, d: Int, alpha: Int, beta: Int): Int {
    val ms = legal(S)
    if (ms.isEmpty()) return if (inCheck(S)) -99999 - d else 0
    if (d == 0) return (if (S.w) 1 else -1) * eval(S)
    var a = alpha
    for (mv in ms) {
        val v = -nm(make(S, mv), d - 1, -beta, -a)
        if (v >= beta) return beta
        if (v > a) a = v
    }
    return a
}

// Easy: random (captures preferred half the time). Hard: 3-ply search.
private fun bestMove(S: CS, hard: Boolean): Mv {
    val ms = legal(S).shuffled()
    if (!hard) {
        val caps = ms.filter { S.b[it.t] != ' ' }
        return if (caps.isNotEmpty() && Random.nextBoolean()) caps[0] else ms[0]
    }
    var best = ms[0]; var bv = Int.MIN_VALUE
    for (mv in ms) {
        val v = -nm(make(S, mv), 2, -1_000_000, 1_000_000)
        if (v > bv) { bv = v; best = mv }
    }
    return best
}

private fun glyph(p: Char) = when (p.lowercaseChar()) {
    'k' -> "\u265A"; 'q' -> "\u265B"; 'r' -> "\u265C"; 'b' -> "\u265D"; 'n' -> "\u265E"; else -> "\u265F"
} + "\uFE0E"

// ---- Screen ----
@Composable
fun Chess(ink: Color, mute: Color, surface: Color, line: Color, onClose: () -> Unit) {
    var cs by remember { mutableStateOf(startPos()) }
    var sel by remember { mutableStateOf(-1) }
    var last by remember { mutableStateOf<Mv?>(null) }
    var started by remember { mutableStateOf(false) }
    var vsComputer by remember { mutableStateOf(false) }
    var hard by remember { mutableStateOf(true) }

    val moves = remember(cs) { legal(cs) }
    val check = remember(cs) { inCheck(cs) }
    val over = moves.isEmpty()
    val thinking = started && vsComputer && !cs.w && !over

    fun reset() { cs = startPos(); sel = -1; last = null }
    val sp = scorePrefs(LocalContext.current)
    var scoreRev by remember { mutableIntStateOf(0) }
    LaunchedEffect(over) {
        if (over && started && vsComputer) { addScore(sp, "chess", if (check) (if (cs.w) 1 else 0) else 2); scoreRev++ }
    }

    LaunchedEffect(cs, started, vsComputer) {
        if (started && vsComputer && !cs.w && moves.isNotEmpty()) {
            delay(300)
            val mv = withContext(Dispatchers.Default) { bestMove(cs, hard) }
            cs = make(cs, mv); last = mv; sel = -1
        }
    }

    @Composable
    fun Pill(label: String, filled: Boolean, onClick: () -> Unit) {
        Box(
            Modifier.clip(RoundedCornerShape(50))
                .then(if (filled) Modifier.background(ink) else Modifier.border(1.5.dp, line, RoundedCornerShape(50)))
                .clickable { onClick() }.padding(horizontal = 18.dp, vertical = 12.dp)
        ) { Text(label, color = if (filled) surface else ink, fontSize = 16.sp) }
    }

    fun tap(s: Int) {
        if (over || thinking) return
        val mv = moves.firstOrNull { it.f == sel && it.t == s }
        if (mv != null) { cs = make(cs, mv); last = mv; sel = -1; return }
        val p = cs.b[s]
        sel = if (p != ' ' && isW(p) == cs.w) s else -1
    }

    Box(
        Modifier.fillMaxSize().background(surface).pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 18.dp, vertical = 20.dp)
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Chess", color = ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            val sTxt = remember(scoreRev, started) { scoreText(sp, "chess") }
            if (sTxt.isNotEmpty()) Text(sTxt, color = mute, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))

            if (!started) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("2 players", !vsComputer) { vsComputer = false }
                    Pill("vs Computer", vsComputer) { vsComputer = true }
                }
                if (vsComputer) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Easy", !hard) { hard = false }
                        Pill("Hard", hard) { hard = true }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("You play White.", color = mute, fontSize = 14.sp)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Start", true) { reset(); started = true }
                    Pill("Close", false) { onClose() }
                }
            } else {
                val status = when {
                    over && check -> "Checkmate: ${if (cs.w) "Black" else "White"} wins!"
                    over -> "Stalemate: it's a draw."
                    thinking -> "Computer is thinking..."
                    else -> (if (cs.w) "White" else "Black") + " to move" + if (check) " (check!)" else ""
                }
                Text(status, color = ink, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                val kp = kingSq(cs)
                val targets = moves.filter { it.f == sel }.map { it.t }
                Column(Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))) {
                    for (r in 0 until 8) Row(Modifier.weight(1f)) {
                        for (c in 0 until 8) {
                            val s = r * 8 + c
                            val p = cs.b[s]
                            val base = if ((r + c) % 2 == 0) Color(0xFFCFC8E6) else Color(0xFF8F84BD)
                            val bg = when {
                                s == sel -> Color(0xFF7B6CFF)
                                check && s == kp -> Color(0xFFE06060)
                                last != null && (s == last!!.f || s == last!!.t) -> Color(0xFFE3CF7A)
                                else -> base
                            }
                            Box(
                                Modifier.weight(1f).fillMaxHeight().background(bg).clickable { tap(s) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (p != ' ') Text(
                                    glyph(p), fontSize = 30.sp,
                                    color = if (isW(p)) Color.White else Color(0xFF1A1530),
                                    style = TextStyle(shadow = if (isW(p)) Shadow(Color.Black, blurRadius = 5f) else null)
                                )
                                if (s in targets) Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(Color(0x66201A33)))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("New game", true) { reset() }
                    Pill("Mode", false) { reset(); started = false }
                    Pill("Close", false) { onClose() }
                }
            }
        }
    }
}
