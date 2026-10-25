package com.personal.footballstats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs

data class LeagueItem(
    val id: String,
    val name: String,
    val country: String,
    val slug: String
)

data class Team(
    val id: String,
    val name: String,
    val shortName: String,
    val score: String
)

data class Match(
    val id: String,
    val homeTeam: Team,
    val awayTeam: Team,
    val statusText: String,
    val isLive: Boolean,
    val isFinished: Boolean,
    val startTime: String,
    val leagueName: String
)

data class Prediction(
    val homeOdds: String,
    val drawOdds: String,
    val awayOdds: String,
    val recommendedPick: String,
    val doubleChance: String,
    val overUnderPick: String,
    val shotsOnTargetPick: String,
    val totalCardsPick: String,
    val cornersPick: String,
    val starPlayerName: String,
    val starPlayerTeam: String,
    val starPlayerProp: String,
    val parlayPick: String,
    val parlayOdds: String
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
    private val starPlayers = mapOf(
        "Boca Juniors" to Pair("Edinson Cavani", "+1.5 remates al arco (Cuota 1.85)"),
        "Boca" to Pair("Edinson Cavani", "+1.5 remates al arco (Cuota 1.85)"),
        "River Plate" to Pair("Miguel Borja", "+1.5 remates al arco (Cuota 1.75)"),
        "River" to Pair("Miguel Borja", "+1.5 remates al arco (Cuota 1.75)"),
        "Racing Club" to Pair("Adrian Martinez", "+1.5 remates al arco (Cuota 1.90)"),
        "Racing" to Pair("Adrian Martinez", "+1.5 remates al arco (Cuota 1.90)"),
        "Independiente" to Pair("Gabriel Avalos", "+1.5 remates al arco (Cuota 2.10)"),
        "San Lorenzo" to Pair("Alexis Cuello", "+0.5 remates al arco (Cuota 1.70)"),
        "Real Madrid" to Pair("Vinicius Jr", "+2.5 remates totales (Cuota 1.65)"),
        "Barcelona" to Pair("Robert Lewandowski", "+1.5 remates al arco (Cuota 1.70)"),
        "Atletico Madrid" to Pair("Julian Alvarez", "+1.5 remates al arco (Cuota 1.80)"),
        "Manchester City" to Pair("Erling Haaland", "Gol o asistencia (Cuota 1.55)"),
        "Arsenal" to Pair("Bukayo Saka", "+1.5 remates al arco (Cuota 1.95)"),
        "Liverpool" to Pair("Mohamed Salah", "+1.5 remates al arco (Cuota 1.65)"),
        "Inter Miami CF" to Pair("Lionel Messi", "Gol o asistencia (Cuota 1.50)"),
        "Inter Miami" to Pair("Lionel Messi", "Gol o asistencia (Cuota 1.50)"),
        "Paris Saint-Germain" to Pair("Ousmane Dembele", "+2.5 remates (Cuota 1.80)"),
        "Bayern Munich" to Pair("Harry Kane", "+1.5 remates al arco (Cuota 1.60)"),
        "Inter" to Pair("Lautaro Martinez", "+1.5 remates al arco (Cuota 1.75)"),
        "Flamengo" to Pair("Pedro", "+1.5 remates al arco (Cuota 1.80)"),
        "Palmeiras" to Pair("Raphael Veiga", "+1.5 remates (Cuota 1.90)")
    )

    fun calculate(match: Match): Prediction {
        val seed = abs((match.homeTeam.name + match.awayTeam.name + match.id).hashCode())
        val homeStrength = (seed % 100) / 100.0
        val isDerby = match.leagueName.contains("Argentina") || match.homeTeam.name.contains("Boca") || match.homeTeam.name.contains("River") || match.homeTeam.name.contains("Real")

        val hOdds: String
        val dOdds: String
        val aOdds: String
        val recPick: String
        val doubleC: String

        if (homeStrength > 0.55) {
            hOdds = String.format(Locale.US, "%.2f", 1.65 + (seed % 35) / 100.0)
            dOdds = String.format(Locale.US, "%.2f", 3.20 + (seed % 40) / 100.0)
            aOdds = String.format(Locale.US, "%.2f", 4.00 + (seed % 80) / 100.0)
            recPick = "Gana " + match.homeTeam.shortName + " (1)"
            doubleC = "1X (" + match.homeTeam.shortName + " o Empate)"
        } else if (homeStrength < 0.40) {
            hOdds = String.format(Locale.US, "%.2f", 3.40 + (seed % 60) / 100.0)
            dOdds = String.format(Locale.US, "%.2f", 3.10 + (seed % 30) / 100.0)
            aOdds = String.format(Locale.US, "%.2f", 1.90 + (seed % 40) / 100.0)
            recPick = "Gana " + match.awayTeam.shortName + " (2)"
            doubleC = "X2 (Empate o " + match.awayTeam.shortName + ")"
        } else {
            hOdds = String.format(Locale.US, "%.2f", 2.45 + (seed % 30) / 100.0)
            dOdds = String.format(Locale.US, "%.2f", 2.95 + (seed % 20) / 100.0)
            aOdds = String.format(Locale.US, "%.2f", 2.75 + (seed % 30) / 100.0)
            recPick = "Empate o Partido Cerrado (X)"
            doubleC = "12 (Cualquiera gana, sin empate)"
        }

        val overUnder = if (seed % 2 == 0) {
            "Mas de 2.5 Goles (Cuota 1.92)"
        } else {
            "Menos de 2.5 Goles (Cuota 1.80)"
        }

        val shots = if (seed % 3 == 0) {
            "Mas de 9.5 Remates al arco (Cuota 1.88)"
        } else {
            "Mas de 8.5 Remates al arco (Cuota 1.72)"
        }

        val cards = if (isDerby) {
            "Mas de 5.5 Tarjetas (Partido de alta friccion, Cuota 1.85)"
        } else {
            "Mas de 4.5 Tarjetas totales (Cuota 1.75)"
        }

        val corners = if (seed % 2 == 0) {
            "Mas de 8.5 Corners (Cuota 1.80)"
        } else {
            "Mas de 9.5 Corners (Cuota 2.05)"
        }

        var starPair = starPlayers[match.homeTeam.name]
        var starTeam = match.homeTeam.name
        if (starPair == null) {
            starPair = starPlayers[match.awayTeam.name]
            starTeam = match.awayTeam.name
        }
        if (starPair == null) {
            val fallbackPlayer = if (seed % 2 == 0) "Centrodelantero titular" else "Extremo goleador"
            starPair = Pair(fallbackPlayer, "+1.5 remates totales (Cuota 1.80)")
            starTeam = match.homeTeam.shortName
        }

        val parlay = doubleC + " + Mas de 1.5 goles + Mas de 7.5 corners"
        val parlayOdds = String.format(Locale.US, "%.2f", 4.20 + (seed % 250) / 100.0)

        return Prediction(
            homeOdds = hOdds,
            drawOdds = dOdds,
            awayOdds = aOdds,
            recommendedPick = recPick,
            doubleChance = doubleC,
            overUnderPick = overUnder,
            shotsOnTargetPick = shots,
            totalCardsPick = cards,
            cornersPick = corners,
            starPlayerName = starPair.first,
            starPlayerTeam = starTeam,
            starPlayerProp = starPair.second,
            parlayPick = parlay,
            parlayOdds = parlayOdds
        )
    }
}

object EspnApiService {
    suspend fun fetchScoreboard(leagueSlug: String): List<Match> = withContext(Dispatchers.IO) {
        val resultList = mutableListOf<Match>()
        try {
            val urlString = "https://site.api.espn.com/apis/site/v2/sports/soccer/" + leagueSlug + "/scoreboard"
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                val root = JSONObject(response.toString())
                val leaguesArray = root.optJSONArray("leagues")
                val leagueDisplayName = if (leaguesArray != null && leaguesArray.length() > 0) {
                    leaguesArray.getJSONObject(0).optString("name", "Liga")
                } else "Liga"

                val events = root.optJSONArray("events") ?: JSONArray()
                for (i in 0 until events.length()) {
                    val event = events.getJSONObject(i)
                    val id = event.optString("id", i.toString())
                    val dateStr = event.optString("date", "")
                    val competitions = event.optJSONArray("competitions") ?: continue
                    if (competitions.length() == 0) continue

                    val comp = competitions.getJSONObject(0)
                    val statusObj = comp.optJSONObject("status")
                    val statusType = statusObj?.optJSONObject("type")
                    val statusName = statusType?.optString("name", "STATUS_SCHEDULED") ?: "STATUS_SCHEDULED"
                    val statusDetail = statusType?.optString("shortDetail", "Programado") ?: "Programado"

                    val isLive = statusName == "STATUS_IN_PROGRESS" || statusName == "STATUS_HALFTIME"
                    val isFinished = statusName == "STATUS_FINAL"

                    val competitors = comp.optJSONArray("competitors") ?: continue
                    var homeTeam = Team("", "Local", "LOC", "-")
                    var awayTeam = Team("", "Visitante", "VIS", "-")

                    for (j in 0 until competitors.length()) {
                        val c = competitors.getJSONObject(j)
                        val homeAway = c.optString("homeAway", "home")
                        val teamObj = c.optJSONObject("team")
                        val tName = teamObj?.optString("displayName", if (homeAway == "home") "Local" else "Visitante") ?: ""
                        val tShort = teamObj?.optString("shortDisplayName", tName) ?: tName
                        val score = c.optString("score", "-")
                        val tId = teamObj?.optString("id", "") ?: ""

                        val team = Team(id = tId, name = tName, shortName = tShort, score = score)
                        if (homeAway == "home") {
                            homeTeam = team
                        } else {
                            awayTeam = team
                        }
                    }

                    resultList.add(
                        Match(
                            id = id,
                            homeTeam = homeTeam,
                            awayTeam = awayTeam,
                            statusText = statusDetail,
                            isLive = isLive,
                            isFinished = isFinished,
                            startTime = dateStr,
                            leagueName = leagueDisplayName
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        resultList
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A)
                ) {
                    FootballAppScreen()
                }
            }
        }
    }
}

@Composable
fun FootballAppScreen() {
    var selectedLeague by remember { mutableStateOf(LEAGUES[0]) }
    var matches by remember { mutableStateOf<List<Match>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var expandedMatchId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun loadMatches(league: LeagueItem) {
        scope.launch {
            isLoading = true
            errorMessage = null
            expandedMatchId = null
            try {
                val data = EspnApiService.fetchScoreboard(league.slug)
                matches = data
                if (data.isEmpty()) {
                    errorMessage = "No hay partidos programados hoy para " + league.name
                }
            } catch (e: Exception) {
                errorMessage = "Error al conectar con ESPN: " + e.localizedMessage
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedLeague) {
        loadMatches(selectedLeague)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Football Stats & Bet Picks",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Resultados en Vivo y Pronosticos ESPN",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }

                Button(
                    onClick = { loadMatches(selectedLeague) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Actualizar", color = Color.White, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LEAGUES.forEach { league ->
                    val isSelected = league.id == selectedLeague.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) Color(0xFF10B981) else Color(0xFF334155))
                            .clickable {
                                if (selectedLeague.id != league.id) {
                                    selectedLeague = league
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "[" + league.country + "] " + league.name,
                            color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF10B981))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Cargando partidos de " + selectedLeague.name + "...",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp
                    )
                }
            }
        } else if (matches.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = errorMessage ?: "No hay partidos en esta fecha",
                        color = Color(0xFFCBD5E1),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { loadMatches(selectedLeague) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                    ) {
                        Text("Reintentar")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                matches.forEach { match ->
                    item(key = match.id) {
                        MatchCard(
                            match = match
