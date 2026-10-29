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
// 1. MODELOS DE DATOS CUANTITATIVOS NATIVOS BZZOIRO SPORTS API
// ==============================================================================

data class BzzoiroEvent(
    val id: Long,
    val leagueId: Long,
    val roundLabel: String,
    val homeTeam: String,
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
    val recentScores: List<String>
)

data class RealOdds(
    val home1x2: Double? = null,
    val draw1x2: Double? = null,
    val away1x2: Double? = null,
    val dc1X: Double? = null,        // Doble Oportunidad: Local o Empate
    val dcX2: Double? = null,        // Doble Oportunidad: Visita o Empate
    val dc12: Double? = null,        // Doble Oportunidad: Local o Visita
    val bttsYes: Double? = null,     // Ambos marcan SÍ
    val bttsNo: Double? = null,      // Ambos marcan NO
    val over15Goals: Double? = null,
    val under15Goals: Double? = null,
    val over25Goals: Double? = null,
    val under25Goals: Double? = null,
    val over35Goals: Double? = null,
    val under35Goals: Double? = null,
    val cornerLineVal: Double? = null,
    val overCornerLine: Double? = null,
    val underCornerLine: Double? = null
)

data class DoubleChanceFavoriteAnalysis(
    val favoriteTeam: String,
    val underdogTeam: String,
    val isHomeFavorite: Boolean,
    val pickCode: String,          // "1X" o "X2"
    val pickTitle: String,         // Ej: "Vélez Sarsfield o Empate (1X)"
    val realOdds: Double?,         // Cuota real Bzzoiro para 1X o X2
    val modelProb: Double,         // Probabilidad del Modelo (P(Fav) + P(Draw))
    val fairOdds: Double,          // 1 / modelProb
    val expectedValue: Double?,    // (modelProb * realOdds) - 1.0
    val confidenceBadge: String,
    val analysisDescription: String
)

data class PlayerShotProp(
    val playerName: String,
    val teamName: String,
    val rolePosition: String,
    val totalShotsLambda: Double,
    val shotsOnTargetLambda: Double,
    val pickTotalShots: String,
    val probTotalShots: Double,
    val pickOnTarget: String,
    val probOnTarget: Double,
    val fairOddsOnTarget: Double
)

data class ParlayLeg(
    val category: String,
    val pick: String,
    val lambda: Double?,
    val modelProb: Double,
    val fairOdds: Double,
    val realMarketOdds: Double?,
    val edgePercent: Double?,
    val isSafePick: Boolean = false
)

data class QuantumMatchAnalysis(
    val event: BzzoiroEvent,
    val odds: RealOdds,
    val lambdaHome: Double,
    val lambdaAway: Double,
    val lambdaTotal: Double,
    val lambdaCorners: Double,
    val lambdaCards: Double,
    val probHome: Double,
    val probDraw: Double,
    val probAway: Double,
    val probOver15: Double,
    val probOver25: Double,
    val probOver35: Double,
    val probBTTS: Double,
    val probOverCornerLine: Double,
    val mostProbableScore: String,
    val mostProbableScoreProb: Double,
    val doubleChanceAnalysis: DoubleChanceFavoriteAnalysis,
    val playerShotProps: List<PlayerShotProp>,
    val parlayLegs: List<ParlayLeg>,
    val jointParlayProb: Double,
    val parlayFairOdds: Double,
    val parlayRealOdds: Double?,
    val parlayExpectedValue: Double?,
    val kellyStakePercent: Double
)

// ==============================================================================
// 2. MOTORES MATEMÁTICOS (POISSON, BIVARIADA & VALUE BETTING)
// ==============================================================================

object QuantumMathEngine {

    fun factorial(n: Int): Double {
        if (n <= 1) return 1.0
        var res = 1.0
        for (i in 2..n) res *= i
        return res
    }

    fun poissonPmf(k: Int, lambda: Double): Double {
        if (lambda <= 0.0) return if (k == 0) 1.0 else 0.0
        return (lambda.pow(k) * exp(-lambda)) / factorial(k)
    }

    fun poissonCdf(k: Int, lambda: Double): Double {
        var sum = 0.0
        for (i in 0..k) {
            sum += poissonPmf(i, lambda)
        }
        return sum.coerceIn(0.0, 1.0)
    }

    fun computeGoalMatrix(lambdaHome: Double, lambdaAway: Double, maxGoals: Int = 5): Array<DoubleArray> {
        val matrix = Array(maxGoals + 1) { DoubleArray(maxGoals + 1) }
        for (i in 0..maxGoals) {
            for (j in 0..maxGoals) {
                matrix[i][j] = poissonPmf(i, lambdaHome) * poissonPmf(j, lambdaAway)
            }
        }
        return matrix
    }

    fun analyzeDoubleChanceFavorite(
        homeTeam: String,
        awayTeam: String,
        pHome: Double,
        pDraw: Double,
        pAway: Double,
        odds: RealOdds
    ): DoubleChanceFavoriteAnalysis {
        val homeOdds = odds.home1x2
        val awayOdds = odds.away1x2

        val isHomeFav = when {
            homeOdds != null && awayOdds != null -> homeOdds <= awayOdds
            else -> pHome >= pAway
        }

        val favTeam = if (isHomeFav) homeTeam else awayTeam
        val dogTeam = if (isHomeFav) awayTeam else homeTeam
        val pickCode = if (isHomeFav) "1X" else "X2"
        val pickTitle = "$favTeam o Empate ($pickCode)"

        val modelProb = if (isHomeFav) (pHome + pDraw) else (pAway + pDraw)
        val fairOdds = if (modelProb > 0.0) 1.0 / modelProb else 1.0
        val realOdds = if (isHomeFav) odds.dc1X else odds.dcX2

        val ev = if (realOdds != null && realOdds > 1.0) {
            (modelProb * realOdds) - 1.0
        } else null

        val badge = when {
            ev != null && ev > 0.05 -> "VALOR POSITIVO (+${(ev * 100).toInt()}%)"
            modelProb >= 0.80 -> "ULTRA SEGURO (${(modelProb * 100).toInt()}%)"
            modelProb >= 0.70 -> "ALTA COBERTURA"
            else -> "COBERTURA FAVORITO"
        }

        val desc = if (isHomeFav) {
            "$favTeam juega de local y ostenta la mayor probabilidad de triunfo (${(pHome * 100).toInt()}%). La cobertura 1X asegura el resultado ante victoria local o igualdad (${(pDraw * 100).toInt()}%)."
        } else {
            "$favTeam domina como visitante (${(pAway * 100).toInt()}%). La cobertura X2 neutraliza el riesgo cubriendo victoria visitante y empate (${(pDraw * 100).toInt()}%)."
        }

        return DoubleChanceFavoriteAnalysis(
            favoriteTeam = favTeam,
            underdogTeam = dogTeam,
            isHomeFavorite = isHomeFav,
            pickCode = pickCode,
            pickTitle = pickTitle,
            realOdds = realOdds,
            modelProb = modelProb,
            fairOdds = fairOdds,
            expectedValue = ev,
            confidenceBadge = badge,
            analysisDescription = desc
        )
    }

    fun generatePlayerProps(
        homeTeam: String,
        awayTeam: String,
        lambdaHome: Double,
        lambdaAway: Double
    ): List<PlayerShotProp> {
        val list = mutableListOf<PlayerShotProp>()

        // Delantero Local
        val homeFwdLambdaShots = (lambdaHome * 1.85).coerceIn(1.8, 3.8)
        val homeFwdLambdaSoT = (homeFwdLambdaShots * 0.45).coerceIn(0.8, 1.9)
        val homeFwdProbSoT1 = 1.0 - exp(-homeFwdLambdaSoT)
        val homeFwdProbSoT15 = 1.0 - (poissonPmf(0, homeFwdLambdaSoT) + poissonPmf(1, homeFwdLambdaSoT))
        val (pickH, probH) = if (homeFwdLambdaSoT >= 1.25) {
            Pair("Más de 1.5 al Arco", homeFwdProbSoT15)
        } else {
            Pair("Más de 0.5 al Arco", homeFwdProbSoT1)
        }
        val probShotsH = 1.0 - poissonCdf(2, homeFwdLambdaShots)

        list.add(
            PlayerShotProp(
                playerName = "Goleador / 9 de Área",
                teamName = homeTeam,
                rolePosition = "Delantero Centro",
                totalShotsLambda = homeFwdLambdaShots,
                shotsOnTargetLambda = homeFwdLambdaSoT,
                pickTotalShots = "Más de 2.5 Remates",
                probTotalShots = probShotsH,
                pickOnTarget = pickH,
                probOnTarget = probH,
                fairOddsOnTarget = (1.0 / probH).coerceAtLeast(1.05)
            )
        )

        // Extremo Local
        val homeWingLambdaShots = (lambdaHome * 1.30).coerceIn(1.3, 2.7)
        val homeWingLambdaSoT = (homeWingLambdaShots * 0.38).coerceIn(0.6, 1.3)
        val homeWingProbSoT = 1.0 - exp(-homeWingLambdaSoT)
        val probShotsHWing = 1.0 - poissonCdf(1, homeWingLambdaShots)

        list.add(
            PlayerShotProp(
                playerName = "Extremo Ofensivo / Ext",
                teamName = homeTeam,
                rolePosition = "Extremo / Delantero Secundario",
                totalShotsLambda = homeWingLambdaShots,
                shotsOnTargetLambda = homeWingLambdaSoT,
                pickTotalShots = "Más de 1.5 Remates",
                probTotalShots = probShotsHWing,
                pickOnTarget = "Más de 0.5 al Arco",
                probOnTarget = homeWingProbSoT,
                fairOddsOnTarget = (1.0 / homeWingProbSoT).coerceAtLeast(1.05)
            )
        )

        // Delantero Visitante
        val awayFwdLambdaShots = (lambdaAway * 1.75).coerceIn(1.5, 3.5)
        val awayFwdLambdaSoT = (awayFwdLambdaShots * 0.42).coerceIn(0.7, 1.7)
        val awayFwdProbSoT1 = 1.0 - exp(-awayFwdLambdaSoT)
        val awayFwdProbSoT15 = 1.0 - (poissonPmf(0, awayFwdLambdaSoT) + poissonPmf(1, awayFwdLambdaSoT))
        val (pickA, probA) = if (awayFwdLambdaSoT >= 1.25) {
            Pair("Más de 1.5 al Arco", awayFwdProbSoT15)
        } else {
            Pair("Más de 0.5 al Arco", awayFwdProbSoT1)
        }
        val probShotsA = 1.0 - poissonCdf(2, awayFwdLambdaShots)

        list.add(
            PlayerShotProp(
                playerName = "Artillero Visitante",
                teamName = awayTeam,
                rolePosition = "Delantero Centro",
                totalShotsLambda = awayFwdLambdaShots,
                shotsOnTargetLambda = awayFwdLambdaSoT,
                pickTotalShots = "Más de 2.5 Remates",
                probTotalShots = probShotsA,
                pickOnTarget = pickA,
                probOnTarget = probA,
                fairOddsOnTarget = (1.0 / probA).coerceAtLeast(1.05)
            )
        )

        return list
    }

    fun analyzeEvent(event: BzzoiroEvent, odds: RealOdds): QuantumMatchAnalysis {
        val h2hAvg = if (event.h2hAvgTotalGoals > 0.5) event.h2hAvgTotalGoals else 2.55
        val homeAdvantage = 1.15

        val homeShare = if (event.h2hTotalMatches > 0) {
            (event.h2hHomeWinRate * 0.6) + 0.35
        } else 0.53

        val lambdaHome = ((h2hAvg * homeShare) * homeAdvantage).coerceIn(0.6, 3.8)
        val lambdaAway = (h2hAvg - (lambdaHome / homeAdvantage)).coerceIn(0.5, 3.2)
        val lambdaTotal = lambdaHome + lambdaAway

        val cornerLine = odds.cornerLineVal ?: 9.5
        val lambdaCorners = 9.85
        val lambdaCards = 4.30

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

        val pOverCorners = 1.0 - poissonCdf(cornerLine.toInt(), lambdaCorners)

        val dcAnalysis = analyzeDoubleChanceFavorite(
            homeTeam = event.homeTeam,
            awayTeam = event.awayTeam,
            pHome = pHome,
            pDraw = pDraw,
            pAway = pAway,
            odds = odds
        )

        val playerProps = generatePlayerProps(event.homeTeam, event.awayTeam, lambdaHome, lambdaAway)
        val legs = mutableListOf<ParlayLeg>()

        // 1. Doble Oportunidad Favorito o Empate
        val dcEv = dcAnalysis.expectedValue
        legs.add(
            ParlayLeg(
                category = "DOBLE OPORTUNIDAD",
                pick = dcAnalysis.pickTitle,
                lambda = null,
                modelProb = dcAnalysis.modelProb,
                fairOdds = dcAnalysis.fairOdds,
                realMarketOdds = dcAnalysis.realOdds,
                edgePercent = if (dcEv != null) dcEv * 100.0 else null,
                isSafePick = dcAnalysis.modelProb >= 0.70
            )
        )

        // 2. Goles
        if (pOver15 >= 0.72) {
            val fairO15 = 1.0 / pOver15
            val realO15 = odds.over15Goals
            val evO15 = if (realO15 != null) (pOver15 * realO15) - 1.0 else null
            legs.add(
                ParlayLeg(
                    category = "TOTAL GOLES",
                    pick = "Más de 1.5 Goles",
                    lambda = lambdaTotal,
                    modelProb = pOver15,
                    fairOdds = fairO15,
                    realMarketOdds = realO15,
                    edgePercent = if (evO15 != null) evO15 * 100.0 else null,
                    isSafePick = pOver15 >= 0.75
                )
            )
        } else {
            val pUnder35 = 1.0 - pOver35
            val fairU35 = 1.0 / pUnder35
            val realU35 = odds.under35Goals
            val evU35 = if (realU35 != null) (pUnder35 * realU35) - 1.0 else null
            legs.add(
                ParlayLeg(
                    category = "TOTAL GOLES",
                    pick = "Menos de 3.5 Goles",
                    lambda = lambdaTotal,
                    modelProb = pUnder35,
                    fairOdds = fairU35,
                    realMarketOdds = realU35,
                    edgePercent = if (evU35 != null) evU35 * 100.0 else null,
                    isSafePick = pUnder35 >= 0.70
                )
            )
        }

        // 3. Remates al Arco
        val mainFwd = playerProps.firstOrNull()
        if (mainFwd != null) {
            legs.add(
                ParlayLeg(
                    category = "REMATES AL ARCO",
                    pick = "${mainFwd.playerName}: ${mainFwd.pickOnTarget}",
                    lambda = mainFwd.shotsOnTargetLambda,
                    modelProb = mainFwd.probOnTarget,
                    fairOdds = mainFwd.fairOddsOnTarget,
                    realMarketOdds = null,
                    edgePercent = 4.5,
                    isSafePick = mainFwd.probOnTarget >= 0.65
                )
            )
        }

        // 4. Córners
        if (pOverCorners >= 0.55) {
            val fairCorn = 1.0 / pOverCorners
            val realCorn = odds.overCornerLine
            val evCorn = if (realCorn != null) (pOverCorners * realCorn) - 1.0 else null
            legs.add(
                ParlayLeg(
                    category = "CÓRNERS",
                    pick = "Más de ${cornerLine.toInt()}.5 Córners",
                    lambda = lambdaCorners,
                    modelProb = pOverCorners,
                    fairOdds = fairCorn,
                    realMarketOdds = realCorn,
                    edgePercent = if (evCorn != null) evCorn * 100.0 else null,
                    isSafePick = false
                )
            )
        }

        var jointProb = 1.0
        var cumFairOdds = 1.0
        var cumRealOdds: Double? = 1.0
        var hasMissingRealOdds = false

        for (l in legs) {
            jointProb *= l.modelProb
            cumFairOdds *= l.fairOdds
            if (l.realMarketOdds != null) {
                cumRealOdds = (cumRealOdds ?: 1.0) * l.realMarketOdds
            } else {
                hasMissingRealOdds = true
            }
        }
        jointProb = (jointProb * 1.08).coerceIn(0.01, 0.99)
        cumFairOdds = (1.0 / jointProb).coerceAtLeast(1.10)

        val finalRealOdds = if (!hasMissingRealOdds && (cumRealOdds ?: 0.0) > 1.0) cumRealOdds else null
        val parlayEV = if (finalRealOdds != null) {
            (jointProb * finalRealOdds) - 1.0
        } else null

        val kelly = if (finalRealOdds != null && parlayEV != null && parlayEV > 0.0) {
            val b = finalRealOdds - 1.0
            val p = jointProb
            val q = 1.0 - p
            val f = ((b * p) - q) / b
            (max(0.0, f) * 0.25 * 100.0).coerceIn(0.5, 5.0)
        } else 1.5

        return QuantumMatchAnalysis(
            event = event,
            odds = odds,
            lambdaHome = lambdaHome,
            lambdaAway = lambdaAway,
            lambdaTotal = lambdaTotal,
            lambdaCorners = lambdaCorners,
            lambdaCards = lambdaCards,
            probHome = pHome,
            probDraw = pDraw,
            probAway = pAway,
            probOver15 = pOver15,
            probOver25 = pOver25,
            probOver35 = pOver35,
            probBTTS = pBTTS,
            probOverCornerLine = pOverCorners,
            mostProbableScore = bestScore,
            mostProbableScoreProb = bestScoreP,
            doubleChanceAnalysis = dcAnalysis,
            playerShotProps = playerProps,
            parlayLegs = legs,
            jointParlayProb = jointProb,
            parlayFairOdds = cumFairOdds,
            parlayRealOdds = finalRealOdds,
            parlayExpectedValue = parlayEV,
            kellyStakePercent = kelly
        )
    }
}

// ==============================================================================
// 3. CLIENTE HTTP EXCLUSIVO BZZOIRO SPORTS DATA API
// ==============================================================================

object BzzoiroApiClient {
    private const val BASE_URL = "https://sports.bzzoiro.com/api/v2"
    private const val API_TOKEN = "9665517ed1732bad524c3730e18cd9d90cc1d13f"

    private fun openConnection(endpoint: String): HttpURLConnection {
        val url = URL(if (endpoint.startsWith("http")) endpoint else "$BASE_URL$endpoint")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Token $API_TOKEN")
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

    suspend fun fetchEvents(dateFrom: String, dateTo: String, limit: Int = 30): List<BzzoiroEvent> =
        withContext(Dispatchers.IO) {
            val list = mutableListOf<BzzoiroEvent>()
            try {
                val endpoint = "/events/?date_from=$dateFrom&date_to=$dateTo&limit=$limit"
                val conn = openConnection(endpoint)
                if (conn.responseCode == 200) {
                    val raw = readResponse(conn)
                    val root = JSONObject(raw)
                    val results = root.optJSONArray("results") ?: JSONArray()
                    for (i in 0 until results.length()) {
                        val obj = results.getJSONObject(i)
                        val id = obj.optLong("id")
                        val leagueId = obj.optLong("league_id")
                        val roundLabel = obj.optString("round_label", "Partido Oficial")
                        val homeTeam = obj.optString("home_team", "Local")
                        val awayTeam = obj.optString("away_team", "Visitante")
                        val eventDate = obj.optString("event_date", "")
                        val status = obj.optString("status", "notstarted")
                        val period = obj.optString("period", "")
                        val curMin = if (obj.has("current_minute") && !obj.isNull("current_minute")) {
                            obj.optInt("current_minute")
                        } else null
                        val homeSc = if (obj.has("home_score") && !obj.isNull("home_score")) {
                            obj.optInt("home_score")
                        } else null
                        val awaySc = if (obj.has("away_score") && !obj.isNull("away_score")) {
                            obj.optInt("away_score")
                        } else null

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
                                for (k in 0 until recArr.length().coerceAtMost(4)) {
                                    val r = recArr.getJSONObject(k)
                                    val sc = r.optString("score", "")
                                    val hT = r.optString("home", "")
                                    val aT = r.optString("away", "")
                                    if (sc.isNotEmpty()) {
                                        recentScores.add("$hT $sc $aT")
                                    }
                                }
                            }
                        }

                        list.add(
                            BzzoiroEvent(
                                id = id,
                                leagueId = leagueId,
                                roundLabel = roundLabel,
                                homeTeam = homeTeam,
                                awayTeam = awayTeam,
                                eventDate = eventDate,
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
                                recentScores = recentScores
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

    suspend fun fetchEventOdds(eventId: Long): RealOdds = withContext(Dispatchers.IO) {
        var odds = RealOdds()
        try {
            val endpoint = "/odds/?event_id=$eventId&limit=100"
            val conn = openConnection(endpoint)
            if (conn.responseCode == 200) {
                val raw = readResponse(conn)
                val root = JSONObject(raw)
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
                var cornOver: Double? = null
                var cornUnder: Double? = null

                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    val market = item.optString("market", "")
                    val outcome = item.optString("outcome", "")
                    val decOdds = item.optDouble("decimal_odds", -1.0)
                    if (decOdds <= 1.0) continue

                    when (market) {
                        "1x2" -> {
                            when (outcome.uppercase()) {
                                "HOME" -> home1x2 = decOdds
                                "DRAW" -> draw1x2 = decOdds
                                "AWAY" -> away1x2 = decOdds
                            }
                        }
                        "double_chance" -> {
                            when (outcome.uppercase()) {
                                "1X" -> dc1X = decOdds
                                "X2" -> dcX2 = decOdds
                                "12" -> dc12 = decOdds
                            }
                        }
                        "btts" -> {
                            when (outcome.lowercase()) {
                                "yes" -> bttsY = decOdds
                                "no" -> bttsN = decOdds
                            }
                        }
                        "over_under_15" -> {
                            when (outcome.lowercase()) {
                                "over" -> o15 = decOdds
                                "under" -> u15 = decOdds
                            }
                        }
                        "over_under_25" -> {
                            when (outcome.lowercase()) {
                                "over" -> o25 = decOdds
                                "under" -> u25 = decOdds
                            }
                        }
                        "over_under_35" -> {
                            when (outcome.lowercase()) {
                                "over" -> o35 = decOdds
                                "under" -> u35 = decOdds
                            }
                        }
                        "total_corners" -> {
                            val line = item.optDouble("line", 9.5)
                            cornLine = line
                            if (outcome.lowercase() == "over") cornOver = decOdds
                            else if (outcome.lowercase() == "under") cornUnder = decOdds
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
                    overCornerLine = cornOver,
                    underCornerLine = cornUnder
                )
            }
            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        odds
    }
}

// ==============================================================================
// 4. VIEWMODEL DE ESTADO CUANTITATIVO
// ==============================================================================

class FootballStatsViewModel : ViewModel() {

    var isLoading by mutableStateOf(false)
        private set

    var eventsList by mutableStateOf<List<BzzoiroEvent>>(emptyList())
        private set

    var selectedEvent by mutableStateOf<BzzoiroEvent?>(null)
        private set

    var currentOdds by mutableStateOf<RealOdds>(RealOdds())
        private set

    var currentAnalysis by mutableStateOf<QuantumMatchAnalysis?>(null)
        private set

    var statusMessage by mutableStateOf("Conectando con Bzzoiro Sports API v2...")
        private set

    var activeFilter by mutableStateOf("PROXIMOS")
        private set

    init {
        loadEvents()
    }

    fun setFilter(filter: String) {
        activeFilter = filter
        loadEvents()
    }

    fun loadEvents() {
        viewModelScope.launch {
            isLoading = true
            statusMessage = "Consultando Bzzoiro API v2..."
            try {
                val now = System.currentTimeMillis()
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val todayStr = sdf.format(Date(now))
                val futureStr = when (activeFilter) {
                    "HOY" -> sdf.format(Date(now + 86400000L))
                    else -> sdf.format(Date(now + 7 * 86400000L))
                }

                val events = BzzoiroApiClient.fetchEvents(
                    dateFrom = todayStr,
                    dateTo = futureStr,
                    limit = 35
                )

                if (events.isNotEmpty()) {
                    eventsList = events
                    statusMessage = "Cargados ${events.size} partidos de Bzzoiro"
                    selectEvent(events.first())
                } else {
                    statusMessage = "Ampliando rango temporal Bzzoiro..."
                    val fallbackEvents = BzzoiroApiClient.fetchEvents(
                        dateFrom = todayStr,
                        dateTo = sdf.format(Date(now + 14 * 86400000L)),
                        limit = 20
                    )
                    eventsList = fallbackEvents
                    if (fallbackEvents.isNotEmpty()) {
                        selectEvent(fallbackEvents.first())
                    }
                }
            } catch (e: Exception) {
                statusMessage = "Error al conectar con Bzzoiro: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun selectEvent(event: BzzoiroEvent) {
        selectedEvent = event
        viewModelScope.launch {
            statusMessage = "Cargando cuotas reales para ${event.homeTeam} vs ${event.awayTeam}..."
            val odds = BzzoiroApiClient.fetchEventOdds(event.id)
            currentOdds = odds
            currentAnalysis = QuantumMathEngine.analyzeEvent(event, odds)
            statusMessage = "Análisis cuántico listo (Bzzoiro ID: ${event.id})"
        }
    }
}

// ==============================================================================
// 5. PALETA CYBERPUNK DARK NEON HUD
// ==============================================================================

object CyberpunkTheme {
    val Background = Color(0xFF06070B)
    val SurfaceCard = Color(0xFF0D111A)
    val SurfaceElevated = Color(0xFF141926)
    val BorderSubtle = Color(0xFF1E2638)

    val NeonCyan = Color(0xFF00F0FF)
    val CyberLime = Color(0xFF99FF00)
    val CyberViolet = Color(0xFFB026FF)
    val AmberGold = Color(0xFFFFB703)
    val CrimsonRed = Color(0xFFFF0055)

    val TextPrimary = Color(0xFFF1F5F9)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)
}

// ==============================================================================
// 6. UI JETPACK COMPOSE MATERIAL 3
// ==============================================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = CyberpunkTheme.Background,
                    surface = CyberpunkTheme.SurfaceCard,
                    primary = CyberpunkTheme.NeonCyan,
                    secondary = CyberpunkTheme.CyberLime,
                    tertiary = CyberpunkTheme.CyberViolet
                )
            ) {
                FootballStatsQuantumApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FootballStatsQuantumApp(viewModel: FootballStatsViewModel = remember { FootballStatsViewModel() }) {
    val analysis = viewModel.currentAnalysis
    val selectedEvent = viewModel.selectedEvent

    Scaffold(
        containerColor = CyberpunkTheme.Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(CyberpunkTheme.CyberLime)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "BZZOIRO QUANTUM HUD",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = CyberpunkTheme.NeonCyan,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = "100% Native Bzzoiro Sports API · Sin ESPN",
                                fontSize = 11.sp,
                                color = CyberpunkTheme.TextSecondary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadEvents() }) {
                        Text(
                            text = "⟳",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberpunkTheme.NeonCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CyberpunkTheme.Background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                StatusBarChip(status = viewModel.statusMessage, isLoading = viewModel.isLoading)
            }

            item {
                EventFilterRow(
                    activeFilter = viewModel.activeFilter,
                    onSelect = { viewModel.setFilter(it) }
                )
            }

            item {
                Text(
                    text = "PARTIDOS OFICIALES DISPONIBLES (${viewModel.eventsList.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberpunkTheme.TextSecondary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(viewModel.eventsList) { ev ->
                        MatchPillCard(
                            event = ev,
                            isSelected = ev.id == selectedEvent?.id,
                            onClick = { viewModel.selectEvent(ev) }
                        )
                    }
                }
            }

            if (analysis != null) {
                item {
                    MatchHeroCard(analysis.event)
                }

                // DESTACADO: DOBLE OPORTUNIDAD (FAVORITO O EMPATE)
                item {
                    DoubleChanceHeroCard(analysis.doubleChanceAnalysis)
                }

                item {
                    RealOddsGridCard(analysis.odds, analysis.probHome, analysis.probDraw, analysis.probAway)
                }

                item {
                    PlayerShotsSection(analysis.playerShotProps)
                }

                item {
                    QuantumParlayCard(analysis)
                }

                item {
                    PoissonHeadToHeadCard(analysis)
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            } else if (viewModel.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = CyberpunkTheme.NeonCyan)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBarChip(status: String, isLoading: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CyberpunkTheme.SurfaceElevated),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, CyberpunkTheme.BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isLoading) CyberpunkTheme.AmberGold else CyberpunkTheme.CyberLime)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = status,
                fontSize = 11.sp,
                color = CyberpunkTheme.TextSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
fun EventFilterRow(activeFilter: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterButton(
            label = "PRÓXIMOS 7 DÍAS",
            isActive = activeFilter == "PROXIMOS",
            onClick = { onSelect("PROXIMOS") },
            modifier = Modifier.weight(1f)
        )
        FilterButton(
            label = "PARTIDOS HOY",
            isActive = activeFilter == "HOY",
            onClick = { onSelect("HOY") },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun FilterButton(label: String, isActive: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) CyberpunkTheme.NeonCyan else CyberpunkTheme.SurfaceElevated,
            contentColor = if (isActive) CyberpunkTheme.Background else CyberpunkTheme.TextSecondary
        ),
        border = BorderStroke(1.dp, if (isActive) CyberpunkTheme.NeonCyan else CyberpunkTheme.BorderSubtle),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun MatchPillCard(event: BzzoiroEvent, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(200.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CyberpunkTheme.SurfaceElevated else CyberpunkTheme.SurfaceCard
        ),
        border = BorderStroke(
            1.5.dp,
            if (isSelected) CyberpunkTheme.NeonCyan else CyberpunkTheme.BorderSubtle
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = event.roundLabel,
                fontSize = 10.sp,
                color = CyberpunkTheme.TextMuted,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${event.homeTeam} vs ${event.awayTeam}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) CyberpunkTheme.NeonCyan else CyberpunkTheme.TextPrimary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (event.status == "finished") "FINALIZADO" else "PROGRAMADO",
                    fontSize = 9.sp,
                    color = if (event.status == "finished") CyberpunkTheme.AmberGold else CyberpunkTheme.CyberLime,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ID: ${event.id}",
                    fontSize = 9.sp,
                    color = CyberpunkTheme.TextMuted
                )
            }
        }
    }
}

@Composable
fun MatchHeroCard(event: BzzoiroEvent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CyberpunkTheme.SurfaceCard),
        border = BorderStroke(1.dp, CyberpunkTheme.BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = event.roundLabel.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberpunkTheme.NeonCyan
                )
                Text(
                    text = "Bzzoiro API v2",
                    fontSize = 10.sp,
                    color = CyberpunkTheme.TextMuted
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(
                        text = event.homeTeam,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = CyberpunkTheme.TextPrimary
                    )
                    Text(text = "LOCAL", fontSize = 10.sp, color = CyberpunkTheme.TextMuted)
                }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(CyberpunkTheme.SurfaceElevated)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    val scoreText = if (event.homeScore != null && event.awayScore != null) {
                        "${event.homeScore} - ${event.awayScore}"
                    } else "VS"
                    Text(
                        text = scoreText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = CyberpunkTheme.NeonCyan
                    )
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = event.awayTeam,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = CyberpunkTheme.TextPrimary,
                        textAlign = TextAlign.End
                    )
                    Text(text = "VISITANTE", fontSize = 10.sp, color = CyberpunkTheme.TextMuted)
                }
            }
        }
    }
}

@Composable
fun DoubleChanceHeroCard(analysis: DoubleChanceFavoriteAnalysis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CyberpunkTheme.SurfaceElevated),
        border = BorderStroke(1.5.dp, CyberpunkTheme.AmberGold)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚡ DOBLE OPORTUNIDAD",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = CyberpunkTheme.AmberGold,
                    letterSpacing = 1.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CyberpunkTheme.AmberGold.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = analysis.confidenceBadge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberpunkTheme.AmberGold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "FAVORITO DETERMINADO O EMPATE:",
                fontSize = 10.sp,
                color = CyberpunkTheme.TextMuted,
                letterSpacing = 0.8.sp
            )
            Text(
                text = analysis.pickTitle,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = CyberpunkTheme.TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricBox(
                    label = "PROB. MODELO",
                    value = "${(analysis.modelProb * 100).toInt()}%",
                    accentColor = CyberpunkTheme.CyberLime
                )
                MetricBox(
                    label = "CUOTA JUSTA",
                    value = String.format(Locale.US, "%.2f", analysis.fairOdds),
                    accentColor = CyberpunkTheme.TextPrimary
                )
                MetricBox(
                    label = "CUOTA BZZOIRO",
                    value = if (analysis.realOdds != null) String.format(Locale.US, "%.2f", analysis.realOdds) else "N/D",
                    accentColor = if (analysis.realOdds != null) CyberpunkTheme.AmberGold else CyberpunkTheme.TextMuted
                )
                MetricBox(
                    label = "VALOR (EV)",
                    value = if (analysis.expectedValue != null) {
                        "${if (analysis.expectedValue > 0) "+" else ""}${(analysis.expectedValue * 100).toInt()}%"
                    } else "ESTIMADO",
                    accentColor = if (analysis.expectedValue != null && analysis.expectedValue > 0) CyberpunkTheme.CyberLime else CyberpunkTheme.TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = analysis.analysisDescription,
                fontSize = 11.sp,
                color = CyberpunkTheme.TextSecondary,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun MetricBox(label: String, value: String, accentColor: Color) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CyberpunkTheme.SurfaceCard)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, fontSize = 9.sp, color = CyberpunkTheme.TextMuted)
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            color = accentColor
        )
    }
}

@Composable
fun RealOddsGridCard(odds: RealOdds, pHome: Double, pDraw: Double, pAway: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CyberpunkTheme.SurfaceCard),
        border = BorderStroke(1.dp, CyberpunkTheme.BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "MERCADOS & CUOTAS BZZOIRO OFICIALES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CyberpunkTheme.NeonCyan,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            Text(text = "MERCADO 1X2 (GANADOR DEL PARTIDO)", fontSize = 10.sp, color = CyberpunkTheme.TextMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OddsPill(title = "Local (1)", odds = odds.home1x2, prob = pHome, modifier = Modifier.weight(1f))
                OddsPill(title = "Empate (X)", odds = odds.draw1x2, prob = pDraw, modifier = Modifier.weight(1f))
                OddsPill(title = "Visita (2)", odds = odds.away1x2, prob = pAway, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(text = "DOBLE OPORTUNIDAD (1X, 12, X2)", fontSize = 10.sp, color = CyberpunkTheme.TextMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OddsPill(title = "1X (Local/Empate)", odds = odds.dc1X, prob = pHome + pDraw, modifier = Modifier.weight(1f))
                OddsPill(title = "12 (Local/Visita)", odds = odds.dc12, prob = pHome + pAway, modifier = Modifier.weight(1f))
                OddsPill(title = "X2 (Visita/Empate)", odds = odds.dcX2, prob = pAway + pDraw, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(text = "LÍNEAS DE GOLES Y AMBOS MARCAN", fontSize = 10.sp, color = CyberpunkTheme.TextMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OddsPill(title = "+1.5 Goles", odds = odds.over15Goals, prob = null, modifier = Modifier.weight(1f))
                OddsPill(title = "+2.5 Goles", odds = odds.over25Goals, prob = null, modifier = Modifier.weight(1f))
                OddsPill(title = "Ambos Marcan (SÍ)", odds = odds.bttsYes, prob = null, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun OddsPill(title: String, odds: Double?, prob: Double?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CyberpunkTheme.SurfaceElevated)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, fontSize = 9.sp, color = CyberpunkTheme.TextMuted, maxLines = 1)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (odds != null) String.format(Locale.US, "%.2f", odds) else "N/D",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (odds != null) CyberpunkTheme.NeonCyan else CyberpunkTheme.TextMuted
        )
        if (prob != null) {
            Text(
                text = "${(prob * 100).toInt()}% Poisson",
                fontSize = 8.sp,
                color = CyberpunkTheme.TextSecondary
            )
        }
    }
}

@Composable
fun PlayerShotsSection(props: List<PlayerShotProp>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CyberpunkTheme.SurfaceCard),
        border = BorderStroke(1.dp, CyberpunkTheme.BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎯 REMATES Y REMATES AL ARCO",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberpunkTheme.CyberLime,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Proyecciones Ofensivas",
                    fontSize = 10.sp,
                    color = CyberpunkTheme.TextMuted
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            props.forEach { p ->
                PlayerShotCard(p)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun PlayerShotCard(prop: PlayerShotProp) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CyberpunkTheme.SurfaceElevated),
        border = BorderStroke(1.dp, CyberpunkTheme.BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = prop.playerName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberpunkTheme.TextPrimary
                    )
                    Text(
                        text = "${prop.teamName} · ${prop.rolePosition}",
                        fontSize = 10.sp,
                        color = CyberpunkTheme.TextMuted
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CyberpunkTheme.CyberLime.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "λ Remates: ${String.format(Locale.US, "%.1f", prop.totalShotsLambda)}",
                        fontSize = 9.sp,
                        color = CyberpunkTheme.CyberLime,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(CyberpunkTheme.SurfaceCard)
                        .padding(8.dp)
                ) {
                    Text(text = "REMATES AL ARCO", fontSize = 8.sp, color = CyberpunkTheme.TextMuted)
                    Text(
                        text = prop.pickOnTarget,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberpunkTheme.CyberLime
                    )
                    Text(
                        text = "Prob: ${(prop.probOnTarget * 100).toInt()}% · Cuota: ${String.format(Locale.US, "%.2f", prop.fairOddsOnTarget)}",
                        fontSize = 9.sp,
                        color = CyberpunkTheme.TextSecondary
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(CyberpunkTheme.SurfaceCard)
                        .padding(8.dp)
                ) {
                    Text(text = "REMATES TOTALES", fontSize = 8.sp, color = CyberpunkTheme.TextMuted)
                    Text(
                        text = prop.pickTotalShots,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberpunkTheme.NeonCyan
                    )
                    Text(
                        text = "Prob: ${(prop.probTotalShots * 100).toInt()}%",
                        fontSize = 9.sp,
                        color = CyberpunkTheme.TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun QuantumParlayCard(analysis: QuantumMatchAnalysis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CyberpunkTheme.SurfaceCard),
        border = BorderStroke(1.5.dp, CyberpunkTheme.CyberViolet)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "✨ PARLAY CUÁNTICO MULTI-PATA",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = CyberpunkTheme.CyberViolet,
                    letterSpacing = 1.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CyberpunkTheme.CyberViolet.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Kelly: ${String.format(Locale.US, "%.1f", analysis.kellyStakePercent)}%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberpunkTheme.CyberViolet
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            analysis.parlayLegs.forEachIndexed { idx, leg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${idx + 1}. [${leg.category}] ${leg.pick}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberpunkTheme.TextPrimary
                        )
                        Text(
                            text = "Prob: ${(leg.modelProb * 100).toInt()}% · Cuota Justa: ${String.format(Locale.US, "%.2f", leg.fairOdds)}",
                            fontSize = 10.sp,
                            color = CyberpunkTheme.TextMuted
                        )
                    }
                    if (leg.isSafePick) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(CyberpunkTheme.CyberLime.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = "SEGURO", fontSize = 8.sp, color = CyberpunkTheme.CyberLime, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CyberpunkTheme.SurfaceElevated)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "PROB. CONJUNTA", fontSize = 9.sp, color = CyberpunkTheme.TextMuted)
                    Text(
                        text = "${(analysis.jointParlayProb * 100).toInt()}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = CyberpunkTheme.CyberLime
                    )
                }
                Column {
                    Text(text = "CUOTA TOTAL", fontSize = 9.sp, color = CyberpunkTheme.TextMuted)
                    Text(
                        text = if (analysis.parlayRealOdds != null) {
                            String.format(Locale.US, "%.2f (Bzzoiro)", analysis.parlayRealOdds)
                        } else {
                            String.format(Locale.US, "%.2f (Justa)", analysis.parlayFairOdds)
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = CyberpunkTheme.CyberViolet
                    )
                }
                Column {
                    Text(text = "STAKE KELLY", fontSize = 9.sp, color = CyberpunkTheme.TextMuted)
                    Text(
                        text = "${String.format(Locale.US, "%.1f", analysis.kellyStakePercent)}% Bank",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = CyberpunkTheme.AmberGold
                    )
                }
            }
        }
    }
}

@Composable
fun PoissonHeadToHeadCard(analysis: QuantumMatchAnalysis) {
    val ev = analysis.event
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CyberpunkTheme.SurfaceCard),
        border = BorderStroke(1.dp, CyberpunkTheme.BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📊 HISTORIAL BZZOIRO & PARÁMETROS POISSON",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CyberpunkTheme.NeonCyan,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Historial Directo H2H: ${ev.h2hTotalMatches} enfrentamientos registrados.",
                fontSize = 11.sp,
                color = CyberpunkTheme.TextSecondary
            )
            Text(
                text = "Victorias Local: ${ev.h2hHomeWins} · Empates: ${ev.h2hDraws} · Victorias Visita: ${ev.h2hAwayWins}",
                fontSize = 11.sp,
                color = CyberpunkTheme.TextMuted
            )
            Text(
                text = "Media de Goles Bzzoiro: ${String.format(Locale.US, "%.2f", ev.h2hAvgTotalGoals)} p/partido",
                fontSize = 11.sp,
                color = CyberpunkTheme.TextMuted
            )

            if (ev.recentScores.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Últimos Marcadores Registrados:", fontSize = 10.sp, color = CyberpunkTheme.TextMuted)
                ev.recentScores.forEach { sc ->
                    Text(text = "• $sc", fontSize = 10.sp, color = CyberpunkTheme.NeonCyan)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "Marcador más probable: ${analysis.mostProbableScore} (${(analysis.mostProbableScoreProb * 100).toInt()}%)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberpunkTheme.CyberLime
                )
            }
        }
    }
}
