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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.round

// =============================================================
// 1. MODELOS DE DATOS
// =============================================================
data class ParlayLeg(val category: String, val pick: String, val modelProb: Double)
data class MathematicalParlay(val legs: List<ParlayLeg>, val jointProb: Double, val isQualified: Boolean, val statusText: String)
data class MatchUi(val id: String, val h: String, val a: String, val hForm: List<Char>, val aForm: List<Char>, val sc: String, val lg: String, val tag: String, val st: String, val live: Boolean, val dt: String, val fav: String, val cardsAvg: String, val cornersAvg: String, val odds: Triple<String, String, String>, val parlay: MathematicalParlay)
data class League(val code: String, val name: String, val tag: String)

// =============================================================
// 2. MOTOR MATEMÁTICO (CERO MENTIRAS)
// =============================================================
object ParlayEngine {
    fun poissonPmf(k: Int, lambda: Double): Double {
        if (lambda <= 0.0) return 0.0
        var fact = 1.0
        for (i in 1..k) fact *= i.toDouble()
        return (lambda.pow(k.toDouble()) * exp(-lambda)) / fact
    }
    fun poissonOver(line: Double, lambda: Double): Double {
        val k = line.toInt(); var sum = 0.0
        for (i in 0..k) sum += poissonPmf(i, lambda)
        return (1.0 - sum).coerceIn(0.0, 1.0)
    }
    fun poissonUnder(line: Double, lambda: Double): Double = 1.0 - poissonOver(line, lambda)

    fun buildDynamicLeg(category: String, lambda: Double?): ParlayLeg? {
        if (lambda == null || lambda <= 0.0) return null
        val line = floor(lambda) + 0.5
        val pOver = poissonOver(line, lambda)
        val pUnder = poissonUnder(line, lambda)
        return if (pOver >= pUnder) ParlayLeg(category, "MÁS de $line $category", pOver) else ParlayLeg(category, "MENOS de $line $category", pUnder)
    }

    fun buildBestParlay(cardLambda: Double?, cornerLambda: Double?): MathematicalParlay {
        val candidates = mutableListOf<ParlayLeg>()
        buildDynamicLeg("TARJETAS", cardLambda)?.let { candidates.add(it) }
        buildDynamicLeg("CÓRNERS", cornerLambda)?.let { candidates.add(it) }

        val sorted = candidates.sortedByDescending { it.modelProb }
        val selected = sorted.filter { it.modelProb > 0.50 }.take(3)

        var jointProb = if (selected.isEmpty()) 0.0 else 1.0
        selected.forEach { jointProb *= it.modelProb }
        jointProb = round(jointProb * 1000.0) / 1000.0
        
        val isQualified = jointProb >= 0.15 && selected.size >= 2
        val statusText = if (isQualified) "PROBABILIDAD CONJUNTA" else "RIESGO ALTO / SIN DATOS"
        return MathematicalParlay(selected, jointProb, isQualified, statusText)
    }
}

// =============================================================
// 3. VIEWMODEL (ESPN + BZZOIRO)
// =============================================================
class FootballViewModel : ViewModel() {
    val leagues = listOf(League("arg.1","Argentina","ARG"), League("bra.1","Brasil","BRA"), League("esp.1","LaLiga","ESP"), League("eng.1","Premier","ENG"), League("uefa.champions","Champions","UCL"), League("mex.1","Liga MX","MEX"), League("uru.1","Uruguay","URU"), League("fra.1","Ligue 1","FRA"), League("por.1","Primeira","POR"), League("usa.1","MLS","MLS"))
    var selLeague by mutableStateOf(leagues[0]); private set
    var matches by mutableStateOf<List<MatchUi>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var errorMsg by mutableStateOf<String?>(null); private set

    // ⚠️ ACÁ VA TU NUEVA API KEY
    private val bzzoiroApiKey = "Token 9665517ed1732bad524c3730e18cd9d90cc1d13f"

    init { fetchMatches(selLeague) }
    fun selectLeague(league: League) { selLeague = league; fetchMatches(league) }
    fun refresh() { fetchMatches(selLeague) }

    private fun getTeamStats(teamName: String): Pair<Double?, Double?> {
        try {
            val cleanName = teamName.split(" ").firstOrNull() ?: teamName
            val encodedName = URLEncoder.encode(cleanName, "UTF-8")
            val url = URL("https://sports.bzzoiro.com/api/v2/teams/?search=$encodedName")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", bzzoiroApiKey)
            conn.setRequestProperty("Accept", "application/json") // AYUDA A EVITAR EL ERROR 400
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode == 200) {
                val json1 = JSONObject(conn.inputStream.bufferedReader().readText())
                val results = json1.optJSONArray("results") ?: json1.optJSONArray("data")
                if (results != null && results.length() > 0) {
                    val teamId = results.getJSONObject(0).optInt("id")
                    val formUrl = URL("https://sports.bzzoiro.com/api/v2/teams/$teamId/form/")
                    val conn2 = formUrl.openConnection() as HttpURLConnection
                    conn2.requestMethod = "GET"
                    conn2.setRequestProperty("Authorization", bzzoiroApiKey)
                    conn2.setRequestProperty("Accept", "application/json")

                    if (conn2.responseCode == 200) {
                        val json2 = JSONObject(conn2.inputStream.bufferedReader().readText())
                        val corners = json2.optDouble("avg_corners", 0.0).takeIf { it > 0.0 }
                        val cards = json2.optDouble("avg_cards", 0.0).takeIf { it > 0.0 }
                        return Pair(corners, cards)
                    } else {
                        errorMsg = "Error Stats: ${conn2.responseCode}"
                    }
                } else {
                    errorMsg = "No existe: $cleanName"
                }
            } else {
                errorMsg = "Error API: ${conn.responseCode}"
            }
        } catch (e: Exception) {
            errorMsg = "Red: ${e.message?.take(10)}"
        }
        return Pair(null, null)
    }

    private fun fetchMatches(lg: League) {
        viewModelScope.launch {
            loading = true; errorMsg = null
            try {
                val list = withContext(Dispatchers.IO) {
                    val text = URL("https://site.api.espn.com/apis/site/v2/sports/soccer/${lg.code}/scoreboard").readText()
                    val events = JSONObject(text).optJSONArray("events") ?: return@withContext emptyList<MatchUi>()
                    val out = mutableListOf<MatchUi>()

                    for (i in 0 until events.length()) {
                        val ev = events.getJSONObject(i)
                        val comps = ev.optJSONArray("competitions") ?: continue
                        if (comps.length() == 0) continue
                        val c = comps.getJSONObject(0)
                        val competitors = c.optJSONArray("competitors") ?: continue
                        var h = "Local"; var a = "Visitante"; var hCp: JSONObject? = null; var aCp: JSONObject? = null

                        for (j in 0 until competitors.length()) {
                            val cp = competitors.getJSONObject(j)
                            val name = cp.optJSONObject("team")?.optString("shortDisplayName", "")?.ifBlank { cp.optJSONObject("team")?.optString("displayName") } ?: "Equipo"
                            if (cp.optString("homeAway") == "home") { h = name; hCp = cp } else { a = name; aCp = cp }
                        }

                        val hFormRaw = hCp?.optString("form", "")?.uppercase() ?: ""
                        val aFormRaw = aCp?.optString("form", "")?.uppercase() ?: ""
                        val hForm = if (hFormRaw.length >= 5) hFormRaw.takeLast(5).map { if (it in "WDL") it else 'N' } else listOf('N','N','N','N','N')
                        val aForm = if (aFormRaw.length >= 5) aFormRaw.takeLast(5).map { if (it in "WDL") it else 'N' } else listOf('N','N','N','N','N')

                        val (hCorners, hCards) = getTeamStats(h)
                        val (aCorners, aCards) = getTeamStats(a)

                        val totalCorners = if (hCorners != null && aCorners != null) hCorners + aCorners else null
                        val totalCards = if (hCards != null && aCards != null) hCards + aCards else null

                        val cardsStr = if (totalCards != null) String.format(Locale.US, "%.1f 🟨", totalCards) else (errorMsg ?: "N/D")
                        val cornersStr = if (totalCorners != null) String.format(Locale.US, "%.1f", totalCorners) else "N/D"

                        val parlay = ParlayEngine.buildBestParlay(totalCards, totalCorners)
                        
                        val id = ev.optString("id", i.toString())
                        val isLive = c.optJSONObject("status")?.optJSONObject("type")?.optString("state") == "in"
                        val dt = ev.optString("date", "").split("T").getOrNull(0) ?: "HOY"

                        out.add(MatchUi(id, h, a, hForm, aForm, "VS", lg.name, lg.tag, if(isLive) "EN VIVO" else "PROGRAMADO", isLive, dt, "N/D", cardsStr, cornersStr, Triple("N/D", "N/D", "N/D"), parlay))
                    }
                    out
                }
                matches = list
            } catch (e: Exception) {
                errorMsg = "Error ESPN: ${e.message}"
            } finally {
                loading = false
            }
        }
    }
}

// =============================================================
// 4. INTERFAZ VISUAL (RESTAURADA CYBERPUNK)
// =============================================================
val DeepBg = Color(0xFF090D16); val GlassCard = Color(0xFF101726); val CyberBorder = Color(0xFF1D283E)
val Cyan = Color(0xFF00F0FF); val BrightGreen = Color(0xFF00FF66); val BrightYellow = Color(0xFFFFE600)
val Rose = Color(0xFFFF0055); val Violet = Color(0xFFB026FF)

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
                else -> Triple(Color(0xFF1D283E), Color.Gray, Color.Gray)
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
                        Text("CÓRNERS", fontSize = 7.sp, color = Violet, fontWeight = FontWeight.Black)
                        Text(m.cornersAvg, fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(5.dp))
            
            // HUD DEL PARLAY
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
                            val badgeColor = when (l.category) { "CÓRNERS" -> Violet; "TARJETAS" -> BrightYellow; else -> BrightGreen }
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = DeepBg)) {
                Surface(modifier = Modifier.fillMaxSize(), color = DeepBg) {
                    val vm = remember { FootballViewModel() }
                    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("BET PRO STATS", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White)
                            Button(onClick = { vm.refresh() }, enabled = !vm.loading, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161F33)), shape = RoundedCornerShape(8.dp)) {
                                Text("↻", fontSize = 13.sp, color = Cyan, fontWeight = FontWeight.Black)
                            }
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(vm.leagues) { l ->
                                val active = l == vm.selLeague
                                Surface(modifier = Modifier.clickable { vm.selectLeague(l) }, color = if (active) Color(0x4400F0FF) else Color(0x99101626), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, if (active) Cyan else CyberBorder)) {
                                    Text("[${l.tag}] ${l.name}", modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (active) Cyan else Color.Gray)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        if (vm.loading && vm.matches.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Cyan) }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(vm.matches, key = { it.id }) { m -> CyberMatch(m) }
                            }
                        }
                    }
                }
            }
        }
    }
}
