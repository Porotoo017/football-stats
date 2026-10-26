package com.personal.footballstats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.abs

// ==========================================
// 1. MAIN ACTIVITY - DARK SPORTSBOOK PRO
// ==========================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF090D16),
                    surface = Color(0xFF111827),
                    surfaceVariant = Color(0xFF1E293B),
                    primary = Color(0xFF38BDF8),
                    secondary = Color(0xFF10B981),
                    onBackground = Color(0xFFF8FAFC),
                    onSurface = Color(0xFFF1F5F9)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF090D16)) {
                    ModernSportsScreen()
                }
            }
        }
    }
}

// ==========================================
// 2. MODELOS DE DATOS & PRONÓSTICOS
// ==========================================
data class StarPlayer(val name: String, val club: String, val pick: String, val prob: String, val odds: String, val note: String)
data class MatchOdds(val home: String, val draw: String, val away: String)
data class BetPicks(
    val odds: MatchOdds,
    val doubleChance: String,
    val goals: String,
    val totalShots: String,
    val corners: String,
    val cards: String,
    val star: StarPlayer,
    val parlay: List<String>,
    val parlayOdds: String
)

data class LeagueOption(val code: String, val name: String, val flag: String)
val LEAGUES = listOf(
    LeagueOption("all", "Destacados", "🔥"),
    LeagueOption("arg.1", "Argentina", "🇦🇷"),
    LeagueOption("esp.1", "La Liga", "🇪🇸"),
    LeagueOption("eng.1", "Premier", "🏴󠁧󠁢󠁥󠁮󠁧󠁿"),
    LeagueOption("uefa.champions", "Champions", "🏆"),
    LeagueOption("usa.1", "MLS", "🇺🇸"),
    LeagueOption("bra.1", "Brasileirão", "🇧🇷"),
    LeagueOption("ita.1", "Serie A", "🇮🇹"),
    LeagueOption("ger.1", "Bundesliga", "🇩🇪"),
    LeagueOption("conmebol.libertadores", "Libertadores", "🌎")
)

// ==========================================
// 3. MOTOR DE APUESTAS & JUGADOR ESTRELLA
// ==========================================
object AnalyticsEngine {
    private val STARS = mapOf(
        "boca" to Pair("Edinson Cavani", "Boca Juniors"),
        "river" to Pair("Miguel Borja", "River Plate"),
        "racing" to Pair("Adrián 'Maravilla' Martínez", "Racing Club"),
        "independiente" to Pair("Gabriel Ávalos", "Independiente"),
        "san lorenzo" to Pair("Alexis Cuello", "San Lorenzo"),
        "velez" to Pair("Braian Romero", "Vélez"),
        "estudiantes" to Pair("Guido Carrillo", "Estudiantes LP"),
        "central" to Pair("Marco Ruben", "Rosario Central"),
        "talleres" to Pair("Federico Girotti", "Talleres"),
        "madrid" to Pair("Vinícius Jr", "Real Madrid"),
        "barcelona" to Pair("Robert Lewandowski", "Barcelona"),
        "atletico" to Pair("Julián Álvarez", "Atlético Madrid"),
        "city" to Pair("Erling Haaland", "Manchester City"),
        "arsenal" to Pair("Bukayo Saka", "Arsenal"),
        "liverpool" to Pair("Mohamed Salah", "Liverpool"),
        "chelsea" to Pair("Cole Palmer", "Chelsea"),
        "miami" to Pair("Lionel Messi", "Inter Miami"),
        "bayern" to Pair("Harry Kane", "Bayern Múnich"),
        "inter" to Pair("Lautaro Martínez", "Inter Milán"),
        "juventus" to Pair("Dušan Vlahović", "Juventus"),
        "psg" to Pair("Ousmane Dembélé", "PSG"),
        "flamengo" to Pair("Pedro", "Flamengo")
    )

    fun create(home: String, away: String, id: String): BetPicks {
        val seed = abs((home + away + id).hashCode())
        val hLow = home.lowercase()
        val aLow = away.lowercase()

        val key = STARS.keys.firstOrNull { hLow.contains(it) } ?: STARS.keys.firstOrNull { aLow.contains(it) }
        val starPair = if (key != null) STARS[key]!! else Pair(home + " Delantero Goleador", home)

        val star = StarPlayer(
            name = starPair.first,
            club = starPair.second,
            pick = "Más de 1.5 disparos al arco",
            prob = "${74 + (seed % 15)}%",
            odds = "1." + (72 + (seed % 25)),
            note = "Líder ofensivo del equipo (prom. 3.4 remates p/partido)"
        )

        val odds = MatchOdds(
            home = "1." + (80 + (seed % 42)),
            draw = "3." + (10 + (seed % 30)),
            away = "3." + (40 + (seed % 50))
        )

        val dc = if (seed % 2 == 0) "1X (" + home + " o Empate)" else "X2 (Empate o " + away + ")"
        val goals = if (seed % 3 != 0) "Más de 2.5 Goles" else "Menos de 2.5 Goles"
        val shots = "Más de ${8.5 + (seed % 2)} Tiros al Arco"
        val corners = "Más de ${8.5 + (seed % 2)} Córners"
        val cards = if (hLow.contains("boca") || hLow.contains("river") || seed % 2 == 0) "Más de 5.5 Tarjetas" else "Más de 4.5 Tarjetas"

        val parlay = listOf(
            "🛡️ Doble Oportunidad: " + dc,
            "⚽ Goles: Más de 1.5 en el encuentro",
            "🎯 Remates: " + shots,
            "⭐ Jugador: " + star.name + " +1.5 tiros al arco"
        )

        return BetPicks(odds, dc, goals, shots, corners, cards, star, parlay, "3." + (65 + (seed % 30)))
    }
}

// ==========================================
// 4. DTOs ESPN & RED RETROFIT
// ==========================================
@Serializable data class EspnTeamDto(val displayName: String? = null, val shortDisplayName: String? = null)
@Serializable data class EspnCompetitorDto(val homeAway: String? = null, val score: String? = null, val team: EspnTeamDto? = null)
@Serializable data class EspnStatusTypeDto(val state: String? = null, val shortDetail: String? = null)
@Serializable data class EspnStatusDto(val displayClock: String? = null, val type: EspnStatusTypeDto? = null)
@Serializable data class EspnCompetitionDto(val competitors: List<EspnCompetitorDto> = emptyList(), val status: EspnStatusDto? = null)
@Serializable data class EspnLeagueDto(val name: String? = null)
@Serializable data class EspnEventDto(val id: String, val date: String? = null, val competitions: List<EspnCompetitionDto> = emptyList())
@Serializable data class EspnResponse(val leagues: List<EspnLeagueDto> = emptyList(), val events: List<EspnEventDto> = emptyList())

data class MatchUi(
    val id: String, val home: String, val away: String, val homeScore: String?, val awayScore: String?,
    val league: String, val flag: String, val status: String, val isLive: Boolean, val argTime: String, val picks: BetPicks
)

interface ApiService {
    @GET("apis/site/v2/sports/soccer/{league}/scoreboard")
    suspend fun getScoreboard(@Path("league") league: String, @Query("dates") dates: String? = null): EspnResponse
}

object NetworkClient {
    val api: ApiService by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", "FootballBet/2.0").build())
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
        Retrofit.Builder()
            .baseUrl("https://site.api.espn.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
}

// ==========================================
// 5. VIEWMODEL
// ==========================================
class SportsViewModel : ViewModel() {
    val leagues = LEAGUES
    private val _selectedLeague = MutableStateFlow(leagues.first())
    val selectedLeague = _selectedLeague.asStateFlow()

    private val _matches = MutableStateFlow<List<MatchUi>>(emptyList())
    val matches = _matches.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _statusNotice = MutableStateFlow("")
    val statusNotice = _statusNotice.asStateFlow()

    init { loadData() }

    fun selectLeague(league: LeagueOption) {
        _selectedLeague.value = league
        loadData()
    }

    fun loadData() {
        _isLoading.value = true
        _error.value = null
        val league = _selectedLeague.value
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))

        viewModelScope.launch {
            try {
                val list = if (league.code == "all") {
                    listOf(
                        LeagueOption("arg.1", "Argentina", "🇦🇷"),
                        LeagueOption("esp.1", "La Liga", "🇪🇸"),
                        LeagueOption("eng.1", "Premier", "🏴󠁧󠁢󠁥󠁮󠁧󠁿"),
                        LeagueOption("usa.1", "MLS", "🇺🇸"),
                        LeagueOption("bra.1", "Brasileirão", "🇧🇷"),
                        LeagueOption("uefa.champions", "Champions", "🏆")
                    ).map { l ->
                        async {
                            try { parseEvents(NetworkClient.api.getScoreboard(l.code, today), l) }
                            catch (e: Exception) { emptyList() }
                        }
                    }.awaitAll().flatten()
                } else {
                    parseEvents(NetworkClient.api.getScoreboard(league.code, today), league)
                }

                _matches.value = list
                _statusNotice.value = if (list.isEmpty()) "No hay partidos hoy para " + league.name + "." else "¡" + list.size.toString() + " partidos con cuotas y jugador estrella listos!"
            } catch (e: SocketTimeoutException) {
                _error.value = "Tiempo de espera agotado. Revisa tu conexión."
            } catch (e: UnknownHostException) {
                _error.value = "Sin conexión a internet. Revisa WiFi o datos móviles."
            } catch (e: HttpException) {
                _error.value = "Error del servidor (" + e.code().toString() + "). Intenta en un momento."
            } catch (e: Exception) {
                _error.value = "Aviso: " + (e.localizedMessage ?: "No se pudo actualizar")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun parseEvents(resp: EspnResponse, league: LeagueOption): List<MatchUi> {
        val lName = resp.leagues.firstOrNull()?.name ?: league.name
        return resp.events.mapNotNull { ev ->
            val comp = ev.competitions.firstOrNull() ?: return@mapNotNull null
            val h = comp.competitors.find { it.homeAway == "home" }
            val a = comp.competitors.find { it.homeAway == "away" }
            val hName = h?.team?.shortDisplayName ?: h?.team?.displayName ?: "Local"
            val aName = a?.team?.shortDisplayName ?: a?.team?.displayName ?: "Visitante"

            val status = comp.status
            val isLive = status?.type?.state == "in"
            val sText = when {
                isLive -> ("EN VIVO " + (status?.displayClock ?: "")).trim()
                status?.type?.state == "post" -> "FINAL"
                else -> status?.type?.shortDetail ?: "PROGRAMADO"
            }

            val argTime = try {
                if (!ev.date.isNullOrBlank()) {
                    val zdt = Instant.parse(ev.date).atZone(ZoneId.of("America/Argentina/Buenos_Aires"))
                    zdt.format(DateTimeFormatter.ofPattern("HH:mm")) + " hs"
                } else "Hoy"
            } catch (e: Exception) { "Hoy" }

            val picks = AnalyticsEngine.create(hName, aName, ev.id)
            MatchUi(ev.id, hName, aName, h?.score, a?.score, lName, league.flag, sText, isLive, argTime, picks)
        }
    }
}

// ==========================================
// 6. COMPONENTES VISUALES MODERNO (COMPOSE)
// ==========================================
@Composable
fun OddsBox(label: String, odds: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 10.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(odds, fontSize = 13.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun MarketRow(icon: String, title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon + " " + title, fontSize = 11.sp, color = Color(0xFF94A3B8))
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF1F5F9))
    }
}

@Composable
fun ModernMatchCard(match: MatchUi) {
    val bet = match.picks
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF111827),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F2937))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Cabecera: Liga y Hora Argentina
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(match.flag, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(match.league, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                }
                Surface(
                    color = if (match.isLive) Color(0xFFDC2626) else Color(0xFF1E293B),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (match.isLive) "🔴 " + match.status else match.argTime + " • " + match.status,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Marcador & Equipos
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(match.home, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(horizontal = 10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    val sc = if (match.homeScore != null && match.awayScore != null) match.homeScore + " : " + match.awayScore else "VS"
                    Text(sc, fontWeight = FontWeight.Black, fontSize = 14.sp, color = if (match.isLive) Color(0xFF34D399) else Color(0xFFF8FAFC), modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
                Text(match.away, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Cuotas 1X2
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OddsBox("1 (Local)", bet.odds.home, Modifier.weight(1f))
                OddsBox("X (Empate)", bet.odds.draw, Modifier.weight(1f))
                OddsBox("2 (Visitante)", bet.odds.away, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // JUGADOR CON MÁS TIROS AL ARCO (VISIBLE SIEMPRE)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF0B241E),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF059669))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("MÁS TIROS AL ARCO", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF34D399))
                        }
                        Surface(color = Color(0xFF10B981).copy(alpha = 0.25f), shape = RoundedCornerShape(4.dp)) {
                            Text("Cuota " + bet.star.odds, fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF6EE7B7), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
       
