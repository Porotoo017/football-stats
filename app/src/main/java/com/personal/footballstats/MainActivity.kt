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
import java.util.concurrent.TimeUnit
import kotlin.math.abs

// MOTOR DE APUESTAS, CUOTAS Y JUGADOR CON MAS TIROS AL ARCO
data class StarPlayer(val name: String, val club: String, val role: String, val avgShots: String, val pick: String, val prob: String, val odds: String)
data class MatchOdds(val home: String, val draw: String, val away: String)
data class BetData(val odds: MatchOdds, val favorite: String, val dc: String, val goals: String, val shots: String, val corn: String, val card: String, val star: StarPlayer, val parlay: String, val parlayOdds: String)
data class League(val code: String, val name: String, val tag: String)

object BetEngine {
    private val GIANTS = setOf(
        "boca", "river", "racing", "independiente", "san lorenzo", "velez", "estudiantes",
        "flamengo", "palmeiras", "botafogo", "mineiro", "sao paulo", "corinthians", "fluminense", "gremio", "internacional",
        "madrid", "barcelona", "atletico", "city", "liverpool", "arsenal", "chelsea", "tottenham",
        "psg", "paris", "monaco", "marseille", "sporting", "benfica", "porto",
        "america", "monterrey", "tigres", "cruz azul", "chivas", "penarol", "nacional", "miami"
    )

    private val STARS = mapOf(
        "boca" to StarPlayer("Edinson Cavani", "Boca Juniors", "Delantero Centro", "3.6 remates p/p (2.1 al arco)", "Mas de 1.5 disparos al arco", "84%", "1.82"),
        "river" to StarPlayer("Miguel Angel Borja", "River Plate", "Delantero Centro", "3.8 remates p/p (2.3 al arco)", "Mas de 1.5 disparos al arco", "86%", "1.75"),
        "racing" to StarPlayer("Adrian Maravilla Martinez", "Racing Club", "Delantero Centro", "3.5 remates p/p (2.0 al arco)", "Mas de 1.5 disparos al arco", "82%", "1.85"),
        "independiente" to StarPlayer("Gabriel Avalos", "Independiente", "Delantero Centro", "2.8 remates p/p (1.5 al arco)", "Mas de 1.5 disparos al arco", "76%", "1.92"),
        "san lorenzo" to StarPlayer("Alexis Cuello", "San Lorenzo", "Delantero", "2.7 remates p/p (1.4 al arco)", "Mas de 1.5 disparos al arco", "74%", "1.95"),
        "velez" to StarPlayer("Braian Romero", "Velez Sarsfield", "Delantero Centro", "3.2 remates p/p (1.8 al arco)", "Mas de 1.5 disparos al arco", "79%", "1.88"),
        "estudiantes" to StarPlayer("Guido Carrillo", "Estudiantes LP", "Delantero Centro", "2.9 remates p/p (1.6 al arco)", "Mas de 1.5 disparos al arco", "77%", "1.90"),
        "flamengo" to StarPlayer("Pedro Guilherme", "Flamengo", "Delantero Centro", "3.9 remates p/p (2.4 al arco)", "Mas de 1.5 disparos al arco", "87%", "1.72"),
        "palmeiras" to StarPlayer("Estevao Willian", "Palmeiras", "Extremo Derecho", "3.3 remates p/p (1.8 al arco)", "Mas de 1.5 disparos al arco", "80%", "1.85"),
        "botafogo" to StarPlayer("Igor Jesus", "Botafogo", "Delantero Centro", "3.4 remates p/p (1.9 al arco)", "Mas de 1.5 disparos al arco", "81%", "1.84"),
        "mineiro" to StarPlayer("Hulk", "Atletico Mineiro", "Delantero", "4.1 remates p/p (2.2 al arco)", "Mas de 1.5 disparos al arco", "85%", "1.78"),
        "sao paulo" to StarPlayer("Jonathan Calleri", "Sao Paulo", "Delantero Centro", "3.0 remates p/p (1.6 al arco)", "Mas de 1.5 disparos al arco", "78%", "1.88"),
        "corinthians" to StarPlayer("Memphis Depay", "Corinthians", "Delantero", "3.5 remates p/p (2.0 al arco)", "Mas de 1.5 disparos al arco", "82%", "1.82"),
        "madrid" to StarPlayer("Vinicius Jr", "Real Madrid", "Extremo Izquierdo", "4.2 remates p/p (2.5 al arco)", "Mas de 1.5 disparos al arco", "88%", "1.70"),
        "barcelona" to StarPlayer("Robert Lewandowski", "FC Barcelona", "Delantero Centro", "4.0 remates p/p (2.4 al arco)", "Mas de 1.5 disparos al arco", "87%", "1.74"),
        "atletico" to StarPlayer("Julian Alvarez", "Atletico Madrid", "Delantero Centro", "3.5 remates p/p (2.0 al arco)", "Mas de 1.5 disparos al arco", "82%", "1.82"),
        "city" to StarPlayer("Erling Haaland", "Manchester City", "Delantero Centro", "4.5 remates p/p (2.8 al arco)", "Mas de 1.5 disparos al arco", "91%", "1.60"),
        "liverpool" to StarPlayer("Mohamed Salah", "Liverpool", "Extremo Derecho", "3.9 remates p/p (2.2 al arco)", "Mas de 1.5 disparos al arco", "86%", "1.76"),
        "arsenal" to StarPlayer("Bukayo Saka", "Arsenal", "Extremo Derecho", "3.4 remates p/p (1.8 al arco)", "Mas de 1.5 disparos al arco", "81%", "1.84"),
        "psg" to StarPlayer("Bradley Barcola", "Paris SG", "Extremo Izquierdo", "3.3 remates p/p (1.9 al arco)", "Mas de 1.5 disparos al arco", "80%", "1.85"),
        "sporting" to StarPlayer("Viktor Gyokeres", "Sporting CP", "Delantero Centro", "4.4 remates p/p (2.7 al arco)", "Mas de 1.5 disparos al arco", "90%", "1.65"),
        "benfica" to StarPlayer("Angel Di Maria", "Benfica", "Extremo / Creador", "3.2 remates p/p (1.7 al arco)", "Mas de 1.5 disparos al arco", "79%", "1.88"),
        "porto" to StarPlayer("Samu Omorodion", "FC Porto", "Delantero Centro", "3.4 remates p/p (2.0 al arco)", "Mas de 1.5 disparos al arco", "81%", "1.83"),
        "america" to StarPlayer("Henry Martin", "Club America", "Delantero Centro", "3.3 remates p/p (1.8 al arco)", "Mas de 1.5 disparos al arco", "80%", "1.85"),
        "monterrey" to StarPlayer("German Berterame", "Monterrey", "Delantero Centro", "3.1 remates p/p (1.7 al arco)", "Mas de 1.5 disparos al arco", "78%", "1.88"),
        "tigres" to StarPlayer("Andre-Pierre Gignac", "Tigres UANL", "Delantero Centro", "3.4 remates p/p (1.9 al arco)", "Mas de 1.5 disparos al arco", "81%", "1.84"),
        "penarol" to StarPlayer("Facundo Batista", "Penarol", "Delantero Centro", "2.9 remates p/p (1.6 al arco)", "Mas de 1.5 disparos al arco", "77%", "1.90"),
        "nacional" to StarPlayer("Ruben Bentancourt", "Nacional", "Delantero Centro", "3.0 remates p/p (1.7 al arco)", "Mas de 1.5 disparos al arco", "78%", "1.88"),
        "miami" to StarPlayer("Lionel Messi", "Inter Miami", "Mediapunta / Delantero", "4.3 remates p/p (2.6 al arco)", "Mas de 1.5 disparos al arco", "92%", "1.62")
    )

    fun build(h: String, a: String, id: String): BetData {
        val s = abs((h + a + id).hashCode())
        val hl = h.lowercase(); val al = a.lowercase()
        val hGiant = GIANTS.any { hl.contains(it) }
        val aGiant = GIANTS.any { al.contains(it) }

        // LOGICA DE FAVORITO REAL: NUNCA SE PONE AL FAVORITO COMO MENOS FAVORITO
        val (favTeam, odds, dc) = when {
            hGiant && !aGiant -> {
                val o = MatchOdds("1." + (35 + (s % 20)), "4." + (10 + (s % 30)), "6." + (50 + (s % 40)))
                Triple(h, o, "1X ($h o Empate)")
            }
            !hGiant && aGiant -> {
                val o = MatchOdds("5." + (20 + (s % 40)), "3." + (80 + (s % 25)), "1." + (45 + (s % 20)))
                Triple(a, o, "X2 (Empate o $a)")
            }
            else -> {
                val o = MatchOdds("2." + (5 + (s % 25)), "3." + (15 + (s % 20)), "3." + (20 + (s % 30)))
                Triple(h, o, "1X ($h o Empate)")
            }
        }

        // JUGADOR CON MAS TIROS AL ARCO
        val starKey = STARS.keys.firstOrNull { hl.contains(it) } ?: STARS.keys.firstOrNull { al.contains(it) }
        val star = if (starKey != null) STARS[starKey]!! else {
            val team = if (favTeam == h) h else a
            StarPlayer("G. Rodriguez", team, "Delantero Centro", "3.0 remates p/p (1.6 al arco)", "Mas de 1.5 disparos al arco", "77%", "1.88")
        }

        val goals = if (s % 3 != 0) "Mas de 2.5 Goles" else "Menos de 2.5 Goles"
        val pPick = "FAVORITO: $dc | Goles +1.5 | ${star.name} +1.5 al arco"
        return BetData(odds, favTeam, dc, goals, "Mas de 8.5 Remates", "Mas de 8.5 Corners", "Mas de 4.5 Tarjetas", star, pPick, "3." + (40 + (s % 30)))
    }
}

// CLIENTE Y DTOS DE ESPN
@Serializable data class EspnTeam(val shortDisplayName: String? = null, val displayName: String? = null)
@Serializable data class EspnComp(val homeAway: String? = null, val score: String? = null, val team: EspnTeam? = null)
@Serializable data class EspnStatus(val state: String? = null, val detail: String? = null, val clock: String? = null)
@Serializable data class EspnEvent(val id: String, val date: String? = null, val competitions: List<EspnCompetition> = emptyList())
@Serializable data class EspnCompetition(val competitors: List<EspnComp> = emptyList(), val status: EspnStatusWrapper? = null)
@Serializable data class EspnStatusWrapper(val displayClock: String? = null, val type: EspnStatusType? = null)
@Serializable data class EspnStatusType(val state: String? = null, val shortDetail: String? = null)
@Serializable data class EspnLeagueDto(val name: String? = null)
@Serializable data class EspnResponse(val leagues: List<EspnLeagueDto> = emptyList(), val events: List<EspnEvent> = emptyList())

data class MatchUi(val id: String, val h: String, val a: String, val hs: String?, val ascore: String?, val lg: String, val tag: String, val st: String, val live: Boolean, val time: String, val b: BetData)

fun formatArgTime(dateStr: String?): String {
    if (dateStr.isNullOrBlank() || !dateStr.contains("T")) return "Horario a confirmar"
    return try {
        val tIdx = dateStr.indexOf('T')
        val hUtc = dateStr.substring(tIdx + 1, tIdx + 3).toInt()
        val m = dateStr.substring(tIdx + 4, tIdx + 6)
        val hArg = (hUtc - 3 + 24) % 24
        val hStr = if (hArg < 10) "0$hArg" else "$hArg"
        "$hStr:$m hs (ARG)"
    } catch (e: Exception) {
        "Horario a confirmar"
    }
}

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

// VIEWMODEL CON 10 LIGAS (ARGENTINA, BRASIL, ESPANA, INGLATERRA, CHAMPIONS, MEXICO, URUGUAY, FRANCIA, PORTUGAL, MLS)
class MainViewModel : ViewModel() {
    val leagues = listOf(
        League("arg.1", "Argentina", "ARG"),
        League("bra.1", "Brasil", "BRA"),
        League("esp.1", "Espana", "ESP"),
        League("eng.1", "Premier", "ENG"),
        League("uefa.champions", "Champions", "UEFA"),
        League("mex.1", "Mexico", "MEX"),
        League("uru.1", "Uruguay", "URU"),
        League("fra.1", "Francia", "FRA"),
        League("por.1", "Portugal", "POR"),
        League("usa.1", "MLS", "USA")
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
                    val t = formatArgTime(ev.date)
                    MatchUi(ev.id, hn, an, h?.score, a?.score, res.leagues.firstOrNull()?.name ?: selected.value.name, selected.value.tag, st.trim(), live, t, BetEngine.build(hn, an, ev.id))
                }
            } catch (e: Exception) { error.value = "Sin conexion o sin partidos hoy." }
            finally { loading.value = false }
        }
    }
}

// UI PRINCIPAL JETPACK COMPOSE ULTRA MODERNA
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
                                Text("Horarios en Hora Argentina (GMT-3) - Tiros al Arco", fontSize = 11.sp, color = Color(0xFF94A3B8))
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
                                    Text("[" + l.tag + "] " + l.name, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        if (load) Box(modifier = Modifier.fillMaxSize().weight(1f), Alignment.Center) { CircularProgressIndicator(color = Color(0xFF38BDF8)) }
                        else if (err != null && list.isEmpty()) Box(modifier = Modifier.fillMaxSize().weight(1f), Alignment.Center) { Text(err ?: "", color = Color(0xFFEF4444), fontSize = 13.sp) }
                        else if (list.isEmpty()) Box(modifier = Modifier.fillMaxSize().weight(1f), Alignment.Center) { Text("No hay partidos hoy en " + sel.name, color = Color(0xFF64748B), fontSize = 13.sp) }
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
            // ENCABEZADO CON HORA ARGENTINA DESTACADA
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("[" + m.tag + "] " + m.lg, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                Surface(color = if (m.live) Color(0xFFDC2626) else Color(0xFF064E3B), shape = RoundedCornerShape(4.dp)) {
                    Text(if (m.live) m.st else m.time, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(m.h, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(6.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(if (m.hs != null && m.ascore != null) m.hs + " : " + m.ascore else "VS", fontWeight = FontWeight.Black, fontSize = 13.sp, color = if (m.live) Color(0xFF34D399) else Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Text(m.a, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            }
            Spacer(modifier = Modifier.height(10.dp))

            // CUOTAS 1X2 CON FAVORITO CLARO
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OddsPill("1 (Local)", b.odds.home, Modifier.weight(1f))
                OddsPill("X (Empate)", b.odds.draw, Modifier.weight(1f))
                OddsPill("2 (Visitante)", b.odds.away, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))

            // TARJETA VERDE ESMERALDA: JUGADOR CON MAS TIROS AL ARCO (NOMBRE, APELLIDO, CLUB, POSICION Y DISPAROS)
            Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF0A221C), shape = RoundedCornerShape(10.
