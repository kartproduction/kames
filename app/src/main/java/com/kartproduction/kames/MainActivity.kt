package com.kartproduction.kames

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KamesApp(onExit = { finish() }) }
    }
}

fun openLink(ctx: Context, url: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) } catch (e: Exception) { }
}

// Same colours as Kauncher, so the games look the same in both apps.
@Composable
fun KamesApp(onExit: () -> Unit) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val surface = if (dark) Color(0xFF221C3D) else Color(0xFFF3F1F8)
    val ink = if (dark) Color(0xFFEDEAF8) else Color(0xFF201A33)
    val mute = if (dark) Color(0xFF9189B0) else Color(0xFF6B6483)
    val line = if (dark) Color(0xFF342D55) else Color(0xFFC9C3DB)
    val accent = if (dark) Color(0xFF9C8CFF) else Color(0xFF4A35FF)
    var game by remember { mutableStateOf<String?>(null) }
    BackHandler(game != null) { game = null }
    val back = { game = null }
    Box(Modifier.fillMaxSize().background(surface).systemBarsPadding()) {
        when (game) {
            null -> Kames(ctx = ctx, ink = ink, mute = mute, surface = surface, line = line, accent = accent,
                onPick = { game = it }, onClose = onExit)
            "ttt" -> TicTacToe(ink = ink, mute = mute, surface = surface, line = line) { back() }
            "chess" -> Chess(ink = ink, mute = mute, surface = surface, line = line) { back() }
            "bagh" -> BaghChal(ink = ink, mute = mute, surface = surface, line = line, accent = accent) { back() }
            "ludo" -> Ludo(ink = ink, mute = mute, surface = surface, line = line, accent = accent) { back() }
            "chungi" -> Chungi(ink = ink, mute = mute, surface = surface, line = line) { back() }
            "keshey" -> Keshey(ink = ink, mute = mute, surface = surface, line = line) { back() }
            else -> back()
        }
    }
}
