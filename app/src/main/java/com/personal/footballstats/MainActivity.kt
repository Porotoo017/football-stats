package com.personal.footballstats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit
import kotlin.math.abs

// MODELOS Y ESTRUCTURAS
data class StarPlayer(val name: String, val club: String, val role: String, val avgShots: String, val pick: String, val prob: String, val odds: String)
data class MatchOdds(val home: String, val draw: String, val away: String)
data class BetData(val odds: MatchOdds, val favorite: String, val dc: String, val goals: String, val shots: String, val corn: String, val card: String, val star: StarPlayer, val parlay: String, val parlayOdds: String)
data class League(val code: String, val name: String, val tag: String)

// MOTOR DE PRONÓSTICOS Y DISPAROS AL ARCO
object BetEngine {
    private val GIANTS = setOf("boca", "river", "racing", "independiente", "san lorenzo", "velez", "flamengo", "palmeiras", "botafogo", "mineiro", "sao paulo", "madrid", "barcelona", "atletico", "city", "liverpool", "arsenal", "psg", "sporting", "benfica", "porto", "america", "monterrey", "tigres", "penarol", "nacional", "miami")
    private val STARS = mapOf(
        "boca" to StarPlayer("Edinson Cavani", "Boca Juniors", "Delantero Centro", "3.6 remates (2.1 al arco)", "Mas de 1.5 disparos al arco", "84%", "1.82"),
        "river" to StarPlayer("Miguel Borja", "River Plate", "Delantero Centro", "3.8 remates (2.3 al arco)", "Mas de 1.5 disparos al arco", "86%", "1.75"),
        "racing" to StarPlayer("A. Maravilla Martinez", "Racing Club", "Delantero Centro", "3.5 remates (2.0 al arco)", "Mas de 1.5 disparos al arco", "82%", "1.85"),
        "independiente" to StarPlayer("Gabriel Avalos", "Independiente", "Delantero Centro", "2.8 remates (1.5 al arco)", "Mas de 1.5 disparos al arco", "76%", "1.92"),
        "san lorenzo" to StarPlayer("Alexis Cuello", "San Lorenzo", "Delantero", "2.7 remates (1.4 al arco)", "Mas de 1.5 disparos al arco", "74%", "1.95"),
        "velez" to StarPlayer("Braian Romero", "Velez Sarsfield", "Delantero Centro", "3.2 remates (1.8 al arco)", "Mas de 1.5 disparos al arco", "79%", "1.88"),
        "flamengo" to StarPlayer("Pedro Guilherme", "Flamengo", "Delantero Centro", "3.9 remates (2.4 al arco)", "Mas de 1.5 disparos al arco", "87%", "1.72"),
        "palmeiras" to StarPlayer("Estevao Willian", "Palmeiras", "Extremo Derecho", "3.3 remates (1.8 al arco)", "Mas de 1.5 disparos al arco", "80%", "1.85"),
        "mineiro" to StarPlayer("Hulk", "Atletico Mineiro", "Delantero", "4.1 remates (2.2 al arco)", "Mas de 1.5 disparos al arco", "85%", "1.78"),
        "sao paulo" to StarPlayer("Jonathan Calleri", "Sao Paulo", "Delantero Centro", "3.0 remates (1.6 al arco)", "Mas de 1.5 disparos al arco", "78%", "1.88"),
        "madrid" to StarPlayer("Vinicius Jr", "Real Madrid", "Extremo Izquierdo", "4.2 remates (2.5 al arco)", "Mas de 1.5 disparos al arco", "88%", "1.70"),
        "barcelona" to StarPlayer("Robert Lewandowski", "FC Barcelona", "Delantero Centro", "4.0 remates (2.4 al arco)", "Mas de 1.5 disparos al arco", "87%", "1.74"),
        "atletico" to StarPlayer("Julian Alvarez", "Atletico Madrid", "Delantero Centro", "3.5 remates (2.0 al arco)", "Mas de 1.5 disparos al arco", "82%", "1.82"),
        "city" to StarPlayer("Erling Haaland", "Manchester City", "Delantero Centro", "4.5 remates (2.8 al arco)", "Mas de 1.5 disparos al arco", "91%", "1.60"),
        "liverpool" to StarPlayer("Mohamed Salah", "Liverpool", "Extremo Derecho", "3.9 remates (2.2 al arco)", "Mas de 1.5 disparos al arco", "86%", "1.76"),
        "arsenal" to StarPlayer("Bukayo Saka", "Arsenal", "Extremo Derecho", "3.4 remates (1.8 al arco)", "Mas de 1.5 disparos al arco", "81%", "1.84"),
        "psg" to StarPlayer("Bradley Barcola", "Paris SG", "Extremo Izquierdo", "3.3 remates (1.9 al arco)", "Mas de 1.5 disparos al arco", "80%", "1.85"),
        "sporting" to StarPlayer("Viktor Gyokeres", "Sporting CP", "Delantero Centro", "4.4 remates (2.7 al arco)", "Mas de 1.5 disparos al arco", "90%", "1.65"),
        "benfica" to StarPlayer("Angel Di Maria", "Benfica", "Extremo", "3.2 remates (1.7 al arco)", "Mas de 1.5 disparos al arco", "79%", "1.88"),
        "america" to StarPlayer("Henry Martin", "Club America", "Delantero Centro", "3.3 remates (1.8 al arco)", "Mas de 1.5 disparos al arco", "80%", "1.85"),
        "tigres" to StarPlayer("Andre-Pierre Gignac", "Tigres UANL", "Delantero Centro", "3.4 remates (1.9 al arco)", "Mas de 1.5 disparos al arco", "81%", "1.84"),
        "penarol" to StarPlayer("Facundo Batista", "Penarol", "Delantero Centro", "2.9 remates (1.6 al arco)", "Mas de 1.5 disparos al arco", "77%", "1.90"),
        "nacional" to StarPlayer("Ruben Bentancourt", "Nacional", "Delantero Centro", "3.0 remates (1.7 al arco)", "Mas de 1.5 disparos al arco", "78%", "1.88"),
        "miami" to StarPlayer("Lionel Messi", "Inter Miami", "Mediapunta", "4.3 remates (2.6 al arco)", "Mas de 1.5 disparos al arco", "92%", "1.62")
    )

    fun build(h: String, a: String, id: String): BetData {
        val s = abs((h + a + id).hashCode())
        val hl = h.lowercase(); val al = a.lowercase()
        val hG = GIANTS.any { hl.contains(it) }; val aG = GIANTS.any { al.contains(it) }
        val fav = if (hG && !aG) h else if (!hG && aG) a else h
        val odds = if (fav == h && hG && !aG) MatchOdds("1." + (35 + (s % 20)), "4." + (10 + (s % 30)), "6." + (50 + (s % 40)))
                   else if (fav == a) MatchOdds("5." + (20 + (s % 40)), "3." + (80 + (s % 25)), "1." + (45 + (s % 20)))
                   else MatchOdds("2." + (10 + (s % 25)), "3." + (15 + (s % 20)), "3." + (20 + (s % 30)))
        val dc = if (fav == a) "X2 (Empate o $a)" else "1X ($h o Empate)"
        val starKey = STARS.keys.firstOrNull { hl.contains(it) } ?: STARS.keys.firstOrNull { al.contains(it) }
        val star = (if (starKey != null) STARS[starKey] else null) ?: StarPlayer("G. Rodriguez", fav, "Delantero Centro", "3.0 remates (1.6 al arco)", "Mas de 1.5 disparos al arco", "77%", "1.88")
        val goals = if (s % 3 != 0) "Mas de 2.5 Goles" else "Menos de 2.5 Goles"
        val parlay = "FAVORITO: $dc | Goles +1.5 | ${star.name} +1.5 al arco"
        return BetData(odds, fav, dc, goals, "Mas de 8.5 Remates", "Mas de 8.5 Corners", "Mas de 4.5 Tarjetas", star, parlay, "3." + (40 + (s % 30)))
    }
}

// HORARIO ARGENTINO (GMT-3)
fun formatArgTime(d: String?): String {
    if (d.isNullOrBlank() || !d.contains("T") || d.length < 16) return "Hoy"
    return try {
        val i = d.indexOf('T')
        val h = (d.substring(i + 1, i + 3).toInt() - 3 + 24) % 24
        val hs = if (h < 10) "0$h" else "$h"
        "$hs:" + d.substring(i + 4, i + 6) + " hs (ARG)"
    } catch (e: Exception) { "Hoy" }
}

// ESPN DTOs
@Serializable data class EspnTeam(val shortDisplayName: String? = null, val displayName: String? = null)
@Serializable data class EspnComp(val homeAway: String? = null, val score: String? = null, val team: EspnTeam? = null)
@Serializable data class EspnStatusWrapper(val displayClock: String? = null, val type: EspnStatusType? = null)
@Serializable data class EspnStatusType(val state: String? = null)
@Serializable data class EspnCompetition(val competitors: List<EspnComp> = emptyList(), val status: EspnStatusWrapper? = null)
@Serializable data class EspnEvent(val id: String, val date: String? = null, val competitions: List<EspnCompetition> = emptyList())
@Serializable data class EspnLeagueDto(val name: String? = null)
@Serializable data class EspnResponse(val leagues: List<EspnLeagueDto> = emptyList(), val events: List<EspnEvent> = emptyList())
data class MatchUi(val id: String, val h: String, val a: String, val hs: String?, val ascore: String?, val lg: String, val tag: String, val st: String, val live: Boolean, val time: String, val b: BetData)

interface ApiService {
    @GET("apis/site/v2/sports/soccer/{league}/scoreboard")
    suspend fun getMatches(@Path("league") lg: String): EspnResponse
}

object Api {
    val service: ApiService by lazy {
        val c = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).build()
        val j = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
        Retrofit.Builder().baseUrl("https://site.api.espn.com/").client(c)
            .addConverterFactory(j.asConverterFactory("application/json".toMediaType())).build().create(ApiService::class.java)
    }
}

// VIEWMODEL
class MainViewModel : ViewModel() {
    val leagues = listOf(
        League("arg.1", "Argentina", "ARG"), League("bra.1", "Brasil", "BRA"),
        League("esp.1", "Espana", "ESP"), League("eng.1", "Premier", "ENG"),
        League("uefa.champions", "Champions", "UEFA"), League("mex.1", "Mexico", "MEX"),
        League("uru.1", "Uruguay", "URU"), League("fra.1", "Francia", "FRA"),
        League("por.1", "Portugal", "POR"), League("usa.1", "MLS", "USA")
    )
    val selected = MutableStateFlow(leagues[0])
    val matches = MutableStateFlow<List<MatchUi>>(emptyList())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    init { load() }
    fun select(l: League) { selected.value = l; load() }
    fun load() {
        loading.value = true; error.value = null
        viewModelScope.launch {
            try {
                val res = Api.service.getMatches(selected.value.code)
                matches.value = res.events.mapNotNull { ev ->
                    val c = ev.competitions.firstOrNull() ?: return@mapNotNull null
                    val h = c.competitors.find { it.homeAway == "home" }
                    val a = c.competitors.find { it.homeAway == "away" }
                    val hn = h?.team?.shortDisplayName ?: h?.team?.displayName ?: "Local"
                    val an = a?.team?.shortDisplayName ?: a?.team?.displayName ?: "Visitante"
                    val live = c.status?.type?.state == "in"
                    val st = if (live) "EN VIVO " + (c.status?.displayClock ?: "") else if (c.status?.type?.state == "post") "FINAL" else "POR COMENZAR"
                    MatchUi(ev.id, hn, an, h?.score, a?.score, res.leagues.firstOrNull()?.name ?: selected.value.name, selected.value.tag, st.trim(), live, formatArgTime(ev.date), BetEngine.build(hn, an, ev.id))
                }
            } catch (e: Exception) { error.value = "Sin conexion o sin partidos hoy." }
            finally { loading.value = false }
        }
    }
}

// ODDS PILL
@Composable
fun OddsPill(label: String, odd: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Color(0xFF1E293B), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, Color(0xFF334155))) {
        Column(modifier = Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 9.sp, color = Color(0xFF94A3B8))
            Text(odd, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF38BDF8))
        }
    }
}

// TARJETA DE PARTIDO
@Composable
fun MatchCard(m: MatchUi) {
    val b = m.b
    Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF101726), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFF1E293B))) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("[" + m.tag + "] " + m.lg, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                Surface(color = if (m.live) Color(0xFFDC2626) else Color(0xFF064E3B), shape = RoundedCornerShape(4.dp)) {
                    Text(if (m.live) m.st else m.time, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(m.h, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(6.dp), modifier = Modifier.padding(horizontal = 6.dp)) {
                    Text(if (m.hs != null && m.ascore != null) m.hs + " : " + m.ascore else "VS", fontWeight = FontWeight.Black, fontSize = 12.sp, color = if (m.live) Color(0xFF34D399) else Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Text(m.a, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OddsPill("1 (Local)", b.odds.home, Modifier.weight(1f))
                OddsPill("X (Empate)", b.odds.draw, Modifier.weight(1f))
                OddsPill("2 (Visitante)", b.odds.away, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF0A221C), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFF059669))) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("MAS TIROS AL ARCO", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF34D399))
                        }
                        Surface(color = Color(0xFF10B981).copy(alpha = 0.25f), shape = RoundedCornerShape(4.dp)) {
                            Text("Cuota " + b.star.odds, fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF6EE7B7), modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                    }
                    Text(b.star.name + " (" + b.star.club + ")", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text("Pos: " + b.star.role + " • " + b.star.avgShots, fontSize = 10.sp, color = Color(0xFFCBD5E1))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(b.star.pick, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA7F3D0))
                        Text("Prob: " + b.star.prob, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF161E2E), shape = RoundedCornerShape(8.dp)) {
                Column(modifier = Modifier.padding(6.dp)) {
                    Text("[DOBLE OPORTUNIDAD] " + b.dc + " (Fav: " + b.favorite + ")", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                    Text("[GOLES] " + b.goals + " | [REMATES] " + b.shots, fontSize = 9.sp, color = Color(0xFFCBD5E1))
                    Text("[CORNERS] " + b.corn + " | [TARJETAS] " + b.card, fontSize = 9.sp, color = Color(0xFFCBD5E1))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF2B1D04), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xFFD97706))) {
                Row(modifier = Modifier.padding(6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("PARLAY (Cuota " + b.parlayOdds + ")", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24))
                        Text(b.parlay, fontSize = 9.sp, color = Color(0xFFFDE68A))
                    }
                }
            }
        }
    }
}

// MAIN ACTIVITY
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF080C14), surface = Color(0xFF101726), primary = Color(0xFF38BDF8))) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF080C14)) {
                    val vm: MainViewModel = viewModel()
                    val list by vm.matches.collectAsState()
                    val load by vm.loading.collectAsState()
                    val err by vm.error.collectAsState()
                    val sel by vm.selected.collectAsState()

                    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("BET PRO STATS", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF38BDF8))
                                Text("Horarios Hora Argentina (GMT-3) - Tiros al Arco", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            }
                            IconButton(onClick = { vm.load() }, enabled = !load) { Icon(Icons.Default.Refresh, null, tint = Color(0xFF38BDF8)) }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(vm.leagues) { l ->
                                val active = l == sel
                                Surface(
                                    modifier = Modifier.clickable { vm.select(l) },
                                    color = if (active) Color(0xFF0284C7) else Color(0xFF1E293B),
                                    shape = RoundedCornerShape(8.dp),
                                    border = if (active) BorderStroke(1.dp, Color(0xFF38BDF8)) else null
                                ) {
                                    Text("[" + l.tag + "] " + l.name, modi
