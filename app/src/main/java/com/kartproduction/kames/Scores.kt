package com.kartproduction.kames

import android.content.Context
import android.content.SharedPreferences

// Wins, losses and draws against the computer, per game ("ttt", "chess", "bagh", "ludo", "keshey").
fun scorePrefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences("studio", Context.MODE_PRIVATE)

fun scoreOf(prefs: SharedPreferences, game: String): IntArray = try {
    val a = org.json.JSONObject(prefs.getString("kameScores", "{}") ?: "{}").optJSONArray(game)
    if (a == null) intArrayOf(0, 0, 0) else intArrayOf(a.getInt(0), a.getInt(1), a.getInt(2))
} catch (e: Exception) { intArrayOf(0, 0, 0) }

// outcome: 0 = you won, 1 = you lost, 2 = draw
fun addScore(prefs: SharedPreferences, game: String, outcome: Int) {
    try {
        val all = org.json.JSONObject(prefs.getString("kameScores", "{}") ?: "{}")
        val s = scoreOf(prefs, game)
        s[outcome]++
        all.put(game, org.json.JSONArray(listOf(s[0], s[1], s[2])))
        prefs.edit().putString("kameScores", all.toString()).apply()
    } catch (e: Exception) { }
}

fun scoreText(prefs: SharedPreferences, game: String): String {
    if (game == "chungi") { val b = prefs.getInt("chungiBest", 0); return if (b == 0) "" else "Best: $b kicks" }
    val s = scoreOf(prefs, game)
    return if (s.sum() == 0) "" else "Against the computer: won ${s[0]}, lost ${s[1]}, drew ${s[2]}"
}

// Moving scores between Kauncher and Kames: "Copy my scores" makes a short text code, "Paste scores" reads it back.
// For each game the record with more games played is kept, and the best Chungi score is the higher of the two.
fun scoreCode(prefs: SharedPreferences): String = try {
    "KAMES1:" + org.json.JSONObject().put("s", org.json.JSONObject(prefs.getString("kameScores", "{}") ?: "{}"))
        .put("c", prefs.getInt("chungiBest", 0)).toString()
} catch (e: Exception) { "" }

fun importScoreCode(prefs: SharedPreferences, code: String): Boolean = try {
    val t = code.trim()
    if (!t.startsWith("KAMES1:")) false else {
        val inc = org.json.JSONObject(t.removePrefix("KAMES1:"))
        val all = org.json.JSONObject(prefs.getString("kameScores", "{}") ?: "{}")
        val sc = inc.getJSONObject("s")
        for (k in sc.keys()) {
            val a = sc.getJSONArray(k)
            if (a.getInt(0) + a.getInt(1) + a.getInt(2) > scoreOf(prefs, k).sum()) all.put(k, a)
        }
        prefs.edit().putString("kameScores", all.toString())
            .putInt("chungiBest", maxOf(prefs.getInt("chungiBest", 0), inc.optInt("c", 0))).apply()
        true
    }
} catch (e: Exception) { false }
