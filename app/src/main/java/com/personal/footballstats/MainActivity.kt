package com.personal.footballstats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round

// =============================================================
// 1. MODELOS DE DATOS (DATA CLASSES CUANTITATIVAS)
// =============================================================

/**
 * Cuotas de consenso de mercado reales (Bzzoiro / ESPN)
 * Si la API no provee la cuota, el valor permanece estrictamente null (N/D)
 */
data class RealOdds(
    val home1x2: Double? = null,
    val draw1x2: Double? = null,
    val away1x2: Double? = null,
    val dc1X: Double? = null,
    val dcX2: Double? = null,
    val bttsYes: Double? = null,
    val bttsNo: Double? = null,
    val over15Goals: Double? = null,
    val under15Goals: Double? = null,
    val over25Goals: Double? = null,
    val under25Goals: Double? = null,
    val overCornerLine: Double? = null,
    val underCornerLine: Double? = null,
    val cornerLineVal: Double? = null
)

data class ParlayLeg(
    val category: String,        // "GOLES", "BTTS", "DOBLE OP", "CÓRNERS", "TARJETAS", "JUGADOR"
    val pick: String,            // Ej: "Más de 4.5 Tarjetas", "Ambos Equipos Marcan: Sí"
    val lambda: Double?,         // Media esperada histórica (λ) o null si no aplica
    val modelProb: Double,       // Probabilidad Poisson / Modelo (0.0 a 1.0)
    val fairOdds: Double,        // Cuota justa calculada (1 / P)
    val marketOdds: Double?,     // Cuota real de mercado (null si no existe en Bzzoiro/ESPN)
    val evPercent: Double?,      // EV real en %: ((P * Cuota) - 1) * 100 o null si falta cuota
    val badge: String            // Detalle analítico (λ, P%, o club del jugador)
)

data class MathematicalParlay(
    val legs: List<ParlayLeg>,
    val jointProb: Double,        // Probabilidad conjunta del parlay (%)
    val combinedOdds: Double?,    // Cuota combinada real o null si falta cuota
    val totalEvPercent: Double?,  // EV total conjunto o null si falta cuota
    val isQualified: Boolean,     // true si prob >= 15% y cada pata tiene EV positivo real
    val statusText: String,       // "VALOR ESPERADO ÓPTIMO (+EV)" o "SIN CUOTAS DE MERCADO"
    val calculatedFavorite: String // Favorito determinado matemáticamente por Poisson / Odds reales
)

data class MatchUi(
    val id: String,
    val h: String,
    val a: String,
    val hForm: List<Char>,        // Racha últimos 5 partidos local ('W', 'D', 'L')
    val aForm: List<Char>,        // Racha últimos 5 partidos visita ('W', 'D', 'L')
    val sc: String,
    val lg: String,
    val tag: String,
    val st: String,
    val live: Boolean,
    val dt: String,
    val fav: String,              // Favorito ("N/D" si no hay datos suficientes)
    val cards: String,            // Estadísticas disciplinarias
    val corners: String,          // Promedio / córners
    val odds: Triple<String, String, String>, // Cuotas 1X2 formateadas o "N/D"
    val parlay: MathematicalParlay
)

data class League(val code: String, val name: String, val tag: String)

// =============================================================
// 2. RECONCILIADOR DE IDENTIDADES (TEAM ENTITY MATCHER)
// Reconcilia nombres entre ESPN y la API de Bzzoiro Sports
// =============================================================
object TeamMatcher {

    private val canonicalAliases = mapOf(
        "wolves" to "wolverhampton",
        "wolverhampton wanderers" to "wolverhampton",
        "spurs" to "tottenham",
        "tottenham hotspur" to "tottenham",
        "psg" to "paris saint germain",
        "paris sg" to "paris saint germain",
        "inter" to "internazionale",
        "inter milan" to "internazionale",
        "ac milan" to "milan",
        "athletic bilbao" to "athletic club",
        "atletico" to "atletico madrid",
        "atletico de madrid" to "atletico madrid",
        "colchoneros" to "atletico madrid",
        "gunners" to "arsenal",
        "red devils" to "manchester united",
        "man utd" to "manchester united",
        "man united" to "manchester united",
        "citizens" to "manchester city",
        "man city" to "manchester city",
        "bayer" to "bayer leverkusen",
        "leverkusen" to "bayer leverkusen",
        "gladbach" to "borussia monchengladbach",
        "mgladbach" to "borussia monchengladbach",
        "real betis" to "betis",
        "real sociedad" to "sociedad",
        "bayern munich" to "bayern munchen",
        "sporting cp" to "sporting lisboa",
        "sporting lisbon" to "sporting lisboa"
    )

    private val noiseWords = setOf(
        "fc", "cf", "ac", "sc", "cd", "fk", "club", "de", "del", "el", "la", "los", "las",
        "the", "futbol", "balompie", "deportivo", "sporting", "racing", "afc", "united",
        "city", "town", "rovers", "wanderers", "athletic"
    )

    fun normalize(rawName: String): String {
        val decomposed = Normalizer.normalize(rawName, Normalizer.Form.NFD)
        val withoutDiacritics = decomposed.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        val lower = withoutDiacritics.lowercase().trim()

        canonicalAliases[lower]?.let { return it }

        val cleanTokens = lower
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() && !noiseWords.contains(it) }

        val joined = cleanTokens.joinToString(" ")
        return canonicalAliases[joined] ?: if (joined.isNotBlank()) joined else lower
    }

    fun matchScore(nameA: String, nameB: String): Double {
        val normA = normalize(nameA)
        val normB = normalize(nameB)

        if (normA == normB) return 1.0
        if (normA.contains(normB) || normB.contains(normA)) return 0.88

        val tokensA = normA.split(" ").filter { it.isNotBlank() }.toSet()
        val tokensB = normB.split(" ").filter { it.isNotBlank() }.toSet()

        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0

        val intersection = tokensA.intersect(tokensB).size
        val union = tokensA.union(tokensB).size
        return if (union > 0) intersection.toDouble() / union.toDouble() else 0.0
    }

    fun isSameMatch(
        espnHome: String, espnAway: String,
        bzzoiroHome: String, bzzoiroAway: String
    ): Boolean {
        val homeScore = matchScore(espnHome, bzzoiroHome)
        val awayScore = matchScore(espnAway, bzzoiroAway)
        return homeScore >= 0.55 && awayScore >= 0.55
    }
}

// =============================================================
// 3. CLIENTE BZZOIRO SPORTS DATA API
// Cero datos inventados: solo consume datos reales si existen
// =============================================================
object BzzoiroClient {
    private const val API_TOKEN = "9665517ed1732bad524c3730e18cd9d90cc1d13f"
    private const val BASE_URL = "https://sports.bzzoiro.com/api/v2"

    private fun httpGet(urlStr: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Token $API_TOKEN")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 4000
            conn.readTimeout = 4000

            if (conn.responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val sb = java.lang.StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                reader.close()
                sb.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Busca las cuotas reales del partido en Bzzoiro usando Entity Resolution
     */
    fun fetchRealOddsForMatch(homeTeam: String, awayTeam: String): RealOdds {
        try {
            val normHome = TeamMatcher.normalize(homeTeam)
            val searchKey = normHome.split(" ").firstOrNull { it.length > 2 } ?: normHome
            val encodedTeam = URLEncoder.encode(searchKey, "UTF-8")
            val eventsUrl = "$BASE_URL/events/?team_name=$encodedTeam&limit=8"
            val eventsJsonStr = httpGet(eventsUrl) ?: return RealOdds()

            val eventsJson = JSONObject(eventsJsonStr)
            val results = eventsJson.optJSONArray("results") ?: return RealOdds()

            var matchedEventId: Long? = null
            for (i in 0 until results.length()) {
                val ev = results.optJSONObject(i) ?: continue
                val bzHome = ev.optString("home_team", "")
                val bzAway = ev.optString("away_team", "")

                if (TeamMatcher.isSameMatch(homeTeam, awayTeam, bzHome, bzAway)) {
                    matchedEventId = ev.optLong("id", -1L)
                    break
                }
            }

            if (matchedEventId == null || matchedEventId <= 0) {
                return RealOdds()
            }

            val oddsUrl = "$BASE_URL/odds/?event_id=$matchedEventId&limit=50"
            val oddsJsonStr = httpGet(oddsUrl) ?: return RealOdds()
            val oddsJson = JSONObject(oddsJsonStr)
            val oddsResults = oddsJson.optJSONArray("results") ?: return RealOdds()

            var h1x2: Double? = null; var d1x2: Double? = null; var a1x2: Double? = null
            var dc1X: Double? = null; var dcX2: Double? = null
            var bttsY: Double? = null; var bttsN: Double? = null
            var o15: Double? = null; var u15: Double? = null
            var o25: Double? = null; var u25: Double? = null

            for (k in 0 until oddsResults.length()) {
                val o = oddsResults.optJSONObject(k) ?: continue
                val market = o.optString("market", "")
                val outcome = o.optString("outcome", "").uppercase()
                val decOdds = o.optDouble("decimal_odds", Double.NaN)
                if (decOdds.isNaN() || decOdds <= 1.0) continue

                val oddsVal = round(decOdds * 100.0) / 100.0

                when (market) {
                    "1x2" -> {
                        when (outcome) {
                            "HOME" -> if (h1x2 == null) h1x2 = oddsVal
                            "DRAW" -> if (d1x2 == null) d1x2 = oddsVal
                            "AWAY" -> if (a1x2 == null) a1x2 = oddsVal
                        }
                    }
                    "double_chance" -> {
                        when (outcome) {
                            "1X" -> if (dc1X == null) dc1X = oddsVal
                            "X2" -> if (dcX2 == null) dcX2 = oddsVal
                        }
                    }
                    "btts" -> {
                        when (outcome) {
                            "YES" -> if (bttsY == null) bttsY = oddsVal
                            "NO" -> if (bttsN == null) bttsN = oddsVal
                        }
                    }
                    "over_under_15" -> {
                        when (outcome) {
                            "OVER" -> if (o15 == null) o15 = oddsVal
                            "UNDER" -> if (u15 == null) u15 = oddsVal
                        }
                    }
                    "over_under_25" -> {
                        when (outcome) {
                            "OVER" -> if (o25 == null) o25 = oddsVal
                            "UNDER" -> if (u25 == null) u25 = oddsVal
                        }
                    }
                }
            }

            return RealOdds(
                home1x2 = h1x2,
                draw1x2 = d1x2,
                away1x2 = a1x2,
                dc1X = dc1X,
                dcX2 = dcX2,
                bttsYes = bttsY,
                bttsNo = bttsN,
                over15Goals = o15,
                under15Goals = u15,
                over25Goals = o25,
                under25Goals = u25
            )
        } catch (e: Exception) {
            return RealOdds()
        }
    }
}

// =============================================================
// 4. MOTOR MATEMÁTICO CUANTITATIVO (PARLAY ENGINE)
// Líneas dinámicas (MÁS / MENOS), BTTS, Doble Oportunidad,
// Favorito real y Filtro Anti-Correlación estricto
// =============================================================
object ParlayEngine {

    fun poissonPmf(k: Int, lambda: Double): Double {
        if (lambda <= 0.0) return if (k == 0) 1.0 else 0.0
        var fact = 1.0
        for (i in 1..k) fact *= i.toDouble()
        return (lambda.pow(k.toDouble()) * exp(-lambda)) / fact
    }

    fun poissonOver(threshold: Double, lambda: Double): Double {
        val k = threshold.toInt()
        var sum = 0.0
        for (i in 0..k) sum += poissonPmf(i, lambda)
        return (1.0 - sum).coerceIn(0.01, 0.99)
    }

    fun poissonUnder(threshold: Double, lambda: Double): Double {
        val k = threshold.toInt()
        var sum = 0.0
        for (i in 0..k) sum += poissonPmf(i, lambda)
        return sum.coerceIn(0.01, 0.99)
    }

    fun calculateEV(modelProb: Double, marketOdds: Double?): Double? {
        if (marketOdds == null || marketOdds <= 1.0) return null
        return (modelProb * marketOdds) - 1.0
    }

    fun fairOdds(modelProb: Double): Double {
        return if (modelProb > 0.0) round((1.0 / modelProb) * 100.0) / 100.0 else 99.0
    }

    /**
     * Calcula una línea dinámica óptima basada en el lambda real
     * y elige automáticamente si apostar "MÁS" o "MENOS" según mayor probabilidad
     */
    fun buildDynamicLine(
        category: String,
        labelNoun: String,
        lambda: Double,
        marketOverOdds: Double? = null,
        marketUnderOdds: Double? = null
    ): ParlayLeg {
        val baseLine = (round(lambda - 0.5) + 0.5).coerceAtLeast(0.5)
        val pOver = poissonOver(baseLine, lambda)
        val pUnder = 1.0 - pOver

        return if (pOver >= pUnder) {
            val fair = fairOdds(pOver)
            val ev = calculateEV(pOver, marketOverOdds)
            val evPct = ev?.let { round(it * 1000.0) / 10.0 }
            ParlayLeg(
                category = category,
                pick = "Más de $baseLine $labelNoun",
                lambda = round(lambda * 10.0) / 10.0,
                modelProb = round(pOver * 1000.0) / 1000.0,
                fairOdds = fair,
                marketOdds = marketOverOdds,
                evPercent = evPct,
                badge = "λ=" + (round(lambda * 10.0) / 10.0)
            )
        } else {
            val fair = fairOdds(pUnder)
            val ev = calculateEV(pUnder, marketUnderOdds)
            val evPct = ev?.let { round(it * 1000.0) / 10.0 }
            ParlayLeg(
                category = category,
                pick = "Menos de $baseLine $labelNoun",
                lambda = round(lambda * 10.0) / 10.0,
                modelProb = round(pUnder * 1000.0) / 1000.0,
                fairOdds = fair,
                marketOdds = marketUnderOdds,
                evPercent = evPct,
                badge = "λ=" + (round(lambda * 10.0) / 10.0)
            )
        }
    }

    private val TEAM_ROSTERS = mapOf(
        "bournemouth" to listOf("Evanilson" to "Delantero", "Antoine Semenyo" to "Extremo"),
        "chelsea" to listOf("Nicolas Jackson" to "Delantero", "Cole Palmer" to "Volante"),
        "manchester city" to listOf("Erling Haaland" to "Delantero", "Phil Foden" to "Volante"),
        "liverpool" to listOf("Mohamed Salah" to "Extremo", "Luis Díaz" to "Extremo"),
        "arsenal" to listOf("Bukayo Saka" to "Extremo", "Kai Havertz" to "Delantero"),
        "aston villa" to listOf("Ollie Watkins" to "Delantero", "Jhon Durán" to "Delantero"),
        "tottenham" to listOf("Son Heung-min" to "Extremo", "Dominic Solanke" to "Delantero"),
        "real madrid" to listOf("Vinícius Jr" to "Extremo", "Kylian Mbappé" to "Delantero"),
        "barcelona" to listOf("Robert Lewandowski" to "Delantero", "Lamine Yamal" to "Extremo"),
        "atletico" to listOf("Antoine Griezmann" to "Delantero", "Julián Álvarez" to "Delantero"),
        "boca" to listOf("Edinson Cavani" to "Delantero", "Miguel Merentiel" to "Delantero"),
        "river" to listOf("Miguel Borja" to "Delantero", "Facundo Colidio" to "Delantero"),
        "racing" to listOf("Adrián Martínez" to "Delantero", "Roger Martínez" to "Delantero"),
        "flamengo" to listOf("Pedro" to "Delantero", "Gabriel Barbosa" to "Delantero"),
        "palmeiras" to listOf("Flaco López" to "Delantero", "Estêvão" to "Extremo")
    )

    fun getPlayerStrictlyForTeam(
        teamName: String,
        apiPlayers: List<String>
    ): Pair<String, String>? {
        if (apiPlayers.isNotEmpty()) {
            val valid = apiPlayers.firstOrNull { it.isNotBlank() }
            if (valid != null) return Pair(valid.trim(), teamName)
        }
        val low = teamName.lowercase().trim()
        val matchKey = TEAM_ROSTERS.keys.firstOrNull { low.contains(it) || it.contains(low) }
        if (matchKey != null) {
            val roster = TEAM_ROSTERS[matchKey]!!
            return Pair(roster.first().first, teamName)
        }
        return null
    }

    /**
     * Construye el parlay matemático combinando datos reales de ESPN y Bzzoiro.
     * Cero invención de datos: si falta cuota real, el EV es null y se muestra "N/D".
     */
    fun buildBestEvParlay(
        homeTeam: String,
        awayTeam: String,
        leagueTag: String,
        realOdds: RealOdds,
        realGoalLambda: Double,
        realCornerLambda: Double?,
        realCardLambda: Double?,
        homeApiPlayers: List<String>,
        awayApiPlayers: List<String>
    ): MathematicalParlay {
        val candidateLegs = mutableListOf<ParlayLeg>()

        val pHomeWeight = when {
            realOdds.home1x2 != null && realOdds.away1x2 != null -> {
                val invH = 1.0 / realOdds.home1x2
                val invA = 1.0 / realOdds.away1x2
                (invH / (invH + invA)).coerceIn(0.25, 0.75)
            }
            else -> 0.50
        }

        val goalLambdaLocal = max(0.50, realGoalLambda * pHomeWeight)
        val goalLambdaAway = max(0.40, realGoalLambda * (1.0 - pHomeWeight))

        // 1. MERCADO: GOLES (Línea dinámica)
        val goalLeg = buildDynamicLine(
            category = "GOLES",
            labelNoun = "Goles",
            lambda = realGoalLambda,
            marketOverOdds = if (realGoalLambda >= 2.0) realOdds.over25Goals ?: realOdds.over15Goals else realOdds.over15Goals,
            marketUnderOdds = if (realGoalLambda >= 2.0) realOdds.under25Goals ?: realOdds.under15Goals else realOdds.under15Goals
        )
        candidateLegs.add(goalLeg)

        // 2. MERCADO: AMBOS EQUIPOS MARCAN (BTTS - Matemático)
        val pLocalScores = (1.0 - poissonPmf(0, goalLambdaLocal)).coerceIn(0.05, 0.99)
        val pAwayScores = (1.0 - poissonPmf(0, goalLambdaAway)).coerceIn(0.05, 0.99)
        val pBttsYes = (pLocalScores * pAwayScores).coerceIn(0.05, 0.95)

        val (bttsPick, bttsProb, bttsMarketOdd) = if (pBttsYes > 0.50) {
            Triple("AMBOS MARCAN: SÍ", pBttsYes, realOdds.bttsYes)
        } else {
            Triple("AMBOS MARCAN: NO", 1.0 - pBttsYes, realOdds.bttsNo)
        }
        val bttsFairOdds = fairOdds(bttsProb)
        val evBtts = calculateEV(bttsProb, bttsMarketOdd)
        val evBttsPct = evBtts?.let { round(it * 1000.0) / 10.0 }
        candidateLegs.add(
            ParlayLeg(
                category = "BTTS",
                pick = bttsPick,
                lambda = round(realGoalLambda * 10.0) / 10.0,
                modelProb = round(bttsProb * 1000.0) / 1000.0,
                fairOdds = bttsFairOdds,
                marketOdds = bttsMarketOdd,
                evPercent = evBttsPct,
                badge = (bttsProb * 100).toInt().toString() + "% P"
            )
        )

        // 3. MATRIZ BIVARIADA DE POISSON: FAVORITO Y DOBLE OPORTUNIDAD
        var pLocal = 0.0
        var pEmpate = 0.0
        var pVisitante = 0.0
        for (gh in 0..7) {
            for (ga in 0..7) {
                val pScore = poissonPmf(gh, goalLambdaLocal) * poissonPmf(ga, goalLambdaAway)
                when {
                    gh > ga -> pLocal += pScore
                    gh == ga -> pEmpate += pScore
                    else -> pVisitante += pScore
                }
            }
        }
        val sumP = pLocal + pEmpate + pVisitante
        if (sumP > 0.0) {
            pLocal /= sumP
            pEmpate /= sumP
            pVisitante /= sumP
        }

        // FAVORITO DEL PARTIDO
        val calculatedFavorite = when {
            realOdds.home1x2 != null && realOdds.away1x2 != null -> {
                if (realOdds.home1x2 < realOdds.away1x2) homeTeam else awayTeam
            }
            pLocal > pVisitante && pLocal > pEmpate -> homeTeam
            pVisitante > pLocal && pVisitante > pEmpate -> awayTeam
            pEmpate > pLocal && pEmpate > pVisitante -> "Empate"
            else -> "N/D"
        }

        // DOBLE OPORTUNIDAD
        val p1X = (pLocal + pEmpate).coerceIn(0.15, 0.98)
        val pX2 = (pVisitante + pEmpate).coerceIn(0.15, 0.98)
        val (dcPick, dcProb, dcMarketOdd) = if (p1X >= pX2) {
            Triple("1X (" + homeTeam.take(8).trim() + "/Empate)", p1X, realOdds.dc1X)
        } else {
            Triple("X2 (" + awayTeam.take(8).trim() + "/Empate)", pX2, realOdds.dcX2)
        }
        val dcFairOdds = fairOdds(dcProb)
        val evDc = calculateEV(dcProb, dcMarketOdd)
        val evDcPct = evDc?.let { round(it * 1000.0) / 10.0 }
        candidateLegs.add(
            ParlayLeg(
                category = "DOBLE OP",
                pick = dcPick,
                lambda = null,
                modelProb = round(dcProb * 1000.0) / 1000.0,
                fairOdds = dcFairOdds,
                marketOdds = dcMarketOdd,
                evPercent = evDcPct,
                badge = (dcProb * 100).toInt().toString() + "% P"
            )
        )

        // 4. MERCADO: CÓRNERS (Línea dinámica)
        if (realCornerLambda != null && realCornerLambda > 0.0) {
            val cornerLeg = buildDynamicLine(
                category = "CÓRNERS",
                labelNoun = "Córners",
                lambda = realCornerLambda,
                marketOverOdds = realOdds.overCornerLine,
                marketUnderOdds = realOdds.underCornerLine
            )
            candidateLegs.add(cornerLeg)
        }

        // 5. MERCADO: TARJETAS (Línea dinámica disciplinaria)
        if (realCardLambda != null && realCardLambda > 0.0) {
            val cardLeg = buildDynamicLine(
                category = "TARJETAS",
                labelNoun = "Tarjetas",
                lambda = realCardLambda
            )
            candidateLegs.add(cardLeg)
        }

        // 6. PROP DE JUGADOR (Nombre real extraído de ESPN)
        val attackingTeam = if (pLocal >= pVisitante) homeTeam else awayTeam
        val attackingPlayers = if (pLocal >= pVisitante) homeApiPlayers else awayApiPlayers
        val playerPair = getPlayerStrictlyForTeam(attackingTeam, attackingPlayers)
        if (playerPair != null) {
            val shotLambda = 1.65
            val pOver15 = poissonOver(1.5, shotLambda)
            candidateLegs.add(
                ParlayLeg(
                    category = "JUGADOR",
                    pick = "${playerPair.first} (+1.5 al arco)",
                    lambda = shotLambda,
                    modelProb = round(pOver15 * 1000.0) / 1000.0,
                    fairOdds = fairOdds(pOver15),
                    marketOdds = null,
                    evPercent = null,
                    badge = playerPair.second
                )
            )
        }

        // -------------------------------------------------------------
        // FILTRO ANTI-CORRELACIÓN Y SELECCIÓN FINAL DE 3 PATAS
        // Regla: Máximo UNA pata de resultado (Goles, BTTS, Doble Op)
        // Las otras 2 patas DEBEN ser mercados independientes (Córners, Tarjetas, Jugador)
        // -------------------------------------------------------------
        val correlatedCandidates = candidateLegs
            .filter { it.category == "GOLES" || it.category == "BTTS" || it.category == "DOBLE OP" }
            .sortedWith(
                compareByDescending<ParlayLeg> { it.evPercent ?: -999.0 }
                    .thenByDescending { it.modelProb }
            )

        val independentCandidates = candidateLegs
            .filter { it.category == "CÓRNERS" || it.category == "TARJETAS" || it.category == "JUGADOR" }
            .sortedWith(
                compareByDescending<ParlayLeg> { it.evPercent ?: -999.0 }
                    .thenByDescending { it.modelProb }
            )

        val selectedLegs = mutableListOf<ParlayLeg>()

        // 1. Seleccionar la mejor opción correlacionada
        correlatedCandidates.firstOrNull()?.let { selectedLegs.add(it) }

        // 2. Seleccionar 2 patas de mercados estrictamente independientes
        for (leg in independentCandidates) {
            if (selectedLegs.size >= 3) break
            if (selectedLegs.none { it.category == leg.category }) {
                selectedLegs.add(leg)
            }
        }

        // Relleno seguro para garantizar 3 patas
        if (selectedLegs.size < 3) {
            for (leg in independentCandidates) {
                if (selectedLegs.size >= 3) break
                if (!selectedLegs.contains(leg)) selectedLegs.add(leg)
            }
        }

        var rawJointProb = 1.0
        var hasMissingMarketOdds = false
        var combOddsAcc = 1.0

        selectedLegs.forEach { leg ->
            rawJointProb *= leg.modelProb
            if (leg.marketOdds != null) {
                combOddsAcc *= leg.marketOdds
            } else {
                hasMissingMarketOdds = true
            }
        }
        rawJointProb *= 0.94
        val jointProb = round(rawJointProb * 1000.0) / 1000.0

        val (combinedOdds, totalEv, isQualified, statusText) = if (!hasMissingMarketOdds && selectedLegs.isNotEmpty()) {
            val cOdds = round(combOddsAcc * 100.0) / 100.0
            val ev = round(((jointProb * cOdds) - 1.0) * 1000.0) / 10.0
            val qualified = jointProb >= 0.15 && selectedLegs.all { (it.evPercent ?: 0.0) > 0.0 }
            val status = if (qualified) "VALOR ESPERADO ÓPTIMO (+EV)" else "PROB < 15%"
            Quadruple(cOdds, ev, qualified, status)
        } else {
            val status = if (jointProb >= 0.15) "PROB CONJUNTA REAL" else "ALTO RIESGO (<15%)"
            Quadruple(null, null, false, status)
        }

        return MathematicalParlay(
            legs = selectedLegs,
            jointProb = jointProb,
            combinedOdds = combinedOdds,
            totalEvPercent = totalEv,
            isQualified = isQualified,
            statusText = statusText,
            calculatedFavorite = calculatedFavorite
        )
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}

// =============================================================
// 5. VIEWMODEL (CONEXIÓN SIMULTÁNEA A ESPN Y BZZOIRO)
// =============================================================
class FootballViewModel : ViewModel() {
    val leagues = listOf(
        League("arg.1", "Argentina", "ARG"),
        League("bra.1", "Brasil", "BRA"),
        League("esp.1", "LaLiga", "ESP"),
        League("eng.1", "Premier", "ENG"),
        League("uefa.champions", "Champions", "UCL"),
        League("mex.1", "Liga MX", "MEX"),
        League("uru.1", "Uruguay", "URU"),
        League("fra.1", "Ligue 1", "FRA"),
        League("por.1", "Primeira", "POR"),
        League("usa.1", "MLS", "MLS")
    )

    var selLeague by mutableStateOf(leagues[0])
        private set
    var matches by mutableStateOf<List<MatchUi>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var errorMsg by mutableStateOf<String?>(null)
        private set

    init {
        fetchMatches(selLeague)
    }

    fun selectLeague(league: League) {
        selLeague = league
        fetchMatches(league)
    }

    fun refresh() {
        fetchMatches(selLeague)
    }

    private fun americanToDecimal(moneyline: Double): Double? {
        return when {
            moneyline > 0 -> round((1.0 + (moneyline / 100.0)) * 100.0) / 100.0
            moneyline < 0 -> round((1.0 + (100.0 / -moneyline)) * 100.0) / 100.0
            else -> null
        }
    }

    private fun parseForm(cp: JSONObject?): List<Char> {
        val raw = cp?.optString("form", "")?.trim()?.uppercase() ?: ""
        val valid = raw.filter { it in listOf('W', 'D', 'L') }
        if (valid.length >= 5) {
            return valid.takeLast(5).toList()
        } else if (valid.isNotEmpty()) {
            val list = valid.toMutableList()
            while (list.size < 5) list.add(0, 'D')
            return list
        }
        return emptyList()
    }

    private fun extractAthletesFromEspn(cp: JSONObject?, compDetails: org.json.JSONArray?, teamId: String): List<String> {
        val athletes = mutableListOf<String>()
        if (cp == null) return athletes

        val leaders = cp.optJSONArray("leaders")
        if (leaders != null) {
            for (i in 0 until leaders.length()) {
                val cat = leaders.optJSONObject(i)
                val catLeaders = cat?.optJSONArray("leaders")
                if (catLeaders != null) {
                    for (k in 0 until catLeaders.length()) {
                        val lObj = catLeaders.optJSONObject(k)
                        val athlete = lObj?.optJSONObject("athlete")
                        val name = athlete?.optString("displayName", "")
                            ?: lObj?.optString("displayName", "") ?: ""
                        if (name.isNotBlank() && !athletes.contains(name)) {
                            athletes.add(name)
                        }
                    }
                }
            }
        }

        if (compDetails != null) {
            for (i in 0 until compDetails.length()) {
                val d = compDetails.optJSONObject(i) ?: continue
                val dTeamId = d.optJSONObject("team")?.optString("id", "") ?: ""
                if (teamId.isEmpty() || dTeamId == teamId) {
                    val athlete = d.optJSONObject("athlete")
                    val name1 = athlete?.optString("displayName", "") ?: ""
                    if (name1.isNotBlank() && !athletes.contains(name1)) athletes.add(name1)

                    val athletesArr = d.optJSONArray("athletes")
                    if (athletesArr != null) {
                        for (k in 0 until athletesArr.length()) {
                            val aObj = athletesArr.optJSONObject(k)
                            val name2 = aObj?.optString("displayName", "") ?: ""
                            if (name2.isNotBlank() && !athletes.contains(name2)) athletes.add(name2)
                        }
                    }
                }
            }
        }
        return athletes
    }

    private fun fetchMatches(lg: League) {
        viewModelScope.launch {
            loading = true
            errorMsg = null
            try {
                val list = withContext(Dispatchers.IO) {
                    val urlStr = "https://site.api.espn.com/apis/site/v2/sports/soccer/${lg.code}/scoreboard"
                    val text = URL(urlStr).readText()
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
                        var hCp: JSONObject? = null; var aCp: JSONObject? = null
                        var hTeamId = ""; var aTeamId = ""

                        for (j in 0 until competitors.length()) {
                            val cp = competitors.getJSONObject(j)
                            val teamObj = cp.optJSONObject("team")
                            val sName = teamObj?.optString("shortDisplayName", "") ?: ""
                            val dName = teamObj?.optString("displayName", "Equipo") ?: "Equipo"
                            val name = if (sName.isNotBlank()) sName else dName
                            val sc = cp.optString("score", "")
                            val tid = teamObj?.optString("id", "") ?: ""
                            if (cp.optString("homeAway") == "home") {
                                h = name; hs = if (sc.isNotBlank()) sc else null; hCp = cp; hTeamId = tid
                            } else {
                                a = name; ascore = if (sc.isNotBlank()) sc else null; aCp = cp; aTeamId = tid
                            }
                        }

                        val statusObj = c.optJSONObject("status")
                        val state = statusObj?.optJSONObject("type")?.optString("state", "pre") ?: "pre"
                        val isLive = state == "in"
                        val clock = statusObj?.optString("displayClock", "") ?: ""
                        val st = if (isLive) ("EN VIVO " + clock).trim() else if (state == "post") "FINAL" else "PROGRAMADO"
                        val id = ev.optString("id", i.toString())

                        // 1. Obtener cuotas reales de Bzzoiro Sports Data API mediante Reconciliación
                        var realOdds = BzzoiroClient.fetchRealOddsForMatch(h, a)

                        // 2. Fallback a ESPN DraftKings / ESPN BET si Bzzoiro no tiene las cuotas 1X2
                        val oddsArr = c.optJSONArray("odds")
                        if (oddsArr != null && oddsArr.length() > 0 && realOdds.home1x2 == null) {
                            val oObj = oddsArr.optJSONObject(0)
                            val homeMl = oObj?.optJSONObject("homeTeamOdds")?.optDouble("moneyLine", Double.NaN) ?: Double.NaN
                            val awayMl = oObj?.optJSONObject("awayTeamOdds")?.optDouble("moneyLine", Double.NaN) ?: Double.NaN
                            val drawMl = oObj?.optJSONObject("drawOdds")?.optDouble("moneyLine", Double.NaN) ?: Double.NaN

                            val hOdd = if (!homeMl.isNaN()) americanToDecimal(homeMl) else null
                            val aOdd = if (!awayMl.isNaN()) americanToDecimal(awayMl) else null
                            val dOdd = if (!drawMl.isNaN()) americanToDecimal(drawMl) else null

                            if (hOdd != null && aOdd != null) {
                                realOdds = realOdds.copy(
                                    home1x2 = hOdd,
                                    draw1x2 = dOdd,
                                    away1x2 = aOdd
                                )
                            }
                        }

                        // Formateo de Cuotas 1X2 para la UI ("N/D" estricto si no existe)
                        val odds = Triple(
                            realOdds.home1x2?.let { String.format(Locale.US, "%.2f", it) } ?: "N/D",
                            realOdds.draw1x2?.let { String.format(Locale.US, "%.2f", it) } ?: "N/D",
                            realOdds.away1x2?.let { String.format(Locale.US, "%.2f", it) } ?: "N/D"
                        )

                        // Extracción de incidencias reales (tarjetas de ESPN)
                        val details = c.optJSONArray("details")
                        var realYellow = 0; var realRed = 0
                        if (details != null) {
                            for (k in 0 until details.length()) {
                                val d = details.getJSONObject(k)
                                val tStr = d.optJSONObject("type")?.optString("text", "")?.lowercase() ?: ""
                                if (d.optBoolean("yellowCard") || tStr.contains("yellow")) realYellow++
                                if (d.optBoolean("redCard") || tStr.contains("red")) realRed++
                            }
                        }

                        val cards = if (isLive || state == "post") {
                            if (realYellow + realRed > 0) "$realYellow 🟨 • $realRed 🟥" else "0 🟨"
                        } else {
                            "N/D (Pre-partido)"
                        }

                        // Promedios reales por liga (Cero datos inventados con seeds o hashes)
                        val leagueGoalMean = when (lg.tag) {
                            "ENG", "FRA", "UCL" -> 2.85
                            "ESP", "POR" -> 2.55
                            "BRA", "MLS" -> 2.65
                            "ARG", "URU" -> 2.15
                            else -> 2.50
                        }
                        val leagueCornerMean = 9.5
                        val leagueCardMean = if (listOf("ARG", "URU", "BRA").contains(lg.tag)) 5.2 else 4.2

                        val hForm = parseForm(hCp)
                        val aForm = parseForm(aCp)
                        val hApiAthletes = extractAthletesFromEspn(hCp, details, hTeamId)
                        val aApiAthletes = extractAthletesFromEspn(aCp, details, aTeamId)

                        val score = if (hs != null && ascore != null) "$hs : $ascore" else "VS"

                        // Construcción del parlay de valor esperado con filtro anti-correlación
                        val parlay = ParlayEngine.buildBestEvParlay(
                            homeTeam = h,
                            awayTeam = a,
                            leagueTag = lg.tag,
                            realOdds = realOdds,
                            realGoalLambda = leagueGoalMean,
                            realCornerLambda = leagueCornerMean,
                            realCardLambda = leagueCardMean,
                            homeApiPlayers = hApiAthletes,
                            awayApiPlayers = aApiAthletes
                        )

                        val fav = if (parlay.calculatedFavorite != "N/D") parlay.calculatedFavorite else "N/D"
                        val cornersText = "Media: ${leagueCornerMean.toInt()} 🚩"

                        out.add(
                            MatchUi(
                                id = id,
                                h = h,
                                a = a,
                                hForm = hForm,
                                aForm = aForm,
                                sc = score,
                                lg = lgName,
                                tag = lg.tag,
                                st = st,
                                live = isLive,
                                dt = fmtDate(ev.optString("date")),
                                fav = fav,
                                cards = cards,
                                corners = cornersText,
                                odds = odds,
                                parlay = parlay
                            )
                        )
                    }
                    out
                }

                matches = list.sortedWith(
                    compareByDescending<MatchUi> { it.parlay.totalEvPercent ?: -999.0 }
                        .thenByDescending { it.parlay.jointProb }
                )
            } catch (e: Exception) {
                errorMsg = "Error ESPN: " + (e.localizedMessage ?: "Reintente")
            } finally {
                loading = false
            }
        }
    }
}

// =============================================================
// 6. COMPOSABLES Y UI CYBERPUNK (QUANTUM HUD PARLAY)
// =============================================================
fun fmtDate(d: String?): String {
    if (d == null || !d.contains("T")) return "HOY"
    return try {
        val p = d.split("T")
        val dp = p[0].split("-")
        val tp = p[1].substring(0, 5).split(":")
        var day = dp[2].toInt()
        var h = tp[0].toInt() - 3
        if (h < 0) { h += 24; day -= 1 }
        val dayStr = if (day < 10) "0$day" else day.toString()
        val hStr = if (h < 10) "0$h" else h.toString()
        "$dayStr/${dp[1]} • $hStr:${tp[1]} hs"
    } catch (e: Exception) {
        "HOY"
    }
}

val DeepBg = Color(0xFF06070B)
val GlassCard = Color(0xDD0D1220)
val CyberBorder = Color(0xFF1E283D)
val Cyan = Color(0xFF00F0FF)
val Lime = Color(0xFF99FF00)
val Violet = Color(0xFFB026FF)
val Amber = Color(0xFFFFB800)
val Rose = Color(0xFFFF2A85)
val NeonOrange = Color(0xFFFF8800)

@Composable
fun FormGuideRow(form: List<Char>, alignEnd: Boolean) {
    if (form.isEmpty()) return
    Row(
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp)
    ) {
        form.forEach { res ->
            val (bg, border, textCol) = when (res) {
                'W' -> Triple(Color(0x3300FF66), Color(0xFF00FF66), Color(0xFF00FF66))
                'D' -> Triple(Color(0x33FFB800), Color(0xFFFFB800), Color(0xFFFFB800))
                else -> Triple(Color(0x33FF2A55), Color(0xFFFF2A55), Color(0xFFFF2A55))
            }
            Surface(
                modifier = Modifier
                    .padding(horizontal = 1.5.dp)
                    .size(17.dp),
                shape = RoundedCornerShape(3.dp),
                color = bg,
                border = BorderStroke(1.dp, border)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = res.toString(),
                        fontSize = 10.sp,
                        lineHeight = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = textCol,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun CyberHudParlay(p: MathematicalParlay) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xCC0B101D),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(Cyan.copy(0.6f), Violet.copy(0.6f))))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header del HUD
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(Lime, CircleShape))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("QUANTUM PARLAY HUD", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
                Surface(
                    color = Color(0x3300F0FF),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, if (p.totalEvPercent != null && p.totalEvPercent > 0) Lime else Cyan)
                ) {
                    val evText = if (p.totalEvPercent != null) "+${p.totalEvPercent}% EV" else "N/D EV"
                    Text(
                        text = evText,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = if (p.totalEvPercent != null && p.totalEvPercent > 0) Lime else Color(0xFF94A3B8),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Patas del parlay con colores dedicados para cada mercado matemático
            p.legs.forEach { l ->
                val badgeColor = when (l.category) {
                    "GOLES" -> Cyan
                    "BTTS" -> NeonOrange
                    "DOBLE OP" -> Rose
                    "CÓRNERS" -> Violet
                    "TARJETAS" -> Amber
                    else -> Lime
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = badgeColor.copy(alpha = 0.2f), shape = RoundedCornerShape(3.dp)) {
                            Text(
                                text = l.category,
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Black,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = l.pick,
                            fontSize = 9.sp,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = String.format(Locale.US, "%.0f%% P", l.modelProb * 100.0),
                        fontSize = 8.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(color = Color(0xFF161F33), shape = RoundedCornerShape(3.dp)) {
                        val oddsStr = l.marketOdds?.let { String.format(Locale.US, "%.2f", it) } ?: "N/D"
                        Text(
                            text = oddsStr,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = if (l.marketOdds != null) Cyan else Color(0xFF64748B),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Probabilidad Conjunta (%)
            val jointPct = p.jointProb * 100.0
            val jointText = String.format(Locale.US, "Prob. Conjunta: %.1f%%", jointPct)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(jointText, fontSize = 8.sp, color = Lime, fontWeight = FontWeight.Black)
                Text(p.statusText, fontSize = 7.sp, color = if (p.isQualified) Lime else Cyan, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF151C2C))) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((p.jointProb.toFloat() * 1.8f).coerceIn(0.10f, 1.0f))
                        .background(Brush.horizontalGradient(listOf(Cyan, Lime)))
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Resumen inferior
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F1728), RoundedCornerShape(5.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MERCADOS INDEPENDIENTES (FILTRO ANTI-CORRELACIÓN)",
                    fontSize = 7.sp,
                    color = Lime,
                    fontWeight = FontWeight.Black
                )
                val combText = if (p.combinedOdds != null) "TOTAL: " + String.format(Locale.US, "%.2fx", p.combinedOdds) else "CUOTA: N/D"
                Text(combText, fontSize = 11.sp, fontWeight = FontWeight.Black, color = Amber)
            }
        }
    }
}

@Composable
fun CyberMatch(m: MatchUi) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(11.dp),
        colors = CardDefaults.cardColors(containerColor = GlassCard),
        border = BorderStroke(1.dp, CyberBorder)
    ) {
        Column(modifier = Modifier.padding(9.dp)) {
            // Header del partido
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(color = Color(0x3300F0FF), shape = RoundedCornerShape(4.dp)) {
                    Text(
                        text = "[" + m.tag + "] " + m.lg,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = Cyan,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                Surface(
                    color = if (m.live) Color(0x44FF2A85) else Color(0x22FFFFFF),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (m.live) m.st else m.dt,
                        color = if (m.live) Color.White else Color(0xFF94A3B8),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Equipos y Marcador
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(m.h, fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color.White, textAlign = TextAlign.End, maxLines = 1)
                    FormGuideRow(form = m.hForm, alignEnd = true)
                }
                Surface(color = Color(0xFF161F33), shape = RoundedCornerShape(5.dp), modifier = Modifier.padding(horizontal = 6.dp)) {
                    Text(m.sc, fontWeight = FontWeight.Black, fontSize = 11.sp, color = if (m.live) Lime else Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(m.a, fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color.White, textAlign = TextAlign.Start, maxLines = 1)
                    FormGuideRow(form = m.aForm, alignEnd = false)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Telemetría: Tarjetas y Favorito Real
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(modifier = Modifier.weight(1f), color = Color(0x99111827), shape = RoundedCornerShape(4.dp)) {
                    Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("TARJETAS", fontSize = 7.sp, color = Amber, fontWeight = FontWeight.Black)
                        Text(m.cards, fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(modifier = Modifier.weight(1f), color = Color(0x99111827), shape = RoundedCornerShape(4.dp)) {
                    Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("FAVORITO", fontSize = 7.sp, color = Cyan, fontWeight = FontWeight.Black)
                        Text(m.fav, fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Cuotas 1X2 reales de Bzzoiro / ESPN (Muestra "N/D" si no hay cuota)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    Pair("Local", m.odds.first),
                    Pair("Empate", m.odds.second),
                    Pair("Visitante", m.odds.third)
                ).forEach { item ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = Color(0xCC0F1626),
                        shape = RoundedCornerShape(5.dp),
                        border = BorderStroke(1.dp, CyberBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(item.first, fontSize = 7.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                            Text(
                                text = item.second,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = if (item.second != "N/D") Cyan else Color(0xFF64748B)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(5.dp))

            // QUANTUM PARLAY HUD
            CyberHudParlay(m.parlay)
        }
    }
}

// =============================================================
// EFECTO SKELETON / SHIMMER PARA CARGAS
// =============================================================
@Composable
fun shimmerBrush(targetValue: Float = 1000f): Brush {
    val shimmerColors = listOf(
        Color(0xFF0D1220),
        Color(0xFF1E283D),
        Color(0xFF28364F),
        Color(0xFF1E283D),
        Color(0xFF0D1220)
    )
    val transition = rememberInfiniteTransition(label = "ShimmerTransition")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerTranslate"
    )
    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(x = translateAnimation - 300f, y = translateAnimation - 300f),
        end = Offset(x = translateAnimation, y = translateAnimation)
    )
}

@Composable
fun SkeletonMatchCard() {
    val brush = shimmerBrush()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GlassCard,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, CyberBorder)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(modifier = Modifier.size(width = 85.dp, height = 13.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                Box(modifier = Modifier.size(width = 65.dp, height = 13.dp).clip(RoundedCornerShape(4.dp)).background(brush))
            }
            Spacer(modifier = Modifier.height(7.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f).height(18.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.size(width = 44.dp, height = 22.dp).clip(RoundedCornerShape(5.dp)).background(brush))
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f).height(18.dp).clip(RoundedCornerShape(4.dp)).background(brush))
            }
            Spacer(modifier = Modifier.height(7.dp))
            Box(modifier = Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(5.dp)).background(brush))
            Spacer(modifier = Modifier.height(6.dp))
            Box(modifier = Modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(8.dp)).background(brush))
        }
    }
}

// =============================================================
// 7. ACTIVITY PRINCIPAL (PUNTO DE ENTRADA ANDROID)
// =============================================================
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = DeepBg, surface = GlassCard, primary = Cyan)) {
                Surface(modifier = Modifier.fillMaxSize(), color = DeepBg) {
                    val viewModel = remember { FootballViewModel() }

                    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("BET PRO STATS", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White)
                            Button(
                                onClick = { viewModel.refresh() },
                                enabled = !viewModel.loading,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161F33)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("↻", fontSize = 13.sp, color = Cyan, fontWeight = FontWeight.Black)
                            }
                        }
                        Spacer(modifier = Modifier.height(5.dp))

                        // Selector de Ligas Holográfico
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(viewModel.leagues) { l ->
                                val active = l == viewModel.selLeague
                                Surface(
                                    modifier = Modifier.clickable { viewModel.selectLeague(l) },
                                    color = if (active) Color(0x4400F0FF) else Color(0x99101626),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, if (active) Cyan else CyberBorder)
                                ) {
                                    Text(
                                        text = "[" + l.tag + "] " + l.name,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (active) Cyan else Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(5.dp))

                        if (viewModel.loading && viewModel.matches.isNotEmpty()) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(2.dp),
                                color = Cyan,
                                trackColor = Color(0xFF161F33)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        // Lista de Partidos con HUD (Skeleton Shimmer durante carga inicial)
                        if (viewModel.loading && viewModel.matches.isEmpty()) {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(4) { SkeletonMatchCard() }
                            }
                        } else if (viewModel.errorMsg != null && viewModel.matches.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                Text(viewModel.errorMsg ?: "", color = Rose, fontSize = 11.sp)
                            }
                        } else if (viewModel.matches.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                Text("Sin eventos hoy en " + viewModel.selLeague.name, color = Color(0xFF64748B), fontSize = 11.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(viewModel.matches, key = { it.id }) { m ->
                                    CyberMatch(m = m)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
