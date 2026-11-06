package com.kartproduction.kames

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Kames: one place to pick a game. Pick one to play in the app, or open the online version.
@Composable
fun Kames(ctx: Context, ink: Color, mute: Color, surface: Color, line: Color, accent: Color, onPick: (String) -> Unit, onClose: () -> Unit) {
    val games = listOf(
        Triple("Bagh-chal", "Tigers and goats, the board game from Nepal", "bagh"),
        Triple("Chess", "Full rules, two players or against the computer", "chess"),
        Triple("Chungi", "Keep the rubber-band ball in the air, a game from Nepal", "chungi"),
        Triple("Keshey", "Bhutanese wrestling: hold the waist, keep your balance, throw your opponent", "keshey"),
        Triple("Ludo", "2 to 4 players, with or without the computer", "ludo"),
        Triple("Tic-Tac-Toe", "Two players or against the computer", "ttt")
    )
    Box(
        Modifier.fillMaxSize().background(surface).pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Kames", color = ink, fontSize = 34.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
                Text("Close", color = accent, fontSize = 16.sp, modifier = Modifier.clickable { onClose() }.padding(8.dp))
            }
            Text("Choose a game.", color = mute, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            for ((name, sub, key) in games) {
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(20.dp))
                        .border(1.5.dp, line, RoundedCornerShape(20.dp)).clickable { onPick(key) }.padding(18.dp)
                ) {
                    Text(name, color = ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Text(sub, color = mute, fontSize = 14.sp)
                    val sc = scoreText(scorePrefs(ctx), key)
                    if (sc.isNotEmpty()) Text(sc, color = accent, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Text("Copy my scores", color = accent, fontSize = 14.sp, modifier = Modifier.clickable {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Kames scores", scoreCode(scorePrefs(ctx))))
                    android.widget.Toast.makeText(ctx, "Scores copied", android.widget.Toast.LENGTH_SHORT).show()
                }.padding(vertical = 8.dp, horizontal = 4.dp))
                Spacer(Modifier.width(16.dp))
                Text("Paste scores", color = accent, fontSize = 14.sp, modifier = Modifier.clickable {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val txt = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString() ?: ""
                    val ok = importScoreCode(scorePrefs(ctx), txt)
                    android.widget.Toast.makeText(ctx, if (ok) "Scores added. Reopen Kames to see them." else "No Kames scores found", android.widget.Toast.LENGTH_SHORT).show()
                }.padding(vertical = 8.dp, horizontal = 4.dp))
            }
            Text("Prefer your browser? Play online", color = accent, fontSize = 16.sp,
                modifier = Modifier.clickable { openLink(ctx, "https://kartproduction.github.io/kauncher/kames.html") }.padding(vertical = 8.dp))
            Text("kartproduction.github.io/kauncher/kames.html", color = mute, fontSize = 12.sp)
        }
    }
}
