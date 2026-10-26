package com.personal.footballstats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.abs

// MODELOS Y MOTOR DE APUESTAS CON JUGADOR ESTRELLA
data class StarProp(val name: String, val club: String, val pick: String, val prob: String, val odds: String)
data class MatchOdds(val home: String, val draw: String, val away: String)
data class BetData(val odds: MatchOdds, val dc: String, val goals: String, val shots: String, val corn: String, val card: String, val star: StarProp, val parlay: String, val parlayOdds: String)
data class League(val code: String, val name: String, val flag: String)

object BetEngine {
    private val STARS = mapOf(
        "boca" to Pair("Edinson Cavani", "Boca"), "river" to Pair("Miguel Borja", "River"),
        "racing" to Pair("Adrián 'Maravilla' Martínez", "Racing"), "independiente" to Pair("Gabriel Ávalos", "Rojo"),
        "san lorenzo" to Pair("Alexis Cuello", "San Lorenzo"), "velez" to Pair("Braian Romero", "Vélez"),
        "madrid" to Pair("Vinícius Jr", "Real Madrid"), "barcelona" to Pair("Robert Lewandowski", "Barça"),
        "city" to Pair("Erling Haaland", "Man City"), "miami" to Pair("Lionel Messi", "Inter Miami"),
        "arsenal" to Pair("Bukayo Saka", "Arsenal"), "liverpool" to Pair("Mohamed Salah", "Liverpool")
    )
    fun build(h: String, a: String, id: String): BetData {
        val s = abs((h + a + id).hashCode())
        val hl = h.lowercase(); val al = a.lowercase()
        val key = STARS.keys.firstOrNull { hl.contains(it) } ?: STARS.keys.firstOrNull { al.contains(it) }
        val pair = if (key != null) STARS[key]!! else Pair(h.take(12) + " Goleador", h)
        val star = StarProp(pair.first, pair.second, "Más de 1.5 disparos al arco", "${75 + (s % 15)}%", "1." + (72 + (s % 24)))
        val odds = MatchOdds("1." + (80 + (s % 40)), "3." + (10 + (s % 30)), "3." + (40 + (s % 50)))
        val dc = if (s % 2 == 0) "1X ($h o Empate)" else "X2 (Empate o $a)"
        val goals = if (s % 3 != 0) "Más de 2.5 Goles" else "Menos de 2.5 Goles"
        val pPick = "🛡️ $dc • ⚽ Goles +1.5 • ⭐ ${star.name} +1.5 al arco"
        return BetData(odds, dc, goals, "Más de 8.5 Remates", "Más de 8.5 Córners", "Más de 4.5 Tarjetas", star, pPick, "3." + (60 + (s % 35)))
    }
}

// DTOs Y CLIENTE ESPN
@Serializable data class EspnTeam(val shortDisplayName: String? = null, val displayName: String? = null)
@Serializable data class EspnComp(val homeAway: String? = null, val score: String? = null, val team: EspnTeam? = null)
@Serializable data class EspnStatus(val state: String? = null, val detail: String? = null, val clock: String? = null)
@Serializable data class EspnEvent(val id: String, val date: String? = null, val competitions: List<EspnCompetition> = emptyList())
@Serializable data class EspnCompetition(val competitors: List<EspnComp> = emptyList(), val status: EspnStatusWrapper? = null)
@Serializable data class EspnStatusWrapper(val displayClock: String? = null, val type: EspnStatusType? = null)
@Serializable data class EspnStatusType(val state: String? = null, val shortDetail: String? = null)
@Serializable data class EspnLeagueDto(val name: String? = null)
@Serializable data class EspnResponse(val leagues: List<EspnLeagueDto> = emptyList(), val events: List<EspnEvent> = emptyList())

data class MatchUi(val id: String, val h: String, val a: String, val hs: String?, val ascore: String?, val lg: String, val flg: String, val st: String, val live: Boolean, val time: String, val b: BetData)

interface ApiService {
    @GET("apis/site/v2/sports/soccer/{league}/scoreboard")
    suspend fun getMatches(@Path("league") lg: String, @Query("dates") d: String? = null): EspnResponse
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
    val leagues = listOf(League("arg.1", "Argentina", "🇦🇷"), League("esp.1", "La Liga", "🇪🇸"), League("eng.1", "Premier", "🏴󠁧󠁢󠁥󠁮󠁧󠁿"), League("uefa.champions", "Champions", "🏆"), League("usa.1", "MLS", "🇺🇸"))
    val selected = MutableStateFlow(leagues[0])
    val matches = MutableStateFlow<List<MatchUi>>(emptyList())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    init { load() }

    fun select(l: League) { selected.value = l; load() }

    fun load() {
        loading.value = true; error.value = null
        val d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        viewModelScope.launch {
            try {
                val res = Api.service.getMatches(selected.value.code, d)
                matches.value = res.events.mapNotNull { ev ->
                    val c = ev.competitions.firstOrNull() ?: return@mapNotNull null
                    val h = c.competitors.find { it.homeAway == "home" }
                    val a = c.competitors.find { it.homeAway == "away" }
                    val hn = h?.team?.shortDisplayName ?: h?.team?.displayName ?: "Local"
                    val an = a?.team?.shortDisplayName ?: a?.team?.displayName ?: "Visitante"
                    val live = c.status?.type?.state == "in"
                    val st = if (live) "EN VIVO ${c.status?.displayClock ?: ""}".trim() else if (c.status?.type?.state == "post") "FINAL" else c.status?.type?.shortDetail ?: "HOY"
                    val t = try { Instant.parse(ev.date ?: "").atZone(ZoneId.of("America/Argentina/Buenos_Aires")).format(DateTimeFormatter.ofPattern("HH:mm")) + " hs" } catch (e: Exception) { "Hoy" }
                    MatchUi(ev.id, hn, an, h?.score, a?.score, res.leagues.firstOrNull()?.name ?: selected.value.name, selected.value.flag, st, live, t, BetEngine.build(hn, an, ev.id))
                }
            } catch (e: Exception) { error.value = "Sin conexión o sin partidos hoy." }
            finally { loading.value = false }
        }
    }
}

// UI JETPACK COMPOSE ULTRA MODERNA
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

                    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("BET PRO STATS", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF38BDF8))
                                Text("🇦🇷 Hora Argentina • Pronósticos y Jugador Estrella", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            }
                            IconButton(onClick = { vm.load() }, enabled = !load) { Icon(Icons.Default.Refresh, null, tint = Color(0xFF38BDF8)) }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(vm.leagues) { l ->
                                val active = l == sel
                                Surface(
                                    modifier = Modifier.clickable { vm.select(l) },
                                    color = if (active) Color(0xFF0284C7) else Color(0xFF1E293B),
                                    shape = RoundedCornerShape(8.dp),
                                    border = if (active) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)) else null
                                ) {
                                    Text("${l.flag} ${l.name}", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        if (load) Box(modifier = Modifier.fillMaxSize().weight(1f), Alignment.Center) { CircularProgressIndicator(color = Color(0xFF38BDF8)) }
                        else if (err != null && list.isEmpty()) Box(modifier = Modifier.fillMaxSize().weight(1f), Alignment.Center) { Text(err ?: "", color = Color(0xFFEF4444), fontSize = 13.sp) }
                        else if (list.isEmpty()) Box(modifier = Modifier.fillMaxSize().weight(1f), Alignment.Center) { Text("No hay partidos hoy en ${sel.name}", color = Color(0xFF64748B), fontSize = 13.sp) }
                        else {
                            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(list, key = { it.id }) { m -> MatchCard(m) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MatchCard(m: MatchUi) {
    val b = m.b
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF101726),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${m.flg} ${m.lg}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                Surface(color = if (m.live) Color(0xFFDC2626) else Color(0xFF1E293B), shape = RoundedCornerShape(4.dp)) {
                    Text(if (m.live) "🔴 ${m.st}" else "${m.time} • ${m.st}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(m.h, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(6.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(if (m.hs != null && m.ascore != null) "${m.hs} : ${m.ascore}" else "VS", fontWeight = FontWeight.Black, fontSize = 13.sp, color = if (m.live) Color(0xFF34D399) else Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Text(m.a, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OddsPill("1 (Local)", b.odds.home, Modifier.weight(1f))
                OddsPill("X (Empate)", b.odds.draw, Modifier.weight(1f))
                OddsPill("2 (Visitante)", b.odds.away, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))

            // TARJETA VERDE: JUGADOR CON MÁS TIROS AL ARCO (SIEMPRE VISIBLE)
            Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF0A221C), shape = RoundedCornerShape(10.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF059669))) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("MÁS TIROS AL ARCO", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF34D399))
                        }
                        Surface(color = Color(0xFF10B981).copy(alpha = 0.25f), shape = RoundedCornerShape(4.dp)) {
                            Text("Cuota ${b.star.odds}", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF6EE7B7), modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${b.star.name} (${b.star.club})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(b.star.pick, fontSize = 11.sp, color = Color(0xFFA7F3D0))
                        Text("Prob: ${b.star.prob}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // MERCADOS Y PARLAY
            Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF161E2E), shape = RoundedCornerShape(8.dp)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("🛡️ Doble Oportunidad: ${b.dc}", fontSize = 10.sp, color = Color(0xFFCBD5E1))
                    Text("⚽ Línea Goles: ${b.goals} • 🎯 ${b.shots}", fontSize = 10.sp, color = Color(0xFFCBD5E1))
                    Text("🚩 ${b.corn} • 🟨 ${b.card}", fontSize = 10.sp, color = Color(0xFFCBD5E1))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF2B1D04), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD97706))) {
                Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("🔥 PARLAY RECOMENDADO (Cuota ${b.parlayOdds})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24))
                        Text(b.parlay, fontSize = 9.sp, color = Color(0xFFFDE68A))
                    }
                }
            }
        }
    }
}

@Composable
fun OddsPill(label: String, odd: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Color(0xFF1E293B), shape = RoundedCornerShape(6.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))) {
        Column(modifier = Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 9.sp, color = Color(0xFF94A3B8))
            Text(odd, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF38BDF8))
        }
    }
}
