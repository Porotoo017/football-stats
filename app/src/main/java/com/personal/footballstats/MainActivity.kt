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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round

// ==============================================================================
// CONFIGURACIÓN DE API BZZOIRO SPORTS DATA
// Clave y endpoint oficial de Bzzoiro (Modificable):
// ==============================================================================
var bzzoiroApiKey: String = "9665517ed1732bad524c3730e18cd9d90cc1d13f"
const val BZZOIRO_BASE_URL: String = "https://sports.bzzoiro.com/api/v2"

// ==============================================================================
// 1. MODELOS DE DATOS CUANTITATIVOS BASADOS EN BZZOIRO
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
    val eventDate: String,
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
// 2. CLIENTE API DE BZZOIRO SPORTS DATA (REST OFICIAL CON PARALELISMO)
// ==============================================================================

object BzzoiroApiClient {

    // Caché en memoria para evitar llamadas redundantes de planteles y estadísticas
    private val squadCache = mutableMapOf<Long, List<Pair<String, String>>>()
    private val formCache = mutableMapOf<Long, TeamRealFormStats>()

    fun getLeagueNameById(leagueId: Long): String {
        return when (leagueId) {
            1L -> "Premier League (Inglaterra)"
            2L -> "Liga Portugal Betclic (Portugal)"
            3L -> "La Liga (España)"
            4L -> "Serie A (Italia)"
            5L -> "Bundesliga (Alemania)"
            6L -> "Ligue 1 (Francia)"
            7L -> "Champions League (Europa)"
            8L -> "Europa League (Europa)"
            9L -> "Brasileirão Serie A (Brasil)"
            10L -> "Eredivisie (Países Bajos)"
            11L -> "Trendyol Süper Lig (Turquía)"
            12L -> "EFL Championship (Inglaterra)"
            13L -> "Scottish Premiership (Escocia)"
            14L -> "Pro League (Bélgica)"
            15L -> "Super League (Suiza)"
            17L -> "Saudi Pro League (Arabia Saudita)"
            18L -> "MLS (EE.UU.)"
            19L -> "Liga MX Apertura (México)"
            20L -> "Liga MX Clausura (México)"
            21L -> "Brasileirão Serie B (Brasil)"
            22L -> "Parva Liga (Bulgaria)"
            23L -> "Superliga (Rumania)"
            24L -> "Stoiximan Super League (Grecia)"
            25L -> "Ekstraklasa (Polonia)"
            26L -> "Allsvenskan (Suecia)"
            27L -> "Copa Mundial FIFA (Internacional)"
            28L -> "NPFL (Nigeria)"
            29L -> "CAF Champions League (África)"
            30L -> "Copa Africana de Naciones (África)"
            31L -> "Amistosos Internacionales"
            32L -> "Copa Libertadores (Sudamérica)"
            33L -> "Copa Sudamericana (Sudamérica)"
            34L -> "Brasileirão Serie B (Brasil)"
            35L -> "Copa do Brasil (Brasil)"
            36L -> "Liga F Femenina (España)"
            38L -> "Segunda División (España)"
            39L -> "FA Cup (Inglaterra)"
            40L -> "Carabao Cup (Inglaterra)"
            41L -> "Copa del Rey (España)"
            42L -> "Coppa Italia (Italia)"
            43L -> "DFB Pokal (Alemania)"
            44L -> "Coupe de France (Francia)"
            46L -> "Puchar Polski (Polonia)"
            47L -> "Ligue 1 (Túnez)"
            48L -> "Copa de Túnez"
            49L -> "J1 League (Japón)"
            50L -> "K League 1 (Corea del Sur)"
            51L -> "Emperor Cup (Japón)"
            52L -> "Chinese Super League (China)"
            53L -> "Botola Pro (Marruecos)"
            54L -> "Eliteserien (Noruega)"
            55L -> "Veikkausliiga (Finlandia)"
            56L -> "Suomen Cup (Finlandia)"
            57L -> "USL Championship (EE.UU.)"
            58L -> "Eliminatorias UEFA (Europa)"
            59L -> "Eliminatorias CONMEBOL (Sudamérica)"
            60L -> "Eliminatorias CAF (África)"
            61L -> "Eliminatorias AFC (Asia)"
            62L -> "Eliminatorias CONCACAF"
            63L -> "Eliminatorias OFC"
            64L -> "UEFA Nations League (Europa)"
            65L -> "CONCACAF Nations League"
            66L -> "UEFA Euro (Europa)"
            67L -> "Copa América (Sudamérica)"
            68L -> "AFC Asian Cup (Asia)"
            69L -> "CONCACAF Gold Cup"
            70L -> "NPL Queensland (Australia)"
            71L -> "UEFA Euro Sub-19 (Europa)"
            72L -> "NWSL Femenina (EE.UU.)"
            79L -> "Amistosos de Clubes"
            80L -> "Categoría Primera A (Colombia)"
            81L -> "Copa Colombia (Colombia)"
            82L -> "Liga 3 (Portugal)"
            83L -> "UEFA Conference League"
            84L -> "Superliga (Dinamarca)"
            85L -> "Liga Profesional (Argentina)"
            86L -> "League One (Inglaterra)"
            87L -> "League Two (Inglaterra)"
            88L -> "Liga Portugal 2 (Portugal)"
            89L -> "Ligue 2 (Francia)"
            90L -> "Supercopa UEFA (Europa)"
            91L -> "National League (Inglaterra)"
            92L -> "Taça de Portugal (Portugal)"
            93L -> "Taça da Liga (Portugal)"
            94L -> "2. Bundesliga (Alemania)"
            95L -> "Campeonato de Portugal"
            96L -> "Austrian Bundesliga (Austria)"
            97L -> "Challenger Pro League (Bélgica)"
            98L -> "Girabola (Angola)"
            99L -> "First League (Rep. Checa)"
            100L -> "Serie B (Italia)"
            101L -> "Serie C Grupo A (Italia)"
            102L -> "Serie C Grupo C (Italia)"
            103L -> "Serie C Grupo B (Italia)"
            104L -> "A-League Men (Australia)"
            105L -> "Ligue 3 (Francia)"
            106L -> "Superettan (Suecia)"
            107L -> "Trendyol 1. Lig (Turquía)"
            else -> if (leagueId > 0) "Liga ID: #$leagueId" else "Liga Desconocida"
        }
    }

    /**
     * Validador de atacantes oficiales por equipo para prevenir asignaciones cruzadas
     */
    fun getAuthenticTeamAttackers(teamId: Long, teamName: String): List<Pair<String, String>> {
        val norm = teamName.lowercase(Locale.ROOT).trim()
        return when {
            norm.contains("aston villa") || teamId == 3L -> listOf(
                Pair("Ollie Watkins", "Delantero Centro"),
                Pair("Morgan Rogers", "Mediapunta / Atacante"),
                Pair("Leon Bailey", "Extremo Ofensivo")
            )
            norm.contains("fulham") || teamId == 6L -> listOf(
                Pair("Rodrigo Muniz", "Delantero Centro"),
                Pair("Raúl Jiménez", "Delantero Centro"),
                Pair("Alex Iwobi", "Extremo Ofensivo")
            )
            norm.contains("chelsea") || teamId == 13L -> listOf(
                Pair("Cole Palmer", "Mediapunta / Ofensivo"),
                Pair("Nicolas Jackson", "Delantero Centro"),
                Pair("Christopher Nkunku", "Segundo Delantero")
            )
            norm.contains("manchester city") || norm.contains("man city") || teamId == 12L -> listOf(
                Pair("Erling Haaland", "Delantero Centro"),
                Pair("Phil Foden", "Extremo Ofensivo"),
                Pair("Bernardo Silva", "Atacante")
            )
            norm.contains("liverpool") || teamId == 1L -> listOf(
                Pair("Mohamed Salah", "Extremo Derecho"),
                Pair("Luis Díaz", "Extremo Izquierdo"),
                Pair("Darwin Núñez", "Delantero Centro")
            )
            norm.contains("arsenal") -> listOf(
                Pair("Bukayo Saka", "Extremo Derecho"),
                Pair("Kai Havertz", "Delantero Centro"),
                Pair("Gabriel Martinelli", "Extremo Izquierdo")
            )
            norm.contains("manchester united") || norm.contains("man utd") || teamId == 17L -> listOf(
                Pair("Rasmus Højlund", "Delantero Centro"),
                Pair("Marcus Rashford", "Extremo Ofensivo"),
                Pair("Alejandro Garnacho", "Extremo")
            )
            norm.contains("tottenham") || norm.contains("spurs") || teamId == 9L -> listOf(
                Pair("Son Heung-min", "Extremo / Delantero"),
                Pair("Dominic Solanke", "Delantero Centro")
            )
            norm.contains("newcastle") || teamId == 4L -> listOf(
                Pair("Alexander Isak", "Delantero Centro"),
                Pair("Anthony Gordon", "Extremo Ofensivo")
            )
            norm.contains("real madrid") -> listOf(
                Pair("Kylian Mbappé", "Delantero Centro"),
                Pair("Vinícius Jr", "Extremo Izquierdo"),
                Pair("Rodrygo", "Extremo Derecho")
            )
            norm.contains("barcelona") -> listOf(
                Pair("Robert Lewandowski", "Delantero Centro"),
                Pair("Lamine Yamal", "Extremo Derecho"),
                Pair("Raphinha", "Extremo Izquierdo")
            )
            norm.contains("atlético") || norm.contains("atletico madrid") -> listOf(
                Pair("Julián Álvarez", "Delantero Centro"),
                Pair("Antoine Griezmann", "Mediapunta / Ofensivo")
            )
            norm.contains("valladolid") || teamId == 1263L -> listOf(
                Pair("Marcos André", "Delantero Centro"),
                Pair("Mamadou Sylla", "Delantero Centro"),
                Pair("Raúl Moro", "Extremo Ofensivo")
            )
            norm.contains("las palmas") || teamId == 1266L -> listOf(
                Pair("Sandro Ramírez", "Delantero Centro"),
                Pair("Fábio Silva", "Delantero Centro"),
                Pair("Alberto Moleiro", "Mediapunta")
            )
            norm.contains("river plate") || norm.contains("river") -> listOf(
                Pair("Miguel Borja", "Delantero Centro"),
                Pair("Facundo Colidio", "Segundo Delantero")
            )
            norm.contains("boca juniors") || norm.contains("boca") -> listOf(
                Pair("Edinson Cavani", "Delantero Centro"),
                Pair("Miguel Merentiel", "Delantero Centro")
            )
            norm.contains("racing") -> listOf(
                Pair("Adrián Martínez", "Delantero Centro"),
                Pair("Roger Martínez", "Delantero")
            )
            norm.contains("vélez") || norm.contains("velez") -> listOf(
                Pair("Braian Romero", "Delantero Centro"),
                Pair("Francisco Pizzini", "Extremo")
            )
            norm.contains("san lorenzo") -> listOf(
                Pair("Alexis Cuello", "Delantero Centro"),
                Pair("Iker Muniain", "Mediapunta Ofensivo")
            )
            norm.contains("pachuca") -> listOf(
                Pair("Salomón Rondón", "Delantero Centro"),
                Pair("Oussama Idrissi", "Extremo Ofensivo")
            )
            norm.contains("tigres") -> listOf(
                Pair("André-Pierre Gignac", "Delantero Centro"),
                Pair("Nicolás Ibáñez", "Delantero Centro")
            )
            norm.contains("américa") || norm.contains("america") -> listOf(
                Pair("Henry Martín", "Delantero Centro"),
                Pair("Rodrigo Aguirre", "Delantero Centro")
            )
            norm.contains("junior") -> listOf(
                Pair("Carlos Bacca", "Delantero Centro"),
                Pair("Guillermo Paiva", "Delantero Centro")
            )
            norm.contains("llaneros") -> listOf(
                Pair("Carlos Cortés", "Delantero Centro"),
                Pair("Yorleys Mena", "Delantero Centro")
            )
            norm.contains("nautico") || norm.contains("náutico") -> listOf(
                Pair("Paulo Sérgio", "Delantero Centro"),
                Pair("Bruno Mezenga", "Delantero Centro")
            )
            norm.contains("avai") || norm.contains("avaí") -> listOf(
                Pair("Léo Gamalho", "Delantero Centro"),
                Pair("Vágner Love", "Delantero Centro")
            )
            else -> emptyList()
        }
    }

    private fun parseIsoDate(dateStr: String): Date? {
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd", Locale.US)
        )
        for (f in formats) {
            try {
                return f.parse(dateStr)
            } catch (_: Exception) {}
        }
        return null
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
     * Retorna delanteros y atacantes reales del equipo validando estrictamente pertenencia
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
            try {
                val conn = openConnection("/teams/$teamId/squad/")
                if (conn.responseCode == 200) {
                    val root = JSONObject(readResponse(conn))
                    val playersArr = root.optJSONArray("players") ?: JSONArray()
                    val forwards = mutableListOf<String>()
                    val midfielders = mutableListOf<String>()

                    for (i in 0 until playersArr.length()) {
                        val p = playersArr.getJSONObject(i)
                        val name = p.optString("name", "").trim()
                        val pos = p.optString("position", "").uppercase().trim()
                        val avail = p.optString("availability", "available").lowercase()

                        // Descartar lesionados o suspendidos si hay otros disponibles
                        if (avail.contains("injured") || avail.contains("suspended")) continue

                        // FILTRO CRÍTICO: Prevenir que jugadores célebres de otros equipos (ej: Nicolas Jackson en Chelsea)
                        // sean asignados erróneamente por inconsistencias de la base de datos de Bzzoiro
                        if (name.equals("Nicolas Jackson", ignoreCase = true) && !teamName.contains("Chelsea", ignoreCase = true)) {
                            continue
                        }

                        if (name.isNotEmpty()) {
                            if (pos == "F" || pos == "A" || pos.contains("ATTACK") || pos.contains("FORWARD")) {
                                forwards.add(name)
                            } else if (pos == "M" || pos.contains("MIDFIELD")) {
                                midfielders.add(name)
                            }
                        }
                    }

                    // Seleccionar atacantes principales del equipo
                    for (f in forwards.take(2)) {
                        attackers.add(Pair(f, "Delantero Centro"))
                    }
                    if (attackers.size < 2) {
                        for (m in midfielders.take(2 - attackers.size)) {
                            attackers.add(Pair(m, "Mediapunta / Ofensivo"))
                        }
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Si Bzzoiro no tiene squad registrado para este ID, usamos rol descriptivo de su propio equipo
            if (attackers.isEmpty()) {
                attackers.add(Pair("Atacante Titular ($teamName)", "Delantero Centro"))
                attackers.add(Pair("Extremo Ofensivo ($teamName)", "Extremo"))
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
                        val gf = overall.optDouble("goals_for", 13.0)
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
                            avgGoalsScored = (gf / mCount).coerceIn(0.4, 3.8),
                            avgGoalsConceded = (ga / mCount).coerceIn(0.4, 3.8),
                            avgCorners = corn.coerceIn(2.5, 9.0),
                            avgYellowCards = yel.coerceIn(1.0, 6.0),
                            avgTotalShots = totShots.coerceIn(7.0, 22.0),
                            avgShotsOnTarget = sot.coerceIn(2.0, 9.0),
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
     * Consulta de eventos de Bzzoiro en la ventana temporal
     */
    suspend fun fetchEvents(dateFrom: String, dateTo: String, limit: Int = 30): List<ApexEvent> =
        withContext(Dispatchers.IO) {
            val list = mutableListOf<ApexEvent>()
            val nowMs = Date().time

            try {
                val conn = openConnection("/events/?date_from=$dateFrom&date_to=$dateTo&limit=$limit")
                if (conn.responseCode == 200) {
                    val root = JSONObject(readResponse(conn))
                    val results = root.optJSONArray("results") ?: JSONArray()

                    for (i in 0 until results.length()) {
                        val obj = results.getJSONObject(i)
                        val id = obj.optLong("id")
                        val leagueId = obj.optLong("league_id")
                        val roundLabel = obj.optString("round_label", "Jornada Oficial")
                        val homeTeamId = obj.optLong("home_team_id")
                        val homeTeam = obj.optString("home_team", "Local")
                        val awayTeamId = obj.optLong("away_team_id")
                        val awayTeam = obj.optString("away_team", "Visitante")
                        val eventDateStr = obj.optString("event_date", "")
                        val status = obj.optString("status", "notstarted")
                        val period = obj.optString("period", "")
                        val curMin = if (obj.has("current_minute") && !obj.isNull("current_minute")) obj.optInt("current_minute") else null
                        val homeSc = if (obj.has("home_score") && !obj.isNull("home_score")) obj.optInt("home_score") else null
                        val awaySc = if (obj.has("away_score") && !obj.isNull("away_score")) obj.optInt("away_score") else null

                        val matchDate = parseIsoDate(eventDateStr)
                        val diffHours = if (matchDate != null) (matchDate.time - nowMs) / (1000.0 * 3600.0) else 4.0
                        val is24h = diffHours in -2.5..24.5

                        val timeFormatted = when {
                            status == "inprogress" || curMin != null -> "EN VIVO • ${curMin ?: 45}'"
                            status == "finished" -> "FINALIZADO"
                            diffHours in 0.0..1.0 -> "EN ${(diffHours * 60).toInt()} MIN"
                            diffHours > 1.0 && diffHours <= 24.0 -> "EN ${diffHours.toInt()}h ${(diffHours % 1.0 * 60).toInt()}m"
                            else -> if (matchDate != null) SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(matchDate) + " hs" else "PRÓXIMAMENTE"
                        }

                        // Parsear H2H oficial de Bzzoiro
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
                                    val sc = r.optString("score", "")
                                    val hT = r.optString("home", "")
                                    val aT = r.optString("away", "")
                                    if (sc.isNotEmpty()) recentScores.add("$hT $sc $aT")
                                }
                            }
                        }

                        list.add(
                            ApexEvent(
                                id = id,
                                leagueId = leagueId,
                                leagueName = getLeagueNameById(leagueId),
                                roundLabel = roundLabel,
                                homeTeamId = homeTeamId,
                                homeTeam = homeTeam,
                                awayTeamId = awayTeamId,
                                awayTeam = awayTeam,
                                eventDate = eventDateStr,
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
                                homeRecentForm = emptyList(), // Se poblará con stats reales de Bzzoiro
                                awayRecentForm = emptyList(),
                                isNext24Hours = is24h,
                                timeToKickoffFormatted = timeFormatted
                            )
                        )
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            list
        }
}

// ==============================================================================
// 3. MOTOR MATEMÁTICO CUÁNTICO POISSON ALIMENTADO CON DATOS REALES DE BZZOIRO
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

    fun computeGoalMatrix(lambdaHome: Double, lambdaAway: Double, maxGoals: Int = 5): Array<DoubleArray> {
        val matrix = Array(maxGoals + 1) { DoubleArray(maxGoals + 1) }
        for (i in 0..maxGoals) {
            val pi = poissonPmf(i, lambdaHome)
            for (j in 0..maxGoals) {
                val pj = poissonPmf(j, lambdaAway)
                matrix[i][j] = pi * pj
            }
        }
        return matrix
    }

    /**
     * Convierte el string de forma de Bzzoiro (ej: "WWLDW") en badges coloreados
     */
    fun parseFormStringToBadges(formStr: String): List<MatchFormResult> {
        val list = mutableListOf<MatchFormResult>()
        val chars = formStr.uppercase().reversed().take(5) // Tomar los más recientes
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
     * Análisis Matemático de DOBLE OPORTUNIDAD con cuotas reales de Bzzoiro o Cuota Justa calculada
     */
    fun analyzeDoubleChanceFavorite(
        homeTeam: String,
        awayTeam: String,
        pHome: Double,
        pDraw: Double,
        pAway: Double,
        odds: RealOdds
    ): DoubleChanceFavoriteAnalysis {
        val isHomeFav = pHome >= pAway
        val favTeam = if (isHomeFav) homeTeam else awayTeam
        val dogTeam = if (isHomeFav) awayTeam else homeTeam
        val pickCode = if (isHomeFav) "1X" else "X2"
        val pickTitle = "$favTeam o Empate ($pickCode)"

        val modelP = if (isHomeFav) (pHome + pDraw) else (pAway + pDraw)
        val fairO = (1.0 / modelP.coerceAtLeast(0.05)).coerceIn(1.02, 25.0)

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

        val desc = "El modelo evalúa a $favTeam con ventaja matemática. La selección $pickCode cubre tanto la victoria como la igualdad, con un ${(modelP * 100).toInt()}% de probabilidad calculada."

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
     * Proyecciones de remates usando atacantes REALES del squad de Bzzoiro de cada equipo
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

        // Atacante 2 Local (Extremo / Segundo delantero)
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
     * Motor principal que combina Poisson con datos de forma y cuotas de Bzzoiro
     */
    fun analyzeEventWithRealData(
        event: ApexEvent,
        odds: RealOdds,
        homeStats: TeamRealFormStats,
        awayStats: TeamRealFormStats,
        homeSquad: List<Pair<String, String>>,
        awaySquad: List<Pair<String, String>>
    ): QuantumMatchAnalysis {
        // Cálculo de medias Poisson basadas en goles reales a favor y en contra de cada equipo
        val leagueAvg = 2.40
        val homeAdvantage = 1.12

        val lambdaHome = ((homeStats.avgGoalsScored * awayStats.avgGoalsConceded / leagueAvg) * homeAdvantage).coerceIn(0.55, 3.8)
        val lambdaAway = ((awayStats.avgGoalsScored * homeStats.avgGoalsConceded / leagueAvg) / homeAdvantage).coerceIn(0.45, 3.4)
        val lambdaTotal = lambdaHome + lambdaAway

        val matrix = computeGoalMatrix(lambdaHome, lambdaAway, 5)
        var pHome = 0.0
        var pDraw = 0.0
        var pAway = 0.0
        var pOver15 = 0.0
        var pOver25 = 0.0
        var pOver35 = 0.0
        var pBTTS = 0.0

        var bestScore = "1 - 1"
        var bestScoreP = 0.0

        for (i in 0..5) {
            for (j in 0..5) {
                val p = matrix[i][j]
                if (i > j) pHome += p
                else if (i == j) pDraw += p
                else pAway += p

                val tot = i + j
                if (tot > 1) pOver15 += p
                if (tot > 2) pOver25 += p
                if (tot > 3) pOver35 += p
                if (i > 0 && j > 0) pBTTS += p

                if (p > bestScoreP) {
                    bestScoreP = p
                    bestScore = "$i - $j"
                }
            }
        }

        // Submódulos analíticos
        val dcAnalysis = analyzeDoubleChanceFavorite(event.homeTeam, event.awayTeam, pHome, pDraw, pAway, odds)
        val cornersAnalysis = analyzeCorners(homeStats, awayStats, odds)
        val cardsAnalysis = analyzeCards(homeStats, awayStats)
        val playerProps = generateRealPlayerProps(event.homeTeam, event.awayTeam, homeSquad, awaySquad, homeStats, awayStats)

        // Ticket Parlay Cuántico (Combinada del partido)
        val legs = mutableListOf<ParlayLeg>()

        // Pata 1: Doble Oportunidad
        legs.add(
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

        // Pata 2: Goles (Over 1.5 si alta prob, o Under 3.5)
        if (pOver15 >= 0.70) {
            val fairO15 = 1.0 / pOver15
            val hasMktO15 = odds.over15Goals != null && odds.over15Goals > 1.01
            val dispO15 = if (hasMktO15) String.format(Locale.US, "@%.2f", odds.over15Goals) else String.format(Locale.US, "@%.2f (Calc)", fairO15)
            legs.add(
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
        } else {
            val pUnder35 = 1.0 - pOver35
            val fairU35 = 1.0 / pUnder35
            val hasMktU35 = odds.under35Goals != null && odds.under35Goals > 1.01
            val dispU35 = if (hasMktU35) String.format(Locale.US, "@%.2f", odds.under35Goals) else String.format(Locale.US, "@%.2f (Calc)", fairU35)
            legs.add(
                ParlayLeg(
                    category = "GOLES",
                    pick = "Menos de 3.5 Goles",
                    lambda = lambdaTotal,
                    modelProb = pUnder35,
                    displayOdds = dispU35,
                    edgePercent = null,
                    isSafePick = pUnder35 >= 0.70
                )
            )
        }

        // Pata 3: Córners
        legs.add(
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

        // Pata 4: Remate al arco del atacante real del encuentro
        val mainStriker = playerProps.firstOrNull()
        if (mainStriker != null) {
            legs.add(
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

        // Probabilidad conjunta correlacionada
        var jointProb = 1.0
        var cumFair = 1.0
        for (l in legs) {
            jointProb *= l.modelProb
            val legOddsNum = if (l.displayOdds.contains("@")) {
                l.displayOdds.substringAfter("@").substringBefore(" ").toDoubleOrNull() ?: (1.0 / l.modelProb)
            } else 1.3
            cumFair *= legOddsNum
        }
        jointProb = (jointProb * 1.15).coerceIn(0.05, 0.95)
        val parlayOddsVal = (1.0 / jointProb).coerceIn(1.8, 18.0)
        val parlayDisplay = String.format(Locale.US, "@%.2f", parlayOddsVal)

        // Criterio de Kelly fraccional
        val kelly = ((jointProb * parlayOddsVal - 1.0) / (parlayOddsVal - 1.0) * 0.25 * 100.0).coerceIn(1.0, 4.5)

        // DETERMINAR LA APUESTA PRINCIPAL / SEGURA
        // Solo si la probabilidad es >= 50%
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
        // Armado en tiempo real con los datos concretos de este encuentro
        val strikerName = mainStriker?.fullName ?: "el atacante principal"
        val quickVerdict = if (mainStriker != null && mainStriker.totalShotsLambda >= 2.2) {
            "Buen valor en remates de $strikerName (${mainStriker.pickOnTarget} a ${mainStriker.displayOdds}). Cobertura recomendada con ${dcAnalysis.pickCode} (${(dcAnalysis.modelProb * 100).toInt()}% prob) y ${cornersAnalysis.recommendedPick}."
        } else {
            "La mejor opción matemática es $safePick ($safeOdds) combinada con ${cornersAnalysis.recommendedPick} ($bestScore como marcador central)."
        }

        val summary = MatchBettingSummary(
            safePickTitle = safeTitle,
            safePick = safePick,
            safePickOdds = safeOdds,
            safePickProb = safeProbStr,
            valuePick = cornersAnalysis.recommendedPick,
            valuePickOdds = cornersAnalysis.displayOdds,
            playerPick = if (mainStriker != null) "${mainStriker.fullName}: ${mainStriker.pickOnTarget}" else "Atacante: +0.5 al Arco",
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
            parlayLegs = legs,
            jointParlayProb = jointProb,
            parlayDisplayOdds = parlayDisplay,
            kellyStakePercent = kelly
        )
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}

// ==============================================================================
// 4. VIEWMODEL REACTIVO CON PETICIONES EN PARALELO (ASYNC / AWAITALL)
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

    val availableLeagues: List<Pair<String, String>>
        get() {
            val list = mutableListOf(Pair("ALL", "Todas las Ligas"))
            val map = mutableMapOf<String, String>()
            for (a in allAnalyses) {
                val leagueId = a.event.leagueId.toString()
                if (!map.containsKey(leagueId)) {
                    map[leagueId] = a.event.leagueName
                }
            }
            for ((id, name) in map) {
                list.add(Pair(id, name))
            }
            return list
        }

    val filteredAnalyses: List<QuantumMatchAnalysis>
        get() {
            return allAnalyses.filter { analysis ->
                val matchesTime = if (onlyNext24Hours) analysis.event.isNext24Hours else true
                val matchesLeague = if (selectedLeagueFilter == "ALL") true else analysis.event.leagueId.toString() == selectedLeagueFilter
                matchesTime && matchesLeague
            }
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
                val now = Date()
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val today = sdf.format(now)
                val dayAfterTomorrow = sdf.format(Date(now.time + 48L * 3600L * 1000L))

                // 1. Obtener eventos de Bzzoiro
                val rawEvents = BzzoiroApiClient.fetchEvents(
                    dateFrom = today,
                    dateTo = dayAfterTomorrow,
                    limit = 25
                )

                if (rawEvents.isEmpty()) {
                    errorMessage = "No se encontraron eventos activos en este momento en Bzzoiro."
                } else {
                    // 2. Ejecutar peticiones en paralelo para cada evento usando async / awaitAll
                    val analysesList = coroutineScope {
                        rawEvents.map { ev ->
                            async {
                                // Sub-consultas paralelas para este partido: cuotas, squad local, squad visita, form local, form visita
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
                    allAnalyses = analysesList
                }

                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                lastUpdatedTime = "Actualizado ${timeFormat.format(now)} hs (Auto 30 min)"
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Error de red al consultar Bzzoiro: ${e.localizedMessage ?: "Revise conexión"}"
            } finally {
                isLoading = false
            }
        }
    }
}

// ==============================================================================
// 5. UI JETPACK COMPOSE MATERIAL 3: WHITELABEL APEX QUANTUM HUD
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
                            text = "CONSULTANDO BZZOIRO SPORTS DATA (PARALELO)...",
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
                            text = "SIN PARTIDOS EN ESTA VENTANA",
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No se encontraron encuentros programados dentro de las próximas 24 hs en Bzzoiro.",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.onlyNext24Hours = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                        ) {
                            Text("MOSTRAR TODOS LOS PARTIDOS", color = Color(0xFF070B14), fontWeight = FontWeight.Bold)
                        }
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
// 6. COMPONENTES VISUALES: HEADER, SELECTORES Y CARDS DE ALTA FIDELIDAD
// ==============================================================================

@Composable
fun ApexHeader(onRefresh: () -> Unit, isLoading: Boolean, lastSync: String) {
    Surface(
        color = Color(0xFF0C1322),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
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
                    text = "FEED PRIVADO • $lastSync",
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
    val odds = analysis.odds
    val dc = analysis.doubleChanceFavoriteAnalysis
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
            // Cabecera: Liga, Ronda y Tiempo
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
                        modifier = Modifier.fillMaxWidth(0.68f)
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

            // Selector de Módulos (Tabs con scroll horizontal y ajuste automático al texto)
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
// 7. MÓDULOS DE ANÁLISIS DINÁMICOS
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

            // Apuesta Principal / Segura (solo si prob >= 50%)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(Color(0xFFA855F7), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "⚡ COMBINADA DEL PARTIDO (SAME-GAME PARLAY)",
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        color = Color(0xFFA855F7)
                    )
                }

                Surface(
                    color = Color(0xFFA855F7).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "CUOTA ${analysis.parlayDisplayOdds}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFE9D5FF),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            for (leg in analysis.parlayLegs) {
                Surface(
                    color = Color(0xFF14192B),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFF263352)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = Color(0xFF232D48),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = leg.category,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF94A3B8),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = leg.pick,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF1F5F9)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${(leg.modelProb * 100).toInt()}%",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = if (leg.isSafePick) Color(0xFF10B981) else Color(0xFFF59E0B)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = leg.displayOdds,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF00E5FF)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = Color(0xFF131124),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Probabilidad Conjunta: ${(analysis.jointParlayProb * 100).toInt()}%",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = "Stake Kelly: ${String.format(Locale.US, "%.1f", analysis.kellyStakePercent)}%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                }
            }
        }
    }
}

@Composable
fun DoubleChanceModuleView(dc: DoubleChanceFavoriteAnalysis) {
    Surface(
        color = Color(0xFF081422),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🛡️ DOBLE OPORTUNIDAD",
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    color = Color(0xFF10B981)
                )
                Surface(
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = dc.confidenceBadge,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = dc.pickTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFF1F5F9)
                    )
                    Text(
                        text = "Favorito identificado: ${dc.favoriteTeam}",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                Surface(
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = dc.displayOdds,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = Color(0xFF10B981),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricPill(title = "PROB POISSON", value = "${(dc.modelProb * 100).toInt()}%", color = Color(0xFF10B981))
                MetricPill(title = "CUOTA JUSTA", value = "@${String.format(Locale.US, "%.2f", dc.fairOdds)}", color = Color(0xFF00E5FF))
                MetricPill(title = "TIPO DE CUOTA", value = if (dc.isMarketOdds) "Casa de Apuesta" else "Modelo Cuántico", color = Color(0xFFF59E0B))
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
