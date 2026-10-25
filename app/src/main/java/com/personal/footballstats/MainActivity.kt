package com.personal.footballstats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF0288D1),
                    surfaceVariant = Color(0xFFF1F5F9),
                    error = Color(0xFFE53935)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GlobalMainScreen()
                }
            }
        }
    }
}

// ========================================================
// 1. MODELOS DE PRONOSTICOS Y MERCADOS DE APUESTAS
// ========================================================

data class PlayerProp(
    val playerName: String,
    val clubName: String,
    val line: String,             // Ej: "Mas de 1.5 remates al arco"
    val probability: String,      // Ej: "74%"
    val estimatedOdds: String     // Ej: "1.85"
)

data class BettingPrediction(
    val matchWinner: String,               // Ganador del encuentro (1X2)
    val doubleChance: String,              // Doble oportunidad (1X, X2 o 12)
    val goalsOverUnder: String,            // Goles (+/- 2.5)
    val totalShotsOnTarget: String,        // Remates al arco totales (+/- 8.5)
    val cardsOverUnder: String,            // Tarjetas (+/- 4.5 o 5.5)
    val cornersOverUnder: String,          // Corners (+/- 8.5 o 9.5)
    val playerProp: PlayerProp,            // Jugador remates al arco (Nombre, Apellido, Club)
    val parlayBetSummary: List<String>,    // Apuesta combinada recomendada
    val parlayTotalOdds: String            // Cuota combinada total (ej: "3.75")
)

// ========================================================
// 2. MOTOR DE ANALISIS Y GENERACION DE APUESTAS
// ========================================================

object BettingAnalyticsEngine {

    // Delanteros estrella por club
    private val STAR_PLAYERS = mapOf(
        "Boca" to Pair("Edinson Cavani", "Boca Juniors"),
        "River" to Pair("Miguel Borja", "River Plate"),
        "Racing" to Pair("Adrian Martinez", "Racing Club"),
        "Independiente" to Pair("Gabriel Avalos", "Independiente"),
        "San Lorenzo" to Pair("Alexis Cuello", "San Lorenzo"),
        "Real Madrid" to Pair("Vinicius Jr", "Real Madrid"),
        "Barcelona" to Pair("Robert Lewandowski", "FC Barcelona"),
        "Arsenal" to Pair("Bukayo Saka", "Arsenal"),
        "City" to Pair("Erling Haaland", "Manchester City"),
        "Liverpool" to Pair("Mohamed Salah", "Liverpool"),
        "Inter Miami" to Pair("Lionel Messi", "Inter Miami"),
        "Flamengo" to Pair("Pedro", "Flamengo"),
        "Palmeiras" to Pair("Raphael Veiga", "Palmeiras"),
        "Milan" to Pair("Rafael Leao", "AC Milan"),
        "Juventus" to Pair("Dusan Vlahovic", "Juventus"),
        "Bayern" to Pair("Harry Kane", "Bayern Munich"),
        "Dortmund" to Pair("Serhou Guirassy", "Borussia Dortmund"),
        "PSG" to Pair("Ousmane Dembele", "Paris Saint-Germain")
    )

    fun generate(homeTeam: String, awayTeam: String, matchId: String): BettingPrediction {
        val seed = abs((homeTeam + awayTeam + matchId).hashCode())

        // 1. Ganador del encuentro (1X2)
        val homeChance = (seed % 100)
        val matchWinner = when {
            homeChance < 52 -> "Local: $homeTeam"
            homeChance < 78 -> "Visitante: $awayTeam"
            else -> "Empate"
        }

        // 2. Doble oportunidad
        val doubleChance = when {
            homeChance < 60 -> "1X ($homeTeam o Empate)"
            homeChance < 85 -> "X2 (Empate o $awayTeam)"
            else -> "12 ($homeTeam o $awayTeam)"
        }

        // 3. Cantidad de Goles (+ / - 2.5)
        val isHighScoring = (seed % 10) > 3
        val goalsLine = if (isHighScoring) "Mas de 2.5 Goles" else "Menos de 2.5 Goles"

        // 4. Cantidad de Remates al arco totales (+ / -)
        val shotsTotal = if ((seed % 2) == 0) "Mas de 8.5 Remates al Arco" else "Mas de 9.5 Remates al Arco"

        // 5. Cantidad de Tarjetas (+ / - 4.5 o 5.5)
        val isCardIntense = homeTeam.contains("Boca", ignoreCase = true) || 
                            homeTeam.contains("River", ignoreCase = true) ||
                            (seed % 3) == 0
        val cardsLine = if (isCardIntense) "Mas de 5.5 Tarjetas" else "Mas de 4.5 Tarjetas"

        // 6. Cantidad de Corners (+ / -)
        val cornersLine = if ((seed % 2) == 0) "Mas de 8.5 Corners" else "Mas de 9.5 Corners"

        // 7. Jugador especifico con Nombre, Apellido, Club y Remates
        val key = STAR_PLAYERS.keys.firstOrNull { homeTeam.contains(it, ignoreCase = true) }
            ?: STAR_PLAYERS.keys.firstOrNull { awayTeam.contains(it, ignoreCase = true) }

        val playerPair = if (key != null) {
            STAR_PLAYERS[key]!!
        } else {
            Pair("Delantero Titular (9)", homeTeam)
        }

        val playerProp = PlayerProp(
            playerName = playerPair.first,
            clubName = playerPair.second,
            line = "Mas de 1.5 remates al arco",
            probability = "74%",
            estimatedOdds = "1.85"
        )

        // 8. Apuesta Combinada del Encuentro
        val parlaySummary = listOf(
            "Doble Oportunidad: $doubleChance",
            "Goles: Mas de 1.5 goles totales",
            "Remates: $shotsTotal",
            "Jugador: ${playerProp.playerName} (${playerProp.clubName}) +1.5 remates"
        )

        val parlayOdds = "3." + ((seed % 60) + 40).toString()

        return BettingPrediction(
            matchWinner = matchWinner,
            doubleChance = doubleChance,
            goalsOverUnder = goalsLine,
            totalShotsOnTarget = shotsTotal,
            cardsOverUnder = cardsLine,
            cornersOverUnder = cornersLine,
            playerProp = playerProp,
            parlayBetSummary = parlaySummary,
            parlayTotalOdds = parlayOdds
        )
    }
}

// ========================================================
// 3. CONFIGURACION DE LIGAS (ARGENTINA, EUROPA, MLS, BRASIL)
// ========================================================

data class LeagueOption(
    val code: String,
    val name: String,
    val tag: String
)

val LEAGUES_LIST = listOf(
    LeagueOption("all", "Todas", "[ALL]"),
    LeagueOption("arg.1", "Argentina", "[ARG]"),
    LeagueOption("esp.1", "La Liga", "[ESP]"),
    LeagueOption("eng.1", "Premier League", "[ENG]"),
    LeagueOption("uefa.champions", "Champions", "[UEFA]"),
    LeagueOption("usa.1", "MLS", "[USA]"),
    LeagueOption("bra.1", "Brasileirao", "[BRA]"),
    LeagueOption("ita.1", "Serie A", "[ITA]"),
    LeagueOption("ger.1", "Bundesliga", "[GER]"),
    LeagueOption("conmebol.libertadores", "Libertadores", "[CONMEBOL]")
)

// ========================================================
// 4. DTOs PARA LA API DE ESPN (100% GRATIS)
// ========================================================

@Serializable
data class EspnTeamDto(
    val id: String? = null,
    val name: String? = null,
    val displayName: String? = null,
    val shortDisplayName: String? = null,
    val abbreviation: String? = null
)

@Serializable
data class EspnCompetitorDto(
    val homeAway: String? = null,
    val score: String? = null,
    val team: EspnTeamDto? = null
)

@Serializable
data class EspnStatusTypeDto(
    val state: String? = null,
    val description: String? = null,
    val shortDetail: String? = null
)

@Serializable
data class EspnStatusDto(
    val displayClock: String? = null,
    val type: EspnStatusTypeDto? = null
)

@Serializable
data class EspnCompetitionDto(
    val id: String? = null,
    val date: String? = null,
    val status: EspnStatusDto? = null,
    val competitors: List<EspnCompetitorDto> = emptyList()
)

@Serializable
data class EspnLeagueDto(
    val id: String? = null,
    val name: String? = null,
    val abbreviation: String? = null
)

@Serializable
data class EspnEventDto(
    val id: String,
    val date: String? = null,
    val name: String? = null,
    val shortName: String? = null,
    val status: EspnStatusDto? = null,
    val competitions: List<EspnCompetitionDto> = emptyList()
)

@Serializable
data class EspnResponse(
    val leagues: List<EspnLeagueDto> = emptyList(),
    val events: List<EspnEventDto> = emptyList()
)

// ========================================================
// 5. MODELO DE UI CON PRONOSTICOS DE APUESTAS
// ========================================================

data class MatchUi(
    val id: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: String?,
    val awayScore: String?,
    val leagueName: String,
    val tag: String,
    val statusText: String,
    val isLive: Boolean,
    val timeText: String,
    val betting: BettingPrediction
)

// ========================================================
// 6. CLIENTE RETROFIT SINGLETON
// ========================================================

interface ApiService {
    @GET("apis/site/v2/sports/soccer/{league}/scoreboard")
    suspend fun getMatches(
        @Path("league") league: String,
        @Query("dates") dates: String? = null
    ): EspnResponse
}

object NetworkClient {
    private const val BASE_URL = "https://site.api.espn.com/"

    private val userAgentInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("User-Agent", "FootballStats-AndroidApp/1.0 (Android; Mobile)")
            .header("Accept", "application/json")
            .build()
        chain.proceed(request)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(userAgentInterceptor)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
}

// ========================================================
// 7. VIEWMODEL
// ========================================================

class GlobalFootballViewModel : ViewModel() {
    val leagues = LEAGUES_LIST

    private val _selectedLeague = MutableStateFlow(leagues.first())
    val selectedLeague = _selectedLeague.asStateFlow()

    private val _matches = MutableStateFlow<List<MatchUi>>(emptyList())
    val matches = _matches.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _message = MutableStateFlow("")
    val message = _message.asStateFlow()

    private val _currentDate = MutableStateFlow(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
    val currentDate = _currentDate.asStateFlow()

    init {
        loadMatches()
    }

    fun selectLeague(league: LeagueOption) {
        _selectedLeague.value = league
        loadMatches()
    }

    fun loadMatches() {
        _isLoading.value = true
        _error.value = null
        _matches.value = emptyList()

        val league = _selectedLeague.value
        _message.value = "Consultando partidos para ${league.tag} ${league.name}..."

        val todayIso = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val todayParam = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        _currentDate.value = todayIso

        viewModelScope.launch {
            try {
                val matchesList = if (league.code == "all") {
                    val activeLeagues = listOf(
                        LeagueOption("arg.1", "Argentina", "[ARG]"),
                        LeagueOption("esp.1", "La Liga", "[ESP]"),
                        LeagueOption("eng.1", "Premier", "[ENG]"),
                        LeagueOption("usa.1", "MLS", "[USA]"),
                        LeagueOption("bra.1", "Brasileirao", "[BRA]"),
                        LeagueOption("uefa.champions", "Champions", "[UEFA]")
                    )

                    val deferredList = activeLeagues.map { l ->
                        async {
                            try {
                                val resp = NetworkClient.api.getMatches(l.code, todayParam)
                                parseMatches(resp, l)
                            } catch (e: Exception) {
                                emptyList()
                            }
                        }
                    }
                    deferredList.awaitAll().flatten()
                } else {
                    val resp = NetworkClient.api.getMatches(league.code, todayParam)
                    parseMatches(resp, league)
                }

                _matches.value = matchesList

                if (matchesList.isEmpty()) {
                    _message.value = "No hay partidos programados hoy ($todayIso) para ${league.name}."
                } else {
                    _message.value = "Se encontraron ${matchesList.size} partidos con pronosticos listos."
                }

            } catch (e: SocketTimeoutException) {
                _error.value = "Tiempo de espera agotado. El servidor tardo en responder."
                _message.value = ""
            } catch (e: UnknownHostException) {
                _error.value = "Sin conexion a Internet. Verifica el WiFi o datos de tu movil."
                _message.value = ""
            } catch (e: HttpException) {
                _error.value = "Error del servidor (${e.code()}): ${e.message()}"
                _message.value = ""
            } catch (e: Exception) {
                _error.value = "Error: ${e.localizedMessage ?: e.javaClass.simpleName}"
                _message.value = ""
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun parseMatches(response: EspnResponse, league: LeagueOption): List<MatchUi> {
        val leagueTitle = response.leagues.firstOrNull()?.name ?: league.name

        return response.events.mapNotNull { event ->
            val competition = event.competitions.firstOrNull() ?: return@mapNotNull null
            val homeComp = competition.competitors.find { it.homeAway == "home" }
            val awayComp = competition.competitors.find { it.homeAway == "away" }

            val homeName = homeComp?.team?.shortDisplayName 
                ?: homeComp?.team?.displayName 
                ?: "Local"
            val awayName = awayComp?.team?.shortDisplayName 
                ?: awayComp?.team?.displayName 
                ?: "Visitante"

            val statusObj = competition.status ?: event.status
            val state = statusObj?.type?.state ?: "pre"
            val isLive = state == "in"

            val statusText = when {
                isLive -> {
                    val clock = statusObj?.displayClock ?: ""
                    if (clock.isNotBlank()) "EN VIVO $clock" else "EN VIVO"
                }
                state == "post" -> "FINALIZADO"
                else -> statusObj?.type?.shortDetail ?: "PROGRAMADO"
            }

            val timeText = event.date?.let { dateStr ->
                if (dateStr.length >= 16) "${dateStr.substring(11, 16)} UTC" else ""
            } ?: ""

            val predictions = BettingAnalyticsEngine.generate(homeName, awayName, event.id)

            MatchUi(
                id = event.id,
                homeTeam = homeName,
                awayTeam = awayName,
                homeScore = homeComp?.score,
                awayScore = awayComp?.score,
                leagueName = leagueTitle,
                tag = league.tag,
                statusText = statusText,
                isLive = isLive,
                timeText = timeText,
                betting = predictions
            )
        }
    }
}

// ========================================================
// 8. INTERFAZ JETPACK COMPOSE CON PANEL DE APUESTAS
// ========================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalMainScreen(viewModel: GlobalFootballViewModel = viewModel()) {
    val matches by viewModel.matches.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val message by viewModel.message.collectAsState()
    val currentDate by viewModel.currentDate.collectAsState()
    val selectedLeague by viewModel.selectedLeague.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Football Stats & Bet Picks",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Hoy: $currentDate - Pronosticos y Combinadas",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.loadMatches() },
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.Refresh, contentDes
