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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

data class MatchUi(val id: String, val h: String, val a: String, val score: String, val lg: String, val tag: String, val st: String, val live: Boolean, val date: String, val fav: String, val dc: String, val star: String, val club: String, val cards: String, val odds: Triple<String, String, String>, val parlay: String)
data class League(val code: String, val name: String, val tag: String)

object BetEngine {
    private val STARS = mapOf(
        "boca" to ("Edinson Cavani" to "Boca Juniors"), "river" to ("Miguel Borja" to "River Plate"),
        "racing" to ("Maravilla Martínez" to "Racing Club"), "san lorenzo" to ("Iker Muniain" to "San Lorenzo"),
        "velez" to ("Braian Romero" to "Vélez Sarsfield"), "independiente" to ("Gabriel Ávalos" to "Independiente"),
        "talleres" to ("Federico Girotti" to "Talleres"), "estudiantes" to ("Guido Carrillo" to "Estudiantes"),
        "madrid" to ("Vinícius Jr" to "Real Madrid"), "barcelona" to ("Robert Lewandowski" to "FC Barcelona"),
        "atletico" to ("Julián Álvarez" to "Atlético Madrid"), "city" to ("Erling Haaland" to "Manchester City"),
        "liverpool" to ("Mohamed Salah" to "Liverpool"), "arsenal" to ("Bukayo Saka" to "Arsenal"),
        "chelsea" to ("Cole Palmer" to "Chelsea"), "psg" to ("Bradley Barcola" to "PSG"),
        "sporting" to ("Viktor Gyökeres" to "Sporting CP"), "benfica" to ("Ángel Di María" to "Benfica"),
        "flamengo" to ("Pedro Guilherme" to "Flamengo"), "palmeiras" to ("Estêvão" to "Palmeiras"),
        "botafogo" to ("Luiz Henrique" to "Botafogo"), "america" to ("Henry Martín" to "Club América"),
        "cruz azul" to ("Ángel Sepúlveda" to "Cruz Azul"), "penarol" to ("Maxi Silvera" to "Peñarol"),
        "nacional" to ("Rubén Bentancourt" to "Nacional"), "miami" to ("Lionel Messi" to "Inter Miami")
    )
    private val GIANTS = STARS.keys
    fun getFav(h: String, a: String): String {
        val hl = h.lowercase(); val al = a.lowercase()
        val hG = GIANTS.any { hl.contains(it) }; val aG = GIANTS.any { al.contains(it) }
        return if (hG && !aG) h else if (!hG && aG) a else h
    }
    fun getStar(fav: String, id: String): Pair<String, String> {
        val fl = fav.lowercase()
        val found = STARS.keys.firstOrNull { fl.contains(it) }
        return if (found != null) STARS[found]!! else {
            val alt = listOf("Mateo Retegui", "Valentín Castellanos", "Facundo Colidio", "Milton Giménez")
            Pair(alt[abs((id + fav).hashCode()) % alt.size], fav)
        }
    }
}

fun formatTime(d: String?): String {
    if (d.isNullOrBlank() || !d.contains("T")) return "Hoy"
    return try {
        val p = d.split("T"); val dp = p[0].split("-"); val tp = p[1].substring(0, 5).split(":")
        var day = dp[2].toInt(); var h = tp[0].toInt() - 3
        if (h < 0) { h += 24; day -= 1 } else if (h >= 24) { h -= 24; day += 1 }
        val dStr = if (day < 10) "0$day" else "$day"
        val hStr = if (h < 10) "0$h" else "$h"
        "$dStr/${dp[1]} • $hStr:${tp[1]} hs"
    } catch (e: Exception) { "Hoy" }
}

val DarkBg = Color(0xFF0A0A0C); val CardBg = Color(0xFF12141A); val CardBorder = Color(0xFF1E2330)
val NeonLime = Color(0xFF99FF00); val Cyan = Color(0xFF00F0FF); val Amber = Color(0xFFFFB800)

@Composable
fun OddsPill(label: String, odd: String, m: Modifier = Modifier) {
    Surface(modifier = m, color = Color(0xFF161A24), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, CardBorder)) {
        Column(modifier = Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 8.sp, color = Color(0xFF94A3B8), textAlign = TextAlign.Center)
            Text(odd, fontSize = 11.sp, fontWeight = FontWeight.Black, color = Cyan, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun MatchCard(m: MatchUi) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, CardBorder)) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color(0xFF101B2B), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, Color(0xFF0284C7))) {
                    Text("[${m.tag}] ${m.lg}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Cyan, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                }
                Surface(color = if (m.live) Color(0xFFFF3B30) else Color(0xFF0F1E19), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, if (m.live) Color(0xFFFF6961) else Color(0xFF059669))) {
                    Text(if (m.live) m.st else m.date, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                }
            }
            Spacer(modifier = Modifier.height(5.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(m.h, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Surface(color = Color(0xFF1A2234), shape = RoundedCornerShape(5.dp), modifier = Modifier.padding(horizontal = 5.dp)) {
                    Text(m.score, fontWeight = FontWeight.Black, fontSize = 11.sp, color = if (m.live) NeonLime else Color.White, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                }
                Text(m.a, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            }
            Spacer(modifier = Modifier.height(5.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Surface(modifier = Modifier.weight(1f), color = Color(0xFF161A24), shape = RoundedCornerShape(5.dp), border = BorderStroke(1.dp, Color(0xFF232838))) {
                    Column(modifier = Modifier.padding(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TARJETAS REALES", fontSize = 8.sp, color = Amber, fontWeight = FontWeight.Bold)
                        Text(m.cards, fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(modifier = Modifier.weight(1f), color = Color(0xFF161A24), shape = RoundedCornerShape(5.dp), border = BorderStroke(1.dp, Color(0xFF232838))) {
                    Column(modifier = Modifier.padding(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("FAVORITO", fontSize = 8.sp, color = Cyan, fontWeight = FontWeight.Bold)
                        Text(m.fav, fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
            Spacer(modifier = Modifier.height(5.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OddsPill("1 (Local)", m.odds.first, Modifier.weight(1f))
                OddsPill("X (Empate)", m.odds.second, Modifier.weight(1f))
                OddsPill("2 (Visitante)", m.odds.third, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(5.dp))
            Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF0D1E16), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, Color(0xFF13583C))) {
                Column(modifier = Modifier.padding(6.dp)) {
                    Text("⚡ ATACANTE TIROS AL ARCO: ${m.star} (${m.club})", fontSize = 9.sp, fontWeight = FontWeight.Black, color = NeonLime)
                    Text("+1.5 disparos al arco • Prob: 82% • Cuota: 1.85", fontSize = 8.sp, color = Color(0xFFA7F3D0))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF1F1A08), shape = RoundedCornerShape(5.dp), border = BorderStroke(1.dp, Color(0xFF854D0E))) {
                Text("🔥 PARLAY: ${m.parlay}", fontSize = 8.sp, color = Color(0xFFFEF08A), modifier = Modifier.padding(5.dp))
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    private val leagues = listOf(League("arg.1","Argentina","ARG"), League("bra.1","Brasil","BRA"), League("esp.1","LaLiga","ESP"), League("eng.1","Premier","ENG"), League("uefa.champions","Champions","UCL"), League("mex.1","Liga MX","MEX"), League("uru.1","Uruguay","URU"), League("fra.1","Ligue 1","FRA"), League("por.1","Primeira","POR"), League("usa.1","MLS","MLS"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = DarkBg, surface = CardBg, primary = NeonLime)) {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
                    var sel by remember { mutableStateOf(leagues[0]) }
                    var matches by remember { mutableStateOf<List<MatchUi>>(emptyList()) }
                    var loading by remember { mutableStateOf(false) }
                    var error by remember { mutableStateOf<String?>(null) }
                    val scope = rememberCoroutineScope()

                    fun fetch(lg: League) {
                        scope.launch {
                            loading = true; error = null
                            try {
                                val list = withContext(Dispatchers.IO) {
                                    val text = URL("https://site.api.espn.com/apis/site/v2/sports/soccer/${lg.code}/scoreboard").readText()
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
                                            val isHome = cp.optString("homeAway") == "home"
                                            val name = cp.optJSONObject("team")?.let { it.optString("shortDisplayName").ifBlank { it.optString("displayName") } } ?: "Equipo"
                                            val sc = cp.optString("score", "").ifBlank { null }
                                            if (isHome) { h = name; hs = sc } else { a = name; ascore = sc }
                                        }
                                        val state = c.optJSONObject("status")?.optJSONObject("type")?.optString("state", "pre") ?: "pre"
                                        val isLive = state == "in"
                                        val clock = c.optJSONObject("status")?.optString("displayClock", "") ?: ""
                                        val st = if (isLive) "EN VIVO $clock".trim() else if (state == "post") "FINAL" else "PROGRAMADO"
                                        val details = c.optJSONArray("details")
                                        val cards = if (state == "pre" || details == null || details.length() == 0) "Sin datos" else {
                                            var y = 0; var r = 0
                                            for (k in 0 until details.length()) {
                                                val d = details.getJSONObject(k)
                                                if (d.optBoolean("yellowCard") || d.optJSONObject("type")?.optString("text", "")?.contains("Yellow", true) == true) y++
                                                if (d.optBoolean("redCard") || d.optJSONObject("type")?.optString("text", "")?.contains("Red", true) == true) r++
                                            }
                                            "${y + r} ($y 🟨 • $r 🟥)"
                                        }
                                        val id = ev.optString("id", "$i")
                                        val fav = BetEngine.getFav(h, a)
                                        val (star, club) = BetEngine.getStar(fav, id)
                                        val s = abs((h + a + id).hashCode())
                                        val odds = if (fav == h) Triple("1." + (35 + (s % 20)), "4." + (10 + (s % 30)), "6." + (50 + (s % 40))) else Triple("5." + (20 + (s % 40)), "3." + (80 + (s % 25)), "1." + (45 + (s % 20)))
                                        val dc = if (fav == a) "X2 ($a/Empate)" else "1X ($h/Empate)"
                                        val parlay = "$dc + Goles +1.5 + $star tiro al arco"
                                        val score = if (hs != null && ascore != null) "$hs : $ascore" else "VS"
                                        out.add(MatchUi(id, h, a, score, lgName, lg.tag, st, isLive, formatTime(ev.optString("date")), fav, dc, star, club, cards, odds, parlay))
                                    }
                                    out
                                }
                                matches = list
                            } catch (e: Exception) { error = "Error ESPN: ${e.localizedMessage ?: "Reintente"}" }
                            finally { loading = false }
                        }
                    }

                    LaunchedEffect(sel) { fetch(sel) }

                    Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("BET PRO STATS", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White)
                            Button(onClick = { fetch(sel) }, enabled = !loading, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                                Text("↻", fontSize = 14.sp, color = Cyan, fontWeight = FontWeight.Black)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            items(leagues) { l ->
                                val active = l == sel
                                Surface(modifier = Modifier.clickable { sel = l }, color = if (active) Color(0xFF10281E) else Color(0xFF161A24), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, if (active) NeonLime else CardBorder)) {
                                    Text("[${l.tag}] ${l.name}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (active) NeonLime else Color(0xFF94A3B8))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        if (loading && matches.isEmpty()) Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = NeonLime) }
                        else if (error != null && matches.isEmpty()) Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text(error ?: "", color = Color(0xFFFF3B30), fontSize = 12.sp) }
                        else if (matches.isEmpty()) Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("Sin partidos hoy en ${sel.name}", color = Color(0xFF64748B), fontSize = 12.sp) }
                        else {
                            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(matches, key = { it.id }) { m -> MatchCard(m = m) }
                            }
                        }
                    }
                }
            }
        }
    }
}
