package com.personal.footballstats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppScreen()
        }
    }
}

// Modelos de datos
data class MatchInfo(
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: String,
    val awayScore: String,
    val status: String,
    val dateStr: String,
    val league: String
)

data class KeyShooter(
    val playerName: String,
    val clubName: String,
    val prediction: String
)

data class BetPrediction(
    val homeOdds: Double,
    val drawOdds: Double,
    val awayOdds: Double,
    val doubleChance: String,
    val keyShooter: KeyShooter,
    val comboRecommendation: String
)

// Motor analítico y de cuotas
object BettingAnalyticsEngine {
    private val teamShooters = mapOf(
        "Boca Juniors" to "Miguel Merentiel",
        "River Plate" to "Facundo Colidio",
        "Racing Club" to "Adrian Martinez",
        "Independiente" to "Gabriel Avalos",
        "San Lorenzo" to "Alexis Cuello",
        "Velez Sarsfield" to "Braian Romero",
        "Estudiantes" to "Guido Carrillo",
        "Talleres" to "Federico Girotti",
        "Real Madrid" to "Vinicius Jr",
        "Barcelona" to "Robert Lewandowski",
        "Atletico Madrid" to "Antoine Griezmann",
        "Manchester City" to "Erling Haaland",
        "Liverpool" to "Mohamed Salah",
        "Arsenal" to "Bukayo Saka",
        "Inter Milan" to "Lautaro Martinez",
        "Juventus" to "Dusan Vlahovic",
        "Bayern Munich" to "Harry Kane",
        "PSG" to "Ousmane Dembele"
    )

    fun calculate(match: MatchInfo): BetPrediction {
        val hScore = match.homeScore.toIntOrNull() ?: 0
        val aScore = match.awayScore.toIntOrNull() ?: 0
        val sumChars = (match.homeTeam.length * 3 + match.awayTeam.length * 2) % 10

        var homeO = 2.10 + (sumChars * 0.08)
        var drawO = 3.10 + ((10 - sumChars) * 0.05)
        var awayO = 2.80 + ((sumChars % 5) * 0.12)

        if (hScore > aScore) {
            homeO = 1.65
            drawO = 3.60
            awayO = 4.80
        } else if (aScore > hScore) {
            homeO = 3.90
            drawO = 3.40
            awayO = 1.85
        }

        val doubleChance = when {
            homeO <= awayO -> "1X (Local o Empate)"
            else -> "X2 (Empate o Visitante)"
        }

        val primaryClub = if (homeO <= awayO) match.homeTeam else match.awayTeam
        val primaryShooter = teamShooters[primaryClub]
            ?: teamShooters[match.homeTeam]
            ?: teamShooters[match.awayTeam]
            ?: "Goleador Principal (${primaryClub})"

        val shooterObj = KeyShooter(
            playerName = primaryShooter,
            clubName = primaryClub,
            prediction = "Principal rematador del club - Pronostico: Mas de 1.5 disparos al arco (1.85)"
        )

        val combo = "${doubleChance} + Mas de 1.5 goles en el partido"

        return BetPrediction(
            homeOdds = (homeO * 100).toInt() / 100.0,
            drawOdds = (drawO * 100).toInt() / 100.0,
            awayOdds = (awayO * 100).toInt() / 100.0,
            doubleChance = doubleChance,
            keyShooter = shooterObj,
            comboRecommendation = combo
        )
    }
}

// Servicio ESPN API
object EspnApiService {
    suspend fun fetchMatches(leagueCode: String): List<MatchInfo> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MatchInfo>()
        try {
            val endpoint = "https://site.api.espn.com/apis/site/v2/sports/soccer/$leagueCode/scoreboard"
            val url = URL(endpoint)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            if (connection.responseCode == 200) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonStr)
                val events = root.optJSONArray("events")

                if (events != null) {
                    for (i in 0 until events.length()) {
                        val event = events.getJSONObject(i)
                        val competitions = event.optJSONArray("competitions") ?: continue
                        if (competitions.length() == 0) continue
                        val comp = competitions.getJSONObject(0)
                        val competitors = comp.optJSONArray("competitors") ?: continue
                        if (competitors.length() < 2) continue

                        var homeName = "Local"
                        var awayName = "Visitante"
                        var homeScore = "-"
                        var awayScore = "-"

                        for (j in 0 until competitors.length()) {
                            val c = competitors.getJSONObject(j)
                            val team = c.optJSONObject("team")
                            val name = team?.optString("displayName") ?: team?.optString("name") ?: "Equipo"
                            val score = c.optString("score", "-")
                            val isHome = c.optString("homeAway", "") == "home"

                            if (isHome) {
                                homeName = name
                                homeScore = if (score.isNotEmpty()) score else "-"
                            } else {
                                awayName = name
                                awayScore = if (score.isNotEmpty()) score else "-"
                            }
                        }

                        val statusObj = comp.optJSONObject("status")
                        val statusType = statusObj?.optJSONObject("type")
                        val state = statusType?.optString("state", "pre") ?: "pre"
                        val detail = statusType?.optString("shortDetail", "") ?: ""

                        val statusDisplay = when (state) {
                            "in" -> "EN VIVO ($detail)"
                            "post" -> "FINAL"
                            else -> "PROXIMO"
                        }

                        val rawDate = comp.optString("date", "")
                        val formattedDate = formatMatchDate(rawDate)

                        list.add(
                            MatchInfo(
                                homeTeam = homeName,
                                awayTeam = awayName,
                                homeScore = homeScore,
                                awayScore = awayScore,
                                status = statusDisplay,
                                dateStr = formattedDate,
                                league = leagueCode
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Manejo silencioso de fallback
        }
        return@withContext list
    }

    private fun formatMatchDate(rawIso: String): String {
        return try {
            val inFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US)
            inFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = inFormat.parse(rawIso)
            if (date != null) {
                val outFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                outFormat.timeZone = TimeZone.getTimeZone("America/Argentina/Buenos_Aires")
                outFormat.format(date) + " hs (ARG)"
            } else {
                "Horario a confirmar"
            }
        } catch (e: Exception) {
            "Horario a confirmar"
        }
    }
}

// Pantalla principal con diseño Dark OLED moderno
@Composable
fun AppScreen() {
    var selectedLeague by remember { mutableStateOf("arg.1") }
    var matches by remember { mutableStateOf<List<MatchInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    val leagues = listOf(
        "arg.1" to "Liga Argentina",
        "esp.1" to "LaLiga Espana",
        "eng.1" to "Premier League",
        "ita.1" to "Serie A",
        "uefa.champions" to "Champions League"
    )

    LaunchedEffect(selectedLeague) {
        isLoading = true
        matches = EspnApiService.fetchMatches(selectedLeague)
        isLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header con Gradiente Premium
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF0F172A))
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ANALYTICS & PRONOSTICOS",
                                color = Color(0xFF06B6D4),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Futbol Match Center",
                            color = Color(0xFFF8FAFC),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "V 2.6 PRO",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Barra horizontal de seleccion de ligas
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                leagues.forEach { (code, name) ->
                    val isSelected = selectedLeague == code
                    val bgCol = if (isSelected) Color(0xFF2563EB) else Color(0xFF1E293B)
                    val textCol = if (isSelected) Color.White else Color(0xFF94A3B8)
                    val borderCol = if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155)

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(bgCol)
                            .border(1.dp, borderCol, RoundedCornerShape(20.dp))
                            .clickable { selectedLeague = code }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = name,
                            color = textCol,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // Lista de partidos o estado de carga
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF38BDF8))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Obteniendo datos de partidos y rematadores...",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                    }
                }
            } else if (matches.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No hay partidos registrados en esta fecha.",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Selecciona otra liga del panel superior.",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(matches) { match ->
                        val analytics = BettingAnalyticsEngine.calculate(match)
                        MatchCardModern(match, analytics)
                    }
                }
            }
        }
    }
}

// Tarjeta moderna de partido con cuotas, rematador y pronosticos
@Composable
fun MatchCardModern(match: MatchInfo, analytics: BetPrediction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            // Fila superior: Estado y Horario ARG
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isLive = match.status.contains("EN VIVO")
                val statusBg = if (isLive) Color(0xFFEF4444) else Color(0xFF1E293B)
                val statusTextCol = if (isLive) Color.White else Color(0xFF38BDF8)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = match.status,
                        color = statusTextCol,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Text(
                    text = match.dateStr,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Marcador y Equipos
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Local
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = match.homeTeam,
                        color = Color(0xFFF1F5F9),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = match.homeScore,
                        color = Color(0xFF38BDF8),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Separador VS
                Text(
                    text = "VS",
                    color = Color(0xFF475569),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // Visitante
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = match.awayTeam,
                        color = Color(0xFFF1F5F9),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = match.awayScore,
                        color = Color(0xFF38BDF8),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Grilla de Cuotas 1X2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MarketBox(
                    title = "1 (Local)",
                    odd = "${analytics.homeOdds}",
                    modifier = Modifier.weight(1f)
          
