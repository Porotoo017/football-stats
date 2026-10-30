package com.personal.footballstats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

// ==============================================================================
// 1. CONFIGURACIÓN WHITELABEL Y LISTA BLANCA DE LIGAS (BZZOIRO DATA API)
// ==============================================================================
var bzzoiroApiKey: String = "196b5da801d2a96f72b4e69ed0f4a430b8b25d99"
const val BZZOIRO_BASE_URL: String = "https://sports.bzzoiro.com/api/v2"

val WHITELISTED_LEAGUE_IDS: Set<Long> = setOf(
    85L, // Liga Profesional Argentina (Primera División)
    18L, // MLS (EE.UU.)
    3L,  // LaLiga (España)
    5L,  // Bundesliga (Alemania)
    10L, // Eredivisie (Países Bajos)
    6L,  // Ligue 1 (Francia)
    4L,  // Serie A (Italia)
    9L,  // Brasileirão Serie A (Brasil)
    2L,  // Primeira Liga (Portugal)
    32L, // Copa Libertadores
    33L, // Copa Sudamericana
    7L,  // UEFA Champions League
    8L,  // UEFA Europa League
    1L   // Premier League (Inglaterra)
)

val LEAGUES_MAP: Map<Long, String> = mapOf(
    85L to "Liga Profesional (Argentina)",
    18L to "MLS (EE.UU.)",
    3L to "LaLiga (España)",
    4L to "Serie A (Italia)",
    5L to "Bundesliga (Alemania)",
    6L to "Ligue 1 (Francia)",
    7L to "UEFA Champions League",
    8L to "UEFA Europa League",
    9L to "Brasileirão Serie A (Brasil)",
    10L to "Eredivisie (Países Bajos)",
    2L to "Primeira Liga (Portugal)",
    32L to "Copa Libertadores",
    33L to "Copa Sudamericana",
    1L to "Premier League (Inglaterra)"
)

val PRIORITY_LEAGUE_ORDER = listOf(85L, 18L, 1L, 3L, 4L, 5L, 6L, 9L, 10L, 2L, 7L, 8L, 32L, 33L)

fun extractLeagueName(obj: JSONObject): String {
    val lObj = obj.optJSONObject("league")
    if (lObj != null) {
        val n = lObj.optString("name", "").trim()
        if (n.isNotEmpty()) return n
    }
    val directLeague = obj.optString("league_name", "").trim()
    if (directLeague.isNotEmpty()) return directLeague

    val leagueStr = obj.optString("league", "").trim()
    if (leagueStr.isNotEmpty() && !leagueStr.startsWith("{") && !leagueStr.matches(Regex("^\\d+$"))) return leagueStr

    val tObj = obj.optJSONObject("tournament")
    if (tObj != null) {
        val tn = tObj.optString("name", "").trim()
        if (tn.isNotEmpty()) return tn
    }
    val tName = obj.optString("tournament_name", "").trim()
    if (tName.isNotEmpty()) return tName

    val cObj = obj.optJSONObject("competition")
    if (cObj != null) {
        val cn = cObj.optString("name", "").trim()
        if (cn.isNotEmpty()) return cn
    }
    return obj.optString("competition_name", "").trim()
}

fun isWhitelistedLeague(leagueId: Long, leagueName: String = ""): Boolean {
    // ==========================================================================
    // MODO DIAGNÓSTICO TEMPORAL ACTIVADO:
    // Retorna true para recibir y mostrar TODOS los partidos devueltos por Bzzoiro.
    // Permite al usuario inspeccionar en la UI los ID y nombres reales de las ligas.
    // ==========================================================================
    return true
}

fun resolveLeagueName(rawObj: JSONObject?, leagueId: Long): String {
    val extractedName = if (rawObj != null) extractLeagueName(rawObj) else ""
    val nameToCheck = extractedName.ifEmpty { LEAGUES_MAP[leagueId] ?: "" }

    if (nameToCheck.contains("libertadores", ignoreCase = true)) return "Copa Libertadores"
    if (nameToCheck.contains("sudamericana", ignoreCase = true)) return "Copa Sudamericana"
    if (nameToCheck.contains("primera nacional", ignoreCase = true) ||
        nameToCheck.contains("nacional b", ignoreCase = true) ||
        nameToCheck.contains("primera b nacional", ignoreCase = true)) return "Primera Nacional (Argentina)"
    if (nameToCheck.contains("uefa champions", ignoreCase = true) || nameToCheck.contains("champions", ignoreCase = true)) return "UEFA Champions League"
    if (nameToCheck.contains("europa league", ignoreCase = true) || nameToCheck.contains("uefa europa", ignoreCase = true) ||
        (nameToCheck.contains("europa", ignoreCase = true) && !nameToCheck.contains("conference", ignoreCase = true))) return "UEFA Europa League"
    if (nameToCheck.contains("liga profesional", ignoreCase = true) ||
        (nameToCheck.contains("primera", ignoreCase = true) && nameToCheck.contains("argentina", ignoreCase = true))) return "Liga Profesional (Argentina)"
    if (nameToCheck.contains("laliga", ignoreCase = true) || nameToCheck.contains("la liga", ignoreCase = true)) return "LaLiga (España)"
    if (nameToCheck.contains("premier league", ignoreCase = true)) return "Premier League (Inglaterra)"
    if (nameToCheck.contains("bundesliga", ignoreCase = true)) return "Bundesliga (Alemania)"
    if (nameToCheck.contains("serie a", ignoreCase = true) && !nameToCheck.contains("brasil", ignoreCase = true)) return "Serie A (Italia)"
    if (nameToCheck.contains("ligue 1", ignoreCase = true)) return "Ligue 1 (Francia)"
    if (nameToCheck.contains("eredivisie", ignoreCase = true)) return "Eredivisie (Países Bajos)"
    if (nameToCheck.contains("brasileir", ignoreCase = true)) return "Brasileirão Serie A (Brasil)"
    if (nameToCheck.contains("primeira liga", ignoreCase = true) || nameToCheck.contains("liga portugal", ignoreCase = true)) return "Primeira Liga (Portugal)"
    if (nameToCheck.contains("major league soccer", ignoreCase = true) || nameToCheck.equals("mls", ignoreCase = true)) return "MLS (EE.UU.)"

    when (leagueId) {
        85L -> return "Liga Profesional (Argentina)"
        18L -> return "MLS (EE.UU.)"
        3L -> return "LaLiga (España)"
        4L -> return "Serie A (Italia)"
        5L -> return "Bundesliga (Alemania)"
        6L -> return "Ligue 1 (Francia)"
        7L -> return "UEFA Champions League"
        8L -> return "UEFA Europa League"
        9L -> return "Brasileirão Serie A (Brasil)"
        10L -> return "Eredivisie (Países Bajos)"
        2L -> return "Primeira Liga (Portugal)"
        32L -> return "Copa Libertadores"
        33L -> return "Copa Sudamericana"
        1L -> return "Premier League (Inglaterra)"
    }

    if (extractedName.isNotEmpty() && !extractedName.contains("#")) return extractedName
    return LEAGUES_MAP[leagueId] ?: if (extractedName.isNotEmpty()) extractedName else "Liga Oficial"
}

// ==============================================================================
// 2. MODELOS DE DATOS CUANTITATIVOS
// ==============================================================================
enum class FormOutcome { WIN, DRAW, LOSS }

data class MatchFormResult(val charCode: String, val outcome: FormOutcome, val rival: String, val scoreDetail: String)

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

data class RealOdds(
    val home1x2: Double? = null,
    val draw1x2: Double? = null,
    val away1x2: Double? = null,
    val dc1X: Double? = null,
    val dcX2: Double? = null,
    val dc12: Double? = null,
    val bttsYes: Double? = null,
    val bttsNo: Double? = null,
    val over15Goals: Double? = null,
    val under15Goals: Double? = null,
    val over25Goals: Double? = null,
    val under25Goals: Double? = null,
    val over35Goals: Double? = null,
    val under35Goals: Double? = null,
    val cornerLineVal: Double = 8.5,
    val overCornerLine: Double? = null,
    val underCornerLine: Double? = null,
    val cardsLineVal: Double = 4.5,
    val overCardsLine: Double? = null
)

data class TeamRealFormStats(
    val teamId: Long,
    val teamName: String,
    val matchesPlayed: Int = 0,
    val avgGoalsScored: Double? = null,
    val avgGoalsConceded: Double? = null,
    val avgCorners: Double? = null,
    val avgYellowCards: Double? = null,
    val avgTotalShots: Double? = null,
    val avgShotsOnTarget: Double? = null,
    val formString: String = ""
)

data class DoubleChanceFavoriteAnalysis(
    val favoriteTeam: String,
    val underdogTeam: String,
    val isHomeFavorite: Boolean,
    val pickCode: String,
    val pickTitle: String,
    val displayOdds: String,
    val rawOdds: Double,
    val isMarketOdds: Boolean,
    val modelProb: Double,
    val fairOdds: Double,
    val expectedValue: Double?,
    val confidenceBadge: String,
    val analysisDescription: String
)

data class CornersAnalysis(
    val hasData: Boolean,
    val expectedTotal: Double?,
    val expectedHome: Double?,
    val expectedAway: Double?,
    val linePrincipal: Double?,
    val probOver: Double?,
    val fairOddsOver: Double?,
    val recommendedPick: String,
    val displayOdds: String,
    val isMarketOdds: Boolean = false
)

data class CardsAnalysis(
    val hasData: Boolean,
    val expectedTotal: Double?,
    val expectedHome: Double?,
    val expectedAway: Double?,
    val linePrincipal: Double?,
    val probOver: Double?,
    val fairOdds: Double?,
    val recommendedPick: String,
    val displayOdds: String,
    val isMarketOdds: Boolean = false
)

data class PlayerShotProp(
    val fullName: String,
    val teamName: String,
    val rolePosition: String,
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
    val rawOdds: Double,
    val displayOdds: String,
    val edgePercent: Double?,
    val isSafePick: Boolean
)

data class MatchBettingSummary(
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
    val homeStats: TeamRealFormStats,
    val awayStats: TeamRealFormStats,
    val lambdaHomeGoals: Double,
    val lambdaAwayGoals: Double,
    val lambdaTotalGoals: Double,
    val probHomeWin: Double,
    val probDraw: Double,
    val probAwayWin: Double,
    val probOver15Goals: Double,
    val probOver25Goals: Double,
    val probOver35Goals: Double,
    val probBttsYes: Double,
    val mostProbableScore: String,
    val scoreProbability: Double,
    val doubleChanceAnalysis: DoubleChanceFavoriteAnalysis,
    val cornersAnalysis: CornersAnalysis,
    val cardsAnalysis: CardsAnalysis,
    val playerProps: List<PlayerShotProp>,
    val parlayLegs: List<ParlayLeg>,
    val jointProbability: Double,
    val parlayDisplayOdds: String,
    val kellyStakePercent: Double,
    val bettingSummary: MatchBettingSummary
)

// ==============================================================================
// 3. CLIENTE HTTP BZZOIRO SPORTS DATA (VALIDACIÓN ESTRICTA Y PAGINACIÓN)
// ==============================================================================
object BzzoiroApiClient {
    private val squadCache = mutableMapOf<Long, List<Pair<String, String>>>()
    private val formCache = mutableMapOf<Long, TeamRealFormStats>()

    fun parseIsoDate(dateStr: String): ZonedDateTime? {
        if (dateStr.isBlank()) return null
        val clean = dateStr.trim().replace(" ", "T")
        return try {
            OffsetDateTime.parse(clean).atZoneSameInstant(ZoneId.systemDefault())
        } catch (_: Exception) {
            try {
                Instant.parse(clean).atZone(ZoneId.systemDefault())
            } catch (_: Exception) {
                try {
                    ZonedDateTime.parse(clean, DateTimeFormatter.ISO_DATE_TIME).withZoneSameInstant(ZoneId.systemDefault())
                } catch (_: Exception) {
                    try {
                        val ld = LocalDate.parse(clean.take(10))
                        ld.atStartOfDay(ZoneId.of("UTC")).withZoneSameInstant(ZoneId.systemDefault())
                    } catch (_: Exception) { null }
                }
            }
        }
    }

    fun extractSafeString(obj: JSONObject, key: String): String {
        if (!obj.has(key) || obj.isNull(key)) return ""
        return obj.optString(key, "").trim()
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
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        return BufferedReader(InputStreamReader(stream)).use { it.readText() }
    }

    suspend fun fetchTeamSquad(teamId: Long, teamName: String, isHomeTeam: Boolean = true): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        if (teamId <= 0L) {
            val fallbackLabel = if (isHomeTeam) "Jugador Local" else "Delantero por confirmar"
            return@withContext listOf(Pair(fallbackLabel, "Atacante Principal"), Pair("Delantero por confirmar", "Segundo Atacante"))
        }
        squadCache[teamId]?.let { return@withContext it }
        val attackers = mutableListOf<Pair<String, String>>()

        try {
            val conn = openConnection("/teams/$teamId/squad/")
            if (conn.responseCode == 200) {
                val root = JSONObject(readResponse(conn))
                val returnedTeamId = root.optLong("team_id", root.optLong("id", 0L))
                if (returnedTeamId == 0L || returnedTeamId == teamId) {
                    val playersArr = root.optJSONArray("players")
                        ?: root.optJSONArray("results")
                        ?: root.optJSONArray("squad")
                        ?: JSONArray()
                    val forwards = mutableListOf<String>()
                    val midfielders = mutableListOf<String>()

                    for (i in 0 until playersArr.length()) {
                        val p = playersArr.getJSONObject(i)
                        val name = p.optString("name", "").trim().ifEmpty {
                            val fn = p.optString("first_name", "").trim()
                            val ln = p.optString("last_name", "").trim()
                            if (fn.isNotEmpty() || ln.isNotEmpty()) "$fn $ln".trim() else ""
                        }
                        if (name.isEmpty()) continue

                        // Validación estricta por team_id: si existe en el objeto del jugador, debe pertenecer al equipo consultado
                        val pTeamId = when {
                            p.has("team_id") && !p.isNull("team_id") -> p.optLong("team_id")
                            p.has("team") && p.optJSONObject("team") != null -> p.optJSONObject("team")!!.optLong("id")
                            else -> 0L
                        }
                        if (pTeamId != 0L && pTeamId != teamId) continue

                        // Descartar placeholders y jugadores fantasma
                        if (name.contains("Mateo Silveira", ignoreCase = true)) continue

                        val pos = (p.optString("position", "") + " " + p.optString("role", "")).uppercase(Locale.ROOT).trim()
                        if (pos.contains("F") || pos.contains("ATTACK") || pos.contains("FORWARD") || pos.contains("STRIKER") || pos.contains("DELANTERO")) {
                            forwards.add(name)
                        } else if (pos.contains("M") || pos.contains("MIDFIELD") || pos.contains("MEDIO") || pos.contains("VOLANTE")) {
                            midfielders.add(name)
                        }
                    }

                    for (f in forwards.take(2)) attackers.add(Pair(f, "Delantero Centro"))
                    if (attackers.size < 2) {
                        for (m in midfielders.take(2 - attackers.size)) attackers.add(Pair(m, "Mediapunta"))
                    }
                }
            }
            conn.disconnect()
        } catch (_: Exception) {}

        // Si Bzzoiro no devuelve el plantel o no hay atacantes confirmados, NUNCA inventar nombres
        if (attackers.isEmpty()) {
            val primaryLabel = if (isHomeTeam) "Jugador Local" else "Delantero por confirmar"
            val secondaryLabel = if (isHomeTeam) "Delantero por confirmar" else "Jugador Visitante"
            attackers.add(Pair(primaryLabel, "Atacante Principal"))
            attackers.add(Pair(secondaryLabel, "Segundo Atacante"))
        }

        squadCache[teamId] = attackers
        attackers
    }

    suspend fun fetchEventLineups(eventId: Long): Set<String> = withContext(Dispatchers.IO) {
        val confirmedPlayers = mutableSetOf<String>()
        try {
            val conn = openConnection("/events/$eventId/lineups/")
            if (conn.responseCode == 200) {
                val root = JSONObject(readResponse(conn))
                for (teamKey in listOf("home", "away")) {
                    val teamObj = root.optJSONObject(teamKey)
                    if (teamObj != null) {
                        for (arrKey in listOf("lineup", "bench", "starting_lineup", "substitutes", "players")) {
                            val arr = teamObj.optJSONArray(arrKey) ?: continue
                            for (i in 0 until arr.length()) {
                                val pObj = arr.getJSONObject(i)
                                val name = extractSafeString(pObj, "name").ifEmpty {
                                    val fn = extractSafeString(pObj, "first_name")
                                    val ln = extractSafeString(pObj, "last_name")
                                    "$fn $ln".trim()
                                }
                                if (name.isNotEmpty()) confirmedPlayers.add(name.lowercase(Locale.ROOT))
                            }
                        }
                    }
                }
                val directArr = root.optJSONArray("lineups") ?: root.optJSONArray("results")
                if (directArr != null) {
                    for (i in 0 until directArr.length()) {
                        val pObj = directArr.getJSONObject(i)
                        val name = extractSafeString(pObj, "player_name").ifEmpty { extractSafeString(pObj, "name") }
                        if (name.isNotEmpty()) confirmedPlayers.add(name.lowercase(Locale.ROOT))
                    }
                }
            }
            conn.disconnect()
        } catch (_: Exception) {}
        confirmedPlayers
    }

    suspend fun fetchTeamForm(teamId: Long, teamName: String): TeamRealFormStats = withContext(Dispatchers.IO) {
        formCache[teamId]?.let { return@withContext it }
        var stats = TeamRealFormStats(teamId = teamId, teamName = teamName)
        try {
            val conn = openConnection("/teams/$teamId/form/")
            if (conn.responseCode == 200) {
                val root = JSONObject(readResponse(conn))
                val overall = root.optJSONObject("overall")
                if (overall != null) {
                    val mCount = overall.optInt("matches", 0)
                    val gf = if (overall.has("goals_for") && !overall.isNull("goals_for")) overall.optDouble("goals_for") else null
                    val ga = if (overall.has("goals_against") && !overall.isNull("goals_against")) overall.optDouble("goals_against") else null
                    val fStr = overall.optString("form", "")

                    val cornObj = overall.optJSONObject("corners")
                    val cornVal = if (cornObj != null && cornObj.has("avg_for") && !cornObj.isNull("avg_for")) {
                        cornObj.optDouble("avg_for").takeIf { !it.isNaN() && it > 0.0 }
                    } else null

                    val cardObj = overall.optJSONObject("cards")
                    val cardVal = if (cardObj != null && cardObj.has("avg_yellow") && !cardObj.isNull("avg_yellow")) {
                        cardObj.optDouble("avg_yellow").takeIf { !it.isNaN() && it > 0.0 }
                    } else null

                    val shotObj = overall.optJSONObject("shots")
                    val totalShots = if (shotObj != null && shotObj.has("avg_total") && !shotObj.isNull("avg_total")) {
                        shotObj.optDouble("avg_total").takeIf { !it.isNaN() && it > 0.0 }
                    } else null
                    val sotVal = if (shotObj != null && shotObj.has("avg_on_target") && !shotObj.isNull("avg_on_target")) {
                        shotObj.optDouble("avg_on_target").takeIf { !it.isNaN() && it > 0.0 }
                    } else null

                    stats = TeamRealFormStats(
                        teamId = teamId,
                        teamName = teamName,
                        matchesPlayed = mCount,
                        avgGoalsScored = if (mCount > 0 && gf != null) gf / mCount.toDouble() else null,
                        avgGoalsConceded = if (mCount > 0 && ga != null) ga / mCount.toDouble() else null,
                        avgCorners = cornVal,
                        avgYellowCards = cardVal,
                        avgTotalShots = totalShots,
                        avgShotsOnTarget = sotVal,
                        formString = fStr
                    )
                }
            }
            conn.disconnect()
        } catch (_: Exception) {}
        formCache[teamId] = stats
        stats
    }

    suspend fun fetchEventOdds(eventId: Long): RealOdds = withContext(Dispatchers.IO) {
        var odds = RealOdds()
        try {
            val conn = openConnection("/events/$eventId/odds/")
            if (conn.responseCode == 200) {
                val root = JSONObject(readResponse(conn))
                val results = root.optJSONArray("results") ?: JSONArray()
                var h1: Double? = null; var d1: Double? = null; var a1: Double? = null
                var d1x: Double? = null; var dx2: Double? = null; var d12: Double? = null
                var bY: Double? = null; var bN: Double? = null
                var o15: Double? = null; var u15: Double? = null
                var o25: Double? = null; var u25: Double? = null
                var o35: Double? = null; var u35: Double? = null
                var cornO: Double? = null; var cornU: Double? = null; var cornLine = 8.5
                var cardsO: Double? = null; var cardsLine = 4.5

                for (i in 0 until results.length()) {
                    val oddObj = results.getJSONObject(i)
                    val mkt = extractSafeString(oddObj, "market_type").lowercase(Locale.ROOT)
                    val out = extractSafeString(oddObj, "outcome").uppercase(Locale.ROOT)
                    val decOdds = oddObj.optDouble("decimal_odds", 0.0).takeIf { it > 1.01 } ?: continue

                    when (mkt) {
                        "1x2" -> {
                            if (out == "HOME" || out == "1") h1 = decOdds
                            else if (out == "DRAW" || out == "X") d1 = decOdds
                            else if (out == "AWAY" || out == "2") a1 = decOdds
                        }
                        "double_chance" -> {
                            if (out == "1X" || out.contains("HOME")) d1x = decOdds
                            else if (out == "X2" || out.contains("AWAY")) dx2 = decOdds
                            else if (out == "12") d12 = decOdds
                        }
                        "over_under_15" -> if (out == "OVER") o15 = decOdds else if (out == "UNDER") u15 = decOdds
                        "over_under_25" -> if (out == "OVER") o25 = decOdds else if (out == "UNDER") u25 = decOdds
                        "over_under_35" -> if (out == "OVER") o35 = decOdds else if (out == "UNDER") u35 = decOdds
                        "total_corners" -> { cornLine = oddObj.optDouble("line", 8.5); if (out == "OVER") cornO = decOdds else cornU = decOdds }
                        "btts" -> if (out == "YES") bY = decOdds else bN = decOdds
                        "total_cards" -> { cardsLine = oddObj.optDouble("line", 4.5); if (out == "OVER") cardsO = decOdds }
                    }
                }
                odds = RealOdds(
                    home1x2 = h1, draw1x2 = d1, away1x2 = a1, dc1X = d1x, dcX2 = dx2, dc12 = d12,
                    bttsYes = bY, bttsNo = bN, over15Goals = o15, under15Goals = u15,
                    over25Goals = o25, under25Goals = u25, over35Goals = o35, under35Goals = u35,
                    cornerLineVal = cornLine, overCornerLine = cornO, underCornerLine = cornU,
                    cardsLineVal = cardsLine, overCardsLine = cardsO
                )
            }
            conn.disconnect()
        } catch (_: Exception) {}
        odds
    }

    fun parseApexEventFromJson(
        obj: JSONObject,
        nowLocalLdt: LocalDateTime = LocalDateTime.now(),
        limitTime: LocalDateTime = LocalDateTime.now().plusHours(48)
    ): ApexEvent {
        val id = obj.optLong("id")
        val leagueId = obj.optLong("league_id")
        val roundLabel = extractSafeString(obj, "round_label").ifEmpty { "Jornada Oficial" }
        val homeTeamId = obj.optLong("home_team_id")
        val homeTeam = extractSafeString(obj, "home_team").ifEmpty { "Local" }
        val awayTeamId = obj.optLong("away_team_id")
        val awayTeam = extractSafeString(obj, "away_team").ifEmpty { "Visitante" }
        val status = extractSafeString(obj, "status").lowercase(Locale.ROOT).ifEmpty { "notstarted" }
        val period = extractSafeString(obj, "period")
        val curMin = if (obj.has("current_minute") && !obj.isNull("current_minute")) obj.optInt("current_minute") else null
        val homeSc = if (obj.has("home_score") && !obj.isNull("home_score")) obj.optInt("home_score") else null
        val awaySc = if (obj.has("away_score") && !obj.isNull("away_score")) obj.optInt("away_score") else null

        val dateCandidates = listOf("event_date", "start_time", "date", "datetime")
        val eventDateRaw = dateCandidates.map { extractSafeString(obj, it) }.firstOrNull { it.isNotBlank() } ?: ""
        val matchLocal = parseIsoDate(eventDateRaw)
        val matchLocalLdt = matchLocal?.toLocalDateTime()

        val isLive = status == "inprogress" || curMin != null
        val isNext24Hours = when {
            isLive -> true
            status == "finished" -> false
            // Ventana ampliada: desde 2 horas antes de ahora hasta 48 horas en adelante
            matchLocalLdt != null -> !matchLocalLdt.isBefore(nowLocalLdt.minusHours(2)) && !matchLocalLdt.isAfter(limitTime)
            // Si la fecha del partido no se puede parsear, NO se oculta para no perder partidos
            else -> true
        }

        val timeFormatted = when {
            isLive -> "EN VIVO • ${curMin ?: 45}'"
            status == "finished" -> "FINALIZADO"
            matchLocal != null -> matchLocal.format(DateTimeFormatter.ofPattern("dd/MM • HH:mm 'hs'", Locale.getDefault()))
            else -> "Fecha por confirmar"
        }

        val resolvedLeagueName = resolveLeagueName(obj, leagueId)

        var h2hTot = 0; var h2hHw = 0; var h2hDr = 0; var h2hAw = 0; var h2hAvgG = 2.45
        val recentScores = mutableListOf<String>()
        val h2hObj = obj.optJSONObject("head_to_head")
        if (h2hObj != null) {
            h2hTot = h2hObj.optInt("total_matches", 0)
            h2hHw = h2hObj.optInt("home_wins", 0)
            h2hDr = h2hObj.optInt("draws", 0)
            h2hAw = h2hObj.optInt("away_wins", 0)
            h2hAvgG = h2hObj.optDouble("avg_total_goals", 2.45)
            val recArr = h2hObj.optJSONArray("recent_matches")
            if (recArr != null) {
                for (k in 0 until recArr.length().coerceAtMost(3)) {
                    val r = recArr.getJSONObject(k)
                    val sc = extractSafeString(r, "score")
                    if (sc.isNotEmpty()) recentScores.add("${extractSafeString(r, "home")} $sc ${extractSafeString(r, "away")}")
                }
            }
        }

        return ApexEvent(
            id = id, leagueId = leagueId, leagueName = resolvedLeagueName, roundLabel = roundLabel,
            homeTeamId = homeTeamId, homeTeam = homeTeam, awayTeamId = awayTeamId, awayTeam = awayTeam,
            eventDateRaw = eventDateRaw, eventDateZdt = matchLocal, status = status, period = period,
            currentMinute = curMin, homeScore = homeSc, awayScore = awaySc, h2hTotalMatches = h2hTot,
            h2hHomeWins = h2hHw, h2hDraws = h2hDr, h2hAwayWins = h2hAw, h2hAvgTotalGoals = h2hAvgG,
            h2hHomeWinRate = if (h2hTot > 0) h2hHw.toDouble() / h2hTot else 0.40,
            h2hAwayWinRate = if (h2hTot > 0) h2hAw.toDouble() / h2hTot else 0.35,
            recentScores = recentScores, homeRecentForm = emptyList(), awayRecentForm = emptyList(),
            isNext24Hours = isNext24Hours, timeToKickoffFormatted = timeFormatted
        )
    }

    suspend fun fetchUpcomingEvents(daysAhead: Int = 3, limitPerPage: Int = 100, limitPerDay: Int = limitPerPage): List<ApexEvent> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ApexEvent>()
        val seenIds = mutableSetOf<Long>()
        val nowUtc = ZonedDateTime.now(ZoneId.of("UTC"))
        val nowLocalLdt = LocalDateTime.now()
        val limit48h = nowLocalLdt.plusHours(48)

        for (dayOffset in -1..daysAhead) {
            val dStr = nowUtc.plusDays(dayOffset.toLong()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US))
            var offset = 0
            var hasMore = true
            var pageCount = 0

            while (hasMore && pageCount < 10) {
                pageCount++
                try {
                    val conn = openConnection("/events/?date_from=$dStr&date_to=$dStr&limit=$limitPerPage&offset=$offset")
                    if (conn.responseCode == 200) {
                        val root = JSONObject(readResponse(conn))
                        val results = root.optJSONArray("results") ?: JSONArray()
                        val countThisPage = results.length()

                        for (i in 0 until countThisPage) {
                            val obj = results.getJSONObject(i)
                            val lid = obj.optLong("league_id")
                            val lName = extractLeagueName(obj)
                            if (!isWhitelistedLeague(lid, lName)) continue
                            val id = obj.optLong("id")
                            if (seenIds.contains(id)) continue
                            seenIds.add(id)
                            list.add(parseApexEventFromJson(obj, nowLocalLdt, limit48h))
                        }
                        val nextUrl = root.optString("next", "")
                        if (nextUrl.isNotEmpty() && countThisPage == limitPerPage) offset += limitPerPage else hasMore = false
                    } else hasMore = false
                    conn.disconnect()
                } catch (_: Exception) { hasMore = false }
            }
        }

        if (list.size < 5) {
            var offset = 0; var hasMore = true; var pageCount = 0
            while (hasMore && pageCount < 5) {
                pageCount++
                try {
                    val conn = openConnection("/events/?limit=100&offset=$offset")
                    if (conn.responseCode == 200) {
                        val root = JSONObject(readResponse(conn))
                        val results = root.optJSONArray("results") ?: JSONArray()
                        val countThisPage = results.length()
                        for (i in 0 until countThisPage) {
                            val obj = results.getJSONObject(i)
                            val lid = obj.optLong("league_id")
                            val lName = extractLeagueName(obj)
                            if (!isWhitelistedLeague(lid, lName)) continue
                            val id = obj.optLong("id")
                            if (seenIds.contains(id)) continue
                            seenIds.add(id)
                            list.add(parseApexEventFromJson(obj, nowLocalLdt, limit48h))
                        }
                        val nextUrl = root.optString("next", "")
                        if (nextUrl.isNotEmpty() && countThisPage == 100) offset += 100 else hasMore = false
                    } else hasMore = false
                    conn.disconnect()
                } catch (_: Exception) { hasMore = false }
            }
        }

        list.sortedWith(
            compareBy<ApexEvent> { if (it.status == "inprogress") 0 else 1 }
                .thenBy { it.eventDateZdt?.toEpochSecond() ?: Long.MAX_VALUE }
        )
    }

    suspend fun fetchEvents(dateFrom: String, dateTo: String, limit: Int = 100): List<ApexEvent> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ApexEvent>()
        val nowLocalLdt = LocalDateTime.now()
        val limit48h = nowLocalLdt.plusHours(48)
        try {
            val conn = openConnection("/events/?date_from=$dateFrom&date_to=$dateTo&limit=$limit")
            if (conn.responseCode == 200) {
                val root = JSONObject(readResponse(conn))
                val results = root.optJSONArray("results") ?: JSONArray()
                for (i in 0 until results.length()) {
                    val obj = results.getJSONObject(i)
                    val lid = obj.optLong("league_id")
                    val lName = extractLeagueName(obj)
                    if (!isWhitelistedLeague(lid, lName)) continue
                    list.add(parseApexEventFromJson(obj, nowLocalLdt, limit48h))
                }
            }
            conn.disconnect()
        } catch (_: Exception) {}
        list.sortedWith(
            compareBy<ApexEvent> { if (it.status == "inprogress") 0 else 1 }
                .thenBy { it.eventDateZdt?.toEpochSecond() ?: Long.MAX_VALUE }
        )
    }
}

// ==============================================================================
// 4. MOTOR MATEMÁTICO CUÁNTICO POISSON
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

    fun parseFormStringToBadges(formStr: String): List<MatchFormResult> {
        val list = mutableListOf<MatchFormResult>()
        val chars = formStr.uppercase(Locale.ROOT).reversed().take(5)
        for (c in chars) {
            when (c) {
                'W' -> list.add(MatchFormResult("V", FormOutcome.WIN, "Oficial", "Victoria"))
                'D' -> list.add(MatchFormResult("E", FormOutcome.DRAW, "Oficial", "Empate"))
                'L' -> list.add(MatchFormResult("D", FormOutcome.LOSS, "Oficial", "Derrota"))
            }
        }
        while (list.size < 5) list.add(MatchFormResult("E", FormOutcome.DRAW, "Oficial", "Empate"))
        return list.take(5)
    }

    fun analyzeDoubleChanceFavorite(
        homeTeam: String, awayTeam: String, pHome: Double, pDraw: Double, pAway: Double, odds: RealOdds
    ): DoubleChanceFavoriteAnalysis {
        val isHomeFav = pHome >= pAway
        val favTeam = if (isHomeFav) homeTeam else awayTeam
        val dogTeam = if (isHomeFav) awayTeam else homeTeam
        val pickCode = if (isHomeFav) "1X" else "X2"
        val pickTitle = "$favTeam o Empate ($pickCode)"

        val modelP = (if (isHomeFav) (pHome + pDraw) else (pAway + pDraw)).coerceIn(0.10, 0.98)
        val fairO = (1.0 / modelP).coerceIn(1.02, 25.0)
        val marketO = if (isHomeFav) odds.dc1X else odds.dcX2
        val hasMarketOdds = marketO != null && marketO > 1.01
        val finalOddsNum = if (hasMarketOdds) marketO!! else fairO
        val displayOdds = if (hasMarketOdds) String.format(Locale.US, "@%.2f", marketO) else String.format(Locale.US, "@%.2f (Calc)", fairO)
        val ev = if (hasMarketOdds) (modelP * marketO!!) - 1.0 else null
        val badge = when {
            ev != null && ev > 0.05 -> "VALOR POSITIVO (+${(ev * 100).toInt()}%)"
            modelP >= 0.75 -> "ALTA COBERTURA (${(modelP * 100).toInt()}%)"
            modelP >= 0.60 -> "PROBABILIDAD ${(modelP * 100).toInt()}%"
            else -> "COBERTURA AJUSTADA (${(modelP * 100).toInt()}%)"
        }
        val favWinProbPct = ((if (isHomeFav) pHome else pAway) * 100).toInt()
        val desc = "$favTeam favorito con ${favWinProbPct}% de prob. directa. La Doble Op ($pickCode) cubre triunfo e igualdad con ${(modelP * 100).toInt()}% de cobertura matemática."

        return DoubleChanceFavoriteAnalysis(
            favoriteTeam = favTeam, underdogTeam = dogTeam, isHomeFavorite = isHomeFav, pickCode = pickCode,
            pickTitle = pickTitle, displayOdds = displayOdds, rawOdds = finalOddsNum, isMarketOdds = hasMarketOdds,
            modelProb = modelP, fairOdds = fairO, expectedValue = ev, confidenceBadge = badge, analysisDescription = desc
        )
    }

    fun analyzeCorners(homeStats: TeamRealFormStats, awayStats: TeamRealFormStats, odds: RealOdds): CornersAnalysis {
        if (homeStats.avgCorners == null || awayStats.avgCorners == null) {
            return CornersAnalysis(
                hasData = false,
                expectedTotal = null,
                expectedHome = null,
                expectedAway = null,
                linePrincipal = null,
                probOver = null,
                fairOddsOver = null,
                recommendedPick = "N/D",
                displayOdds = "N/D"
            )
        }

        val expHome = homeStats.avgCorners
        val expAway = awayStats.avgCorners
        val expTotal = expHome + expAway

        // Línea dinámica: si el promedio combinado es 5.2, la línea es 5.5; si es 8.3, es 8.5
        val dynamicLine = floor(expTotal) + 0.5
        val kLine = floor(dynamicLine).toInt()
        val pOver = (1.0 - poissonCdf(kLine, expTotal)).coerceIn(0.05, 0.95)
        val pUnder = 1.0 - pOver

        val (recPick, recProb) = if (pOver >= 0.50) {
            Pair("Más de $dynamicLine Córners", pOver)
        } else {
            Pair("Menos de $dynamicLine Córners", pUnder)
        }

        val fairOdds = (1.0 / recProb).coerceIn(1.05, 20.0)
        val hasMarketOdds = odds.overCornerLine != null && odds.overCornerLine > 1.01
        val displayOdds = if (hasMarketOdds && pOver >= 0.50) {
            String.format(Locale.US, "@%.2f", odds.overCornerLine)
        } else {
            String.format(Locale.US, "@%.2f (Calc)", fairOdds)
        }

        return CornersAnalysis(
            hasData = true,
            expectedTotal = expTotal,
            expectedHome = expHome,
            expectedAway = expAway,
            linePrincipal = dynamicLine,
            probOver = recProb,
            fairOddsOver = fairOdds,
            recommendedPick = recPick,
            displayOdds = displayOdds,
            isMarketOdds = hasMarketOdds
        )
    }

    fun analyzeCards(homeStats: TeamRealFormStats, awayStats: TeamRealFormStats, odds: RealOdds): CardsAnalysis {
        if (homeStats.avgYellowCards == null || awayStats.avgYellowCards == null) {
            return CardsAnalysis(
                hasData = false,
                expectedTotal = null,
                expectedHome = null,
                expectedAway = null,
                linePrincipal = null,
                probOver = null,
                fairOdds = null,
                recommendedPick = "N/D",
                displayOdds = "N/D"
            )
        }

        val expHome = homeStats.avgYellowCards
        val expAway = awayStats.avgYellowCards
        val expTotal = expHome + expAway

        // Línea dinámica: si el promedio es 4.2 -> 4.5
        val dynamicLine = floor(expTotal) + 0.5
        val kLine = floor(dynamicLine).toInt()
        val pOver = (1.0 - poissonCdf(kLine, expTotal)).coerceIn(0.05, 0.95)
        val pUnder = 1.0 - pOver

        val (recPick, recProb) = if (pOver >= 0.50) {
            Pair("Más de $dynamicLine Tarjetas", pOver)
        } else {
            Pair("Menos de $dynamicLine Tarjetas", pUnder)
        }

        val fairOdds = (1.0 / recProb).coerceIn(1.05, 20.0)
        val hasMarketOdds = odds.overCardsLine != null && odds.overCardsLine > 1.01
        val displayOdds = if (hasMarketOdds && pOver >= 0.50) {
            String.format(Locale.US, "@%.2f", odds.overCardsLine)
        } else {
            String.format(Locale.US, "@%.2f (Calc)", fairOdds)
        }

        return CardsAnalysis(
            hasData = true,
            expectedTotal = expTotal,
            expectedHome = expHome,
            expectedAway = expAway,
            linePrincipal = dynamicLine,
            probOver = recProb,
            fairOdds = fairOdds,
            recommendedPick = recPick,
            displayOdds = displayOdds,
            isMarketOdds = hasMarketOdds
        )
    }

    fun generateRealPlayerProps(
        homeTeam: String, awayTeam: String, homeSquad: List<Pair<String, String>>, awaySquad: List<Pair<String, String>>,
        homeStats: TeamRealFormStats, awayStats: TeamRealFormStats, confirmedLineups: Set<String> = emptySet()
    ): List<PlayerShotProp> {
        val list = mutableListOf<PlayerShotProp>()

        // Filtrar jugadores por alineación oficial confirmada si está disponible
        val validHomePlayers = if (confirmedLineups.isNotEmpty()) {
            homeSquad.filter { pair ->
                val low = pair.first.lowercase(Locale.ROOT)
                confirmedLineups.any { it.contains(low) || low.contains(it) }
            }
        } else homeSquad

        val validAwayPlayers = if (confirmedLineups.isNotEmpty()) {
            awaySquad.filter { pair ->
                val low = pair.first.lowercase(Locale.ROOT)
                confirmedLineups.any { it.contains(low) || low.contains(it) }
            }
        } else awaySquad

        val p1 = validHomePlayers.firstOrNull() ?: Pair("Jugador Local", "Delantero Centro")
        val hBaseSoT = homeStats.avgShotsOnTarget ?: 4.2
        val hBaseTotal = homeStats.avgTotalShots ?: 12.0
        val hFwdSoT = (hBaseSoT * 0.32).coerceIn(0.8, 2.1)
        val hFwdProbSoT1 = 1.0 - exp(-hFwdSoT)
        val (pickH, probH) = if (hFwdSoT >= 1.25) Pair("Más de 1.5 al Arco", 1.0 - (poissonPmf(0, hFwdSoT) + poissonPmf(1, hFwdSoT))) else Pair("Más de 0.5 al Arco", hFwdProbSoT1)
        val fairOH = (1.0 / probH).coerceAtLeast(1.05)
        list.add(PlayerShotProp(
            fullName = p1.first, teamName = homeTeam, rolePosition = p1.second, totalShotsLambda = (hBaseTotal * 0.22).coerceIn(1.8, 4.2),
            shotsOnTargetLambda = hFwdSoT, pickTotalShots = "Más de 2.5 Remates", probTotalShots = 1.0 - poissonCdf(2, 2.8),
            pickOnTarget = pickH, probOnTarget = probH, fairOddsOnTarget = fairOH, displayOdds = String.format(Locale.US, "@%.2f (Calc)", fairOH)
        ))

        val p2 = validAwayPlayers.firstOrNull() ?: Pair("Delantero por confirmar", "Delantero Centro")
        val aBaseSoT = awayStats.avgShotsOnTarget ?: 3.8
        val aBaseTotal = awayStats.avgTotalShots ?: 11.0
        val aFwdSoT = (aBaseSoT * 0.28).coerceIn(0.6, 1.8)
        val aFwdProbSoT1 = 1.0 - exp(-aFwdSoT)
        val (pickA, probA) = if (aFwdSoT >= 1.25) Pair("Más de 1.5 al Arco", 1.0 - (poissonPmf(0, aFwdSoT) + poissonPmf(1, aFwdSoT))) else Pair("Más de 0.5 al Arco", aFwdProbSoT1)
        val fairOA = (1.0 / probA).coerceAtLeast(1.05)
        list.add(PlayerShotProp(
            fullName = p2.first, teamName = awayTeam, rolePosition = p2.second, totalShotsLambda = (aBaseTotal * 0.20).coerceIn(1.5, 3.8),
            shotsOnTargetLambda = aFwdSoT, pickTotalShots = "Más de 2.5 Remates", probTotalShots = 1.0 - poissonCdf(2, 2.5),
            pickOnTarget = pickA, probOnTarget = probA, fairOddsOnTarget = fairOA, displayOdds = String.format(Locale.US, "@%.2f (Calc)", fairOA)
        ))
        return list
    }

    fun analyzeEventWithRealData(
        event: ApexEvent, odds: RealOdds, homeStats: TeamRealFormStats, awayStats: TeamRealFormStats,
        homeSquad: List<Pair<String, String>>, awaySquad: List<Pair<String, String>>,
        confirmedLineups: Set<String> = emptySet()
    ): QuantumMatchAnalysis {
        val leagueAvg = 1.35
        val homeAttack = ((homeStats.avgGoalsScored ?: 1.35) / leagueAvg).coerceIn(0.60, 2.50)
        val awayDefense = ((awayStats.avgGoalsConceded ?: 1.20) / leagueAvg).coerceIn(0.60, 2.40)
        val awayAttack = ((awayStats.avgGoalsScored ?: 1.25) / leagueAvg).coerceIn(0.60, 2.50)
        val homeDefense = ((homeStats.avgGoalsConceded ?: 1.20) / leagueAvg).coerceIn(0.60, 2.40)

        val lambdaHome = (homeAttack * awayDefense * 1.45).coerceIn(0.40, 3.50)
        val lambdaAway = (awayAttack * homeDefense * 1.10).coerceIn(0.30, 3.00)
        val lambdaTotal = lambdaHome + lambdaAway

        var pHome = 0.0; var pDraw = 0.0; var pAway = 0.0
        var bestScore = "1 - 1"; var bestScoreP = 0.0

        for (h in 0..5) {
            for (a in 0..5) {
                val pScore = poissonPmf(h, lambdaHome) * poissonPmf(a, lambdaAway)
                when {
                    h > a -> pHome += pScore
                    h == a -> pDraw += pScore
                    else -> pAway += pScore
                }
                if (pScore > bestScoreP) { bestScoreP = pScore; bestScore = "$h - $a" }
            }
        }
        val totP = (pHome + pDraw + pAway).coerceAtLeast(0.01)
        pHome /= totP; pDraw /= totP; pAway /= totP

        val pOver15 = (1.0 - poissonCdf(1, lambdaTotal)).coerceIn(0.20, 0.95)
        val pOver25 = (1.0 - poissonCdf(2, lambdaTotal)).coerceIn(0.15, 0.90)
        val pOver35 = (1.0 - poissonCdf(3, lambdaTotal)).coerceIn(0.05, 0.80)
        val pBttsY = ((1.0 - poissonPmf(0, lambdaHome)) * (1.0 - poissonPmf(0, lambdaAway))).coerceIn(0.20, 0.90)

        val dcAnalysis = analyzeDoubleChanceFavorite(event.homeTeam, event.awayTeam, pHome, pDraw, pAway, odds)
        val cornersAnalysis = analyzeCorners(homeStats, awayStats, odds)
        val cardsAnalysis = analyzeCards(homeStats, awayStats, odds)
        val playerProps = generateRealPlayerProps(event.homeTeam, event.awayTeam, homeSquad, awaySquad, homeStats, awayStats, confirmedLineups)
        val mainStriker = playerProps.firstOrNull()

        // --------------------------------------------------------------------------
        // 1. TARJETA DE PARLAY REAL: FILTRO ANTI-CORRELACIÓN DE MERCADOS INDEPENDIENTES
        // --------------------------------------------------------------------------
        val candidateLegs = mutableListOf<ParlayLeg>()

        // Mercado A: Doble Oportunidad / Favorito
        if (dcAnalysis.modelProb >= 0.55) {
            candidateLegs.add(
                ParlayLeg(
                    category = "Doble Op",
                    pick = dcAnalysis.pickTitle,
                    lambda = null,
                    modelProb = dcAnalysis.modelProb,
                    rawOdds = dcAnalysis.rawOdds,
                    displayOdds = dcAnalysis.displayOdds,
                    edgePercent = dcAnalysis.expectedValue?.let { it * 100.0 },
                    isSafePick = dcAnalysis.modelProb >= 0.70
                )
            )
        }

        // Mercado B: Goles o BTTS (Estricta Exclusión Mutua para Cero Correlación)
        val goalCandidates = mutableListOf<ParlayLeg>()
        if (pOver15 >= 0.55) {
            val fairO = (1.0 / pOver15).coerceIn(1.05, 15.0)
            val hasMkt = odds.over15Goals != null && odds.over15Goals > 1.01
            val rawO = if (hasMkt) odds.over15Goals!! else fairO
            val dispO = if (hasMkt) String.format(Locale.US, "@%.2f", rawO) else String.format(Locale.US, "@%.2f (Calc)", fairO)
            goalCandidates.add(ParlayLeg("Goles", "Más de 1.5 Goles", lambdaTotal, pOver15, rawO, dispO, null, pOver15 >= 0.75))
        }
        val pU35 = 1.0 - pOver35
        if (pU35 >= 0.55) {
            val fairO = (1.0 / pU35).coerceIn(1.05, 15.0)
            val hasMkt = odds.under35Goals != null && odds.under35Goals > 1.01
            val rawO = if (hasMkt) odds.under35Goals!! else fairO
            val dispO = if (hasMkt) String.format(Locale.US, "@%.2f", rawO) else String.format(Locale.US, "@%.2f (Calc)", fairO)
            goalCandidates.add(ParlayLeg("Goles", "Menos de 3.5 Goles", lambdaTotal, pU35, rawO, dispO, null, pU35 >= 0.75))
        }
        if (pOver25 >= 0.55) {
            val fairO = (1.0 / pOver25).coerceIn(1.05, 15.0)
            val hasMkt = odds.over25Goals != null && odds.over25Goals > 1.01
            val rawO = if (hasMkt) odds.over25Goals!! else fairO
            val dispO = if (hasMkt) String.format(Locale.US, "@%.2f", rawO) else String.format(Locale.US, "@%.2f (Calc)", fairO)
            goalCandidates.add(ParlayLeg("Goles", "Más de 2.5 Goles", lambdaTotal, pOver25, rawO, dispO, null, pOver25 >= 0.70))
        }
        val pU25 = 1.0 - pOver25
        if (pU25 >= 0.55) {
            val fairO = (1.0 / pU25).coerceIn(1.05, 15.0)
            val hasMkt = odds.under25Goals != null && odds.under25Goals > 1.01
            val rawO = if (hasMkt) odds.under25Goals!! else fairO
            val dispO = if (hasMkt) String.format(Locale.US, "@%.2f", rawO) else String.format(Locale.US, "@%.2f (Calc)", fairO)
            goalCandidates.add(ParlayLeg("Goles", "Menos de 2.5 Goles", lambdaTotal, pU25, rawO, dispO, null, pU25 >= 0.70))
        }
        if (pBttsY >= 0.55) {
            val fairO = (1.0 / pBttsY).coerceIn(1.05, 15.0)
            val hasMkt = odds.bttsYes != null && odds.bttsYes > 1.01
            val rawO = if (hasMkt) odds.bttsYes!! else fairO
            val dispO = if (hasMkt) String.format(Locale.US, "@%.2f", rawO) else String.format(Locale.US, "@%.2f (Calc)", fairO)
            goalCandidates.add(ParlayLeg("BTTS", "Ambos Equipos Anotan: Sí", null, pBttsY, rawO, dispO, null, pBttsY >= 0.65))
        }
        val pBttsN = 1.0 - pBttsY
        if (pBttsN >= 0.55) {
            val fairO = (1.0 / pBttsN).coerceIn(1.05, 15.0)
            val hasMkt = odds.bttsNo != null && odds.bttsNo > 1.01
            val rawO = if (hasMkt) odds.bttsNo!! else fairO
            val dispO = if (hasMkt) String.format(Locale.US, "@%.2f", rawO) else String.format(Locale.US, "@%.2f (Calc)", fairO)
            goalCandidates.add(ParlayLeg("BTTS", "Ambos Equipos Anotan: No", null, pBttsN, rawO, dispO, null, pBttsN >= 0.65))
        }
        // Se selecciona solo el mejor candidato de goles/BTTS para evitar correlación
        goalCandidates.maxByOrNull { it.modelProb }?.let { candidateLegs.add(it) }

        // Mercado C: Córners (100% Real, Línea Dinámica)
        val cornerCandidates = mutableListOf<ParlayLeg>()
        if (cornersAnalysis.hasData && cornersAnalysis.probOver != null && cornersAnalysis.probOver >= 0.55 && cornersAnalysis.linePrincipal != null) {
            val fairO = cornersAnalysis.fairOddsOver ?: (1.0 / cornersAnalysis.probOver)
            val dispO = cornersAnalysis.displayOdds
            cornerCandidates.add(ParlayLeg("Córners", cornersAnalysis.recommendedPick, cornersAnalysis.expectedTotal, cornersAnalysis.probOver, fairO, dispO, null, cornersAnalysis.probOver >= 0.65))
        }
        cornerCandidates.maxByOrNull { it.modelProb }?.let { candidateLegs.add(it) }

        // Mercado D: Tarjetas (100% Real, Línea Dinámica)
        val cardCandidates = mutableListOf<ParlayLeg>()
        if (cardsAnalysis.hasData && cardsAnalysis.probOver != null && cardsAnalysis.probOver >= 0.55 && cardsAnalysis.linePrincipal != null) {
            val fairO = cardsAnalysis.fairOdds ?: (1.0 / cardsAnalysis.probOver)
            val dispO = cardsAnalysis.displayOdds
            cardCandidates.add(ParlayLeg("Tarjetas", cardsAnalysis.recommendedPick, cardsAnalysis.expectedTotal, cardsAnalysis.probOver, fairO, dispO, null, cardsAnalysis.probOver >= 0.65))
        }
        cardCandidates.maxByOrNull { it.modelProb }?.let { candidateLegs.add(it) }

        // Mercado E: Remates de Jugador (Solo si es un jugador auténtico con nombre confirmado)
        if (mainStriker != null && !mainStriker.fullName.contains("Local", ignoreCase = true) && !mainStriker.fullName.contains("confirmar", ignoreCase = true)) {
            if (mainStriker.probOnTarget >= 0.55) {
                candidateLegs.add(
                    ParlayLeg(
                        category = "Remates",
                        pick = "${mainStriker.fullName}: ${mainStriker.pickOnTarget}",
                        lambda = mainStriker.shotsOnTargetLambda,
                        modelProb = mainStriker.probOnTarget,
                        rawOdds = mainStriker.fairOddsOnTarget,
                        displayOdds = mainStriker.displayOdds,
                        edgePercent = null,
                        isSafePick = mainStriker.probOnTarget >= 0.65
                    )
                )
            }
        }

        // FILTRO ANTI-CORRELACIÓN ESTRICTO:
        // Si se elige 'Más de 2.5 Goles', se descarta 'BTTS Sí' y 'Doble Oportunidad 1X' para evitar correlación
        val filteredLegs = mutableListOf<ParlayLeg>()
        val hasOver25 = candidateLegs.any { it.category == "Goles" && it.pick.contains("Más de 2.5") }
        val hasBttsYes = candidateLegs.any { it.category == "BTTS" && it.pick.contains("Sí") }

        for (leg in candidateLegs.sortedByDescending { it.modelProb }) {
            if (hasOver25 && (leg.category == "BTTS" || leg.category == "Doble Op")) continue
            if (hasBttsYes && (leg.category == "Goles" && leg.pick.contains("Más de 2.5"))) continue
            filteredLegs.add(leg)
        }

        // NO LIMITAR LA COMBINADA: si hay 4, 5 o 6 apuestas que superen el 55%, SE AGREGAN TODAS
        val parlayLegs = if (filteredLegs.size >= 2) filteredLegs else emptyList()

        val (jointProb, parlayDisplay, kelly) = if (parlayLegs.size >= 2) {
            var jProb = 1.0
            var totalOdds = 1.0
            for (l in parlayLegs) {
                jProb *= l.modelProb
                totalOdds *= l.rawOdds
            }
            val pDisp = String.format(Locale.US, "@%.2f", totalOdds)
            val kStake = ((jProb * totalOdds - 1.0) / (totalOdds - 1.0) * 0.25 * 100.0).coerceIn(1.0, 5.0)
            Triple(jProb, pDisp, kStake)
        } else {
            Triple(0.0, "-", 0.0)
        }

        val safePickTitle = dcAnalysis.pickTitle
        val safePickOdds = dcAnalysis.displayOdds
        val safePickProb = "${(dcAnalysis.modelProb * 100).toInt()}%"
        val (valPick, valOdds) = if (pOver25 >= 0.55) Pair("Más de 2.5 Goles", if (odds.over25Goals != null) String.format(Locale.US, "@%.2f", odds.over25Goals) else String.format(Locale.US, "@%.2f (Calc)", 1.0 / pOver25))
        else Pair("Menos de 2.5 Goles", if (odds.under25Goals != null) String.format(Locale.US, "@%.2f", odds.under25Goals) else String.format(Locale.US, "@%.2f (Calc)", 1.0 / (1.0 - pOver25)))

        val pPick = mainStriker?.let { "${it.fullName} (${it.teamName}): ${it.pickOnTarget}" } ?: "Remate al arco atacante principal"
        val pOdds = mainStriker?.displayOdds ?: "@1.45 (Calc)"
        val qVerdict = "${dcAnalysis.favoriteTeam} parte con ventaja matemática (${(max(pHome, pAway) * 100).toInt()}%). La Doble Op (${dcAnalysis.pickCode}) ofrece cobertura sólida del ${(dcAnalysis.modelProb * 100).toInt()}%."

        val summary = MatchBettingSummary(
            safePick = safePickTitle, safePickOdds = safePickOdds, safePickProb = safePickProb,
            valuePick = valPick, valuePickOdds = valOdds, playerPick = pPick, playerPickOdds = pOdds,
            cardsPick = cardsAnalysis.recommendedPick, predictedScore = bestScore, quickVerdict = qVerdict
        )

        val updatedEvent = event.copy(
            homeRecentForm = parseFormStringToBadges(homeStats.formString),
            awayRecentForm = parseFormStringToBadges(awayStats.formString)
        )

        return QuantumMatchAnalysis(
            event = updatedEvent, odds = odds, homeStats = homeStats, awayStats = awayStats,
            lambdaHomeGoals = lambdaHome, lambdaAwayGoals = lambdaAway, lambdaTotalGoals = lambdaTotal,
            probHomeWin = pHome, probDraw = pDraw, probAwayWin = pAway,
            probOver15Goals = pOver15, probOver25Goals = pOver25, probOver35Goals = pOver35, probBttsYes = pBttsY,
            mostProbableScore = bestScore, scoreProbability = bestScoreP, doubleChanceAnalysis = dcAnalysis,
            cornersAnalysis = cornersAnalysis, cardsAnalysis = cardsAnalysis, playerProps = playerProps,
            parlayLegs = parlayLegs, jointProbability = jointProb, parlayDisplayOdds = parlayDisplay,
            kellyStakePercent = kelly, bettingSummary = summary
        )
    }
}

// ==============================================================================
// 5. VIEWMODEL REACTIVO CON PETICIONES EN PARALELO Y AUTO-REFRESH
// ==============================================================================
class FootballStatsViewModel : ViewModel() {
    var allAnalyses by mutableStateOf<List<QuantumMatchAnalysis>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var lastUpdatedTime by mutableStateOf("Sincronizando...")
        private set
    var selectedLeagueFilter by mutableStateOf("ALL")
    var onlyNext24Hours by mutableStateOf(true)

    private var autoRefreshJob: Job? = null

    val availableLeagues: List<Pair<String, String>>
        get() {
            val list = mutableListOf(Pair("ALL", "Todas las Ligas"))
            val map = mutableMapOf<Long, String>()
            for (a in allAnalyses) {
                val lid = a.event.leagueId
                if (!map.containsKey(lid)) map[lid] = a.event.leagueName
            }
            for (pId in PRIORITY_LEAGUE_ORDER) {
                val name = map.remove(pId)
                if (name != null) list.add(Pair(pId.toString(), name))
            }
            for ((id, name) in map.toList().sortedBy { it.second }) {
                list.add(Pair(id.toString(), name))
            }
            return list
        }

    val filteredAnalyses: List<QuantumMatchAnalysis>
        get() {
            val nowLdt = LocalDateTime.now()
            val limit48h = nowLdt.plusHours(48)
            val minTime = nowLdt.minusHours(2)
            val list = allAnalyses.filter { analysis ->
                val matchLdt = analysis.event.eventDateZdt?.toLocalDateTime()
                val matchesTime = if (onlyNext24Hours) {
                    if (analysis.event.status == "finished") false
                    else if (analysis.event.status == "inprogress" || analysis.event.currentMinute != null) true
                    else if (matchLdt != null) !matchLdt.isBefore(minTime) && !matchLdt.isAfter(limit48h)
                    else true // Si no hay fecha parseada, NO ocultar
                } else true
                val matchesLeague = if (selectedLeagueFilter == "ALL") true else analysis.event.leagueId.toString() == selectedLeagueFilter
                matchesTime && matchesLeague
            }
            return list.sortedWith(
                compareBy<QuantumMatchAnalysis> { if (it.event.status == "inprogress" || it.event.currentMinute != null) 0 else 1 }
                    .thenBy { it.event.eventDateZdt?.toEpochSecond() ?: Long.MAX_VALUE }
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
                delay(30L * 60L * 1000L) // Auto actualización cada 30 minutos
                loadData()
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val rawEvents = BzzoiroApiClient.fetchUpcomingEvents(daysAhead = 3, limitPerPage = 100)
                if (rawEvents.isEmpty()) {
                    errorMessage = "No se encontraron partidos devueltos por Bzzoiro para el rango consultado."
                } else {
                    val analysesList = coroutineScope {
                        rawEvents.map { ev ->
                            async {
                                val nowLocal = LocalDateTime.now()
                                val matchLdt = ev.eventDateZdt?.toLocalDateTime()
                                val minsToStart = if (matchLdt != null) java.time.Duration.between(nowLocal, matchLdt).toMinutes() else Long.MAX_VALUE
                                val isUnder90MinOrLive = ev.status == "inprogress" || (minsToStart in -120..90)

                                val oddsDeferred = async { BzzoiroApiClient.fetchEventOdds(ev.id) }
                                val homeSquadDeferred = async { BzzoiroApiClient.fetchTeamSquad(ev.homeTeamId, ev.homeTeam, isHomeTeam = true) }
                                val awaySquadDeferred = async { BzzoiroApiClient.fetchTeamSquad(ev.awayTeamId, ev.awayTeam, isHomeTeam = false) }
                                val homeFormDeferred = async { BzzoiroApiClient.fetchTeamForm(ev.homeTeamId, ev.homeTeam) }
                                val awayFormDeferred = async { BzzoiroApiClient.fetchTeamForm(ev.awayTeamId, ev.awayTeam) }
                                val lineupsDeferred = if (isUnder90MinOrLive) async { BzzoiroApiClient.fetchEventLineups(ev.id) } else null

                                val odds = oddsDeferred.await()
                                val homeSquad = homeSquadDeferred.await()
                                val awaySquad = awaySquadDeferred.await()
                                val homeForm = homeFormDeferred.await()
                                val awayForm = awayFormDeferred.await()
                                val lineups = lineupsDeferred?.await() ?: emptySet()

                                QuantumMathEngine.analyzeEventWithRealData(
                                    event = ev, odds = odds, homeStats = homeForm, awayStats = awayForm,
                                    homeSquad = homeSquad, awaySquad = awaySquad, confirmedLineups = lineups
                                )
                            }
                        }.awaitAll()
                    }
                    allAnalyses = analysesList.sortedBy { it.event.eventDateZdt?.toEpochSecond() ?: Long.MAX_VALUE }
                }
                val nowLocal = ZonedDateTime.now(ZoneId.systemDefault())
                lastUpdatedTime = "Actualizado ${nowLocal.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))} hs (Auto 30 min)"
            } catch (e: Exception) {
                errorMessage = "Error de conexión: ${e.localizedMessage ?: "Revise red"}"
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
        topBar = { ApexHeader(onRefresh = { viewModel.loadData() }, isLoading = isLoading, lastSync = viewModel.lastUpdatedTime) },
        containerColor = Color(0xFF070B14)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Brush.verticalGradient(listOf(Color(0xFF070B14), Color(0xFF0C1322), Color(0xFF070B14))))
        ) {
            TimeWindowSelectorRow(
                onlyNext24h = viewModel.onlyNext24Hours,
                onSelectWindow = { viewModel.onlyNext24Hours = it }
            )
            LeagueSelectorRow(
                leagues = leagues,
                selectedLeagueId = viewModel.selectedLeagueFilter,
                onSelectLeague = { viewModel.selectedLeagueFilter = it }
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF00E5FF), strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("CONSULTANDO BZZOIRO SPORTS DATA...", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            } else if (errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(errorMessage, color = Color.White, textAlign = TextAlign.Center, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadData() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))) {
                            Text("Reintentar", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (analyses.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No hay partidos programados para la liga o rango seleccionado.", color = Color(0xFF94A3B8), textAlign = TextAlign.Center, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(analyses, key = { it.event.id }) { analysis ->
                        QuantumMatchCard(analysis = analysis)
                    }
                }
            }
        }
    }
}

@Composable
fun ApexHeader(onRefresh: () -> Unit, isLoading: Boolean, lastSync: String) {
    Surface(color = Color(0xFF0C1322), shadowElevation = 8.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF00E5FF)))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("APEX QUANTUM STRIKE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.sp)
                }
                Text(lastSync, color = Color(0xFF64748B), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            IconButton(onClick = onRefresh, enabled = !isLoading) {
                if (isLoading) CircularProgressIndicator(color = Color(0xFF00E5FF), modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = Color(0xFF00E5FF))
            }
        }
    }
}

@Composable
fun TimeWindowSelectorRow(onlyNext24h: Boolean, onSelectWindow: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = onlyNext24h, onClick = { onSelectWindow(true) },
            label = { Text("PRÓXIMAS 48H", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFF00E5FF), selectedLabelColor = Color.Black,
                containerColor = Color(0xFF141E33), labelColor = Color(0xFF94A3B8)
            )
        )
        FilterChip(
            selected = !onlyNext24h, onClick = { onSelectWindow(false) },
            label = { Text("TODOS LOS DÍAS", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFF00E5FF), selectedLabelColor = Color.Black,
                containerColor = Color(0xFF141E33), labelColor = Color(0xFF94A3B8)
            )
        )
    }
}

@Composable
fun LeagueSelectorRow(leagues: List<Pair<String, String>>, selectedLeagueId: String, onSelectLeague: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(leagues) { (id, name) ->
            val isSelected = selectedLeagueId == id
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) Color(0xFF1E293B) else Color(0xFF0E1626),
                border = BorderStroke(1.dp, if (isSelected) Color(0xFF00E5FF) else Color(0xFF1E293B)),
                modifier = Modifier.clickable { onSelectLeague(id) }
            ) {
                Text(
                    text = name,
                    color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun QuantumMatchCard(analysis: QuantumMatchAnalysis) {
    val ev = analysis.event
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Resumen", "Combinada", "Doble Op", "Córners/Tarj", "Remates", "Poisson 1X2")

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1626)),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = ev.leagueName.uppercase(Locale.ROOT),
                        color = Color(0xFF00E5FF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF141E33)) {
                        Text(
                            text = "ID: ${ev.leagueId}",
                            color = Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (ev.status == "inprogress") Color(0x33EF4444) else Color(0xFF141E33),
                    border = BorderStroke(1.dp, if (ev.status == "inprogress") Color(0xFFEF4444) else Color(0xFF1E293B))
                ) {
                    Text(
                        text = ev.timeToKickoffFormatted,
                        color = if (ev.status == "inprogress") Color(0xFFEF4444) else Color(0xFF38BDF8),
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(ev.homeTeam, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(3.dp))
                    RecentFormDotsRow(ev.homeRecentForm, "Local")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
                    if (ev.homeScore != null && ev.awayScore != null) {
                        Text("${ev.homeScore} - ${ev.awayScore}", color = Color(0xFF00E5FF), fontSize = 18.sp, fontWeight = FontWeight.Black)
                    } else {
                        Text("VS", color = Color(0xFF64748B), fontSize = 13.sp, fontWeight = FontWeight.Black)
                    }
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(ev.awayTeam, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                    Spacer(modifier = Modifier.height(3.dp))
                    RecentFormDotsRow(ev.awayRecentForm, "Visita")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF0A0F1D),
                contentColor = Color(0xFF00E5FF),
                edgePadding = 0.dp,
                indicator = {},
                divider = {}
            ) {
                tabTitles.forEachIndexed { idx, title ->
                    val isTabSelected = selectedTab == idx
                    Tab(
                        selected = isTabSelected,
                        onClick = { selectedTab = idx },
                        text = {
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isTabSelected) Color(0xFF00E5FF) else Color(0xFF64748B)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (selectedTab) {
                0 -> MatchBettingSummaryView(summary = analysis.bettingSummary)
                1 -> MatchBetBuilderCardView(analysis = analysis)
                2 -> DoubleChanceModuleView(dc = analysis.doubleChanceAnalysis)
                3 -> CornersAndCardsModuleView(corners = analysis.cornersAnalysis, cards = analysis.cardsAnalysis)
                4 -> PlayerShotsModuleView(players = analysis.playerProps)
                5 -> Poisson1X2ModuleView(analysis = analysis)
            }
        }
    }
}

@Composable
fun RecentFormDotsRow(formList: List<MatchFormResult>, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        formList.forEach { item ->
            val color = when (item.outcome) {
                FormOutcome.WIN -> Color(0xFF10B981)
                FormOutcome.DRAW -> Color(0xFFEAB308)
                FormOutcome.LOSS -> Color(0xFFEF4444)
            }
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.charCode,
                    color = Color.Black,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    lineHeight = 10.sp,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        textAlign = TextAlign.Center
                    )
                )
            }
        }
    }
}

@Composable
fun MatchBettingSummaryView(summary: MatchBettingSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF141E33), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text("RESUMEN RECOMENDADO PARA EL PARTIDO", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Apuesta Segura:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Text("${summary.safePick} ${summary.safePickOdds}", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Apuesta de Valor:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Text("${summary.valuePick} ${summary.valuePickOdds}", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Remate Jugador:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Text("${summary.playerPick} ${summary.playerPickOdds}", color = Color(0xFFA855F7), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Marcador Más Probable:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Text(summary.predictedScore, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(summary.quickVerdict, color = Color(0xFFCBD5E1), fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
    }
}

@Composable
fun MatchBetBuilderCardView(analysis: QuantumMatchAnalysis) {
    val parlay = analysis.parlayLegs
    if (parlay.size < 2) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF141E33),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ) {
            Text(
                text = "No hay valor suficiente para una combinada",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        }
    } else {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF141E33),
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "COMBINADA RECOMENDADA (PARLAY REAL)",
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF0A0F1D)) {
                        Text(
                            text = "${parlay.size} SELECCIONES",
                            color = Color(0xFF38BDF8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Divider(color = Color(0xFF1E293B), thickness = 1.dp)

                parlay.forEachIndexed { idx, leg ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0E1626),
                        border = BorderStroke(1.dp, Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Pata ${idx + 1}: [${leg.category}] - ${leg.pick} (${leg.displayOdds})",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Probabilidad individual: ${(leg.modelProb * 100).toInt()}%",
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                Divider(color = Color(0xFF1E293B), thickness = 1.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("CUOTA TOTAL COMBINADA", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(analysis.parlayDisplayOdds, color = Color(0xFF10B981), fontSize = 16.sp, fontWeight = FontWeight.Black)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("PROBABILIDAD CONJUNTA", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${(analysis.jointProbability * 100).toInt()}%",
                            color = Color(0xFF00E5FF),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DoubleChanceModuleView(dc: DoubleChanceFavoriteAnalysis) {
    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF141E33), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("DOBLE OPORTUNIDAD: ${dc.pickCode}", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(dc.displayOdds, color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
            Text(dc.pickTitle, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(dc.analysisDescription, color = Color(0xFF94A3B8), fontSize = 11.sp)
        }
    }
}

@Composable
fun CornersAndCardsModuleView(corners: CornersAnalysis, cards: CardsAnalysis) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF141E33), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text("PROYECCIÓN DE CÓRNERS (TIROS DE ESQUINA)", color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                if (corners.hasData && corners.expectedTotal != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Línea Dinámica:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        Text("${corners.recommendedPick} (${corners.displayOdds})", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Promedio Real: ${String.format(Locale.US, "%.1f", corners.expectedTotal)} córners/partido", color = Color(0xFF64748B), fontSize = 11.sp)
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Línea de Córners:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        Text("N/D", color = Color(0xFF64748B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Sin datos de córners reportados en Bzzoiro", color = Color(0xFF64748B), fontSize = 11.sp)
                }
            }
        }
        Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF141E33), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text("PROYECCIÓN DE TARJETAS", color = Color(0xFFEAB308), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                if (cards.hasData && cards.expectedTotal != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Línea Dinámica:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        Text("${cards.recommendedPick} (${cards.displayOdds})", color = Color(0xFFEAB308), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Promedio Real: ${String.format(Locale.US, "%.1f", cards.expectedTotal)} tarjetas/partido", color = Color(0xFF64748B), fontSize = 11.sp)
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Línea de Tarjetas:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        Text("N/D", color = Color(0xFF64748B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Sin datos de tarjetas reportadas en Bzzoiro", color = Color(0xFF64748B), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun PlayerShotsModuleView(players: List<PlayerShotProp>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        players.forEach { p ->
            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF141E33), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(p.fullName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(p.displayOdds, color = Color(0xFFA855F7), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Text("${p.teamName} • ${p.rolePosition}", color = Color(0xFF64748B), fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(p.pickOnTarget, color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun Poisson1X2ModuleView(analysis: QuantumMatchAnalysis) {
    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF141E33), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("PROBABILIDADES POISSON 1X2", color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MetricPill("Local (1)", "${(analysis.probHomeWin * 100).toInt()}%")
                MetricPill("Empate (X)", "${(analysis.probDraw * 100).toInt()}%")
                MetricPill("Visita (2)", "${(analysis.probAwayWin * 100).toInt()}%")
            }
            Divider(color = Color(0xFF1E293B), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Más de 1.5 Goles: ${(analysis.probOver15Goals * 100).toInt()}%", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                Text("Más de 2.5 Goles: ${(analysis.probOver25Goals * 100).toInt()}%", color = Color(0xFFCBD5E1), fontSize = 11.sp)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Ambos Marcan (BTTS): ${(analysis.probBttsYes * 100).toInt()}%", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                Text("Marcador: ${analysis.mostProbableScore}", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MetricPill(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Color(0xFF94A3B8), fontSize = 10.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
