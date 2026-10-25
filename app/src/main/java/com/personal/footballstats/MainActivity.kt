package com.personal.footballstats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.util.Locale
import kotlin.math.abs

data class LeagueItem(val id: String, val name: String, val country: String, val slug: String)
data class Team(val name: String, val shortName: String, val score: String)
data class Match(val id: String, val home: Team, val away: Team, val status: String, val live: Boolean)
data class Prediction(
    val odds1X2: String, val doubleC: String, val goals: String,
    val shots: String, val cards: String, val corners: String,
    val star: String, val parlay: String, val parlayOdds: String
)

val LEAGUES = listOf(
    LeagueItem("arg.1", "Liga Profesional", "ARG", "arg.1"),
    LeagueItem("arg.copa_liga", "Copa de la Liga", "ARG", "arg.copa_liga"),
    LeagueItem("arg.copa_arg", "Copa Argentina", "ARG", "arg.copa_arg"),
    LeagueItem("esp.1", "La Liga", "ESP", "esp.1"),
    LeagueItem("eng.1", "Premier League", "ENG", "eng.1"),
    LeagueItem("uefa.champions", "Champions League", "EUR", "uefa.champions"),
    LeagueItem("ita.1", "Serie A", "ITA", "ita.1"),
    LeagueItem("ger.1", "Bundesliga", "GER", "ger.1"),
    LeagueItem("fra.1", "Ligue 1", "FRA", "fra.1"),
    LeagueItem("usa.1", "MLS", "USA", "usa.1"),
    LeagueItem("bra.1", "Brasileirao", "BRA", "bra.1"),
    LeagueItem("conmebol.libertadores", "Libertadores", "SUD", "conmebol.libertadores")
)

object BettingAnalyticsEngine {
    fun calculate(m: Match): Prediction {
        val seed = abs((m.home.name + m.away.name + m.id).hashCode())
        val str = (seed % 100) / 100.0
        val (odds, dc) = if (str > 0.55) {
            val o = String.format(Locale.US, "1: %.2f | X: %.2f | 2: %.2f", 1.70 + (seed % 30) / 100.0, 3.30, 4.20)
            Pair(o, "1X (" + m.home.shortName + " o Empate)")
        } else if (str < 0.40) {
            val o = String.format(Locale.US, "1: %.2f | X: %.2f | 2: %.2f", 4.10, 3.20, 1.85 + (seed % 30) / 100.0)
            Pair(o, "X2 (Empate o " + m.away.shortName + ")")
        } else {
            val o = String.format(Locale.US, "1: %.2f | X: %.2f | 2: %.2f", 2.60, 2.90, 2.80)
            Pair(o, "12 (Gana Cualquiera)")
        }

        val goals = if (seed % 2 == 0) "+2.5 Goles (Cuota 1.95)" else "-2.5 Goles (Cuota 1.80)"
        val shots = if (seed % 3 == 0) "+9.5 Remates al arco (1.88)" else "+8.5 Remates al arco (1.75)"
        val cards = if (seed % 2 == 0) "+4.5 Tarjetas (1.75)" else "+5.5 Tarjetas (1.90)"
        val corners = if (seed % 2 == 0) "+8.5 Corners (1.80)" else "+9.5 Corners (2.00)"

        val starPlayer = when {
            m.home.name.contains("Boca") -> "E. Cavani (+1.5 remates al arco - 1.85)"
            m.home.name.contains("River") -> "M. Borja (+1.5 remates al arco - 1.75)"
            m.home.name.contains("Racing") -> "A. Martinez (+1.5 remates - 1.90)"
            m.home.name.contains("Real") -> "Vinicius Jr (+2.5 tiros totales - 1.65)"
            m.home.name.contains("Barca") || m.home.name.contains("Barcelona") -> "R. Lewandowski (+1.5 remates - 1.70)"
            m.home.name.contains("Miami") -> "L. Messi (Gol o asistencia - 1.50)"
            m.home.name.contains("City") -> "E. Haaland (Gol o asistencia - 1.55)"
            else -> "Delantero Estrella (+1.5 tiros - 1.80)"
        }

        val parlay = dc + " + Mas de 1.5 goles + Mas de 7.5 corners"
        val pOdds = String.format(Locale.US, "%.2f", 4.20 + (seed % 250) / 100.0)

        return Prediction(odds, dc, goals, shots, cards, corners, starPlayer, parlay, pOdds)
    }
}

object EspnApiService {
    suspend fun fetchScoreboard(slug: String): List<Match> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Match>()
        try {
            val url = "https://site.api.espn.com/apis/site/v2/sports/soccer/" + slug + "/scoreboard"
            val text = URL(url).readText()
            val root = JSONObject(text)
            val events = root.optJSONArray("events") ?: return@withContext list

            for (i in 0 until events.length()) {
                val ev = events.getJSONObject(i)
                val id = ev.optString("id", i.toString())
                val comps = ev.optJSONArray("competitions") ?: continue
                if (comps.length() == 0) continue
                val comp = comps.getJSONObject(0)
                val st = comp.optJSONObject("status")
                val stType = st?.optJSONObject("type")
                val statusName = stType?.optString("name", "") ?: ""
                val statusDetail = stType?.optString("shortDetail", "Programado") ?: "Programado"
                val isLive = statusName == "STATUS_IN_PROGRESS" || statusName == "STATUS_HALFTIME"

                val competitors = comp.optJSONArray("competitors") ?: continue
                var home = Team("Local", "LOC", "-")
                var away = Team("Visitante", "VIS", "-")

                for (j in 0 until competitors.length()) {
                    val c = competitors.getJSONObject(j)
                    val tm = c.optJSONObject("team")
                    val name = tm?.optString("displayName", "Equipo") ?: "Equipo"
                    val shortName = tm?.optString("shortDisplayName", name) ?: name
                    val sc = c.optString("score", "-")
                    val isHome = c.optString("homeAway", "home") == "home"
                    if (isHome) home = Team(name, shortName, sc) else away = Team(name, shortName, sc)
                }
                list.add(Match(id, home, away, statusDetail, isLive))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F172A)) {
                    AppScreen()
                }
            }
        }
    }
}

@Composable
fun AppScreen() {
    var selectedLeague by remember { mutableStateOf(LEAGUES[0]) }
    var matches by remember { mutableStateOf<List<Match>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh(l: LeagueItem) {
        scope.launch {
            loading = true
            expandedId = null
            matches = EspnApiService.fetchScoreboard(l.slug)
            loading = false
        }
    }

    LaunchedEffect(selectedLeague) { refresh(selectedLeague) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A))) {
        Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF1E293B)).padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Football Stats & Bets", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("ESPN Multi-Liga & Pronosticos", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
                Button(
                    onClick = { refresh(selectedLeague) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Actualizar", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LEAGUES.forEach { l ->
                    val sel = l.id == selectedLeague.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (sel) Color(0xFF10B981) else Color(0xFF334155))
                            .clickable { selectedLeague = l }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text("[" + l.country + "] " + l.name, color = if (sel) Color.White else Color(0xFFCBD5E1), fontSize = 12.sp)
                    }
                }
            }
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF10B981))
            }
        } else if (matches.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay partidos programados hoy para esta liga", color = Color(0xFF94A3B8), fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                matches.forEach { m ->
                    item(key = m.id) {
                        CardItem(
                            m = m,
                            abierto = (expandedId == m.id),
                            onToggle = { expandedId = if (expandedId == m.id) null else m.id }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CardItem(m: Match, abierto: Boolean, onToggle: () -> Unit) {
    val p = remember(m.id) { BettingAnalyticsEngine.calculate(m) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (m.live) Color(0xFFDC2626) else Color(0xFF0284C7))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(m.status, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(if (abierto) "Ocultar ▲" else "Pronosticos ▼", color = Color(0xFF38BDF8), fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(m.home.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(
                    modifier = Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF0F172A)).padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (m.home.score != "-" && m.home.score.isNotEmpty()) m.home.score + " - " + m.away.score else "VS", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Text(m.away.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = TextAlign.Start, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Pick: " + p.doubleC, color = Color(0xFFFBBF24), fontSize = 11.sp)
                Spacer(modifier = Modifier.weight(1f))
                Text("Parlay Cuota: " + p.parlayOdds, color = Color(0xFF34D399), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            if (abierto) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF334155)))
                Spacer(modifier = Modifier.height(10.dp))

                Text("PRONOSTICOS DEL PARTIDO", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                InfoTag("Cuotas 1X2", p.odds1X2, Color.White)
                InfoTag("Doble Oportunidad", p.doubleC, Color(0xFF60A5FA))
                InfoTag("Goles (+/- 2.5)", p.goals, Color(0xFFF472B6))
                InfoTag("Tiros al Arco", p.shots, Color(0xFFA78BFA))
                InfoTag("Tarjetas", p.cards, Color(0xFFFBBF24))
                InfoTag("Corners", p.corners, Color(0xFF2DD4BF))
                InfoTag("Estrella", p.star, Color(0xFF5EEAD4))
                InfoTag("Parlay", p.parlay + " (Cuota " + p.parlayOdds + ")", Color(0xFFA5B4FC))
            }
        }
    }
}

@Composable
fun InfoTag(label: String, value: String, col: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF0F172A)).padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFF94A3B8), fontSize = 11.sp)
        Spacer(modifier = Modifier.weight(1f))
        Text(value, color = col, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}
