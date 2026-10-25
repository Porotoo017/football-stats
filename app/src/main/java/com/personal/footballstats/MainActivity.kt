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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

data class LeagueItem(val id: String, val name: String, val country: String, val slug: String)
data class Team(val name: String, val shortName: String, val score: String, val record: String, val leaderName: String, val leaderStat: String)
data class RealOdds(val h: Double, val d: Double, val a: Double, val ov: Double, val un: Double, val details: String, val hasReal: Boolean)
data class Match(val id: String, val home: Team, val away: Team, val status: String, val live: Boolean, val timeArg: String, val odds: RealOdds)
data class Prediction(
    val favorite: String, val hOdd: String, val dOdd: String, val aOdd: String,
    val doubleC: String, val dcQuote: String,
    val goals: String, val shots: String, val cards: String, val corners: String,
    val starName: String, val starClub: String, val starDetail: String,
    val parlayText: String, val parlayOdds: String
)

val LEAGUES = listOf(
    LeagueItem("arg.1", "Liga Profesional", "ARG", "arg.1"),
    LeagueItem("arg.copa_liga", "Copa de la Liga", "ARG", "arg.copa_liga"),
    LeagueItem("arg.copa_arg", "Copa Argentina", "ARG", "arg.copa_arg"),
    LeagueItem("esp.1", "La Liga", "ESP", "esp.1"),
    LeagueItem("eng.1", "Premier League", "ENG", "eng.1"),
    LeagueItem("uefa.champions", "Champions League", "EUR", "uefa.champions"),
    LeagueItem("ita.1", "Serie A", "ITA", "ita.1"),
    LeagueItem("ger.1", "Bundesliga", "GER", "ger.1"),
    LeagueItem("fra.1", "Ligue 1", "FRA", "fra.1"),
    LeagueItem("usa.1", "MLS", "USA", "usa.1"),
    LeagueItem("bra.1", "Brasileirao", "BRA", "bra.1"),
    LeagueItem("conmebol.libertadores", "Libertadores", "SUD", "conmebol.libertadores")
)

object BettingAnalyticsEngine {
    private val starDB = mapOf(
        "Boca" to Pair("Miguel Merentiel", "Boca Juniors"),
        "River" to Pair("Facundo Colidio", "River Plate"),
        "Racing" to Pair("Adrian Martinez", "Racing Club"),
        "Independiente" to Pair("Gabriel Avalos", "Independiente"),
        "San Lorenzo" to Pair("Alexis Cuello", "San Lorenzo"),
        "Velez" to Pair("Braian Romero", "Velez Sarsfield"),
        "Estudiantes" to Pair("Guido Carrillo", "Estudiantes LP"),
        "Talleres" to Pair("Federico Girotti", "Talleres"),
        "Belgrano" to Pair("Lucas Zelarayan", "Belgrano"),
        "Real Madrid" to Pair("Vinicius Jr", "Real Madrid"),
        "Barcelona" to Pair("Robert Lewandowski", "FC Barcelona"),
        "Atletico" to Pair("Julian Alvarez", "Atletico Madrid"),
        "City" to Pair("Erling Haaland", "Manchester City"),
        "Arsenal" to Pair("Bukayo Saka", "Arsenal FC"),
        "Liverpool" to Pair("Mohamed Salah", "Liverpool FC"),
        "Miami" to Pair("Lionel Messi", "Inter Miami CF"),
        "PSG" to Pair("Ousmane Dembele", "PSG"),
        "Bayern" to Pair("Harry Kane", "Bayern Munich"),
        "Inter" to Pair("Lautaro Martinez", "Inter Milan"),
        "Flamengo" to Pair("Pedro", "Flamengo")
    )

    fun calculate(m: Match): Prediction {
        val o = m.odds
        val h = o.h
        val d = o.d
        val a = o.a

        val favTeam: String
        val dcText: String
        val dcQuoteVal: Double

        if (h < a - 0.20) {
            favTeam = m.home.name + " (" + String.format(Locale.US, "%.2f", h) + ")"
            dcText = "1X (" + m.home.shortName + " o Empate)"
            dcQuoteVal = ((1.0 / ((1.0 / h) + (1.0 / d))) * 0.95).coerceAtLeast(1.10)
        } else if (a < h - 0.20) {
            favTeam = m.away.name + " (" + String.format(Locale.US, "%.2f", a) + ")"
            dcText = "X2 (Empate o " + m.away.shortName + ")"
            dcQuoteVal = ((1.0 / ((1.0 / a) + (1.0 / d))) * 0.95).coerceAtLeast(1.10)
        } else {
            favTeam = "Partido Parejo (L: " + String.format(Locale.US, "%.2f", h) + " | V: " + String.format(Locale.US, "%.2f", a) + ")"
            dcText = "12 (Cualquiera Gana)"
            dcQuoteVal = ((1.0 / ((1.0 / h) + (1.0 / a))) * 0.95).coerceAtLeast(1.20)
        }

        val hOdd = String.format(Locale.US, "%.2f", h)
        val dOdd = String.format(Locale.US, "%.2f", d)
        val aOdd = String.format(Locale.US, "%.2f", a)
        val dcQuoteStr = String.format(Locale.US, "%.2f", dcQuoteVal)

        val goalsPick: String
        val goalsOddsVal: Double
        if (o.un < o.ov) {
            goalsPick = "Menos de 2.5 Goles (Cuota " + String.format(Locale.US, "%.2f", o.un) + ")"
            goalsOddsVal = o.un
        } else {
            goalsPick = "Mas de 2.5 Goles (Cuota " + String.format(Locale.US, "%.2f", o.ov) + ")"
            goalsOddsVal = o.ov
        }

        val shotsPick = "Mas de 8.5 Tiros al arco (Cuota 1.75)"
        val cardsPick = "Mas de 4.5 Tarjetas totales (Cuota 1.70)"
        val cornersPick = "Mas de 8.5 Corners totales (Cuota 1.80)"

        val starName: String
        val starClub: String
        val starDetail: String

        if (m.home.leaderName.isNotEmpty()) {
            starName = m.home.leaderName
            starClub = m.home.name
            starDetail = "Maximo artillero (" + m.home.leaderStat + " goles) - Pronostico: Mas de 1.5 disparos al arco (1.85)"
        } else if (m.away.leaderName.isNotEmpty()) {
            starName = m.away.leaderName
            starClub = m.away.name
            starDetail = "Maximo artillero (" + m.away.leaderStat + " goles) - Pronostico: Mas de 1.5 disparos al arco (1.85)"
        } else {
            var found: Pair<String, String>? = null
            for ((k, v) in starDB) {
                if (m.home.name.contains(k, ignoreCase = true) || m.away.name.contains(k, ignoreCase = true)) {
                    found = v
                    break
                }
            }
            if (found != null) {
                starName = found.first
                starClub = found.second
                starDetail = "Principal rematador del club - Pronostico: Mas de 1.5 disparos al arco (1.75)"
            } else {
                starName = "Delantero Principal"
                starClub = if (h <= a) m.home.name else m.away.name
                starDetail = "Centrodelantero titular - Pronostico: Mas de 1.5 disparos al arco (1.80)"
            }
        }

        val parlayVal = dcQuoteVal * goalsOddsVal.coerceAtLeast(1.50) * 1.65
        val parlayOddsStr = String.format(Locale.US, "%.2f", parlayVal)
        val parlayText = dcText + " + " + (if (o.un < o.ov) "Menos" else "Mas") + " de 2.5 goles + Mas de 7.5 corners"

        return Prediction(favTeam, hOdd, dOdd, aOdd, dcText, dcQuoteStr, goalsPick, shotsPick, cardsPick, cornersPick, starName, starClub, starDetail, parlayText, parlayOddsStr)
    }
}

object EspnApiService {
    private fun parseAmerican(s: String?): Double {
        if (s == null || s.isEmpty()) return 2.00
        return try {
            val n = s.replace("+", "").toDouble()
            if (n > 0) ((n / 100.0) + 1.0) else ((100.0 / abs(n)) + 1.0)
        } catch (e: Exception) { 2.00 }
    }

    private fun formatArgTime(dateStr: String): String {
        if (dateStr.isEmpty()) return "Horario a confirmar"
        return try {
            val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val d = utc.parse(dateStr)
            val arg = SimpleDateFormat("EEE dd MMM - HH:mm 'hs (ARG)'", Locale("es", "AR")).apply {
                timeZone = TimeZone.getTimeZone("America/Argentina/Buenos_Aires")
            }
            arg.format(d ?: Date()).replaceFirstChar { it.uppercase() }
        } catch (e: Exception) {
            try {
                val utc2 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                val d = utc2.parse(dateStr)
                val arg = SimpleDateFormat("EEE dd MMM - HH:mm 'hs (ARG)'", Locale("es", "AR")).apply {
                    timeZone = TimeZone.getTimeZone("America/Argentina/Buenos_Aires")
                }
                arg.format(d ?: Date()).replaceFirstChar { it.uppercase() }
            } catch (e2: Exception) { "Horario Arg a confirmar" }
        }
    }

    suspend fun fetchScoreboard(slug: String): List<Match> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Match>()
        try {
            val url = "https://site.api.espn.com/apis/site/v2/sports/soccer/" + slug + "/scoreboard"
            val text = URL(url).readText()
            val root = JSONObject(text)
            val events = root.optJSONArray("events") ?: return@withContext list

            for (i in 0 until events.length()) {
                val ev = events.getJSONObject(i)
                val id = ev.optString("id", i.toString())
                val timeArg = formatArgTime(ev.optString("date", ""))
                val comps = ev.optJSONArray("competitions") ?: continue
                if (comps.length() == 0) continue
                val comp = comps.getJSONObject(0)

                val st = comp.optJSONObject("status")
                val stType = st?.optJSONObject("type")
                val statusName = stType?.optString("name", "") ?: ""
                val statusDetail = stType?.optString("shortDetail", "Programado") ?: "Programado"
                val isLive = statusName == "STATUS_IN_PROGRESS" || statusName == "STATUS_HALFTIME"

                val oddsArr = comp.optJSONArray("odds")
                var h = 2.35
                var d = 3.10
                var a = 2.80
                var ov = 1.95
                var un = 1.80
                var dt = ""
                var hasReal = false

                if (oddsArr != null && oddsArr.length() > 0) {
                    val od = oddsArr.getJSONObject(0)
                    dt = od.optString("details", "")
                    val ml = od.optJSONObject("moneyline")
                    if (ml != null) {
                        val ho = ml.optJSONObject("home")?.optJSONObject("close")?.optString("odds")
                        val dro = ml.optJSONObject("draw")?.optJSONObject("close")?.optString("odds")
                        val ao = ml.optJSONObject("away")?.optJSONObject("close")?.optString("odds")
                        if (!ho.isNullOrEmpty()) {
                            h = parseAmerican(ho)
                            d = parseAmerican(dro)
                            a = parseAmerican(ao)
                            hasReal = true
                        }
                    }
                    val tot = od.optJSONObject("total")
                    if (tot != null) {
                        val ovo = tot.optJSONObject("over")?.optJSONObject("close")?.optString("odds")
                        val uno = tot.optJSONObject("under")?.optJSONObject("close")?.optString("odds")
                        if (!ovo.isNullOrEmpty()) ov = parseAmerican(ovo)
                        if (!uno.isNullOrEmpty()) un = parseAmerican(uno)
                    }
                }

                val realOdds = RealOdds(h, d, a, ov, un, dt, hasReal)
                val competitors = comp.optJSONArray("competitors") ?: continue
                var home = Team("Local", "LOC", "-", "", "", "")
                var away = Team("Visitante", "VIS", "-", "", "", "")

                for (j in 0 until competitors.length()) {
                    val c = competitors.getJSONObject(j)
                    val tm = c.optJSONObject("team")
                    val name = tm?.optString("displayName", "Equipo") ?: "Equipo"
                    val shortName = tm?.optString("shortDisplayName", name) ?: name
                    val sc = c.optString("score", "-")

                    val recs = c.optJSONArray("records")
                    val recStr = if (recs != null && recs.length() > 0) recs.getJSONObject(0).optString("summary", "") else ""

                    var lName = ""
                    var lStat = ""
                    val leadArr = c.optJSONArray("leaders")
                    if (leadArr != null && leadArr.length() > 0) {
                        val lList = leadArr.getJSONObject(0).optJSONArray("leaders")
                        if (lList != null && lList.length() > 0) {
                            val lObj = lList.getJSONObject(0)
                            lStat = lObj.optString("displayValue", "")
                            lName = lObj.optJSONObject("athlete")?.optString("fullName", "") ?: ""
                        }
                    }

                    val teamObj = Team(name, shortName, sc, recStr, lName, lStat)
                    if (c.optString("homeAway", "home") == "home") home = teamObj else away = teamObj
                }

                list.add(Match(id, home, away, statusDetail, isLive, timeArg, realOdds))
            }
        } catch (e: Exception) { e.printStackTrace() }
        list
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF070B14)) {
                    AppScreen()
                }
            }
        }
    }
}

@Composable
fun AppScreen() {
    var selectedLeague by remember { mutableStateOf(LEAGUES[0]) }
    var matches by remember { mutableStateOf<List<Match>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh(l: LeagueItem) {
        scope.launch {
            loading = true
            expandedId = null
            matches = EspnApiService.fetchScoreboard(l.slug)
            loading = false
        }
    }

    LaunchedEffect(selectedLeague) { refresh(selectedLeague) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF070B14))) {
        // HEADER CON GRADIENTE Y NEON
        Box(
            modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF0B0F19)))).padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "FUTBOL STATS PRO",
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF0284C7)).padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Cuotas DraftKings Reales - Horario Argentina (GMT-3)",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF10B981), Color(0xFF059669)))).clickable { refresh(selectedLeague) }.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("Actualizar", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // SELECTOR DE LIGAS CON EFECTO PILL MODERNO
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LEAGUES.forEach { l ->
                        val sel = l.id == selectedLeague.id
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(
                                    if (sel) Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF06B6D4)))
                                    else Brush.horizontalGradient(listOf(Color(0xFF1E293B), Color(0xFF111827)))
                                )
                                .border(1.dp, if (sel) Color(0xFF38BDF8) else Color(0xFF334155), RoundedCornerShape(24.dp))
                                .clickable { selectedLeague = l }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(if (sel) Color.White.copy(alpha = 0.25f) else Color(0xFF0F172A)).padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(l.country, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = l.name,
                                    color = if (sel) Color.White else Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // LINEA NEON SEPARADORA
        Box(
            modifier = Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF06B6D4), Color.Transparent)))
        )

        // CONTENIDO PRINCIPAL
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF38BDF8), strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Cargando partidos y cuotas en tiempo real...", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }
        } else if (matches.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No hay partidos en disputa hoy para " + selectedLeague.name,
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF2563EB)).clickable { refresh(selectedLeague) }.padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text("Reintentar", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                matches.forEach { m ->
                    item(key = m.id) {
                        CardItem(
                            m = m,
                            abierto = (expandedId == m.id),
                            onToggle = { expandedId = if (expandedId == m.id) null else m.id }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MarketBox(label: String, odds: String, col: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF070B14)).border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp)).padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Color(0xFF64748B), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(odds, color = col, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, col: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF070B14)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFF94A3B8), fontSize = 11.sp)
        Spacer(modifier = Modifier.weight(1f))
        Text(value, color = col, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}

@Composable
fun CardItem(m: Match, abierto: Boolean, onToggle: () -> Unit) {
    val p = remember(m.id) { BettingAnalyticsEngine.calculate(m) }

    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF0F172A)).border(
                1.dp,
                if (m.live) Color(0xFFEF4444) else if (abierto) Color(0xFF38BDF8) else Color(0xFF1E293B),
                RoundedCornerShape(16.dp)
            ).clickable { onToggle() }.padding(14.dp)
    ) {
        Column {
            // ENCABEZADO: ESTADO EN VIVO Y HORA ARGENTINA
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (m.live) {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFDC2626)).padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("EN VIVO - " + m.status, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    }
                } else {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF1E293B)).padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(m.status, color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Text(m.timeArg, color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // EQUIPOS Y MARCADOR
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        m.home.name,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (m.home.record.isNotEmpty()) {
                        Text(m.home.record, color = Color(0xFF64748B), fontSize = 10.sp)
                    }
                }

                Box(
                    modifier = Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF070B14)).border(1.dp, if (m.live) Color(0xFF10B981) else Color(0xFF1E293B), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (m.home.score != "-") m.home.score + " : " + m.away.score else "VS",
                        color = if (m.live) Color(0xFF22C55E) else Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        m.away.name,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (m.away.record.isNotEmpty()) {
                        Text(m.away.record, color = Color(0xFF64748B), fontSize = 10.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // PRONOSTICO FAVORITO RESUMEN
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF131D31)).border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Favorito: ", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text(p.favorite, color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        if (abierto) "Cerrar analisis" else "Ver analisis completo",
                        color = Color(0xFF10B981),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // PANEL EXPANDIBLE CON ESTADISTICAS Y MERCADOS REALES
            if (abierto) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1E293B)))
                Spacer(modifier = Modifier.height(12.dp))

                // ENCABEZADO DE MERCADOS
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("CUOTAS Y MERCADOS REALES", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF064E3B)).padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("DraftKings Oficial", color = Color(0xFF34D399), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // BOTONES ESTILO 1X2 DEPORTIVO
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MarketBox(label = "1 (Local)", odds = p.hOdd, col = Color(0xFF38BDF8), modifier = Modifier.weight(1f))
                    MarketBox(label = "X (Empate)", odds = p.dOdd, col = Color(0xFF94A3B8), modifier = Modifier.weight(1f))
                    MarketBox(label = "2 (Visitante)", odds = p.aOdd, col = Color(0xFFF472B6), modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(6.dp))

                InfoRow("Doble Oportunidad", p.doubleC + " (Cuota " + p.dcQuote + ")", Color(0xFF60A5FA))
                InfoRow("Goles (+/- 2.5)", p.goals, Color(0xFFF472B6))
                InfoRow("Tiros al Arco", p.shots, Color(0xFFA78BFA))
                InfoRow("Tarjetas", p.cards, Color(0xFFFBBF24))
                InfoRow("Tiros de Esquina", p.corners, Color(0xFF2DD4BF))

                Spacer(modifier = Modifier.height(10.dp))

                // JUGADOR CON MAS DISPAROS AL ARCO (DISEÑO VIP NEON)
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF042F2E), Color(0xFF021B1B))
                            )
                        )
                        .border(1.dp, Color(0xFF14B8A6), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF0D9488)).padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("TARGET PRO", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("JUGADOR CON MAS DISPAROS AL ARCO:", color = Color(0xFF2DD4BF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            p.starName + "  -  Club: " + p.starClub,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(p.starDetail, color = Color(0xFF99F6E4), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // COMBINADA / PARLAY DE VALOR
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1E1B4B), Color(0xFF0F0C2E))
                            )
                        )
                        .border(1.dp, Color(0xFF6366F1), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("COMBINADA / PARLAY DE VALOR", color = Color(0xFFA5B4FC), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF10B981)).padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Cuota " + p.parlayOdds, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(p.parlayText, color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
