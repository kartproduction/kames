package com.kartproduction.kames

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

private val WIN_LINES = listOf(
    listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),   // rows
    listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),   // columns
    listOf(0, 4, 8), listOf(2, 4, 6)                     // diagonals
)

// The computer (O): easy = random cell; hard = win, block, centre, corner, any.
private fun computerMove(b: List<Char>, hard: Boolean): Int {
    val free = b.indices.filter { b[it] == ' ' }
    if (!hard) return free.random()
    fun finishing(m: Char): Int? = free.firstOrNull { i ->
        WIN_LINES.any { w -> i in w && w.all { it == i || b[it] == m } }
    }
    finishing('O')?.let { return it }   // win now
    finishing('X')?.let { return it }   // block the human
    if (4 in free) return 4
    free.filter { it in listOf(0, 2, 6, 8) }.randomOrNull()?.let { return it }
    return free.random()
}

// Kart's Tic-Tac-Toe: two players or you vs the computer. X goes first, cells 1-9, play again.
@Composable
fun TicTacToe(ink: Color, mute: Color, surface: Color, line: Color, onClose: () -> Unit) {
    var name1 by remember { mutableStateOf("") }
    var name2 by remember { mutableStateOf("") }
    var started by remember { mutableStateOf(false) }
    var vsComputer by remember { mutableStateOf(false) }
    var hard by remember { mutableStateOf(true) }
    val board = remember { mutableStateListOf(' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ') }
    var current by remember { mutableStateOf(0) }          // 0 = X, 1 = O
    var result by remember { mutableStateOf<String?>(null) } // null while playing
    var note by remember { mutableStateOf("") }
    val sp = scorePrefs(LocalContext.current)
    var scoreRev by remember { mutableIntStateOf(0) }
    LaunchedEffect(result) {
        val r = result
        if (r != null && vsComputer) { addScore(sp, "ttt", if (r.startsWith("Computer")) 1 else if (r.contains("draw")) 2 else 0); scoreRev++ }
    }

    val names = if (vsComputer) listOf(name1.trim().ifEmpty { "Player" }, "Computer")
                else listOf(name1.trim().ifEmpty { "Player 1" }, name2.trim().ifEmpty { "Player 2" })
    val marks = listOf('X', 'O')

    fun reset() {
        for (i in 0 until 9) board[i] = ' '
        current = 0; result = null; note = ""
    }

    fun play(i: Int) {
        if (result != null) return
        if (board[i] != ' ') { note = "That cell is already used! Try again."; return }
        val m = marks[current]
        board[i] = m
        note = ""
        if (WIN_LINES.any { w -> w.all { board[it] == m } }) {
            result = if (vsComputer && current == 1) "Computer wins! Better luck next time."
                     else "Congratulations, ${names[current]} wins! Game over!"
        } else if (board.none { it == ' ' }) {
            result = "It's a draw! Game over!"
        } else {
            current = 1 - current
        }
    }

    // The computer plays O after a short pause.
    LaunchedEffect(started, vsComputer, current, result) {
        if (started && vsComputer && current == 1 && result == null) {
            delay(600)
            play(computerMove(board.toList(), hard))
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

    @Composable
    fun Field(value: String, hint: String, onChange: (String) -> Unit) {
        BasicTextField(
            value = value, onValueChange = { onChange(it.take(16)) }, singleLine = true,
            textStyle = TextStyle(color = ink, fontSize = 20.sp), cursorBrush = SolidColor(ink),
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            decorationBox = { inner ->
                Box { if (value.isEmpty()) Text(hint, color = mute, fontSize = 20.sp); inner() }
            }
        )
    }

    Box(
        Modifier.fillMaxSize().background(surface).pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 26.dp, vertical = 20.dp)
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Tic-Tac-Toe", color = ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            val sTxt = remember(scoreRev, started) { scoreText(sp, "ttt") }
            if (sTxt.isNotEmpty()) Text(sTxt, color = mute, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))

            if (!started) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("2 players", !vsComputer) { vsComputer = false }
                    Pill("vs Computer", vsComputer) { vsComputer = true }
                }
                Spacer(Modifier.height(12.dp))
                if (vsComputer) {
                    Field(name1, "Your name (X)") { name1 = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Easy", !hard) { hard = false }
                        Pill("Hard", hard) { hard = true }
                    }
                } else {
                    Field(name1, "Player one name (X)") { name1 = it }
                    Field(name2, "Player two name (O)") { name2 = it }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Start", true) { reset(); started = true }
                    Pill("Close", false) { onClose() }
                }
            } else {
                Text(
                    result ?: "${names[current]} [${marks[current]}], your move",
                    color = ink, fontSize = 18.sp, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                for (r in 0 until 3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in 0 until 3) {
                            val i = r * 3 + c
                            Box(
                                Modifier.size(84.dp).clip(RoundedCornerShape(16.dp))
                                    .border(1.5.dp, line, RoundedCornerShape(16.dp))
                                    .clickable { if (!(vsComputer && current == 1)) play(i) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (board[i] == ' ') Text("${i + 1}", color = mute, fontSize = 18.sp)
                                else Text("${board[i]}", color = ink, fontSize = 40.sp, fontWeight = FontWeight.Light)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (note.isNotEmpty()) Text(note, color = mute, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (result != null) Pill("Play again", true) { reset() }
                    Pill("Mode", false) { reset(); started = false }
                    Pill("Close", false) { onClose() }
                }
            }
        }
    }
}
