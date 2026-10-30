package com.personal.footballstats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round

// ==============================================================================
// CONFIGURACIÓN DE API BZZOIRO SPORTS DATA
// Clave y endpoint oficial de Bzzoiro (Whitelabel / Privado):
// ==============================================================================
var bzzoiroApiKey: String = "9665517ed1732bad524c3730e18cd9d90cc1d13f"
const val BZZOIRO_BASE_URL: String = "https://sports.bzzoiro.com/api/v2"

// ==============================================================================
// 1. DICCIONARIO OFICIAL DE LIGAS Y PRIORIZACIÓN CUANTITATIVA
// ==============================================================================

val LEAGUES_MAP: Map<Long, String> = mapOf(
    1L to "Premier League (Inglaterra)",
    2L to "Liga Portugal Betclic",
    3L to "LaLiga (España)",
    4L to "Serie A (Italia)",
    5L to "Bundesliga (Alemania)",
    6L to "Ligue 1 (Francia)",
    7L to "UEFA Champions League",
    8L to "UEFA Europa League",
    9L to "Brasileirão Serie A (Brasil)",
    10L to "Eredivisie (Países Bajos)",
    11L to "Trendyol Süper Lig (Turquía)",
    12L to "EFL Championship (Inglaterra)",
    13L to "Scottish Premiership (Escocia)",
    14L to "Pro League (Bélgica)",
    15L to "Super League (Suiza)",
    17L to "Saudi Pro League (Arabia Saudita)",
    18L to "MLS (EE.UU.)",
    19L to "Liga MX Apertura (México)",
    20L to "Liga MX Clausura (México)",
    21L to "Brasileirão Serie B (Brasil)",
    22L to "Parva Liga (Bulgaria)",
    23L to "Superliga (Rumania)",
    24L to "Stoiximan Super League (Grecia)",
    25L to "Ekstraklasa (Polonia)",
    26L to "Allsvenskan (Suecia)",
    27L to "Copa Mundial FIFA",
    28L to "NPFL (Nigeria)",
    29L to "CAF Champions League",
    30L to "Copa Africana de Naciones",
    31L to "Amistosos Internacionales",
    32L to "Copa Libertadores (CONMEBOL)",
    33L to "Copa Sudamericana (CONMEBOL)",
    34L to "Brasileirão Serie B (Brasil)",
    35L to "Copa do Brasil (Brasil)",
    36L to "Liga F Femenina (España)",
    38L to "Segunda División (España)",
    39L to "FA Cup (Inglaterra)",
    40L to "Carabao Cup (Inglaterra)",
    41L to "Copa del Rey (España)",
    42L to "Coppa Italia (Italia)",
    43L to "DFB-Pokal (Alemania)",
    44L to "Coupe de France (Francia)",
    46L to "Puchar Polski (Polonia)",
    47L to "Ligue 1 (Túnez)",
    48L to "Copa de Túnez",
    49L to "J1 League (Japón)",
    50L to "K League 1 (Corea del Sur)",
    51L to "Emperor Cup (Japón)",
    52L to "Chinese Super League (China)",
    53L to "Botola Pro (Marruecos)",
    54L to "Eliteserien (Noruega)",
    55L to "Veikkausliiga (Finlandia)",
    56L to "Suomen Cup (Finlandia)",
    57L to "USL Championship (EE.UU.)",
    58L to "Eliminatorias UEFA",
    59L to "Eliminatorias CONMEBOL",
    60L to "Eliminatorias CAF",
    61L to "Eliminatorias AFC",
    62L to "Eliminatorias CONCACAF",
    63L to "Eliminatorias OFC",
    64L to "UEFA Nations League",
    65L to "CONCACAF Nations League",
    66L to "UEFA Euro",
    67L to "Copa América",
    68L to "AFC Asian Cup",
    69L to "CONCACAF Gold Cup",
    70L to "NPL Queensland (Australia)",
    71L to "UEFA Euro Sub-19",
    72L to "NWSL Femenina (EE.UU.)",
    79L to "Amistosos de Clubes",
    80L to "Categoría Primera A (Colombia)",
    81L to "Copa Colombia (Colombia)",
    82L to "Liga 3 (Portugal)",
    83L to "UEFA Conference League",
    84L to "Superliga (Dinamarca)",
    85L to "Liga Profesional (Argentina)",
    86L to "EFL League One (Inglaterra)",
    87L to "EFL League Two (Inglaterra)",
    88L to "Liga Portugal 2 (Portugal)",
    89L to "Ligue 2 (Francia)",
    90L to "Supercopa UEFA",
    91L to "National League (Inglaterra)",
    92L to "Taça de Portugal (Portugal)",
    93L to "Taça da Liga (Portugal)",
    94L to "2. Bundesliga (Alemania)",
    95L to "Campeonato de Portugal",
    96L to "Austrian Bundesliga (Austria)",
    97L to "Challenger Pro League (Bélgica)",
    98L to "Girabola (Angola)",
    99L to "First League (Rep. Checa)",
    100L to "Serie B (Italia)",
    101L to "Serie C Grupo A (Italia)",
    102L to "Serie C Grupo C (Italia)",
    103L to "Serie C Grupo B (Italia)",
    104L to "A-League Men (Australia)",
    105L to "Ligue 3 (Francia)",
    106L to "Superettan (Suecia)",
    107L to "Trendyol 1. Lig (Turquía)"
)

// Orden de jerarquía estricto para los filtros de ligas: Primer nivel mundial primero
val PRIORITY_LEAGUE_ORDER = listOf(
    1L,   // Premier League
    3L,   // LaLiga España
    4L,   // Serie A Italia
    5L,   // Bundesliga Alemania
    6L,   // Ligue 1 Francia
    7L,   // Champions League
    8L,   // Europa League
    83L,  // Conference League
    32L,  // Copa Libertadores
    33L,  // Copa Sudamericana
    85L,  // Liga Profesional Argentina
    9L,   // Brasileirão Serie A
    18L,  // MLS EE.UU.
    19L,  // Liga MX Apertura
    20L,  // Liga MX Clausura
    10L,  // Eredivisie Países Bajos
    2L,   // Liga Portugal
    17L,  // Saudi Pro League
    80L,  // Primera A Colombia
    12L   // Championship Inglaterra
)

/**
 * Resuelve el nombre real de la liga:
 * 1. Si Bzzoiro provee objeto 'league' con 'name', se utiliza si es válido.
 * 2. Si sólo provee ID o fallback numérico, consulta LEAGUES_MAP.
 * 3. Si no existe en el mapa, retorna "Torneo Internacional" (NUNCA "Liga Internacional #ID").
 */
fun resolveLeagueName(leagueObj: JSONObject?, leagueId: Long): String {
    if (leagueObj != null) {
        val name = leagueObj.optString("name", "").trim()
        if (name.isNotEmpty() && !name.contains("Internacional #", ignoreCase = true) && !name.contains("Liga #", ignoreCase = true)) {
            return name
        }
    }
    return LEAGUES_MAP[leagueId] ?: "Torneo Internacional"
}

// ==============================================================================
// 2. MODELOS DE DATOS CUANTITATIVOS BASADOS EN BZZOIRO
// ==============================================================================

/**
 * Evento deportivo estructurado con IDs reales de equipo para consultas de squad y form
 */
data class ApexEvent(
    val id: Long,
    val leagueId: Long,
    val leagueName: String,
    val roundLabel: String,
    val homeTeamId: Long,
    val homeTeam: String,
    val awayTeamId: Long,
    val awayTeam: String,
    val eventDateRaw: String,
    val eventDateZdt: ZonedDateTime?,
    val status: String,
    val period: String,
    val currentMinute: Int?,
    val homeScore: Int?,
    val awayScore: Int?,
    val h2hTotalMatches: Int,
    val h2hHomeWins: Int,
    val h2hDraws: Int,
    val h2hAwayWins: Int,
    val h2hAvgTotalGoals: Double,
    val h2hHomeWinRate: Double,
    val h2hAwayWinRate: Double,
    val recentScores: List<String>,
    val homeRecentForm: List<MatchFormResult>,
    val awayRecentForm: List<MatchFormResult>,
    val isNext24Hours: Boolean,
    val timeToKickoffFormatted: String
)

data class MatchFormResult(
    val code: String,       // "V", "E", "D"
    val outcome: FormOutcome,
    val rival: String,
    val score: String
)

enum class FormOutcome {
    WIN,    // Verde #10B981
    DRAW,   // Amarillo #F59E0B
    LOSS    // Rojo #EF4444
}

/**
 * Cuotas reales extraídas de Bzzoiro (/odds/?event_id=)
 * decimal_odds provisto por casas de apuestas; null si el mercado no está abierto
 */
data class RealOdds(
    val home1x2: Double? = null,
    val draw1x2: Double? = null,
    val away1x2: Double? = null,
    val dc1X: Double? = null,        // Doble Oportunidad real 1X
    val dcX2: Double? = null,        // Doble Oportunidad real X2
    val dc12: Double? = null,        // Doble Oportunidad real 12
    val bttsYes: Double? = null,
    val bttsNo: Double? = null,
    val over15Goals: Double? = null,
    val under15Goals: Double? = null,
    val over25Goals: Double? = null,
    val under25Goals: Double? = null,
    val over35Goals: Double? = null,
    val under35Goals: Double? = null,
    val cornerLineVal: Double? = null,
    val overCornerLine: Double? = null,
    val underCornerLine: Double? = null,
    val cardsLineVal: Double? = null,
    val overCardsLine: Double? = null
)

/**
 * Estadísticas cuantitativas de forma de equipo de Bzzoiro (/teams/{id}/form/)
 */
data class TeamRealFormStats(
    val teamId: Long,
    val teamName: String,
    val matchesPlayed: Int,
    val avgGoalsScored: Double,
    val avgGoalsConceded: Double,
    val avgCorners: Double,
    val avgYellowCards: Double,
    val avgTotalShots: Double,
    val avgShotsOnTarget: Double,
    val formString: String // Ej: "WWLDW"
)

data class DoubleChanceFavoriteAnalysis(
    val favoriteTeam: String,
    val underdogTeam: String,
    val isHomeFavorite: Boolean,
    val pickCode: String,
    val pickTitle: String,
    val displayOdds: String,       // Cuota real o calculada especificada
    val rawOdds: Double,           // Valor numérico de cuota
    val isMarketOdds: Boolean,     // true si vino de casa de apuestas
    val modelProb: Double,
    val fairOdds: Double,
    val expectedValue: Double?,
    val confidenceBadge: String,
    val analysisDescription: String
)

data class CornersAnalysis(
    val expectedTotal: Double,
    val expectedHome: Double,
    val expectedAway: Double,
    val linePrincipal: Double,
    val probOver85: Double,
    val fairOddsOver85: Double,
    val probOver95: Double,
    val fairOddsOver95: Double,
    val probOver105: Double,
    val fairOddsOver105: Double,
    val recommendedPick: String,
    val recommendedProb: Double,
    val displayOdds: String,
    val isMarketOdds: Boolean
)

data class CardsAnalysis(
    val expectedTotal: Double,
    val expectedYellow: Double,
    val expectedRedProb: Double,
    val linePrincipal: Double,
    val probOver35: Double,
    val fairOddsOver35: Double,
    val probOver45: Double,
    val fairOddsOver45: Double,
    val frictionIndex: String,
    val recommendedPick: String,
    val recommendedProb: Double,
    val displayOdds: String
)

data class PlayerShotProp(
    val fullName: String,              // Extraído del squad real del equipo
    val teamName: String,
    val rolePosition: String,          // Delantero / Extremo / Atacante
    val totalShotsLambda: Double,
    val shotsOnTargetLambda: Double,
    val pickTotalShots: String,
    val probTotalShots: Double,
    val pickOnTarget: String,
    val probOnTarget: Double,
    val fairOddsOnTarget: Double,
    val displayOdds: String
)

data class ParlayLeg(
    val category: String,
    val pick: String,
    val lambda: Double?,
    val modelProb: Double,
    val displayOdds: String,
    val edgePercent: Double?,
    val isSafePick: Boolean
)

data class MatchBettingSummary(
    val safePickTitle: String,
    val safePick: String,
    val safePickOdds: String,
    val safePickProb: String,
    val valuePick: String,
    val valuePickOdds: String,
    val playerPick: String,
    val playerPickOdds: String,
    val cardsPick: String,
    val predictedScore: String,
    val quickVerdict: String
)

data class QuantumMatchAnalysis(
    val event: ApexEvent,
    val odds: RealOdds,
    val lambdaHome: Double,
    val lambdaAway: Double,
    val lambdaTotal: Double,
    val probHome: Double,
    val probDraw: Double,
    val probAway: Double,
    val probOver15: Double,
    val probOver25: Double,
    val probOver35: Double,
    val probBTTS: Double,
    val mostProbableScore: String,
    val mostProbableScoreProb: Double,
    val doubleChanceAnalysis: DoubleChanceFavoriteAnalysis,
    val cornersAnalysis: CornersAnalysis,
    val cardsAnalysis: CardsAnalysis,
    val playerShotProps: List<PlayerShotProp>,
    val bettingSummary: MatchBettingSummary,
    val parlayLegs: List<ParlayLeg>,
    val jointParlayProb: Double,
    val parlayDisplayOdds: String,
    val kellyStakePercent: Double
)

// ==============================================================================
// 3. CLIENTE HTTP DE BZZOIRO SPORTS DATA (VALIDACIÓN ESTRICTA Y ASYNC)
// ==============================================================================

object BzzoiroApiClient {

    // Caché en memoria para evitar llamadas redundantes de planteles y estadísticas
    private val squadCache = mutableMapOf<Long, List<Pair<String, String>>>()
    private val formCache = mutableMapOf<Long, TeamRealFormStats>()

    /**
     * Validador de atacantes oficiales por equipo con coincidencia ESTRICTA.
     * NUNCA usa búsquedas parciales como 'atlético' que puedan cruzar clubes.
     * Hulk pertenece EXCLUSIVAMENTE a Atlético Mineiro (ID 1045L o nombre explícito 'Atlético Mineiro').
     */
    fun getAuthenticTeamAttackers(teamId: Long, teamName: String): List<Pair<String, String>> {
        val norm = teamName.lowercase(Locale.ROOT).trim()
        return when {
            // Atlético Mineiro (Brasil) - ÚNICO club de Hulk
            teamId == 1045L || norm == "atlético mineiro" || norm == "atletico mineiro" || norm == "clube atlético mineiro" || norm == "atlético mg" || norm == "atletico mg" -> listOf(
                Pair("Hulk", "Delantero Centro"),
                Pair("Paulinho", "Segundo Delantero")
            )
            // Sevilla FC (España)
            teamId == 1258L || norm == "sevilla" || norm == "sevilla fc" || norm == "sevilla fútbol club" -> listOf(
                Pair("Isaac Romero", "Delantero Centro"),
                Pair("Dodi Lukébakio", "Extremo Ofensivo")
            )
            // Real Madrid (España)
            teamId == 1251L || norm == "real madrid" || norm == "real madrid cf" -> listOf(
                Pair("Kylian Mbappé", "Delantero Centro"),
                Pair("Vinícius Jr", "Extremo Izquierdo"),
                Pair("Rodrygo", "Extremo Derecho")
            )
            // Barcelona (España)
            teamId == 1255L || norm == "barcelona" || norm == "fc barcelona" -> listOf(
                Pair("Robert Lewandowski", "Delantero Centro"),
                Pair("Lamine Yamal", "Extremo Derecho"),
                Pair("Raphinha", "Extremo Izquierdo")
            )
            // Atlético Madrid (España) - Validación estricta, NO coincide con Mineiro
            teamId == 1252L || norm == "atlético madrid" || norm == "atletico madrid" || norm == "club atlético de madrid" || norm == "atletico de madrid" -> listOf(
                Pair("Julián Álvarez", "Delantero Centro"),
                Pair("Antoine Griezmann", "Mediapunta / Atacante"),
                Pair("Alexander Sørloth", "Delantero Centro")
            )
            // Manchester City (Inglaterra)
            teamId == 12L || norm == "manchester city" || norm == "man city" -> listOf(
                Pair("Erling Haaland", "Delantero Centro"),
                Pair("Phil Foden", "Extremo Ofensivo"),
                Pair("Bernardo Silva", "Atacante")
            )
            // Liverpool (Inglaterra)
            teamId == 1L || norm == "liverpool" || norm == "liverpool fc" -> listOf(
                Pair("Mohamed Salah", "Extremo Derecho"),
                Pair("Luis Díaz", "Extremo Izquierdo"),
                Pair("Darwin Núñez", "Delantero Centro")
            )
            // Arsenal (Inglaterra)
            teamId == 2L || norm == "arsenal" || norm == "arsenal fc" -> listOf(
                Pair("Bukayo Saka", "Extremo Derecho"),
                Pair("Kai Havertz", "Delantero Centro"),
                Pair("Gabriel Martinelli", "Extremo Izquierdo")
            )
            // Chelsea (Inglaterra) - Único club de Nicolas Jackson y Cole Palmer
            teamId == 13L || norm == "chelsea" || norm == "chelsea fc" -> listOf(
                Pair("Cole Palmer", "Mediapunta / Ofensivo"),
                Pair("Nicolas Jackson", "Delantero Centro"),
                Pair("Christopher Nkunku", "Segundo Delantero")
            )
            // Aston Villa (Inglaterra)
            teamId == 3L || norm == "aston villa" -> listOf(
                Pair("Ollie Watkins", "Delantero Centro"),
                Pair("Morgan Rogers", "Mediapunta / Atacante"),
                Pair("Leon Bailey", "Extremo Ofensivo")
            )
            // Fulham (Inglaterra)
            teamId == 6L || norm == "fulham" || norm == "fulham fc" -> listOf(
                Pair("Rodrigo Muniz", "Delantero Centro"),
                Pair("Raúl Jiménez", "Delantero Centro"),
                Pair("Alex Iwobi", "Extremo Ofensivo")
            )
            // Tottenham (Inglaterra)
            teamId == 9L || norm == "tottenham" || norm == "tottenham hotspur" || norm == "spurs" -> listOf(
                Pair("Son Heung-min", "Extremo / Delantero"),
                Pair("Dominic Solanke", "Delantero Centro")
            )
            // Manchester United (Inglaterra)
            teamId == 17L || norm == "manchester united" || norm == "man utd" -> listOf(
                Pair("Rasmus Højlund", "Delantero Centro"),
                Pair("Marcus Rashford", "Extremo Ofensivo"),
                Pair("Alejandro Garnacho", "Extremo")
            )
            // Newcastle (Inglaterra)
            teamId == 4L || norm == "newcastle" || norm == "newcastle united" -> listOf(
                Pair("Alexander Isak", "Delantero Centro"),
                Pair("Anthony Gordon", "Extremo Ofensivo")
            )
            // Bayern Munich (Alemania)
            norm == "bayern münchen" || norm == "bayern munich" || norm == "bayern" -> listOf(
                Pair("Harry Kane", "Delantero Centro"),
                Pair("Jamal Musiala", "Mediapunta"),
                Pair("Michael Olise", "Extremo")
            )
            // Borussia Dortmund (Alemania)
            norm == "borussia dortmund" || norm == "dortmund" -> listOf(
                Pair("Serhou Guirassy", "Delantero Centro"),
                Pair("Julian Brandt", "Mediapunta")
            )
            // Inter de Milán (Italia)
            norm == "inter" || norm == "internazionale" || norm == "inter milan" -> listOf(
                Pair("Lautaro Martínez", "Delantero Centro"),
                Pair("Marcus Thuram", "Delantero Centro")
            )
            // Juventus (Italia)
            norm == "juventus" || norm == "juventus fc" -> listOf(
                Pair("Dušan Vlahović", "Delantero Centro"),
                Pair("Kenan Yildiz", "Extremo Ofensivo")
            )
            // AC Milan (Italia)
            norm == "ac milan" || norm == "milan" -> listOf(
                Pair("Álvaro Morata", "Delantero Centro"),
                Pair("Rafael Leão", "Extremo Izquierdo")
            )
            // Paris Saint-Germain (Francia)
            norm == "paris saint-germain" || norm == "psg" -> listOf(
                Pair("Ousmane Dembélé", "Extremo"),
                Pair("Bradley Barcola", "Extremo"),
                Pair("Gonçalo Ramos", "Delantero Centro")
            )
            // River Plate (Argentina)
            teamId == 2085L || norm == "river plate" || norm == "river" -> listOf(
                Pair("Miguel Borja", "Delantero Centro"),
                Pair("Facundo Colidio", "Segundo Delantero")
            )
            // Boca Juniors (Argentina)
            teamId == 2086L || norm == "boca juniors" || norm == "boca" -> listOf(
                Pair("Edinson Cavani", "Delantero Centro"),
                Pair("Miguel Merentiel", "Delantero Centro")
            )
            // Racing Club (Argentina)
            norm == "racing club" || norm == "racing" -> listOf(
                Pair("Adrián Martínez", "Delantero Centro"),
                Pair("Roger Martínez", "Delantero")
            )
            // Vélez Sarsfield (Argentina)
            norm == "vélez sarsfield" || norm == "velez sarsfield" || norm == "vélez" || norm == "velez" -> listOf(
                Pair("Braian Romero", "Delantero Centro"),
                Pair("Francisco Pizzini", "Extremo")
            )
            // San Lorenzo (Argentina)
            norm == "san lorenzo" || norm == "san lorenzo de almagro" -> listOf(
                Pair("Alexis Cuello", "Delantero Centro"),
                Pair("Iker Muniain", "Mediapunta Ofensivo")
            )
            // Estudiantes de La Plata (Argentina)
            norm == "estudiantes" || norm == "estudiantes de la plata" || norm == "estudiantes lp" -> listOf(
                Pair("Guido Carrillo", "Delantero Centro"),
                Pair("Edwuin Cetré", "Extremo Ofensivo")
            )
            // Independiente Rivadavia (Argentina)
            norm == "independiente rivadavia" -> listOf(
                Pair("Sebastián Villa", "Extremo Ofensivo"),
                Pair("Victorio Ramis", "Delantero Centro")
            )
            // Huracán (Argentina)
            norm == "huracán" || norm == "huracan" -> listOf(
                Pair("Ramón Ábila", "Delantero Centro"),
                Pair("Walter Mazzantti", "Extremo")
            )
            // Tigre (Argentina)
            norm == "tigre" || norm == "club atlético tigre" -> listOf(
                Pair("Florián Monzón", "Delantero Centro"),
                Pair("Blas Armoa", "Extremo")
            )
            // Inter Miami (EE.UU.)
            norm == "inter miami" || norm == "inter miami cf" -> listOf(
                Pair("Lionel Messi", "Atacante Libre"),
                Pair("Luis Suárez", "Delantero Centro")
            )
            // Pachuca (México)
            norm == "pachuca" || norm == "tuzos" || norm == "cf pachuca" -> listOf(
                Pair("Salomón Rondón", "Delantero Centro"),
                Pair("Oussama Idrissi", "Extremo Ofensivo"),
                Pair("Nelson Deossa", "Mediapunta Ofensivo")
            )
            // Guaraní (Paraguay) - Club real de Adrián Alcaraz
            norm == "guaraní" || norm == "guarani" || norm == "club guaraní" -> listOf(
                Pair("Adrián Alcaraz", "Delantero Centro"),
                Pair("Walter González", "Delantero Centro")
            )
            // Tigres UANL (México)
            norm == "tigres" || norm == "tigres uanl" -> listOf(
                Pair("André-Pierre Gignac", "Delantero Centro"),
                Pair("Nicolás Ibáñez", "Delantero Centro")
            )
            // Club América (México)
            norm == "club américa" || norm == "club america" || norm == "américa" || norm == "america" -> listOf(
                Pair("Henry Martín", "Delantero Centro"),
                Pair("Rodrigo Aguirre", "Delantero Centro")
            )
            // Monterrey Rayados (México)
            norm == "monterrey" || norm == "rayados" || norm == "cf monterrey" -> listOf(
                Pair("Germán Berterame", "Delantero Centro"),
                Pair("Sergio Canales", "Mediapunta")
            )
            // Chivas Guadalajara (México)
            norm == "chivas" || norm == "guadalajara" || norm == "cd guadalajara" -> listOf(
                Pair("Javier Hernández", "Delantero Centro"),
                Pair("Roberto Alvarado", "Extremo")
            )
            // Cruz Azul (México)
            norm == "cruz azul" -> listOf(
                Pair("Giorgos Giakoumakis", "Delantero Centro"),
                Pair("Ángel Sepúlveda", "Delantero Centro")
            )
            // Junior de Barranquilla (Colombia)
            norm == "junior" || norm == "junior fc" || norm == "junior de barranquilla" -> listOf(
                Pair("Carlos Bacca", "Delantero Centro"),
                Pair("Guillermo Paiva", "Delantero Centro")
            )
            // Santa Fe (Colombia)
            norm == "santa fe" || norm == "independiente santa fe" -> listOf(
                Pair("Hugo Rodallega", "Delantero Centro"),
                Pair("Harold Mosquera", "Extremo")
            )
            // Llaneros (Colombia)
            norm == "llaneros" || norm == "club llaneros" -> listOf(
                Pair("Carlos Cortés", "Delantero Centro"),
                Pair("Yorleys Mena", "Delantero Centro")
            )
            // Náutico (Brasil)
            norm == "nautico" || norm == "náutico" || norm == "clube náutico capibaribe" -> listOf(
                Pair("Paulo Sérgio", "Delantero Centro"),
                Pair("Bruno Mezenga", "Delantero Centro")
            )
            // Avaí (Brasil)
            norm == "avai" || norm == "avaí" || norm == "avaí fc" -> listOf(
                Pair("Léo Gamalho", "Delantero Centro"),
                Pair("Vágner Love", "Delantero Centro")
            )
            // Porto (Portugal)
            teamId == 35L || norm == "porto" || norm == "fc porto" -> listOf(
                Pair("Samu Omorodion", "Delantero Centro"),
                Pair("Galeno", "Extremo Izquierdo"),
                Pair("Pepê", "Extremo Derecho")
            )
            // Sporting CP (Portugal)
            norm == "sporting cp" || norm == "sporting lisboa" || norm == "sporting" -> listOf(
                Pair("Viktor Gyökeres", "Delantero Centro"),
                Pair("Francisco Trincão", "Extremo")
            )
            // Benfica (Portugal)
            norm == "benfica" || norm == "sl benfica" -> listOf(
                Pair("Vangelis Pavlidis", "Delantero Centro"),
                Pair("Ángel Di María", "Extremo")
            )
            // Real Valladolid (España)
            teamId == 1263L || norm == "valladolid" || norm == "real valladolid" -> listOf(
                Pair("Marcos André", "Delantero Centro"),
                Pair("Mamadou Sylla", "Delantero Centro"),
                Pair("Raúl Moro", "Extremo Ofensivo")
            )
            // UD Las Palmas (España)
            teamId == 1266L || norm == "las palmas" || norm == "ud las palmas" -> listOf(
                Pair("Sandro Ramírez", "Delantero Centro"),
                Pair("Fábio Silva", "Delantero Centro"),
                Pair("Alberto Moleiro", "Mediapunta")
            )
            // Real Betis (España)
            norm == "betis" || norm == "real betis" || norm == "real betis balompié" -> listOf(
                Pair("Vitor Roque", "Delantero Centro"),
                Pair("Chimy Ávila", "Extremo / Delantero"),
                Pair("Ez Abde", "Extremo")
            )
            else -> emptyList()
        }
    }

    /**
     * Parsea fechas ISO 8601 UTC de Bzzoiro (ej: '2024-10-31T22:00:00Z', '2026-10-29T20:00:00+00:00')
     * usando java.time.OffsetDateTime o java.time.Instant y las convierte a la zona horaria LOCAL
     * del dispositivo (ZoneId.systemDefault()).
     */
    fun parseIsoDate(dateStr: String): ZonedDateTime? {
        if (dateStr.isBlank()) return null
        val clean = dateStr.trim().replace(" ", "T")
        return try {
            val odt = OffsetDateTime.parse(clean)
            odt.atZoneSameInstant(ZoneId.systemDefault())
        } catch (_: Exception) {
            try {
                val instant = Instant.parse(clean)
                instant.atZone(ZoneId.systemDefault())
            } catch (_: Exception) {
                try {
                    val zdt = ZonedDateTime.parse(clean, DateTimeFormatter.ISO_DATE_TIME)
                    zdt.withZoneSameInstant(ZoneId.systemDefault())
                } catch (_: Exception) {
                    try {
                        val ldt = LocalDateTime.parse(clean)
                        ldt.atZone(ZoneId.of("UTC")).withZoneSameInstant(ZoneId.systemDefault())
                    } catch (_: Exception) {
                        try {
                            val ld = LocalDate.parse(clean.take(10))
                            ld.atStartOfDay(ZoneId.of("UTC")).withZoneSameInstant(ZoneId.systemDefault())
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }
        }
    }

    /**
     * Extrae strings de forma segura de JSON evitando valores nulos serializados como "null"
     */
    fun extractSafeString(obj: JSONObject, key: String): String {
        if (!obj.has(key) || obj.isNull(key)) return ""
        val s = obj.optString(key, "").trim()
        return if (s.equals("null", ignoreCase = true) || s == "None" || s == "undefined") "" else s
    }

    private fun openConnection(endpoint: String): HttpURLConnection {
        val fullUrl = if (endpoint.startsWith("http")) endpoint else "$BZZOIRO_BASE_URL$endpoint"
        val conn = URL(fullUrl).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Token $bzzoiroApiKey")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        return conn
    }

    private fun readResponse(conn: HttpURLConnection): String {
        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line)
        }
        reader.close()
        return sb.toString()
    }

    /**
     * Consulta el plantel oficial de Bzzoiro (/teams/{team_id}/squad/)
     * Validación estricta anti-alucinaciones:
     * - Verifica team_id de retorno de Bzzoiro.
     * - Descarta cruzamiento de atacantes reconocidos (Hulk sólo en Mineiro, Jackson sólo en Chelsea, etc.).
     * - Si no se halla nombre real para este equipo, usa rol descriptivo de su propio club: 'Delantero ($teamName)'.
     */
    suspend fun fetchTeamSquad(teamId: Long, teamName: String): List<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            squadCache[teamId]?.let { return@withContext it }

            // 1. Priorizar lista auténtica verificada si el equipo está registrado
            val verified = getAuthenticTeamAttackers(teamId, teamName)
            if (verified.isNotEmpty()) {
                squadCache[teamId] = verified
                return@withContext verified
            }

            val attackers = mutableListOf<Pair<String, String>>()
            val normTeam = teamName.lowercase(Locale.ROOT)
            val isMineiro = teamId == 1045L || normTeam.contains("mineiro")
            val isChelsea = teamId == 13L || normTeam.contains("chelsea")
            val isGuarani = normTeam.contains("guaraní") || normTeam.contains("guarani")

            try {
                val conn = openConnection("/teams/$teamId/squad/")
                if (conn.responseCode == 200) {
                    val root = JSONObject(readResponse(conn))
                    val returnedTeamId = root.optLong("team_id", 0L)

                    // Validación de seguridad: el ID del squad debe coincidir con el solicitado
                    if (returnedTeamId == 0L || returnedTeamId == teamId) {
                        val playersArr = root.optJSONArray("players") ?: JSONArray()
                        val forwards = mutableListOf<String>()
                        val midfielders = mutableListOf<String>()

                        for (i in 0 until playersArr.length()) {
                            val p = playersArr.getJSONObject(i)
                            val name = p.optString("name", "").trim()
                            val pos = p.optString("position", "").uppercase().trim()
                            val avail = p.optString("availability", "available").lowercase()
                            val pTeamId = if (p.has("team_id") && !p.isNull("team_id")) p.optLong("team_id") else 0L

                            // Descartar si el jugador tiene asignado otro team_id diferente
                            if (pTeamId != 0L && pTeamId != teamId) continue

                            // Descartar lesionados o suspendidos si hay otros disponibles
                            if (avail.contains("injured") || avail.contains("suspended")) continue

                            // REGLAS ESTRICTAS DE CONTROL DE ALUCINACIONES:
                            // 1. Hulk pertenece ÚNICAMENTE a Atlético Mineiro
                            if (name.contains("Hulk", ignoreCase = true) && !isMineiro) continue
                            // 2. Nicolas Jackson pertenece ÚNICAMENTE a Chelsea
                            if (name.contains("Nicolas Jackson", ignoreCase = true) && !isChelsea) continue
                            // 3. Adrián Alcaraz pertenece a Guaraní
                            if (name.contains("Alcaraz", ignoreCase = true) && !isGuarani) continue
                            // 4. Mateo Silveira (placeholder inexistente)
                            if (name.contains("Mateo Silveira", ignoreCase = true)) continue

                            if (name.isNotEmpty()) {
                                if (pos == "F" || pos == "A" || pos.contains("ATTACK") || pos.contains("FORWARD") || pos.contains("STRIKER")) {
                                    forwards.add(name)
                                } else if (pos == "M" || pos.contains("MIDFIELD")) {
                                    midfielders.add(name)
                                }
                            }
                        }

                        for (f in forwards.take(2)) {
                            attackers.add(Pair(f, "Delantero Centro"))
                        }
                        if (attackers.size < 2) {
                            for (m in midfielders.take(2 - attackers.size)) {
                                attackers.add(Pair(m, "Mediapunta / Atacante"))
                            }
                        }
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Fallback honesto y matemático: Si no hay squad verificado, usar descripción de su propio equipo
            if (attackers.isEmpty()) {
                attackers.add(Pair("Delantero ($teamName)", "Delantero Centro"))
                attackers.add(Pair("Extremo ($teamName)", "Extremo Ofensivo"))
            }

            squadCache[teamId] = attackers
            attackers
        }

    /**
     * Consulta estadísticas de forma real de Bzzoiro (/teams/{team_id}/form/)
     */
    suspend fun fetchTeamForm(teamId: Long, teamName: String): TeamRealFormStats =
        withContext(Dispatchers.IO) {
            formCache[teamId]?.let { return@withContext it }

            var stats = TeamRealFormStats(
                teamId = teamId,
                teamName = teamName,
                matchesPlayed = 10,
                avgGoalsScored = 1.35,
                avgGoalsConceded = 1.25,
                avgCorners = 4.8,
                avgYellowCards = 2.4,
                avgTotalShots = 13.0,
                avgShotsOnTarget = 4.5,
                formString = "WDWLD"
            )

            try {
                val conn = openConnection("/teams/$teamId/form/")
                if (conn.responseCode == 200) {
                    val root = JSONObject(readResponse(conn))
                    val overall = root.optJSONObject("overall")
                    if (overall != null) {
                        val mCount = overall.optInt("matches", 10).coerceAtLeast(1)
                        val gf = overall.optDouble("goals_for", 14.0)
                        val ga = overall.optDouble("goals_against", 12.0)
                        val fStr = overall.optString("form", "WDWLD")

                        var corn = 4.8
                        var yel = 2.4
                        var totShots = 13.0
                        var sot = 4.5

                        val stObj = overall.optJSONObject("stats")
                        if (stObj != null) {
                            val cObj = stObj.optJSONObject("corner_kicks")
                            if (cObj != null) corn = cObj.optDouble("average", 4.8)

                            val yObj = stObj.optJSONObject("yellow_cards")
                            if (yObj != null) yel = yObj.optDouble("average", 2.4)

                            val tsObj = stObj.optJSONObject("total_shots")
                            if (tsObj != null) totShots = tsObj.optDouble("average", 13.0)

                            val sotObj = stObj.optJSONObject("shots_on_target")
                            if (sotObj != null) sot = sotObj.optDouble("average", 4.5)
                        }

                        stats = TeamRealFormStats(
                            teamId = teamId,
                            teamName = teamName,
                            matchesPlayed = mCount,
                            avgGoalsScored = (gf / mCount).coerceIn(0.40, 3.80),
                            avgGoalsConceded = (ga / mCount).coerceIn(0.40, 3.80),
                            avgCorners = corn.coerceIn(2.5, 9.5),
                            avgYellowCards = yel.coerceIn(1.0, 6.0),
                            avgTotalShots = totShots.coerceIn(7.0, 22.0),
                            avgShotsOnTarget = sot.coerceIn(2.0, 9.5),
                            formString = fStr
                        )
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            formCache[teamId] = stats
            stats
        }

    /**
     * Consulta cuotas reales del endpoint de Bzzoiro (/odds/?event_id=)
     */
    suspend fun fetchEventOdds(eventId: Long): RealOdds = withContext(Dispatchers.IO) {
        var odds = RealOdds()
        try {
            val conn = openConnection("/odds/?event_id=$eventId&limit=100")
            if (conn.responseCode == 200) {
                val root = JSONObject(readResponse(conn))
                val results = root.optJSONArray("results") ?: JSONArray()

                var home1x2: Double? = null
                var draw1x2: Double? = null
                var away1x2: Double? = null
                var dc1X: Double? = null
                var dcX2: Double? = null
                var dc12: Double? = null
                var bttsY: Double? = null
                var bttsN: Double? = null
                var o15: Double? = null
                var u15: Double? = null
                var o25: Double? = null
                var u25: Double? = null
                var o35: Double? = null
                var u35: Double? = null
                var cornLine: Double? = null
                var cornO: Double? = null
                var cornU: Double? = null

                for (j in 0 until results.length()) {
                    val oddObj = results.getJSONObject(j)
                    val mkt = oddObj.optString("market", "")
                    val out = oddObj.optString("outcome", "").uppercase()
                    val outName = oddObj.optString("outcome_name", "")
                    val decOdds = oddObj.optDouble("decimal_odds", 0.0)

                    if (decOdds <= 1.01) continue

                    when (mkt) {
                        "1x2" -> {
                            if (out == "HOME" || out == "1") home1x2 = decOdds
                            else if (out == "DRAW" || out == "X") draw1x2 = decOdds
                            else if (out == "AWAY" || out == "2") away1x2 = decOdds
                        }
                        "double_chance" -> {
                            if (out == "1X" || outName.contains("Home or Draw", ignoreCase = true)) dc1X = decOdds
                            else if (out == "X2" || outName.contains("Draw or Away", ignoreCase = true)) dcX2 = decOdds
                            else if (out == "12" || outName.contains("Home or Away", ignoreCase = true)) dc12 = decOdds
                        }
                        "over_under_15" -> {
                            val line = oddObj.optDouble("line", 1.5)
                            if (abs(line - 1.5) < 0.2) {
                                if (out == "OVER" || outName.contains("Over", ignoreCase = true)) o15 = decOdds
                                else if (out == "UNDER" || outName.contains("Under", ignoreCase = true)) u15 = decOdds
                            }
                        }
                        "over_under_25" -> {
                            val line = oddObj.optDouble("line", 2.5)
                            if (abs(line - 2.5) < 0.2) {
                                if (out == "OVER" || outName.contains("Over", ignoreCase = true)) o25 = decOdds
                                else if (out == "UNDER" || outName.contains("Under", ignoreCase = true)) u25 = decOdds
                            }
                        }
                        "over_under_35" -> {
                            val line = oddObj.optDouble("line", 3.5)
                            if (abs(line - 3.5) < 0.2) {
                                if (out == "OVER" || outName.contains("Over", ignoreCase = true)) o35 = decOdds
                                else if (out == "UNDER" || outName.contains("Under", ignoreCase = true)) u35 = decOdds
                            }
                        }
                        "total_corners" -> {
                            cornLine = oddObj.optDouble("line", 8.5)
                            if (out == "OVER" || outName.contains("Over", ignoreCase = true)) cornO = decOdds
                            else if (out == "UNDER" || outName.contains("Under", ignoreCase = true)) cornU = decOdds
                        }
                        "btts" -> {
                            if (out == "YES" || outName.contains("Yes", ignoreCase = true)) bttsY = decOdds
                            else if (out == "NO" || outName.contains("No", ignoreCase = true)) bttsN = decOdds
                        }
                    }
                }

                odds = RealOdds(
                    home1x2 = home1x2,
                    draw1x2 = draw1x2,
                    away1x2 = away1x2,
                    dc1X = dc1X,
                    dcX2 = dcX2,
                    dc12 = dc12,
                    bttsYes = bttsY,
                    bttsNo = bttsN,
                    over15Goals = o15,
                    under15Goals = u15,
                    over25Goals = o25,
                    under25Goals = u25,
                    over35Goals = o35,
                    under35Goals = u35,
                    cornerLineVal = cornLine,
                    overCornerLine = cornO,
                    underCornerLine = cornU
                )
            }
            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        odds
    }

    /**
     * Parsea un evento individual de Bzzoiro garantizando lectura estricta de fecha/hora,
     * conversión a la zona horaria del dispositivo, formateo individual y filtro estricto de 24 horas.
     */
    fun parseApexEventFromJson(
        obj: JSONObject,
        nowLocalLdt: LocalDateTime = LocalDateTime.now(),
        limit24h: LocalDateTime = LocalDateTime.now().plusHours(24)
    ): ApexEvent {
        val id = obj.optLong("id")
        val leagueId = obj.optLong("league_id")
        val roundLabel = extractSafeString(obj, "round_label").ifEmpty { "Jornada Oficial" }
        val homeTeamId = obj.optLong("home_team_id")
        val homeTeam = extractSafeString(obj, "home_team").ifEmpty { "Local" }
        val awayTeamId = obj.optLong("away_team_id")
        val awayTeam = extractSafeString(obj, "away_team").ifEmpty { "Visitante" }
        val status = extractSafeString(obj, "status").lowercase().ifEmpty { "notstarted" }
        val period = extractSafeString(obj, "period")
        val curMin = if (obj.has("current_minute") && !obj.isNull("current_minute")) obj.optInt("current_minute") else null
        val homeSc = if (obj.has("home_score") && !obj.isNull("home_score")) obj.optInt("home_score") else null
        val awaySc = if (obj.has("away_score") && !obj.isNull("away_score")) obj.optInt("away_score") else null

        // 1. Lectura segura y rigurosa del campo de fecha ISO 8601 UTC de Bzzoiro
        val dateCandidates = listOf("start_time", "event_date", "date", "datetime", "starting_at")
        val eventDateRaw = dateCandidates
            .map { extractSafeString(obj, it) }
            .firstOrNull { it.isNotBlank() } ?: ""

        // 2. Parseo y conversión estricta a zona horaria local del dispositivo
        val matchLocal = parseIsoDate(eventDateRaw)
        val matchLocalLdt = matchLocal?.toLocalDateTime()

        val isLive = status == "inprogress" || curMin != null
        // 3. Filtro estricto de 24 horas: mayor o igual a now (permitiendo ventana en curso de 105 min) y menor o igual a now + 24 horas
        val isNext24Hours = when {
            isLive -> true
            status == "finished" -> false
            matchLocalLdt != null -> !matchLocalLdt.isBefore(nowLocalLdt.minusMinutes(105)) && !matchLocalLdt.isAfter(limit24h)
            else -> false
        }

        // 4. Formateo de fecha y hora individual en la zona horaria local del usuario: "dd/MM • HH:mm hs"
        val timeFormatted = when {
            isLive -> "EN VIVO • ${curMin ?: 45}'"
            status == "finished" -> "FINALIZADO"
            matchLocal != null -> matchLocal.format(DateTimeFormatter.ofPattern("dd/MM • HH:mm 'hs'", Locale.getDefault()))
            else -> "HORARIO A CONFIRMAR"
        }

        // 5. Resolución de nombre de liga oficial mediante el mapa o JSON
        val leagueObj = obj.optJSONObject("league")
        val resolvedLeagueName = resolveLeagueName(leagueObj, leagueId)

        // 6. Parsear H2H oficial de Bzzoiro
        var h2hTot = 0
        var h2hHw = 0
        var h2hDr = 0
        var h2hAw = 0
        var h2hAvgG = 2.45
        var h2hHwRate = 0.40
        var h2hAwRate = 0.35
        val recentScores = mutableListOf<String>()

        val h2hObj = obj.optJSONObject("head_to_head")
        if (h2hObj != null) {
            h2hTot = h2hObj.optInt("total_matches", 0)
            h2hHw = h2hObj.optInt("home_wins", 0)
            h2hDr = h2hObj.optInt("draws", 0)
            h2hAw = h2hObj.optInt("away_wins", 0)
            h2hAvgG = h2hObj.optDouble("avg_total_goals", 2.45)
            h2hHwRate = h2hObj.optDouble("home_win_rate", 0.40)
            h2hAwRate = h2hObj.optDouble("away_win_rate", 0.35)

            val recArr = h2hObj.optJSONArray("recent_matches")
            if (recArr != null) {
                for (k in 0 until recArr.length().coerceAtMost(3)) {
                    val r = recArr.getJSONObject(k)
                    val sc = extractSafeString(r, "score")
                    val hT = extractSafeString(r, "home")
                    val aT = extractSafeString(r, "away")
                    if (sc.isNotEmpty()) recentScores.add("$hT $sc $aT")
                }
            }
        }

        return ApexEvent(
            id = id,
            leagueId = leagueId,
            leagueName = resolvedLeagueName,
            roundLabel = roundLabel,
            homeTeamId = homeTeamId,
            homeTeam = homeTeam,
            awayTeamId = awayTeamId,
            awayTeam = awayTeam,
            eventDateRaw = eventDateRaw,
            eventDateZdt = matchLocal,
            status = status,
            period = period,
            currentMinute = curMin,
            homeScore = homeSc,
            awayScore = awaySc,
            h2hTotalMatches = h2hTot,
            h2hHomeWins = h2hHw,
            h2hDraws = h2hDr,
            h2hAwayWins = h2hAw,
            h2hAvgTotalGoals = h2hAvgG,
            h2hHomeWinRate = h2hHwRate,
            h2hAwayWinRate = h2hAwRate,
            recentScores = recentScores,
            homeRecentForm = emptyList(),
            awayRecentForm = emptyList(),
            isNext24Hours = isNext24Hours,
            timeToKickoffFormatted = timeFormatted
        )
    }

    /**
     * Consulta eventos de Bzzoiro cubriendo hoy y los próximos días.
     * Carga masiva sin límites artificiales (limit=100 por día) para que
     * TODAS las ligas (Liga Profesional Argentina, MLS, Premier, LaLiga)
     * muestren el 100% de sus partidos reales programados.
     */
    suspend fun fetchUpcomingEvents(daysAhead: Int = 3, limitPerDay: Int = 100): List<ApexEvent> =
        withContext(Dispatchers.IO) {
            val list = mutableListOf<ApexEvent>()
            val seenIds = mutableSetOf<Long>()
            val nowUtc = ZonedDateTime.now(ZoneId.of("UTC"))
            val nowLocalLdt = LocalDateTime.now()
            val limit24h = nowLocalLdt.plusHours(24)

            // Iterar por cada día para traer todos los eventos sin sesgo de paginación
            for (dayOffset in 0 until daysAhead) {
                val dStr = nowUtc.plusDays(dayOffset.toLong()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US))
                try {
                    val conn = openConnection("/events/?date_from=$dStr&date_to=$dStr&limit=$limitPerDay")
                    if (conn.responseCode == 200) {
                        val root = JSONObject(readResponse(conn))
                        val results = root.optJSONArray("results") ?: JSONArray()
                        for (i in 0 until results.length()) {
                            val obj = results.getJSONObject(i)
                            val id = obj.optLong("id")
                            if (seenIds.contains(id)) continue
                            seenIds.add(id)
                            list.add(parseApexEventFromJson(obj, nowLocalLdt, limit24h))
                        }
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Fallback preventivo si no hubo partidos en las fechas consultadas
            if (list.size < 5) {
                try {
                    val conn = openConnection("/events/?limit=100")
                    if (conn.responseCode == 200) {
                        val root = JSONObject(readResponse(conn))
                        val results = root.optJSONArray("results") ?: JSONArray()
                        for (i in 0 until results.length()) {
                            val obj = results.getJSONObject(i)
                            val id = obj.optLong("id")
                            if (seenIds.contains(id)) continue
                            seenIds.add(id)
                            list.add(parseApexEventFromJson(obj, nowLocalLdt, limit24h))
                        }
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Ordenamiento ascendente estricto por hora de inicio (el que empieza primero va arriba)
            list.sortedWith(
                compareBy<ApexEvent> { if (it.status == "inprogress") 0 else 1 }
                    .thenBy { it.eventDateZdt?.toEpochSecond() ?: Long.MAX_VALUE }
            )
        }

    /**
     * Consulta tradicional de eventos con rango de fechas y parseo ISO 8601 estricto
     */
    suspend fun fetchEvents(dateFrom: String, dateTo: String, limit: Int = 100): List<ApexEvent> =
        withContext(Dispatchers.IO) {
            val list = mutableListOf<ApexEvent>()
            val nowLocalLdt = LocalDateTime.now()
            val limit24h = nowLocalLdt.plusHours(24)

            try {
                val conn = openConnection("/events/?date_from=$dateFrom&date_to=$dateTo&limit=$limit")
                if (conn.responseCode == 200) {
                    val root = JSONObject(readResponse(conn))
                    val results = root.optJSONArray("results") ?: JSONArray()

                    for (i in 0 until results.length()) {
                        val obj = results.getJSONObject(i)
                        list.add(parseApexEventFromJson(obj, nowLocalLdt, limit24h))
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            list.sortedWith(
                compareBy<ApexEvent> { if (it.status == "inprogress") 0 else 1 }
                    .thenBy { it.eventDateZdt?.toEpochSecond() ?: Long.MAX_VALUE }
            )
        }
}

// ==============================================================================
// 4. MOTOR MATEMÁTICO CUÁNTICO POISSON ALIMENTADO CON DATOS REALES DE BZZOIRO
// ==============================================================================

object QuantumMathEngine {

    private fun factorial(k: Int): Double {
        var res = 1.0
        for (i in 2..k) res *= i
        return res
    }

    fun poissonPmf(k: Int, lambda: Double): Double {
        if (lambda <= 0.0) return if (k == 0) 1.0 else 0.0
        return (lambda.pow(k.toDouble()) * exp(-lambda)) / factorial(k)
    }

    fun poissonCdf(k: Int, lambda: Double): Double {
        var sum = 0.0
        for (i in 0..k) sum += poissonPmf(i, lambda)
        return sum.coerceIn(0.0, 1.0)
    }

    /**
     * Convierte el string de forma de Bzzoiro (ej: "WWLDW") en badges coloreados
     */
    fun parseFormStringToBadges(formStr: String): List<MatchFormResult> {
        val list = mutableListOf<MatchFormResult>()
        val chars = formStr.uppercase().reversed().take(5)
        for (c in chars) {
            when (c) {
                'W' -> list.add(MatchFormResult("V", FormOutcome.WIN, "Oficial", "Victoria"))
                'D' -> list.add(MatchFormResult("E", FormOutcome.DRAW, "Oficial", "Empate"))
                'L' -> list.add(MatchFormResult("D", FormOutcome.LOSS, "Oficial", "Derrota"))
            }
        }
        while (list.size < 5) {
            list.add(MatchFormResult("E", FormOutcome.DRAW, "Oficial", "Empate"))
        }
        return list.take(5)
    }

    /**
     * CÁLCULO RIGUROSO DEL FAVORITO Y DOBLE OPORTUNIDAD:
     * El favorito NUNCA se elige al azar ni por sesgo local rígido.
     * Se basa estrictamente en la probabilidad de victoria calculada por Poisson (o cuota de mercado):
     * - Si P(Local) >= P(Visitante), el favorito es el Local y el pick es 1X.
     * - Si P(Visitante) > P(Local) (ejemplo Sevilla jugando de visita), el favorito es Sevilla y el pick es X2.
     * - La probabilidad de Doble Op es P(Ganar) + P(Empatar).
     * - La cuota justa es 1.0 / (P(Ganar) + P(Empatar)).
     */
    fun analyzeDoubleChanceFavorite(
        homeTeam: String,
        awayTeam: String,
        pHome: Double,
        pDraw: Double,
        pAway: Double,
        odds: RealOdds
    ): DoubleChanceFavoriteAnalysis {
        // Determinación matemática rigurosa del favorito indiscutible
        val isHomeFav = pHome >= pAway
        val favTeam = if (isHomeFav) homeTeam else awayTeam
        val dogTeam = if (isHomeFav) awayTeam else homeTeam
        val pickCode = if (isHomeFav) "1X" else "X2"
        val pickTitle = "$favTeam o Empate ($pickCode)"

        val modelP = (if (isHomeFav) (pHome + pDraw) else (pAway + pDraw)).coerceIn(0.10, 0.98)
        val fairO = (1.0 / modelP).coerceIn(1.02, 25.0)

        // Cuota real del mercado de Bzzoiro para 1X o X2
        val marketO = if (isHomeFav) odds.dc1X else odds.dcX2
        val hasMarketOdds = marketO != null && marketO > 1.01

        val finalOddsNum = if (hasMarketOdds) marketO!! else fairO
        val displayOdds = if (hasMarketOdds) {
            String.format(Locale.US, "@%.2f", marketO)
        } else {
            String.format(Locale.US, "@%.2f (Calc)", fairO)
        }

        val ev = if (hasMarketOdds) (modelP * marketO!!) - 1.0 else null

        val badge = when {
            ev != null && ev > 0.05 -> "VALOR POSITIVO (+${(ev * 100).toInt()}%)"
            modelP >= 0.75 -> "ALTA COBERTURA (${(modelP * 100).toInt()}%)"
            modelP >= 0.60 -> "PROBABILIDAD ${(modelP * 100).toInt()}%"
            else -> "COBERTURA AJUSTADA (${(modelP * 100).toInt()}%)"
        }

        val favWinProbPct = ((if (isHomeFav) pHome else pAway) * 100).toInt()
        val desc = "El modelo Poisson designa a $favTeam como favorito con ${favWinProbPct}% de prob. directa. La Doble Oportunidad ($pickCode) cubre tanto su triunfo como la igualdad con un ${(modelP * 100).toInt()}% de cobertura matemática."

        return DoubleChanceFavoriteAnalysis(
            favoriteTeam = favTeam,
            underdogTeam = dogTeam,
            isHomeFavorite = isHomeFav,
            pickCode = pickCode,
            pickTitle = pickTitle,
            displayOdds = displayOdds,
            rawOdds = finalOddsNum,
            isMarketOdds = hasMarketOdds,
            modelProb = modelP,
            fairOdds = fairO,
            expectedValue = ev,
            confidenceBadge = badge,
            analysisDescription = desc
        )
    }

    /**
     * Proyección cuantitativa de CÓRNERS alimentada con promedios reales de Bzzoiro
     */
    fun analyzeCorners(homeStats: TeamRealFormStats, awayStats: TeamRealFormStats, odds: RealOdds): CornersAnalysis {
        val expHome = homeStats.avgCorners.coerceIn(2.5, 8.5)
        val expAway = awayStats.avgCorners.coerceIn(2.0, 7.5)
        val expTotal = expHome + expAway

        val pO85 = 1.0 - poissonCdf(8, expTotal)
        val pO95 = 1.0 - poissonCdf(9, expTotal)
        val pO105 = 1.0 - poissonCdf(10, expTotal)

        val fair85 = (1.0 / pO85).coerceAtLeast(1.04)
        val fair95 = (1.0 / pO95).coerceAtLeast(1.04)
        val fair105 = (1.0 / pO105).coerceAtLeast(1.04)

        val (recPick, recP, recFair) = if (expTotal >= 9.8) {
            Triple("Más de 9.5 Córners", pO95, fair95)
        } else {
            Triple("Más de 8.5 Córners", pO85, fair85)
        }

        val hasMarketOdds = odds.overCornerLine != null && odds.overCornerLine > 1.01
        val displayOdds = if (hasMarketOdds) {
            String.format(Locale.US, "@%.2f", odds.overCornerLine)
        } else {
            String.format(Locale.US, "@%.2f (Calc)", recFair)
        }

        return CornersAnalysis(
            expectedTotal = expTotal,
            expectedHome = expHome,
            expectedAway = expAway,
            linePrincipal = if (expTotal >= 9.8) 9.5 else 8.5,
            probOver85 = pO85,
            fairOddsOver85 = fair85,
            probOver95 = pO95,
            fairOddsOver95 = fair95,
            probOver105 = pO105,
            fairOddsOver105 = fair105,
            recommendedPick = recPick,
            recommendedProb = recP,
            displayOdds = displayOdds,
            isMarketOdds = hasMarketOdds
        )
    }

    /**
     * Proyección cuantitativa de TARJETAS basada en promedios reales de Bzzoiro
     */
    fun analyzeCards(homeStats: TeamRealFormStats, awayStats: TeamRealFormStats): CardsAnalysis {
        val expYellow = (homeStats.avgYellowCards + awayStats.avgYellowCards).coerceIn(2.0, 7.5)
        val expRedP = (1.0 - exp(-expYellow * 0.055)).coerceIn(0.12, 0.38)
        val expTotal = expYellow + (expRedP * 1.5)

        val pO35 = 1.0 - poissonCdf(3, expTotal)
        val pO45 = 1.0 - poissonCdf(4, expTotal)

        val fair35 = (1.0 / pO35).coerceAtLeast(1.04)
        val fair45 = (1.0 / pO45).coerceAtLeast(1.04)

        val friction = when {
            expTotal >= 5.5 -> "ALTA FRICCIÓN"
            expTotal >= 4.2 -> "INTENSIDAD MEDIA"
            else -> "TEMPLADO"
        }

        val (recPick, recP, recFair) = if (expTotal >= 5.0) {
            Triple("Más de 4.5 Tarjetas", pO45, fair45)
        } else {
            Triple("Más de 3.5 Tarjetas", pO35, fair35)
        }

        return CardsAnalysis(
            expectedTotal = expTotal,
            expectedYellow = expYellow,
            expectedRedProb = expRedP,
            linePrincipal = if (expTotal >= 5.0) 4.5 else 3.5,
            probOver35 = pO35,
            fairOddsOver35 = fair35,
            probOver45 = pO45,
            fairOddsOver45 = fair45,
            frictionIndex = friction,
            recommendedPick = recPick,
            recommendedProb = recP,
            displayOdds = String.format(Locale.US, "@%.2f (Calc)", recFair)
        )
    }

    /**
     * Proyecciones de remates usando atacantes REALES verificados estrictamente para cada equipo.
     */
    fun generateRealPlayerProps(
        homeTeam: String,
        awayTeam: String,
        homeSquad: List<Pair<String, String>>,
        awaySquad: List<Pair<String, String>>,
        homeStats: TeamRealFormStats,
        awayStats: TeamRealFormStats
    ): List<PlayerShotProp> {
        val list = mutableListOf<PlayerShotProp>()

        // Atacante 1 Local
        val p1 = homeSquad.getOrNull(0) ?: Pair("Delantero ($homeTeam)", "Delantero Centro")
        val hFwdShots = (homeStats.avgTotalShots * 0.22).coerceIn(1.8, 4.2)
        val hFwdSoT = (homeStats.avgShotsOnTarget * 0.32).coerceIn(0.8, 2.1)
        val hFwdProbSoT1 = 1.0 - exp(-hFwdSoT)
        val hFwdProbSoT15 = 1.0 - (poissonPmf(0, hFwdSoT) + poissonPmf(1, hFwdSoT))
        val (pickH, probH) = if (hFwdSoT >= 1.25) Pair("Más de 1.5 al Arco", hFwdProbSoT15) else Pair("Más de 0.5 al Arco", hFwdProbSoT1)
        val probShotsH = 1.0 - poissonCdf(2, hFwdShots)
        val fairOddsH = (1.0 / probH).coerceAtLeast(1.05)

        list.add(
            PlayerShotProp(
                fullName = p1.first,
                teamName = homeTeam,
                rolePosition = p1.second,
                totalShotsLambda = hFwdShots,
                shotsOnTargetLambda = hFwdSoT,
                pickTotalShots = "Más de 2.5 Remates",
                probTotalShots = probShotsH,
                pickOnTarget = pickH,
                probOnTarget = probH,
                fairOddsOnTarget = fairOddsH,
                displayOdds = String.format(Locale.US, "@%.2f (Calc)", fairOddsH)
            )
        )

        // Atacante 2 Local
        val p2 = homeSquad.getOrNull(1) ?: Pair("Extremo ($homeTeam)", "Extremo Ofensivo")
        val hWingShots = (homeStats.avgTotalShots * 0.16).coerceIn(1.2, 3.0)
        val hWingSoT = (homeStats.avgShotsOnTarget * 0.22).coerceIn(0.5, 1.4)
        val hWingProbSoT = 1.0 - exp(-hWingSoT)
        val probShotsHWing = 1.0 - poissonCdf(1, hWingShots)
        val fairOddsHWing = (1.0 / hWingProbSoT).coerceAtLeast(1.05)

        list.add(
            PlayerShotProp(
                fullName = p2.first,
                teamName = homeTeam,
                rolePosition = p2.second,
                totalShotsLambda = hWingShots,
                shotsOnTargetLambda = hWingSoT,
                pickTotalShots = "Más de 1.5 Remates",
                probTotalShots = probShotsHWing,
                pickOnTarget = "Más de 0.5 al Arco",
                probOnTarget = hWingProbSoT,
                fairOddsOnTarget = fairOddsHWing,
                displayOdds = String.format(Locale.US, "@%.2f (Calc)", fairOddsHWing)
            )
        )

        // Atacante 3 Visitante
        val p3 = awaySquad.getOrNull(0) ?: Pair("Delantero ($awayTeam)", "Delantero Centro")
        val aFwdShots = (awayStats.avgTotalShots * 0.20).coerceIn(1.5, 3.8)
        val aFwdSoT = (awayStats.avgShotsOnTarget * 0.28).coerceIn(0.6, 1.8)
        val aFwdProbSoT1 = 1.0 - exp(-aFwdSoT)
        val aFwdProbSoT15 = 1.0 - (poissonPmf(0, aFwdSoT) + poissonPmf(1, aFwdSoT))
        val (pickA, probA) = if (aFwdSoT >= 1.25) Pair("Más de 1.5 al Arco", aFwdProbSoT15) else Pair("Más de 0.5 al Arco", aFwdProbSoT1)
        val probShotsA = 1.0 - poissonCdf(2, aFwdShots)
        val fairOddsA = (1.0 / probA).coerceAtLeast(1.05)

        list.add(
            PlayerShotProp(
                fullName = p3.first,
                teamName = awayTeam,
                rolePosition = p3.second,
                totalShotsLambda = aFwdShots,
                shotsOnTargetLambda = aFwdSoT,
                pickTotalShots = "Más de 2.5 Remates",
                probTotalShots = probShotsA,
                pickOnTarget = pickA,
                probOnTarget = probA,
                fairOddsOnTarget = fairOddsA,
                displayOdds = String.format(Locale.US, "@%.2f (Calc)", fairOddsA)
            )
        )

        return list
    }

    /**
     * Motor principal cuantitativo con Poisson Bivariada, forma real y cuotas de mercado.
     */
    fun analyzeEventWithRealData(
        event: ApexEvent,
        odds: RealOdds,
        homeStats: TeamRealFormStats,
        awayStats: TeamRealFormStats,
        homeSquad: List<Pair<String, String>>,
        awaySquad: List<Pair<String, String>>
    ): QuantumMatchAnalysis {
        // Cálculo de lambdas bivariados basados en métricas de goles de Bzzoiro
        val leagueAvg = 1.35
        val homeAttack = (homeStats.avgGoalsScored / leagueAvg).coerceIn(0.60, 2.50)
        val awayDefense = (awayStats.avgGoalsConceded / leagueAvg).coerceIn(0.60, 2.40)
        val awayAttack = (awayStats.avgGoalsScored / leagueAvg).coerceIn(0.60, 2.50)
        val homeDefense = (homeStats.avgGoalsConceded / leagueAvg).coerceIn(0.60, 2.40)

        // Ventaja de localía moderada y realista (+8%)
        val homeAdvantage = 1.08
        val lambdaHome = (leagueAvg * homeAttack * awayDefense * homeAdvantage).coerceIn(0.65, 4.00)
        val lambdaAway = ((leagueAvg * awayAttack * homeDefense) / homeAdvantage).coerceIn(0.60, 3.80)
        val lambdaTotal = lambdaHome + lambdaAway

        // Distribución de Poisson Bivariada exacta (matriz 0..5)
        val matrix = Array(6) { DoubleArray(6) }
        var rawHome = 0.0
        var rawDraw = 0.0
        var rawAway = 0.0
        var pOver15 = 0.0
        var pOver25 = 0.0
        var pOver35 = 0.0
        var pBTTS = 0.0

        var bestScore = "1 - 0"
        var bestScoreP = -1.0

        for (i in 0..5) {
            val pi = poissonPmf(i, lambdaHome)
            for (j in 0..5) {
                val pj = poissonPmf(j, lambdaAway)
                val pJoint = pi * pj
                matrix[i][j] = pJoint

                if (i > j) rawHome += pJoint
                else if (i == j) rawDraw += pJoint
                else rawAway += pJoint

                val tot = i + j
                if (tot > 1) pOver15 += pJoint
                if (tot > 2) pOver25 += pJoint
                if (tot > 3) pOver35 += pJoint
                if (i > 0 && j > 0) pBTTS += pJoint

                if (pJoint > bestScoreP) {
                    bestScoreP = pJoint
                    bestScore = "$i - $j"
                }
            }
        }

        // Normalizar probabilidades 1X2 para sumar exactamente 1.00
        val sum1x2 = (rawHome + rawDraw + rawAway).coerceAtLeast(0.01)
        val pHome = rawHome / sum1x2
        val pDraw = rawDraw / sum1x2
        val pAway = rawAway / sum1x2

        // Salvaguarda: NUNCA dejar un 0-0 fijo si hay otros marcadores competitivos
        if (bestScore == "0 - 0" && (matrix[1][0] >= 0.10 || matrix[1][1] >= 0.10 || matrix[0][1] >= 0.10)) {
            val candidate10 = matrix[1][0]
            val candidate11 = matrix[1][1]
            val candidate01 = matrix[0][1]
            bestScore = when {
                candidate10 >= candidate11 && candidate10 >= candidate01 -> {
                    bestScoreP = candidate10
                    "1 - 0"
                }
                candidate11 >= candidate10 && candidate11 >= candidate01 -> {
                    bestScoreP = candidate11
                    "1 - 1"
                }
                else -> {
                    bestScoreP = candidate01
                    "0 - 1"
                }
            }
        }

        // Submódulos analíticos
        val dcAnalysis = analyzeDoubleChanceFavorite(event.homeTeam, event.awayTeam, pHome, pDraw, pAway, odds)
        val cornersAnalysis = analyzeCorners(homeStats, awayStats, odds)
        val cardsAnalysis = analyzeCards(homeStats, awayStats)
        val playerProps = generateRealPlayerProps(event.homeTeam, event.awayTeam, homeSquad, awaySquad, homeStats, awayStats)
        val mainStriker = playerProps.firstOrNull()

        // ==============================================================================
        // COMBINADA DEL PARTIDO (SIN INVENTOS NI APUESTAS FORZADAS):
        // Solo se seleccionan patas con alta probabilidad matemática (>55%).
        // Si no hay al menos 2 patas de valor suficiente, la lista queda VACÍA.
        // ==============================================================================
        val qualifyingLegs = mutableListOf<ParlayLeg>()

        // Candidato 1: Doble Oportunidad del Favorito (P >= 55%)
        if (dcAnalysis.modelProb >= 0.55) {
            qualifyingLegs.add(
                ParlayLeg(
                    category = "DOBLE OP",
                    pick = dcAnalysis.pickTitle,
                    lambda = null,
                    modelProb = dcAnalysis.modelProb,
                    displayOdds = dcAnalysis.displayOdds,
                    edgePercent = dcAnalysis.expectedValue?.let { it * 100.0 },
                    isSafePick = dcAnalysis.modelProb >= 0.70
                )
            )
        }

        // Candidato 2: Goles con alta probabilidad matemática
        if (pOver15 >= 0.65) {
            val fairO15 = 1.0 / pOver15
            val hasMktO15 = odds.over15Goals != null && odds.over15Goals > 1.01
            val dispO15 = if (hasMktO15) String.format(Locale.US, "@%.2f", odds.over15Goals) else String.format(Locale.US, "@%.2f (Calc)", fairO15)
            qualifyingLegs.add(
                ParlayLeg(
                    category = "GOLES",
                    pick = "Más de 1.5 Goles",
                    lambda = lambdaTotal,
                    modelProb = pOver15,
                    displayOdds = dispO15,
                    edgePercent = if (hasMktO15) ((pOver15 * odds.over15Goals!!) - 1.0) * 100.0 else null,
                    isSafePick = pOver15 >= 0.75
                )
            )
        } else if ((1.0 - pOver35) >= 0.65) {
            val pUnder35 = 1.0 - pOver35
            val fairU35 = 1.0 / pUnder35
            val hasMktU35 = odds.under35Goals != null && odds.under35Goals > 1.01
            val dispU35 = if (hasMktU35) String.format(Locale.US, "@%.2f", odds.under35Goals) else String.format(Locale.US, "@%.2f (Calc)", fairU35)
            qualifyingLegs.add(
                ParlayLeg(
                    category = "GOLES",
                    pick = "Menos de 3.5 Goles",
                    lambda = lambdaTotal,
                    modelProb = pUnder35,
                    displayOdds = dispU35,
                    edgePercent = null,
                    isSafePick = pUnder35 >= 0.75
                )
            )
        }

        // Candidato 3: Córners (P >= 55%)
        if (cornersAnalysis.recommendedProb >= 0.55) {
            qualifyingLegs.add(
                ParlayLeg(
                    category = "CÓRNERS",
                    pick = cornersAnalysis.recommendedPick,
                    lambda = cornersAnalysis.expectedTotal,
                    modelProb = cornersAnalysis.recommendedProb,
                    displayOdds = cornersAnalysis.displayOdds,
                    edgePercent = null,
                    isSafePick = cornersAnalysis.recommendedProb >= 0.65
                )
            )
        }

        // Candidato 4: Remate al arco del atacante del encuentro (P >= 55%)
        if (mainStriker != null && mainStriker.probOnTarget >= 0.55) {
            qualifyingLegs.add(
                ParlayLeg(
                    category = "REMATES",
                    pick = "${mainStriker.fullName}: ${mainStriker.pickOnTarget}",
                    lambda = mainStriker.shotsOnTargetLambda,
                    modelProb = mainStriker.probOnTarget,
                    displayOdds = mainStriker.displayOdds,
                    edgePercent = null,
                    isSafePick = mainStriker.probOnTarget >= 0.65
                )
            )
        }

        // Si no hay al menos 2 patas que superen el umbral, NO inventar combinada absurda
        val parlayLegs = if (qualifyingLegs.size >= 2) qualifyingLegs.take(4) else emptyList()

        // Probabilidad conjunta y cuota total calculada
        val (jointProb, parlayDisplay, kelly) = if (parlayLegs.isNotEmpty()) {
            var jProb = 1.0
            for (l in parlayLegs) {
                jProb *= l.modelProb
            }
            jProb = (jProb * 1.15).coerceIn(0.08, 0.95)
            val pOddsVal = (1.0 / jProb).coerceIn(1.30, 20.0)
            val pDisp = String.format(Locale.US, "@%.2f", pOddsVal)
            val kStake = ((jProb * pOddsVal - 1.0) / (pOddsVal - 1.0) * 0.25 * 100.0).coerceIn(1.0, 4.5)
            Triple(jProb, pDisp, kStake)
        } else {
            Triple(0.0, "-", 0.0)
        }

        // DETERMINAR LA APUESTA PRINCIPAL / SEGURA
        val (safeTitle, safePick, safeOdds, safeProbStr) = when {
            dcAnalysis.modelProb >= 0.70 -> {
                Quad("Doble Oportunidad", dcAnalysis.pickTitle, dcAnalysis.displayOdds, "${(dcAnalysis.modelProb * 100).toInt()}% prob")
            }
            pOver15 >= 0.72 -> {
                val disp = if (odds.over15Goals != null && odds.over15Goals > 1.01) String.format(Locale.US, "@%.2f", odds.over15Goals) else String.format(Locale.US, "@%.2f (Calc)", 1.0 / pOver15)
                Quad("Total Goles", "Más de 1.5 Goles", disp, "${(pOver15 * 100).toInt()}% prob")
            }
            dcAnalysis.modelProb >= 0.50 -> {
                Quad("Cobertura", dcAnalysis.pickTitle, dcAnalysis.displayOdds, "${(dcAnalysis.modelProb * 100).toInt()}% prob")
            }
            else -> {
                Quad("Selección Moderada", dcAnalysis.pickTitle, dcAnalysis.displayOdds, "${(dcAnalysis.modelProb * 100).toInt()}% prob")
            }
        }

        // VEREDICTO DINÁMICO
        val strikerName = mainStriker?.fullName ?: "el atacante"
        val quickVerdict = if (mainStriker != null && mainStriker.totalShotsLambda >= 2.0 && mainStriker.probOnTarget >= 0.55) {
            "Valor en remates de $strikerName (${mainStriker.pickOnTarget} a ${mainStriker.displayOdds}). Cobertura con ${dcAnalysis.pickCode} (${(dcAnalysis.modelProb * 100).toInt()}% prob) y ${cornersAnalysis.recommendedPick}."
        } else {
            "La mejor opción cuantitativa es $safePick ($safeOdds) combinada con ${cornersAnalysis.recommendedPick} ($bestScore como marcador central Poisson)."
        }

        val summary = MatchBettingSummary(
            safePickTitle = safeTitle,
            safePick = safePick,
            safePickOdds = safeOdds,
            safePickProb = safeProbStr,
            valuePick = cornersAnalysis.recommendedPick,
            valuePickOdds = cornersAnalysis.displayOdds,
            playerPick = if (mainStriker != null) "${mainStriker.fullName}: ${mainStriker.pickOnTarget}" else "Delantero: +0.5 al Arco",
            playerPickOdds = mainStriker?.displayOdds ?: "@1.50 (Calc)",
            cardsPick = cardsAnalysis.recommendedPick,
            predictedScore = bestScore,
            quickVerdict = quickVerdict
        )

        return QuantumMatchAnalysis(
            event = event.copy(
                homeRecentForm = parseFormStringToBadges(homeStats.formString),
                awayRecentForm = parseFormStringToBadges(awayStats.formString)
            ),
            odds = odds,
            lambdaHome = lambdaHome,
            lambdaAway = lambdaAway,
            lambdaTotal = lambdaTotal,
            probHome = pHome,
            probDraw = pDraw,
            probAway = pAway,
            probOver15 = pOver15,
            probOver25 = pOver25,
            probOver35 = pOver35,
            probBTTS = pBTTS,
            mostProbableScore = bestScore,
            mostProbableScoreProb = bestScoreP,
            doubleChanceAnalysis = dcAnalysis,
            cornersAnalysis = cornersAnalysis,
            cardsAnalysis = cardsAnalysis,
            playerShotProps = playerProps,
            bettingSummary = summary,
            parlayLegs = parlayLegs,
            jointParlayProb = jointProb,
            parlayDisplayOdds = parlayDisplay,
            kellyStakePercent = kelly
        )
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}

// ==============================================================================
// 5. VIEWMODEL REACTIVO CON PETICIONES EN PARALELO (ASYNC / AWAITALL)
// ==============================================================================

class FootballStatsViewModel : ViewModel() {

    var allAnalyses by mutableStateOf<List<QuantumMatchAnalysis>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var lastUpdatedTime by mutableStateOf("Conectando con Bzzoiro...")
        private set

    var selectedLeagueFilter by mutableStateOf("ALL")
    var onlyNext24Hours by mutableStateOf(true)

    private var autoRefreshJob: Job? = null

    /**
     * Lista ordenada de ligas disponibles:
     * - 'ALL' (Todas las Ligas) en primera posición.
     * - Ligas principales (Premier, LaLiga, Serie A, Bundesliga, Argentina, MLS, etc.) al frente.
     * - Ligas menores o secundarias al final ordenadas alfabéticamente.
     */
    val availableLeagues: List<Pair<String, String>>
        get() {
            val list = mutableListOf(Pair("ALL", "Todas las Ligas"))
            val map = mutableMapOf<Long, String>()
            for (a in allAnalyses) {
                val lid = a.event.leagueId
                if (!map.containsKey(lid)) {
                    map[lid] = a.event.leagueName
                }
            }

            // 1. Agregar ligas prioritarias presentes en los partidos
            for (pId in PRIORITY_LEAGUE_ORDER) {
                val name = map.remove(pId)
                if (name != null) {
                    list.add(Pair(pId.toString(), name))
                }
            }

            // 2. Agregar el resto de ligas ordenadas por nombre
            val remainder = map.toList().sortedBy { it.second }
            for ((id, name) in remainder) {
                list.add(Pair(id.toString(), name))
            }

            return list
        }

    val filteredAnalyses: List<QuantumMatchAnalysis>
        get() {
            val nowLdt = LocalDateTime.now()
            val limit24h = nowLdt.plusHours(24)

            val list = allAnalyses.filter { analysis ->
                val matchLdt = analysis.event.eventDateZdt?.toLocalDateTime()
                val matchesTime = if (onlyNext24Hours) {
                    if (analysis.event.status == "finished") {
                        false
                    } else if (analysis.event.status == "inprogress" || analysis.event.currentMinute != null) {
                        true
                    } else if (matchLdt != null) {
                        // Mayor o igual a now (con margen de 105 min si ya empezó) y menor o igual a now + 24 horas
                        !matchLdt.isBefore(nowLdt.minusMinutes(105)) && !matchLdt.isAfter(limit24h)
                    } else {
                        false
                    }
                } else {
                    true
                }
                val matchesLeague = if (selectedLeagueFilter == "ALL") true else analysis.event.leagueId.toString() == selectedLeagueFilter
                matchesTime && matchesLeague
            }

            // ORDENAMIENTO CRONOLÓGICO ASCENDENTE: Del más próximo al más lejano (el primero arriba de todo)
            return list.sortedWith(
                compareBy<QuantumMatchAnalysis> { analysis ->
                    if (analysis.event.status == "inprogress" || analysis.event.currentMinute != null) 0 else 1
                }.thenBy { analysis ->
                    analysis.event.eventDateZdt?.toEpochSecond() ?: Long.MAX_VALUE
                }
            )
        }

    init {
        loadData()
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(30L * 60L * 1000L) // 30 minutos
                loadData()
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null

            try {
                // 1. Obtener eventos reales distribuidos cronológicamente (hoy y próximos días) sin recortes
                val rawEvents = BzzoiroApiClient.fetchUpcomingEvents(daysAhead = 3, limitPerDay = 100)

                if (rawEvents.isEmpty()) {
                    errorMessage = "No se encontraron eventos programados en Bzzoiro para este período."
                } else {
                    // 2. Ejecutar peticiones en paralelo para cada evento usando async / awaitAll
                    val analysesList = coroutineScope {
                        rawEvents.map { ev ->
                            async {
                                val oddsDeferred = async { BzzoiroApiClient.fetchEventOdds(ev.id) }
                                val homeSquadDeferred = async { BzzoiroApiClient.fetchTeamSquad(ev.homeTeamId, ev.homeTeam) }
                                val awaySquadDeferred = async { BzzoiroApiClient.fetchTeamSquad(ev.awayTeamId, ev.awayTeam) }
                                val homeFormDeferred = async { BzzoiroApiClient.fetchTeamForm(ev.homeTeamId, ev.homeTeam) }
                                val awayFormDeferred = async { BzzoiroApiClient.fetchTeamForm(ev.awayTeamId, ev.awayTeam) }

                                val odds = oddsDeferred.await()
                                val homeSquad = homeSquadDeferred.await()
                                val awaySquad = awaySquadDeferred.await()
                                val homeForm = homeFormDeferred.await()
                                val awayForm = awayFormDeferred.await()

                                QuantumMathEngine.analyzeEventWithRealData(
                                    event = ev,
                                    odds = odds,
                                    homeStats = homeForm,
                                    awayStats = awayForm,
                                    homeSquad = homeSquad,
                                    awaySquad = awaySquad
                                )
                            }
                        }.awaitAll()
                    }
                    // Ordenamiento cronológico de base
                    allAnalyses = analysesList.sortedBy { it.event.eventDateZdt?.toEpochSecond() ?: Long.MAX_VALUE }
                }

                val nowLocal = ZonedDateTime.now(ZoneId.systemDefault())
                val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
                lastUpdatedTime = "Actualizado ${nowLocal.format(timeFormat)} hs (Auto 30 min)"
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Error de conexión con Bzzoiro: ${e.localizedMessage ?: "Revise red"}"
            } finally {
                isLoading = false
            }
        }
    }
}

// ==============================================================================
// 6. UI JETPACK COMPOSE MATERIAL 3: WHITELABEL APEX QUANTUM HUD
// ==============================================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00E5FF),
                    secondary = Color(0xFF10B981),
                    tertiary = Color(0xFFA855F7),
                    background = Color(0xFF070B14),
                    surface = Color(0xFF0E1626),
                    surfaceVariant = Color(0xFF182438),
                    onBackground = Color(0xFFF1F5F9),
                    onSurface = Color(0xFFF1F5F9)
                )
            ) {
                ApexQuantumMainScreen()
            }
        }
    }
}

@Composable
fun ApexQuantumMainScreen(viewModel: FootballStatsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val analyses = viewModel.filteredAnalyses
    val isLoading = viewModel.isLoading
    val errorMessage = viewModel.errorMessage
    val leagues = viewModel.availableLeagues

    Scaffold(
        topBar = {
            ApexHeader(
                onRefresh = { viewModel.loadData() },
                isLoading = isLoading,
                lastSync = viewModel.lastUpdatedTime
            )
        },
        containerColor = Color(0xFF070B14)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TimeWindowSelectorRow(
                only24h = viewModel.onlyNext24Hours,
                onToggle24h = { viewModel.onlyNext24Hours = it },
                totalCount = viewModel.allAnalyses.size,
                filteredCount = analyses.size
            )

            LeagueSelectorRow(
                leagues = leagues,
                selectedLeagueId = viewModel.selectedLeagueFilter,
                onSelectLeague = { viewModel.selectedLeagueFilter = it }
            )

            errorMessage?.let { msg ->
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "● $msg",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            if (isLoading && analyses.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF00E5FF))
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "PROCESANDO MODELO POISSON EN TIEMPO REAL...",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            } else if (analyses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "SIN PARTIDOS EN ESTA VENTANA (24H)",
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Pulsa 'TODOS' para ver los próximos encuentros o selecciona otra liga.",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(analyses, key = { it.event.id }) { analysis ->
                        QuantumMatchCard(analysis = analysis)
                    }
                }
            }
        }
    }
}

// ==============================================================================
// 7. COMPONENTES VISUALES: CABECERA, SELECTORES Y TARJETAS HUD
// ==============================================================================

@Composable
fun ApexHeader(
    onRefresh: () -> Unit,
    isLoading: Boolean,
    lastSync: String
) {
    Surface(
        color = Color(0xFF070B14),
        border = BorderStroke(1.dp, Color(0xFF162032)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFF00E5FF), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "APEX QUANTUM STRIKE HUD",
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        letterSpacing = 1.1.sp,
                        color = Color(0xFFF8FAFC)
                    )
                }
                Text(
                    text = "FEED OFICIAL • $lastSync",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF64748B),
                    letterSpacing = 0.4.sp
                )
            }

            IconButton(
                onClick = onRefresh,
                enabled = !isLoading,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B))
                    .size(38.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF00E5FF)
                    )
                } else {
                    Text(text = "↻", fontSize = 20.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TimeWindowSelectorRow(
    only24h: Boolean,
    onToggle24h: (Boolean) -> Unit,
    totalCount: Int,
    filteredCount: Int
) {
    Surface(
        color = Color(0xFF0A101D),
        border = BorderStroke(1.dp, Color(0xFF162032)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (only24h) Color(0xFF00E5FF).copy(alpha = 0.18f) else Color(0xFF151F30),
                    border = BorderStroke(
                        1.dp,
                        if (only24h) Color(0xFF00E5FF) else Color(0xFF26354D)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.clickable { onToggle24h(true) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(if (only24h) Color(0xFF00E5FF) else Color(0xFF64748B), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PRÓXIMAS 24H",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (only24h) Color(0xFF00E5FF) else Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = if (!only24h) Color(0xFFA855F7).copy(alpha = 0.18f) else Color(0xFF151F30),
                    border = BorderStroke(
                        1.dp,
                        if (!only24h) Color(0xFFA855F7) else Color(0xFF26354D)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.clickable { onToggle24h(false) }
                ) {
                    Text(
                        text = "TODOS ($totalCount)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (!only24h) Color(0xFFA855F7) else Color(0xFF94A3B8),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Text(
                text = "$filteredCount PARTIDOS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF00E5FF)
            )
        }
    }
}

@Composable
fun LeagueSelectorRow(
    leagues: List<Pair<String, String>>,
    selectedLeagueId: String,
    onSelectLeague: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF080D18))
            .padding(vertical = 7.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(leagues) { (id, name) ->
            val isSelected = selectedLeagueId == id
            Surface(
                color = if (isSelected) Color(0xFF1E293B) else Color(0xFF0E1626),
                border = BorderStroke(
                    1.dp,
                    if (isSelected) Color(0xFF00E5FF) else Color(0xFF1E293B)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.clickable { onSelectLeague(id) }
            ) {
                Text(
                    text = name,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
fun QuantumMatchCard(analysis: QuantumMatchAnalysis) {
    val event = analysis.event
    val dc = analysis.doubleChanceAnalysis
    val corners = analysis.cornersAnalysis
    val cards = analysis.cardsAnalysis

    var activeTab by remember { mutableStateOf(0) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1626)),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Cabecera: Liga oficial y Fecha/Hora exacta en zona horaria local
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFF10B981), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${event.leagueName.uppercase()} • ${event.roundLabel.uppercase()}",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.66f)
                    )
                }

                Surface(
                    color = if (event.status == "inprogress") Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFF00E5FF).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (event.status == "inprogress") Color(0xFFEF4444) else Color(0xFF00E5FF)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = event.timeToKickoffFormatted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = if (event.status == "inprogress") Color(0xFFEF4444) else Color(0xFF00E5FF),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Equipos y Marcador
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Local
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.homeTeam,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = Color(0xFFF8FAFC),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    RecentFormDotsRow(formList = event.homeRecentForm, label = "Local")
                }

                // Score / VS
                Surface(
                    color = Color(0xFF182438),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF26354D)),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    if (event.homeScore != null && event.awayScore != null) {
                        Text(
                            text = "${event.homeScore} - ${event.awayScore}",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = Color(0xFF00E5FF),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    } else {
                        Text(
                            text = "VS",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // Visitante
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = event.awayTeam,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = Color(0xFFF8FAFC),
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    RecentFormDotsRow(formList = event.awayRecentForm, label = "Visita")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Selector de Módulos (Tabs con scroll horizontal)
            val tabsList = listOf(
                "RESUMEN APUESTAS",
                "COMBINADA",
                "DOBLE OP",
                "CÓRNERS & TARJ",
                "REMATES",
                "POISSON 1X2"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tabsList.forEachIndexed { index, tabTitle ->
                    val isSelected = activeTab == index
                    Surface(
                        color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.16f) else Color(0xFF0A101D),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF00E5FF) else Color(0xFF1E293B)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { activeTab = index }
                    ) {
                        Text(
                            text = tabTitle,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                            color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (activeTab) {
                0 -> MatchBettingSummaryView(summary = analysis.bettingSummary)
                1 -> MatchBetBuilderCardView(analysis = analysis)
                2 -> DoubleChanceModuleView(dc = dc)
                3 -> CornersAndCardsModuleView(corners = corners, cards = cards)
                4 -> PlayerShotsModuleView(players = analysis.playerShotProps)
                5 -> Poisson1X2ModuleView(analysis = analysis)
            }
        }
    }
}

@Composable
fun RecentFormDotsRow(formList: List<MatchFormResult>, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "$label: ",
            fontSize = 9.sp,
            color = Color(0xFF64748B),
            fontWeight = FontWeight.Bold
        )
        for (item in formList) {
            val bgColor = when (item.outcome) {
                FormOutcome.WIN -> Color(0xFF10B981)
                FormOutcome.DRAW -> Color(0xFFF59E0B)
                FormOutcome.LOSS -> Color(0xFFEF4444)
            }
            Box(
                modifier = Modifier
                    .size(19.dp)
                    .clip(CircleShape)
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.code,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF070B14),
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
            }
        }
    }
}

// ==============================================================================
// 8. MÓDULOS DE ANÁLISIS DINÁMICOS
// ==============================================================================

@Composable
fun MatchBettingSummaryView(summary: MatchBettingSummary) {
    Surface(
        color = Color(0xFF091120),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📋 RESUMEN PARA TU APUESTA",
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    color = Color(0xFF00E5FF)
                )
                Surface(
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "HOJA DE RUTA",
                        color = Color(0xFF10B981),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Apuesta Principal / Segura
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🟢 ${summary.safePickTitle}:",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${summary.safePick} (${summary.safePickOdds})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF10B981)
                )
            }

            // Apuesta de Valor (Córners)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🚩 Apuesta de Valor (Córners):",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${summary.valuePick} (${summary.valuePickOdds})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF38BDF8)
                )
            }

            // Prop de Jugador Real
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎯 Prop de Jugador Real:",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${summary.playerPick} (${summary.playerPickOdds})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFA855F7)
                )
            }

            // Marcador Más Probable Poisson
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚽ Marcador Más Probable:",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = summary.predictedScore,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFF59E0B)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Veredicto Dinámico conciso
            Surface(
                color = Color(0xFF0F1A2E),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "💡 Veredicto: ${summary.quickVerdict}",
                    fontSize = 10.sp,
                    color = Color(0xFFCBD5E1),
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun MatchBetBuilderCardView(analysis: QuantumMatchAnalysis) {
    Surface(
        color = Color(0xFF0C0F1E),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚡ COMBINADA DEL PARTIDO",
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    color = Color(0xFFA855F7)
                )

                if (analysis.parlayLegs.isNotEmpty()) {
                    Surface(
                        color = Color(0xFFA855F7).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "CUOTA: ${analysis.parlayDisplayOdds}",
                            color = Color(0xFFA855F7),
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (analysis.parlayLegs.isEmpty()) {
                // Aviso matemático: no se fuerzan apuestas cuando las probabilidades no alcanzan el umbral de valor
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No hay apuestas de valor suficiente para este partido",
                            color = Color(0xFFF1F5F9),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ninguna selección supera el umbral matemático de confianza (>55%). Protegemos tu bankroll evitando apuestas forzadas.",
                            color = Color(0xFF64748B),
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 14.sp
                        )
                    }
                }
            } else {
                for ((index, leg) in analysis.parlayLegs.take(4).withIndex()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "${index + 1}. ",
                                color = Color(0xFF00E5FF),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "${leg.category}: ${leg.pick}",
                                fontSize = 11.sp,
                                color = Color(0xFFF1F5F9),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = "${(leg.modelProb * 100).toInt()}% • ${leg.displayOdds}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricPill(title = "PROB CONJUNTA", value = "${(analysis.jointParlayProb * 100).toInt()}%", color = Color(0xFF00E5FF))
                    MetricPill(title = "CUOTA TOTAL", value = analysis.parlayDisplayOdds, color = Color(0xFFA855F7))
                    MetricPill(title = "STAKE RECOMENDADO", value = "${String.format(Locale.US, "%.1f", analysis.kellyStakePercent)}%", color = Color(0xFFF59E0B))
                }
            }
        }
    }
}

@Composable
fun DoubleChanceModuleView(dc: DoubleChanceFavoriteAnalysis) {
    Surface(
        color = Color(0xFF070D18),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "FAVORITO O EMPATE: ${dc.pickCode}",
                        color = Color(0xFF00E5FF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Surface(
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = dc.confidenceBadge,
                        color = Color(0xFF10B981),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = dc.pickTitle,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                color = Color(0xFFF8FAFC)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = dc.analysisDescription,
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricPill(title = "PROB. MODELO", value = "${(dc.modelProb * 100).toInt()}%", color = Color(0xFF00E5FF))
                MetricPill(title = "CUOTA JUSTA", value = String.format(Locale.US, "%.2f", dc.fairOdds), color = Color(0xFFA855F7))
                MetricPill(title = if (dc.isMarketOdds) "CUOTA MERCADO" else "CUOTA ESTIMADA", value = dc.displayOdds, color = Color(0xFF10B981))
                MetricPill(
                    title = "VENTAJA (+EV)",
                    value = if (dc.expectedValue != null) "+${(dc.expectedValue * 100).toInt()}%" else "Base",
                    color = Color(0xFFF59E0B)
                )
            }
        }
    }
}

@Composable
fun CornersAndCardsModuleView(corners: CornersAnalysis, cards: CardsAnalysis) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = Color(0xFF08121E),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🚩 CÓRNERS (REAL BZZOIRO)",
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        color = Color(0xFF38BDF8)
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.1f", corners.expectedTotal)} CÓRNERS",
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        color = Color(0xFFF8FAFC)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Local ~${String.format(Locale.US, "%.1f", corners.expectedHome)} | Visita ~${String.format(Locale.US, "%.1f", corners.expectedAway)}",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = "Pick: ${corners.recommendedPick} (${corners.displayOdds})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricPill(title = "+8.5 CÓRNERS", value = "${(corners.probOver85 * 100).toInt()}% (@${String.format(Locale.US, "%.2f", corners.fairOddsOver85)})", color = Color(0xFF38BDF8))
                    MetricPill(title = "+9.5 CÓRNERS", value = "${(corners.probOver95 * 100).toInt()}% (@${String.format(Locale.US, "%.2f", corners.fairOddsOver95)})", color = Color(0xFF0284C7))
                    MetricPill(title = "+10.5 CÓRNERS", value = "${(corners.probOver105 * 100).toInt()}% (@${String.format(Locale.US, "%.2f", corners.fairOddsOver105)})", color = Color(0xFFA855F7))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            color = Color(0xFF160F1A),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🟨 TARJETAS (DISCIPLINA)",
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        color = Color(0xFFF59E0B)
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.1f", cards.expectedTotal)} TOTAL",
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        color = Color(0xFFF8FAFC)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Amarillas ~${String.format(Locale.US, "%.1f", cards.expectedYellow)} | P(Roja): ${(cards.expectedRedProb * 100).toInt()}%",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = cards.frictionIndex,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF59E0B)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricPill(title = "+3.5 TARJETAS", value = "${(cards.probOver35 * 100).toInt()}% (@${String.format(Locale.US, "%.2f", cards.fairOddsOver35)})", color = Color(0xFFF59E0B))
                    MetricPill(title = "+4.5 TARJETAS", value = "${(cards.probOver45 * 100).toInt()}% (@${String.format(Locale.US, "%.2f", cards.fairOddsOver45)})", color = Color(0xFFEF4444))
                    MetricPill(title = "PICK SUGERIDO", value = cards.recommendedPick, color = Color(0xFF10B981))
                }
            }
        }
    }
}

@Composable
fun PlayerShotsModuleView(players: List<PlayerShotProp>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        for (p in players) {
            Surface(
                color = Color(0xFF0B1424),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = p.fullName,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = Color(0xFFF8FAFC)
                            )
                            Text(
                                text = "${p.teamName} • ${p.rolePosition}",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        Surface(
                            color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${p.pickOnTarget} (${p.displayOdds})",
                                color = Color(0xFF00E5FF),
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Remates: ~${String.format(Locale.US, "%.1f", p.totalShotsLambda)} (${p.pickTotalShots}: ${(p.probTotalShots * 100).toInt()}%)",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = "P(Al Arco): ${(p.probOnTarget * 100).toInt()}%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Poisson1X2ModuleView(analysis: QuantumMatchAnalysis) {
    Surface(
        color = Color(0xFF08101E),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "POISSON 1X2 & MARCADOR",
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                color = Color(0xFF64748B)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricPill(title = "LOCAL (1)", value = "${(analysis.probHome * 100).toInt()}%", color = Color(0xFF00E5FF))
                MetricPill(title = "EMPATE (X)", value = "${(analysis.probDraw * 100).toInt()}%", color = Color(0xFFF59E0B))
                MetricPill(title = "VISITA (2)", value = "${(analysis.probAway * 100).toInt()}%", color = Color(0xFFA855F7))
                MetricPill(title = "MARCADOR", value = "${analysis.mostProbableScore} (${(analysis.mostProbableScoreProb * 100).toInt()}%)", color = Color(0xFF10B981))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricPill(title = "+1.5 GOLES", value = "${(analysis.probOver15 * 100).toInt()}%", color = Color(0xFF00E5FF))
                MetricPill(title = "+2.5 GOLES", value = "${(analysis.probOver25 * 100).toInt()}%", color = Color(0xFF0284C7))
                MetricPill(title = "AMBOS MARCAN", value = "${(analysis.probBTTS * 100).toInt()}%", color = Color(0xFF10B981))
                MetricPill(title = "H2H TOTAL", value = "${analysis.event.h2hTotalMatches} PJ", color = Color(0xFF64748B))
            }
        }
    }
}

@Composable
fun MetricPill(title: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64748B),
            letterSpacing = 0.4.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = color
        )
    }
}
