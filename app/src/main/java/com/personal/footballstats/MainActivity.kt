package com.personal.footballstats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.round

data class Leg(val cat: String, val pick: String, val p: Double, val odd: Double, val ev: Double)
data class Parlay(val legs: List<Leg>, val jointP: Double, val combOdds: Double, val evPct: Double)
data class MatchUi(val id: String, val h: String, val a: String, val sc: String, val lg: String, val tag: String, val st: String, val live: Boolean, val dt: String, val fav: String, val cards: String, val odds: Triple<String, String, String>, val parlay: Parlay)
data class League(val code: String, val name: String, val tag: String)

object CyberEngine {
    private val STARS = mapOf("boca" to ("Edinson Cavani" to "Boca"), "river" to ("Miguel Borja" to "River"), "racing" to ("Maravilla Martínez" to "Racing"), "san lorenzo" to ("Iker Muniain" to "San Lorenzo"), "velez" to ("Braian Romero" to "Vélez"), "madrid" to ("Vinícius Jr" to "Real Madrid"), "barcelona" to ("R. Lewandowski" to "Barça"), "city" to ("Erling Haaland" to "Man City"), "liverpool" to ("Mohamed Salah" to "Liverpool"), "arsenal" to ("Bukayo Saka" to "Arsenal"), "miami" to ("Lionel Messi" to "Inter Miami"), "penarol" to ("Maxi Silvera" to "Peñarol"), "nacional" to ("R. Bentancourt" to "Nacional"))

    private fun fact(n: Int): Double {
        var r = 1.0
        for (i in 1..n) r *= i.toDouble()
        return r
    }

    fun poissonOver(k: Double, lam: Double): Double {
        var s = 0.0
        for (i in 0..k.toInt()) s += (lam.pow(i.toDouble()) * exp(-lam)) / fact(i)
        return (1.0 - s).coerceIn(0.05, 0.95)
    }

    fun getFav(h: String, a: String): String {
        val hl = h.lowercase(); val al = a.lowercase()
        val hG = STARS.keys.any { hl.contains(it) }
        val aG = STARS.keys.any { al.contains(it) }
        return if (hG && !aG) h else if (!hG && aG) a else h
    }

    fun getStar(fav: String, id: String): Pair<String, String> {
        val found = STARS.keys.firstOrNull { fav.lowercase().contains(it) }
        if (found != null) return STARS[found]!!
        val alts = listOf("Mateo Retegui", "V. Castellanos", "F. Colidio", "Milton Giménez")
        return Pair(alts[abs((id + fav).hashCode()) % alts.size], fav)
    }

    fun buildParlay(h: String, a: String, star: String, club: String, tag: String, id: String): Parlay {
        val s = abs((h + a + id).hashCode())
        val gLam = if (listOf("ENG", "FRA", "UCL").contains(tag)) 2.95 else 2.45
        val pG = poissonOver(2.5, gLam)
        val oG = round((1.82 + ((s % 12).toDouble() * 0.02)) * 100.0) / 100.0
        val evG = round(((pG * oG) - 1.0) * 1000.0) / 10.0

        val cLam = 9.8 + ((s % 6).toDouble() * 0.2)
        val pC = poissonOver(9.5, cLam)
        val oC = round((1.95 + ((s % 10).toDouble() * 0.02)) * 100.0) / 100.0
        val evC = round(((pC * oC) - 1.0) * 1000.0) / 10.0

        val sLam = 1.85 + ((s % 5).toDouble() * 0.1)
        val pS = poissonOver(1.5, sLam)
        val oS = round((1.80 + ((s % 8).toDouble() * 0.02)) * 100.0) / 100.0
        val evS = round(((pS * oS) - 1.0) * 1000.0) / 10.0

        val legs = listOf(
            Leg("GOLES", "Más de 2.5 Goles", round(pG * 100.0) / 100.0, oG, evG),
            Leg("CÓRNERS", "Más de 9.5 Córners", round(pC * 100.0) / 100.0, oC, evC),
            Leg("JUGADOR", star + " (+1.5 al arco)", round(pS * 100.0) / 100.0, oS, evS)
        )
        val jP = round((pG * pC * pS * 0.94) * 100.0) / 100.0
        val cO = round((oG * oC * oS) * 100.0) / 100.0
        val totEv = round(((jP * cO) - 1.0) * 1000.0) / 10.0
        return Parlay(legs, jP, cO, totEv)
    }
}

fun fmtDate(d: String?): String {
    if (d.isNullOrBlank() || !d.contains("T")) return "HOY"
    return try {
        val p = d.split("T")
        val dp = p[0].split("-")
        val tp = p[1].substring(0, 5).split(":")
        var day = dp[2].toInt(); var h = tp[0].toInt() - 3
        if (h < 0) { h += 24; day -= 1 } else if (h >= 24) { h -= 24; day += 1 }
        val dStr = if (day < 10) "0" + day else day.toString()
        val hStr = if (h < 10) "0" + h else h.toString()
        dStr + "/" + dp[1] + " • " + hStr + ":" + tp[1] + " hs"
    } catch (e: Exception) { "HOY" }
}

val DeepBg = Color(0xFF06070B)
val GlassCard = Color(0xDD0D1220)
val CyberBorder = Color(0xFF1E283D)
val Cyan = Color(0xFF00F0FF)
val Lime = Color(0xFF99FF00)
val Violet = Color(0xFFB026FF)
val Amber = Color(0xFFFFB800)

@Composable
fun CyberHudParlay(p: Parlay) {
    Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xCC0B101D), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Brush.linearGradient(listOf(Cyan.copy(0.6f), Violet.copy(0.6f))))) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(Lime, CircleShape))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("QUANTUM PARLAY HUD", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
                Surface(color = Color(0x3300F0FF), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, Lime)) {
                    Text("+" + p.evPct.toString() + "% EV", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Lime, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            p.legs.forEach { l ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(l.cat + " • " + l.pick, fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                    Text((l.p * 100.0).toInt().toString() + "%", fontSize = 8.sp, color = Color(0xFF94A3B8))
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(color = Color(0xFF161F30), shape = RoundedCornerShape(3.dp)) {
                        Text(l.odd.toString(), fontSize = 9.sp, fontWeight = FontWeight.Black, color = Cyan, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF151C2C))) {
                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth((p.jointP.toFloat() * 1.8f).coerceIn(0.15f, 1.0f)).background(Brush.horizontalGradient(listOf(Cyan, Lime))))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF0F1728), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (p.jointP >= 0.15) "POISSON " + (p.jointP * 100.0).toInt().toString() + "% CONFIRMADO" else "PROB < 15%", fontSize = 7.sp, color = if (p.jointP >= 0.15) Lime else Color(0xFFFF2A85), fontWeight = FontWeight.Black)
                Text("TOTAL: " + p.combOdds.toString() + "x", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Amber)
            }
        }
    }
}

@Composable
fun CyberMatch(m: MatchUi) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp), colors = CardDefaults.cardColors(containerColor = GlassCard), border = BorderStroke(1.dp, CyberBorder)) {
        Column(modifier = Modifier.padding(9.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color(0x3300F0FF), shape = RoundedCornerShape(4.dp)) {
                    Text("[" + m.tag + "] " + m.lg, fontSize = 8.sp, fontWeight = FontWeight.Black, color = Cyan, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                }
                Surface(color = if (m.live) Color(0x44FF2A85) else Color(0x22FFFFFF), shape = RoundedCornerShape(4.dp)) {
                    Text(if (m.live) m.st else m.dt, color = if (m.live) Color.White else Color(0xFF94A3B8), fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(m.h, fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1)
                Surface(color = Color(0xFF161F33), shape = RoundedCornerShape(5.dp), modifier = Modifier.padding(horizontal = 5.dp)) {
                    Text(m.sc, fontWeight = FontWeight.Black, fontSize = 11.sp, color = if (m.live) Lime else Color.White, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                }
                Text(m.a, fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Start, maxLines = 1)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(modifier = Modifier.weight(1f), color = Color(0x99111827), shape = RoundedCornerShape(4.dp)) {
                    Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TARJETAS", fontSize = 7.sp, color = Amber, fontWeight = FontWeight.Black)
                        Text(m.cards, fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(modifier = Modifier.weight(1f), color = Color(0x99111827), shape = RoundedCornerShape(4.dp)) {
                    Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("FAVORITO", fontSize = 7.sp, color = Cyan, fontWeight = FontWeight.Black)
                        Text(m.fav, fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("1 " + m.odds.first, "X " + m.odds.second, "2 " + m.odds.third).forEach { o ->
                    Surface(modifier = Modifier.weight(1f), color = Color(0xCC0F1626), shape = RoundedCornerShape(5.dp), border = BorderStroke(1.dp, CyberBorder)) {
                        Text(o, fontSize = 9.sp, fontWeight = FontWeight.Black, color = Cyan, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 3.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(5.dp))
            CyberHudParlay(m.parlay)
        }
    }
}

class MainActivity : ComponentActivity() {
    private val leagues = listOf(League("arg.1","Argentina","ARG"), League("bra.1","Brasil","BRA"), League("esp.1","LaLiga","ESP"), League("eng.1","Premier","ENG"), League("uefa.champions","Champions","UCL"), League("mex.1","Liga MX","MEX"), League("uru.1","Uruguay","URU"), League("fra.1","Ligue 1","FRA"), League("por.1","Primeira","POR"), League("usa.1","MLS","MLS"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = DeepBg, surface = GlassCard, primary = Cyan)) {
                Surface(modifier = Modifier.fillMaxSize(), color = DeepBg) {
                    var sel by remember { mutableStateOf(leagues[0]) }
                    var matches by remember { mutableStateOf<List<MatchUi>>(emptyList()) }
                    var loading by remember { mutableStateOf(false) }
                    var err by remember { mutableStateOf<String?>(null) }
                    val scope = rememberCoroutineScope()

                    fun fetch(lg: League) {
                        scope.launch {
                            loading = true; err = null
                            try {
                                val list = withContext(Dispatchers.IO) {
                                    val text = URL("https://site.api.espn.com/apis/site/v2/sports/soccer/" + lg.code + "/scoreboard").readText()
                                    val json = JSONObject(text)
                                    val events = json.optJSONArray("events") ?: return@withContext emptyList<MatchUi>()
                                    val lgName = json.optJSONArray("leagues")?.optJSONObject(0)?.optString("name") ?: lg.name
                                    val out = mutableListOf<MatchUi>()
                                    for (i in 0 until events.length()) {
                                        val ev = events.getJSONObject(i)
                                        val comps = ev.optJSONArray("competitions") ?: continue
                                        if (comps.length() == 0) continue
                                        val c = comps.getJSONObject(0)
                                        val competitors = c.optJSONArray("competitors") ?: continue
                                        var h = "Local"; var a = "Visitante"; var hs: String? = null; var ascore: String? = null
                                        for (j in 0 until competitors.length()) {
                                            val cp = competitors.getJSONObject(j)
                                            val name = cp.optJSONObject("team")?.let { it.optString("shortDisplayName").ifBlank { it.optString("displayName") } } ?: "Equipo"
                                            val sc = cp.optString("score", "").ifBlank { null }
                                            if (cp.optString("homeAway") == "home") { h = name; hs = sc } else { a = name; ascore = sc }
                                        }
                                        val statusObj = c.optJSONObject("status")
                                        val state = statusObj?.optJSONObject("type")?.optString("state", "pre") ?: "pre"
                                        val isLive = state == "in"
                                        val clock = statusObj?.optString("displayClock", "") ?: ""
                                        val st = if (isLive) ("EN VIVO " + clock).trim() else if (state == "post") "FINAL" else "PROGRAMADO"
                                        val details = c.optJSONArray("details")
                                        val cards = if (state == "pre" || details == null || details.length() == 0) "Sin datos" else {
                                            var y = 0; var r = 0
                                            for (k in 0 until details.length()) {
                                                val d = details.getJSONObject(k)
                                                if (d.optBoolean("yellowCard") || d.optJSONObject("type")?.optString("text", "")?.contains("Yellow", true) == true) y++
                                                if (d.optBoolean("redCard") || d.optJSONObject("type")?.optString("text", "")?.contains("Red", true) == true) r++
                                            }
                                            (y + r).toString() + " (" + y + " 🟨 • " + r + " 🟥)"
                                        }
                                        val id = ev.optString("id", i.toString())
                                        val fav = CyberEngine.getFav(h, a)
                                        val (star, club) = CyberEngine.getStar(fav, id)
                                        val s = abs((h + a + id).hashCode())
                                        val odds = if (fav == h) Triple("1." + (35 + (s % 20)), "4." + (10 + (s % 30)), "6." + (50 + (s % 40))) else Triple("5." + (20 + (s % 40)), "3." + (80 + (s % 25)), "1." + (45 + (s % 20)))
                                        val score = if (hs != null && ascore != null) (hs + " : " + ascore) else "VS"
                                        val parlay = CyberEngine.buildParlay(h, a, star, club, lg.tag, id)
                                        out.add(MatchUi(id, h, a, score, lgName, lg.tag, st, isLive, fmtDate(ev.optString("date")), fav, cards, odds, parlay))
                                    }
                                    out
                                }
                                matches = list
                            } catch (e: Exception) { err = "Error ESPN: " + (e.localizedMessage ?: "Reintente") }
                            finally { loading = false }
                        }
                    }

                    LaunchedEffect(sel) { fetch(sel) }

                    Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("BET PRO STATS", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White)
                            Button(onClick = { fetch(sel) }, enabled = !loading, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161F33)), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                                Text("↻", fontSize = 13.sp, color = Cyan, fontWeight = FontWeight.Black)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(leagues) { l ->
                                val active = l == sel
                                Surface(modifier = Modifier.clickable { sel = l }, color = if (active) Color(0x4400F0FF) else Color(0x99101626), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, if (active) Cyan else CyberBorder)) {
                                    Text("[" + l.tag + "] " + l.name, modifier = Modifier.padding(horizont
