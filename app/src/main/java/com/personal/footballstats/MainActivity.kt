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
import kotlinx.coroutines.flow.StateFlow
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

data class StarPlayer(val name: String, val club: String, val role: String, val avgShots: String, val pick: String, val prob: String, val odds: String)
data class MatchOdds(val home: String, val draw: String, val away: String)
data class BetData(val odds: MatchOdds, val favorite: String, val dc: String, val goals: String, val shots: String, val corn: String, val card: String, val star: StarPlayer, val parlay: String, val parlayOdds: String)
data class League(val code: String, val name: String, val tag: String)

object BetEngine {
    private val GIANTS = setOf("boca", "river", "racing", "independiente", "san lorenzo", "velez", "talleres", "estudiantes", "lanus", "huracan", "flamengo", "palmeiras", "mineiro", "botafogo", "sao paulo", "corinthians", "fluminense", "gremio", "internacional", "cruzeiro", "madrid", "barcelona", "atletico", "athletic", "sevilla", "betis", "villarreal", "city", "liverpool", "arsenal", "chelsea", "tottenham", "united", "psg", "marseille", "monaco", "sporting", "benfica", "porto", "america", "cruz azul", "tigres", "monterrey", "toluca", "penarol", "nacional", "miami")
    private val SQUADS = mapOf(
        "boca" to listOf("Edinson Cavani", "Miguel Merentiel"), "river" to listOf("Miguel Borja", "Facundo Colidio"), "racing" to listOf("A. Maravilla Martinez", "Maximiliano Salas"),
        "independiente" to listOf("Gabriel Avalos", "Santiago Montiel"), "san lorenzo" to listOf("Alexis Cuello", "Iker Muniain"), "velez" to listOf("Braian Romero", "Claudio Aquino"),
        "talleres" to listOf("Federico Girotti", "Ruben Botta"), "estudiantes" to listOf("Guido Carrillo", "Tiago Palacios"), "lanus" to listOf("Walter Bou", "Marcelino Moreno"),
        "huracan" to listOf("Rodrigo Echeverria", "Eric Ramirez"), "belgrano" to listOf("Franco Jara", "Bryan Reyna"), "instituto" to listOf("Damian Puebla", "Facundo Suarez"),
        "riestra" to listOf("Jonathan Herrera", "Antony Alonso"), "central" to listOf("Marco Ruben", "Ignacio Malcorra"), "newell" to listOf("Juan I. Ramirez", "Ever Banega"),
        "gimnasia" to listOf("Rodrigo Castillo", "Matias Abaldo"), "platense" to listOf("Mateo Pellegrino", "Ronaldo Martinez"), "banfield" to listOf("Bruno Sepulveda", "Geronimo Rivera"),
        "tigre" to listOf("Florian Monzon", "Armoa Blas"), "union" to listOf("Nicolas Orsini", "Lucas Gamba"), "flamengo" to listOf("Pedro Guilherme", "Gabriel Barbosa"),
        "palmeiras" to listOf("Estevao Willian", "Flaco Lopez"), "corinthians" to listOf("Memphis Depay", "Yuri Alberto"), "fluminense" to listOf("German Cano", "Jhon Arias"),
        "vasco" to listOf("Pablo Vegetti", "Dimitri Payet"), "cruzeiro" to listOf("Kaio Jorge", "Matheus Pereira"), "gremio" to listOf("Martin Braithwaite", "Diego Costa"),
        "internacional" to listOf("Rafael Borre", "Enner Valencia"), "mineiro" to listOf("Hulk", "Paulinho"), "botafogo" to listOf("Igor Jesus", "Luiz Henrique"),
        "sao paulo" to listOf("Jonathan Calleri", "Lucas Moura"), "madrid" to listOf("Vinicius Jr", "Kylian Mbappe"), "barcelona" to listOf("Robert Lewandowski", "Lamine Yamal"),
        "atletico" to listOf("Julian Alvarez", "Antoine Griezmann"), "city" to listOf("Erling Haaland", "Phil Foden"), "liverpool" to listOf("Mohamed Salah", "Darwin Nunez"),
        "arsenal" to listOf("Bukayo Saka", "Kai Havertz"), "chelsea" to listOf("Cole Palmer", "Nicolas Jackson"), "psg" to listOf("Bradley Barcola", "Ousmane Dembele"),
        "sporting" to listOf("Viktor Gyokeres", "Trincao"), "benfica" to listOf("Angel Di Maria", "Pavlidis"), "porto" to listOf("Samu Omorodion", "Galeno"),
        "america" to listOf("Henry Martin", "Zendejas"), "cruz azul" to listOf("Angel Sepulveda", "Giakoumakis"), "tigres" to listOf("Andre-Pierre Gignac", "Brunetta"),
        "monterrey" to listOf("German Berterame", "Canales"), "penarol" to listOf("Facundo Batista", "Maxi Silvera"), "nacional" to listOf("Ruben Bentancourt", "Santander"), "miami" to listOf("Lionel Messi", "Luis Suarez")
    )
    private val DYNAMIC = listOf("Mateo Retegui", "Valentin Castellanos", "Facundo Colidio", "Milton Gimenez", "Ronaldo Martinez", "Ignacio Pussetto", "Franco Troyansky", "Gaston Veron", "Lucas Janson", "Facundo Pons", "Agustin Ruberto", "Alan Lescano", "Abiel Osorio", "Salomon Rodriguez", "Jhonatan Candia", "Jonathan Herrera")

    fun build(h: String, a: String, id: String): BetData {
        val s = abs((h + a + id).hashCode())
        val hl = h.lowercase(); val al = a.lowercase()
        val hG = GIANTS.any { hl.contains(it) }; val aG = GIANTS.any { al.contains(it) }
        val fav = if (hG && !aG) h else if (!hG && aG) a else h
        val odds = if (fav == h && hG && !aG) MatchOdds("1." + (35 + (s % 20)), "4." + (10 + (s % 30)), "6." + (50 + (s % 40)))
                   else if (fav == a) MatchOdds("5." + (20 + (s % 40)), "3." + (80 + (s % 25)), "1." + (45 + (s % 20)))
                   else MatchOdds("2." + (10 + (s % 25)), "3." + (15 + (s % 20)), "3." + (20 + (s % 30)))
        val dc = if (fav == a) "X2 (Empate o $a)" else "1X ($h o Empate)"
        val clubKey = SQUADS.keys.firstOrNull { hl.contains(it) } ?: SQUADS.keys.firstOrNull { al.contains(it) }
        val squad = if (clubKey != null) SQUADS[clubKey] else null
        val chosen = when {
            squad != null && squad.isNotEmpty() -> squad[abs((id + fav).hashCode()) % squad.size]
            else -> DYNAMIC[abs((id + fav).hashCode()) % DYNAMIC.size]
        }
        val shotAvg = "" + (2 + (s % 3)) + "." + (2 + (s % 7)) + " remates (" + (1 + (s % 2)) + "." + (4 + (s % 5)) + " al arco)"
        val star = StarPlayer(chosen, fav, "Delantero en Cancha", shotAvg, "Mas de 1.5 disparos al arco", "" + (75 + (s % 15)) + "%", "1." + (70 + (s % 25)))
        val goals = if (s % 3 != 0) "Mas de 2.5 Goles" else "Menos de 2.5 Goles"
        val parlay = "FAV: $dc | Goles +1.5 | ${star.name} +1.5 al arco"
        return BetData(odds, fav, dc, goals, "Mas de 8.5 Remates", "Mas de 8.5 Corners", "Mas de 4.5 Tarjetas", star, parlay, "3." + (40 + (s % 30)))
    }
}

fun formatMatchDateTime(d: String?): String {
    if (d.isNullOrBlank() || !d.contains("T") || d.length < 16) return "Hoy"
    return try {
        val dp = d.substring(0, d.indexOf('T')).split("-")
        var day = dp[2].toInt(); val m = dp[1]
        val tp = d.substring(d.indexOf('T') + 1, d.indexOf('T') + 6).split(":")
        var h = tp[0].toInt() - 3
        if (h < 0) { h += 24; day -= 1 } else if (h >= 24) { h -= 24; day += 1 }
        val dStr = if (day < 10) "0$day" else "$day"
        val hStr = if (h < 10) "0$h" else "$h"
        "$dStr/$m • $hStr:${tp[1]} hs"
    } catch (e: Exception) { "Hoy" }
}

@Serializable data class EspnTeam(val shortDisplayName: String? = null, val displayName: String? = null)
@Serializable data class EspnComp(val homeAway: String? = null, val score: String? = null, val team: EspnTeam? = null)
@Serializable data class EspnStatusWrapper(val displayClock: String? = null, val type: EspnStatusType? = null)
@Serializable data class EspnStatusType(val state: String? = null)
@Serializable data class EspnCompetition(val competitors: List<EspnComp> = emptyList(), val status: EspnStatusWrapper? = null)
@Serializable data class EspnEvent(val id: String, val date: String? = null, val competitions: List<EspnCompetition> = emptyList())
@Serializable data class EspnLeagueDto(val name: String? = null)
@Serializable data class EspnResponse(val leagues: List<EspnLeagueDto> = emptyList(), val events: List<EspnEvent> = emptyList())
data class MatchUi(val id: String, val h: String, val a: String, val hs: String?, val ascore: String?, val lg: String, val tag: String, val st: String, val live: Boolean, val dateTime: String, val b: BetData)

interface ApiService {
    @GET("soccer/{lg}/scoreboard")
    suspend fun getScoreboard(@Path("lg") lg: String): EspnResponse
}

class MainViewModel : ViewModel() {
    val leagues = listOf(
        League("arg.1", "Argentina", "ARG"), League("bra.1", "Brasil", "BRA"), League("esp.1", "España", "ESP"),
        League("eng.1", "Premier League", "ENG"), League("uefa.champions", "Champions", "UCL"), League("mex.1", "México", "MEX"),
        League("uru.1", "Uruguay", "URU"), League("fra.1", "Francia", "FRA"), League("por.1", "Portugal", "POR"), League("usa.1", "MLS", "MLS")
    )
    private val _matches = MutableStateFlow<List<MatchUi>>(emptyList())
    val matches: StateFlow<List<MatchUi>> = _matches
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _selected = MutableStateFlow(leagues[0])
    val selected: StateFlow<League> = _selected

    private val api: ApiService by lazy {
        val ok = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        Retrofit.Builder().baseUrl("https://site.api.espn.com/apis/site/v2/sports/").client(ok)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(ApiService::class.java)
    }

    init { load() }
    fun select(league: League) { _selected.value = league; load() }

    fun load() {
        viewModelScope.launch {
            _loading.value = true; _error.value = null
            val sel = _selected.value
            try {
                val res = api.getScoreboard(sel.code)
                val leagueName = res.leagues.firstOrNull()?.name ?: sel.name
                val parsed = res.events.mapNotNull { ev ->
                    val comp = ev.competitions.firstOrNull() ?: return@mapNotNull null
                    val homeComp = comp.competitors.find { it.homeAway == "home" }
                    val awayComp = comp.competitors.find { it.homeAway == "away" }
                    val hName = homeComp?.team?.shortDisplayName ?: homeComp?.team?.displayName ?: "Local"
                    val aName = awayComp?.team?.shortDisplayName ?: awayComp?.team?.displayName ?: "Visitante"
                    val state = comp.status?.type?.state ?: "pre"
                    val isLive = state == "in"
                    val statusText = if (isLive) "EN VIVO ${comp.status?.displayClock ?: ""}".trim() else if (state == "post") "FINAL" else "PROGRAMADO"
                    val dt = formatMatchDateTime(ev.date)
                    MatchUi(ev.id, hName, aName, homeComp?.score, awayComp?.score, leagueName, sel.tag, statusText, isLive, dt, BetEngine.build(hName, aName, ev.id))
                }
                _matches.value = parsed
            } catch (e: Exception) {
                _error.value = "Error al conectar con ESPN: ${e.localizedMessage ?: "Intente nuevamente"}"
            } finally {
                _loading.value = false
            }
        }
    }
}

@Composable
fun OddsPill(label: String, odd: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Color(0xFF1E293B), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xFF334155))) {
        Column(modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 9.sp, color = Color(0xFF94A3B8), textAlign = TextAlign.Center)
            Text(odd, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF38BDF8), textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun MatchCard(m: MatchUi) {
    val b = m.b
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF101726)), border = BorderStroke(1.dp, Color(0xFF1E293B))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("[" + m.tag + "] " + m.lg, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                Surface(color = if (m.live) Color(0xFFDC2626) else Color(0xFF0F2E22), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, if (m.live) Color(0xFFEF4444) else Color(0xFF059669))) {
                    Text(if (m.live) m.st else m.dateTime, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
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
                        Text("★ MAS TIROS AL ARCO", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF34D399))
                        Surface(color = Color(0xFF064E3B), shape = RoundedCornerShape(4.dp)) {
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
                            Text("BET PRO STATS", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF38BDF8))
                            Button(onClick = { vm.load() }, enabled = !load, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))) {
                                Text("↻", fontSize = 16.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Black)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(vm.leagues) { l ->
                                val active = l == sel
                                Surface(
                                    modifier = Modifier.clickable { vm.select(l) },
                                    color = if (active) Color(0xFF0284C7) else Color(0xFF1E293B),
                                    shape = RoundedCornerShape(8.dp),
                                    border = if (active) BorderStroke(1.dp, Color(0xFF38BDF8)) else null
                                ) {
                                    Text("[" + l.tag + "] " + l.name, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        if (load) Box(modifier = Modifier.fillMaxSize().weight(1f), Alignment.Center) { CircularProgressIndicator(color = Color(0xFF38BDF8)) }
                        else if (err != null && list.isEmpty()) Box(modifier = Modifier.fillMaxSize().weight(1f), Alignment.Center) { Text(err ?: "", color = Color(0xFFEF4444), fontSize = 13.sp) }
                        else if (list.isEmpty()) Box(modifier = Modifier.fillMaxSize().weight(1f), Alignment.Center) { Text("No hay partidos hoy en " + sel.name, color = Color(0xFF64748B), fontSize = 13.sp) }
                        else {
                            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(list, key = { it.id }) { m -> MatchCard(m) }
                            }
                        }
                    }
                }
            }
        }
    }
}
