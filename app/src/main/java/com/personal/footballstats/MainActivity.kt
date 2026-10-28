package com.personal.footballstats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round

// =============================================================
// 1. MODELOS DE DATOS (ESTRUCTURA UNIFICADA)
// =============================================================
data class ParlayLeg(
    val category: String,
    val pick: String,           // Ej: "MÁS de 4.5 Tarjetas", "MENOS de 9.5 Córners"
    val modelProb: Double       // Probabilidad real (0.0 a 1.0)
)

data class MathematicalParlay(
    val legs: List<ParlayLeg>,
    val jointProb: Double,       // Probabilidad conjunta real
    val isQualified: Boolean,
    val statusText: String
)

data class MatchUi(
    val id: String,
    val h: String,
    val a: String,
    val hForm: List<Char>,
    val aForm: List<Char>,
    val sc: String,
    val lg: String,
    val tag: String,
    val st: String,
    val live: Boolean,
    val dt: String,
    val fav: String,
    val cardsAvg: String,       // Promedio o "N/D"
    val cornersAvg: String,     // Promedio o "N/D"
    val odds: Triple<String, String, String>, // "N/D" si no hay
    val parlay: MathematicalParlay
)

data class League(val code: String, val name: String, val tag: String)

// =============================================================
// 2. MOTOR MATEMÁTICO (PARLAY ENGINE) - CERO DATOS INVENTADOS
// =============================================================
object ParlayEngine {

    fun poissonPmf(k: Int, lambda: Double): Double {
        if (lambda <= 0.0) return 0.0
        var fact = 1.0
        for (i in 1..k) fact *= i.toDouble()
        return (lambda.pow(k.toDouble()) * exp(-lambda)) / fact
    }

    fun poissonOver(line: Double, lambda: Double): Double {
        val k = line.toInt()
        var sum = 0.0
        for (i in 0..k) sum += poissonPmf(i, lambda)
        return (1.0 - sum).coerceIn(0.0, 1.0)
    }
    
    fun poissonUnder(line: Double, lambda: Double): Double {
        return 1.0 - poissonOver(line, lambda)
    }

    // Función para armar apuestas dinámicas MÁS/MENOS basadas en el promedio real
    fun buildDynamicLeg(category: String, lambda: Double?, baseLine: Double): ParlayLeg? {
        if (lambda == null || lambda <= 0.0) return null
        
        // La línea se ajusta al promedio real (ej: si el promedio es 4.8, la línea es 4.5)
        val line = floor(lambda) + 0.5
        val pOver = poissonOver(line, lambda)
        val pUnder = poissonUnder(line, lambda)

        return if (pOver >= pUnder) {
            ParlayLeg(category, "MÁS de $line $category", pOver)
        } else {
            ParlayLeg(category, "MENOS de $line $category", pUnder)
        }
    }

    fun buildBestParlay(
        homeTeam: String,
        awayTeam: String,
        goalLambdaHome: Double?,
        goalLambdaAway: Double?,
        cardLambda: Double?,
        cornerLambda: Double?,
        playerShotsLambda: Double?
    ): MathematicalParlay {
        val candidates = mutableListOf<ParlayLeg>()
        var favTeam = "N/D"

        // 1. GOLES Y DERIVADOS (BTTS, Doble Op, Favorito)
        if (goalLambdaHome != null && goalLambdaAway != null) {
            val totalGoalsLambda = goalLambdaHome + goalLambdaAway
            val pOver25 = poissonOver(2.5, totalGoalsLambda)
            val pUnder25 = poissonUnder(2.5, totalGoalsLambda)
            candidates.add(if (pOver25 >= pUnder25) ParlayLeg("GOLES", "MÁS de 2.5 Goles", pOver25) else ParlayLeg("GOLES", "MENOS de 2.5 Goles", pUnder25))

            // BTTS
            val pHomeScores = 1.0 - exp(-goalLambdaHome)
            val pAwayScores = 1.0 - exp(-goalLambdaAway)
            val pBtts = pHomeScores * pAwayScores
            candidates.add(if (pBtts >= 0.5) ParlayLeg("BTTS", "AMBOS MARCAN: SÍ", pBtts) else ParlayLeg("BTTS", "AMBOS MARCAN: NO", 1.0 - pBtts))

            // Doble Oportunidad (Aproximación real con probabilidades de Poisson)
            val p1X = pHomeScores + (1.0 - pHomeScores) * (1.0 - pAwayScores)
            val pX2 = pAwayScores + (1.0 - pHomeScores) * (1.0 - pAwayScores)
            if (p1X > pX2) {
                candidates.add(ParlayLeg("DOBLE OP", "1X (Local/Empate)", p1X.coerceIn(0.1, 0.95)))
            } else {
                candidates.add(ParlayLeg("DOBLE OP", "X2 (Visita/Empate)", pX2.coerceIn(0.1, 0.95)))
            }

            // Favorito por probabilidad de gol (real)
            favTeam = if (goalLambdaHome > goalLambdaAway) homeTeam else awayTeam
        }

        // 2. TARJETAS
        buildDynamicLeg("TARJETAS", cardLambda, 4.5)?.let { candidates.add(it) }

        // 3. CÓRNERS
        buildDynamicLeg("CÓRNERS", cornerLambda, 9.5)?.let { candidates.add(it) }

        // 4. JUGADOR (Tiros)
        buildDynamicLeg("JUGADOR", playerShotsLambda, 1.5)?.let { candidates.add(it) }

        // FILTRO ANTI-CORRELACIÓN Y SELECCIÓN POR PROBABILIDAD
        val sortedCandidates = candidates.sortedByDescending { it.modelProb }
        val selectedLegs = mutableListOf<ParlayLeg>()
        var hasGoalOutcome = false

        for (leg in sortedCandidates) {
            if (selectedLegs.size >= 3) break
            if (leg.modelProb <= 0.50) continue // Solo elegir si la probabilidad es mayor al 50%

            val isGoalCat = leg.category in listOf("GOLES", "BTTS", "DOBLE OP")
            if (isGoalCat && hasGoalOutcome) continue // Evita correlación en el mismo ticket
            if (isGoalCat) hasGoalOutcome = true

            if (selectedLegs.any { it.category == leg.category }) continue // No duplicar categoría
            selectedLegs.add(leg)
        }

        var jointProb = 1.0
        selectedLegs.forEach { jointProb *= it.modelProb }
        jointProb = round(jointProb * 1000.0) / 1000.0
        
        val isQualified = jointProb >= 0.15 && selectedLegs.size >= 2
        val statusText = if (isQualified) "PROBABILIDAD CONJUNTA" else "RIESGO ALTO (<15%)"

        return MathematicalParlay(selectedLegs, jointProb, isQualified, statusText)
    }
}

// =============================================================
// 3. VIEWMODEL (ESPN + BZZOIRO API REAL)
// =============================================================
class FootballViewModel : ViewModel() {
    val leagues = listOf(
        League("arg.1","Argentina","ARG"), League("bra.1","Brasil","BRA"),
        League("esp.1","LaLiga","ESP"), League("eng.1","Premier","ENG"),
        League("uefa.champions","Champions","UCL"), League("mex.1","Liga MX","MEX"),
        League("uru.1","Uruguay","URU"), League("fra.1","Ligue 1","FRA"),
        League("por.1","Primeira","POR"), League("usa.1","MLS","MLS")
    )

    var selLeague by mutableStateOf(leagues[0])
        private set
    var matches by mutableStateOf<List<MatchUi>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var errorMsg by mutableStateOf<String?>(null)
        private set

    // ⚠️ PEGÁ TU API KEY DE BZZOIRO ACÁ ADENTRO DE LAS COMILLAS
    private val bzzoiroApiKey = "Token 9665517ed1732bad524c3730e18cd9d90cc1d13f"

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

    private fun parseForm(cp: JSONObject?): List<Char> {
        val raw = cp?.optString("form", "")?.trim()?.uppercase() ?: ""
        if (raw.length >= 5) return raw.takeLast(5).map { if (it in listOf('W', 'D', 'L')) it else 'N' }
        return listOf('N', 'N', 'N', 'N', 'N')
    }

    // CONEXIÓN A BZZOIRO (DATOS REALES)
    private fun getTeamStatsFromBzzoiro(teamName: String): Pair<Double?, Double?> {
        try {
            // 1. Buscamos el ID del equipo en Bzzoiro por su nombre
            val searchUrl = URL("https://sports.bzzoiro.com/api/v2/teams/?search=$teamName")
            val conn1 = searchUrl.openConnection() as HttpURLConnection
            conn1.requestMethod = "GET"
            conn1.setRequestProperty("Authorization", bzzoiroApiKey)
            
            if (conn1.responseCode == 200) {
                val response1 = conn1.inputStream.bufferedReader().readText()
                val json1 = JSONObject(response1)
                val results = json1.optJSONArray("results") ?: json1.optJSONArray("data")
                if (results != null && results.length() > 0) {
                    val teamId = results.getJSONObject(0).optInt("id")
                    
                    // 2. Con el ID, buscamos las estadísticas reales (Form)
                    val formUrl = URL("https://sports.bzzoiro.com/api/v2/teams/$teamId/form/")
                    val conn2 = formUrl.openConnection() as HttpURLConnection
                    conn2.requestMethod = "GET"
                    conn2.setRequestProperty("Authorization", bzzoiroApiKey)
                    
                    if (conn2.responseCode == 200) {
                        val response2 = conn2.inputStream.bufferedReader().readText()
                        val json2 = JSONObject(response2)
                        // Extraemos los promedios (si existen en la respuesta)
                        val corners = json2.optDouble("avg_corners", 0.0).takeIf { it > 0.0 }
                        val cards = json2.optDouble("avg_cards", 0.0).takeIf { it > 0.0 }
                        return Pair(corners, cards)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Pair(null, null)
    }

    private fun fetchMatches(lg: League) {
        viewModelScope.launch {
            loading = true
            errorMsg = null
            try {
                delay(500)
                val list = withContext(Dispatchers.IO) {
                    val text = URL("https://site.api.espn.com/apis/site/v2/sports/soccer/${lg.code}/scoreboard").readText()
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

                        for (j in 0 until competitors.length()) {
                            val cp = competitors.getJSONObject(j)
                            val teamObj = cp.optJSONObject("team")
                            val name = teamObj?.optString("shortDisplayName", "")?.ifBlank { teamObj.optString("displayName") } ?: "Equipo"
                            val sc = cp.optString("score", "")
                            if (cp.optString("homeAway") == "home") {
                                h = name; hs = if (sc.isNotBlank()) sc else null; hCp = cp
                            } else {
                                a = name; ascore = if (sc.isNotBlank()) sc else null; aCp = cp
                            }
                        }

                        val statusObj = c.optJSONObject("status")
                        val state = statusObj?.optJSONObject("type")?.optString("state", "pre") ?: "pre"
                        val isLive = state == "in"
                        val clock = statusObj?.optString("displayClock", "") ?: ""
                        val st = if (isLive) "EN VIVO $clock".trim() else if (state == "post") "FINAL" else "PROGRAMADO"

                        val id = ev.optString("id", i.toString())
                        val score = if (hs != null && ascore != null) "$hs : $ascore" else "VS"
                        
                        val hForm = parseForm(hCp)
                        val aForm = parseForm(aCp)

                        // CUOTAS 1X2 (Si ESPN las provee)
                        var oddH = "N/D"; var oddX = "N/D"; var oddA = "N/D"
                        val oddsArr = c.optJSONArray("odds")
                        if (oddsArr != null && oddsArr.length() > 0) {
                            val primaryOdds = oddsArr.getJSONObject(0)
                            val mlH = primaryOdds.optJSONObject("homeTeamOdds")?.optString("moneyLine", "")
                            val mlA = primaryOdds.optJSONObject("awayTeamOdds")?.optString("moneyLine", "")
                            val mlD = primaryOdds.optJSONObject("drawOdds")?.optString("moneyLine", "")
                            
                            fun americanToDec(ml: String?): String {
                                if (ml.isNullOrBlank()) return "N/D"
                                return try {
                                    val v = ml.replace("+", "").toDouble()
                                    val dec = if (v > 0) (v / 100.0) + 1.0 else (100.0 / abs(v)) + 1.0
                                    String.format(Locale.US, "%.2f", dec)
                                } catch (e: Exception) { "N/D" }
                            }
                            oddH = americanToDec(mlH); oddA = americanToDec(mlA); oddX = americanToDec(mlD)
                        }

                        // 🔥 CONSULTA A BZZOIRO (ESTADÍSTICAS REALES)
                        val (hCorners, hCards) = getTeamStatsFromBzzoiro(h)
                        val (aCorners, aCards) = getTeamStatsFromBzzoiro(a)

                        // Sumamos los promedios de ambos equipos (aproximación del total del partido)
                        val totalCornersLambda = if (hCorners != null && aCorners != null) hCorners + aCorners else null
                        val totalCardsLambda = if (hCards != null && aCards != null) hCards + aCards else null

                        val cardsStr = if (totalCardsLambda != null) {
    String.format(Locale.US, "%.1f 🟨", totalCardsLambda)
} else {
    errorMsg ?: "Promedio: N/D"
}
                        val cornersStr = if (totalCornersLambda != null) String.format(Locale.US, "%.1f", totalCornersLambda) else "Promedio: N/D"

                        var fav = "N/D"
                        if (oddH != "N/D" && oddA != "N/D") {
                            fav = if (oddH.toDouble() < oddA.toDouble()) h else a
                        }

                        // MOTOR MATEMÁTICO CON DATOS REALES
                        val parlay = ParlayEngine.buildBestParlay(
                            homeTeam = h, awayTeam = a,
                            goalLambdaHome = null, goalLambdaAway = null, // Si Bzzoiro tuviera goles, se pondrían acá
                            cardLambda = totalCardsLambda,   
                            cornerLambda = totalCornersLambda, 
                            playerShotsLambda = null
                        )

                        out.add(MatchUi(id, h, a, hForm, aForm, score, lgName, lg.tag, st, isLive, fmtDate(ev.optString("date")), fav, cardsStr, cornersStr, Triple(oddH, oddX, oddA), parlay))
                    }
                    out
                }
                matches = list
            } catch (e: Exception) {
                errorMsg = "Error de conexión: ${e.localizedMessage}"
            } finally {
                loading = false
            }
        }
    }
}

// =============================================================
// 4. INTERFAZ VISUAL CYBERPUNK
// =============================================================
fun fmtDate(d: String?): String {
    if (d == null || !d.contains("T")) return "HOY"
    return try {
        val p = d.split("T"); val dp = p[0].split("-"); val tp = p[1].substring(0, 5).split(":")
        var day = dp[2].toInt(); var h = tp[0].toInt() - 3
        if (h < 0) { h += 24; day -= 1 }
        "${if (day < 10) "0$day" else day}/${dp[1]} • ${if (h < 10) "0$h" else h}:${tp[1]} hs"
    } catch (e: Exception) { "HOY" }
}

val DeepBg = Color(0xFF090D16)
val GlassCard = Color(0xFF101726)
val CyberBorder = Color(0xFF1D283E)
val Cyan = Color(0xFF00F0FF)
val BrightGreen = Color(0xFF00FF66)
val BrightYellow = Color(0xFFFFE600)
val Rose = Color(0xFFFF0055)
val OrangeNeon = Color(0xFFFF9900)
val Violet = Color(0xFFB026FF)

@Composable
fun FormGuideRow(form: List<Char>, alignEnd: Boolean) {
    Row(
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp)
    ) {
        form.forEach { res ->
            val (bg, border, textCol) = when (res) {
                'W' -> Triple(Color(0x3300FF66), BrightGreen, BrightGreen)
                'D' -> Triple(Color(0x33FFE600), BrightYellow, BrightYellow)
                'L' -> Triple(Color(0x33FF0055), Rose, Rose)
                else -> Triple(Color(0xFF1D283E), Color.Gray, Color.Gray) // 'N' para No Datos
            }
            Surface(
                modifier = Modifier.padding(horizontal = 1.5.dp).size(17.dp),
                shape = RoundedCornerShape(3.dp), color = bg, border = BorderStroke(1.dp, border)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(res.toString(), fontSize = 10.sp, fontWeight = FontWeight.Black, color = textCol, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun CyberMatch(m: MatchUi) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp), colors = CardDefaults.cardColors(containerColor = GlassCard), border = BorderStroke(1.dp, CyberBorder)) {
        Column(modifier = Modifier.padding(9.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Surface(color = Color(0x3300F0FF), shape = RoundedCornerShape(4.dp)) {
                    Text("[${m.tag}] ${m.lg}", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Cyan, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                }
                Surface(color = if (m.live) Color(0x44FF0055) else Color(0x22FFFFFF), shape = RoundedCornerShape(4.dp)) {
                    Text(if (m.live) m.st else m.dt, color = if (m.live) Color.White else Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(m.h, fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color.White, textAlign = TextAlign.End, maxLines = 1)
                    FormGuideRow(form = m.hForm, alignEnd = true)
                }
                Surface(color = Color(0xFF161F33), shape = RoundedCornerShape(5.dp), modifier = Modifier.padding(horizontal = 6.dp)) {
                    Text(m.sc, fontWeight = FontWeight.Black, fontSize = 11.sp, color = if (m.live) BrightGreen else Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(m.a, fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color.White, textAlign = TextAlign.Start, maxLines = 1)
                    FormGuideRow(form = m.aForm, alignEnd = false)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(modifier = Modifier.weight(1f), color = Color(0x99111827), shape = RoundedCornerShape(4.dp)) {
                    Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TARJETAS", fontSize = 7.sp, color = BrightYellow, fontWeight = FontWeight.Black)
                        Text(m.cardsAvg, fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(modifier = Modifier.weight(1f), color = Color(0x99111827), shape = RoundedCornerShape(4.dp)) {
                    Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("FAVORITO", fontSize = 7.sp, color = Cyan, fontWeight = FontWeight.Black)
                        Text(m.fav, fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Cuotas 1X2 (Muestra N/D si no hay cuota real)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(Pair("Local", m.odds.first), Pair("Empate", m.odds.second), Pair("Visitante", m.odds.third)).forEach { item ->
                    Surface(modifier = Modifier.weight(1f), color = Color(0xCC0F1626), shape = RoundedCornerShape(5.dp), border = BorderStroke(1.dp, CyberBorder)) {
                        Column(modifier = Modifier.padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(item.first, fontSize = 7.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text(item.second, fontSize = 10.sp, fontWeight = FontWeight.Black, color = if (item.second == "N/D") Color.Gray else Cyan)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(5.dp))

            // HUD DEL PARLAY (100% MATEMÁTICO)
            Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xCC0B101D), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Brush.linearGradient(listOf(Cyan.copy(0.6f), Rose.copy(0.6f))))) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("QUANTUM PARLAY HUD", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.White)
                        Surface(color = Color(0x3300FF66), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, BrightGreen)) {
                            Text("${(m.parlay.jointProb * 100).toInt()}% PROB", fontSize = 8.sp, fontWeight = FontWeight.Black, color = BrightGreen, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    if (m.parlay.legs.isEmpty()) {
                        Text("Faltan datos reales de mercado o estadísticas para calcular un Parlay.", fontSize = 9.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))
                    } else {
                        m.parlay.legs.forEach { l ->
                            val badgeColor = when (l.category) {
                                "GOLES" -> Cyan
                                "BTTS" -> OrangeNeon
                                "CÓRNERS" -> Violet 
                                "TARJETAS" -> BrightYellow
                                "DOBLE OP" -> Rose
                                else -> BrightGreen
                            }
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.5.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(color = badgeColor.copy(alpha = 0.2f), shape = RoundedCornerShape(3.dp)) {
                                        Text(l.category, fontSize = 7.sp, fontWeight = FontWeight.Black, color = badgeColor, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(l.pick, fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                                Text(String.format(Locale.US, "%.0f%% P", l.modelProb * 100.0), fontSize = 8.sp, color = Color.Gray)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Prob. Conjunta: ${String.format(Locale.US, "%.1f", m.parlay.jointProb * 100.0)}%", fontSize = 8.sp, color = BrightGreen, fontWeight = FontWeight.Black)
                        Text(m.parlay.statusText, fontSize = 7.sp, color = if (m.parlay.isQualified) BrightGreen else Rose, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// =============================================================
// 5. PUNTO DE ENTRADA (MAIN ACTIVITY)
// =============================================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = DeepBg, surface = GlassCard, primary = Cyan)) {
                Surface(modifier = Modifier.fillMaxSize(), color = DeepBg) {
                    val viewModel = remember { FootballViewModel() }

                    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("BET PRO STATS", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White)
                            Button(onClick = { viewModel.refresh() }, enabled = !viewModel.loading, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161F33)), shape = RoundedCornerShape(8.dp)) {
                                Text("↻", fontSize = 13.sp, color = Cyan, fontWeight = FontWeight.Black)
                            }
                        }
                        Spacer(modifier = Modifier.height(5.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(viewModel.leagues) { l ->
                                val active = l == viewModel.selLeague
                                Surface(modifier = Modifier.clickable { viewModel.selectLeague(l) }, color = if (active) Color(0x4400F0FF) else Color(0x99101626), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, if (active) Cyan else CyberBorder)) {
                                    Text("[${l.tag}] ${l.name}", modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (active) Cyan else Color.Gray)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(5.dp))

                        if (viewModel.loading && viewModel.matches.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Cyan) }
                        } else if (viewModel.errorMsg != null && viewModel.matches.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text(viewModel.errorMsg ?: "", color = Rose, fontSize = 11.sp) }
                        } else if (viewModel.matches.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("Sin eventos hoy en ${viewModel.selLeague.name}", color = Color.Gray, fontSize = 11.sp) }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(viewModel.matches, key = { it.id }) { m -> CyberMatch(m = m) }
                            }
                        }
                    }
                }
            }
        }
    }
}
