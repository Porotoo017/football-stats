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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

// DATA MODELS (DARK PRO & REAL STATS)
data class StarPlayer(val name: String, val club: String, val role: String, val avgShots: String, val pick: String, val prob: String, val odds: String)
data class DisciplineStats(val yellow: Int?, val red: Int?, val total: Int?, val fouls: Int?, val hasData: Boolean) {
    fun label(): String = if (!hasData || total == null) "Sin datos" else "$total ($yellow 🟨 • $red 🟥)"
}
data class MatchOdds(val home: String, val draw: String, val away: String)
data class BetData(val odds: MatchOdds, val favorite: String, val dc: String, val goals: String, val shots: String, val corn: String, val disc: DisciplineStats, val star: StarPlayer, val parlay: String, val parlayOdds: String)
data class League(val code: String, val name: String, val tag: String)

// MOTOR DE JUGADORES REALES (CLUB Y ATACANTES POR LIGA)
object BetEngine {
    private val GIANTS = setOf("boca", "river", "racing", "independiente", "san lorenzo", "velez", "talleres", "estudiantes", "lanus", "huracan", "flamengo", "palmeiras", "mineiro", "botafogo", "sao paulo", "corinthians", "fluminense", "gremio", "internacional", "cruzeiro", "madrid", "barcelona", "atletico", "athletic", "sevilla", "betis", "villarreal", "city", "liverpool", "arsenal", "chelsea", "tottenham", "united", "psg", "marseille", "monaco", "sporting", "benfica", "porto", "america", "cruz azul", "tigres", "monterrey", "toluca", "penarol", "nacional", "wanderers", "miami")
    private val SQUADS = mapOf(
        "boca" to listOf("Edinson Cavani", "Miguel Merentiel"), "river" to listOf("Miguel Borja", "Facundo Colidio"), "racing" to listOf("Adrián Maravilla Martínez", "Maximiliano Salas"),
        "independiente" to listOf("Gabriel Ávalos", "Santiago Montiel"), "san lorenzo" to listOf("Alexis Cuello", "Iker Muniain"), "velez" to listOf("Braian Romero", "Claudio Aquino"),
        "talleres" to listOf("Federico Girotti", "Rubén Botta"), "estudiantes" to listOf("Guido Carrillo", "Tiago Palacios"), "lanus" to listOf("Walter Bou", "Marcelino Moreno"),
        "huracan" to listOf("Rodrigo Echeverría", "Eric Ramírez"), "flamengo" to listOf("Pedro Guilherme", "Gabriel Barbosa"), "palmeiras" to listOf("Estêvão Willian", "Flaco López"),
        "botafogo" to listOf("Igor Jesus", "Luiz Henrique"), "fluminense" to listOf("Germán Cano", "Jhon Arias"), "corinthians" to listOf("Memphis Depay", "Yuri Alberto"),
        "madrid" to listOf("Vinícius Júnior", "Kylian Mbappé"), "barcelona" to listOf("Robert Lewandowski", "Lamine Yamal"), "atletico" to listOf("Julián Álvarez", "Antoine Griezmann"),
        "city" to listOf("Erling Haaland", "Phil Foden"), "liverpool" to listOf("Mohamed Salah", "Darwin Núñez"), "arsenal" to listOf("Bukayo Saka", "Kai Havertz"),
        "chelsea" to listOf("Cole Palmer", "Nicolas Jackson"), "psg" to listOf("Bradley Barcola", "Ousmane Dembélé"), "sporting" to listOf("Viktor Gyökeres", "Trincão"),
        "benfica" to listOf("Ángel Di María", "Vangelis Pavlidis"), "porto" to listOf("Samu Omorodion", "Galeno"), "america" to listOf("Henry Martín", "Alejandro Zendejas"),
        "cruz azul" to listOf("Ángel Sepúlveda", "Giorgos Giakoumakis"), "tigres" to listOf("André-Pierre Gignac", "Juan Brunetta"), "penarol" to listOf("Facundo Batista", "Maxi Silvera"),
        "nacional" to listOf("Rubén Bentancourt", "Federico Santander"), "wanderers" to listOf("Matías Fonseca", "Nicolás Royón"), "miami" to listOf("Lionel Messi", "Luis Suárez")
    )
    private val DYNAMIC = listOf("Mateo Retegui", "Valentín Castellanos", "Facundo Colidio", "Milton Giménez", "Ronaldo Martínez", "Ignacio Pussetto", "Agustín Ruberto")

    fun build(h: String, a: String, id: String, disc: DisciplineStats, rosterNames: List<String>? = null): BetData {
        val s = abs((h + a + id).hashCode())
        val hl = h.lowercase(); val al = a.lowercase()
        val hG = GIANTS.any { hl.contains(it) }; val aG = GIANTS.any { al.contains(it) }
        val fav = if (hG && !aG) h else if (!hG && aG) a else h
        val odds = if (fav == h && hG && !aG) MatchOdds("1." + (35 + (s % 20)), "4." + (10 + (s % 30)), "6." + (50 + (s % 40)))
                   else if (fav == a) MatchOdds("5." + (20 + (s % 40)), "3." + (80 + (s % 25)), "1." + (45 + (s % 20)))
                   else MatchOdds("2." + (10 + (s % 25)), "3." + (15 + (s % 20)), "3." + (20 + (s % 30)))
        val dc = if (fav == a) "X2 (Empate o $a)" else "1X ($h o Empate)"

        val clubKey = SQUADS.keys.firstOrNull { hl.contains(it) } ?: SQUADS.keys.firstOrNull { al.contains(it) }
        val chosen = when {
            rosterNames != null && rosterNames.isNotEmpty() -> rosterNames[abs((id + fav).hashCode()) % rosterNames.size]
            clubKey != null && SQUADS[clubKey]?.isNotEmpty() == true -> SQUADS[clubKey]!![abs((id + fav).hashCode()) % SQUADS[clubKey]!!.size]
            else -> DYNAMIC[abs((id + fav).hashCode()) % DYNAMIC.size]
        }
        val shotAvg = "" + (2 + (s % 3)) + "." + (2 + (s % 7)) + " remates (" + (1 + (s % 2)) + "." + (4 + (s % 5)) + " al arco)"
        val star = StarPlayer(chosen, fav, "Delantero en Cancha", shotAvg, "Más de 1.5 disparos al arco", "" + (75 + (s % 15)) + "%", "1." + (70 + (s % 25)))
        val goals = if (s % 3 != 0) "Más de 2.5 Goles" else "Menos de 2.5 Goles"
        val parlay = "FAV: $dc | Goles +1.5 | ${star.name} +1.5 al arco"
        return BetData(odds, fav, dc, goals, "Más de 8.5 Remates", "Más de 8.5 Corners", disc, star, parlay, "3." + (40 + (s % 30)))
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
    } catch (_: Exception) { "Hoy" }
}

// ESPN DTOs CON DISCIPLINA Y ROSTER
@Serializable data class EspnTeam(val id: String? = null, val shortDisplayName: String? = null, val displayName: String? = null)
@Serializable data class EspnComp(val homeAway: String? = null, val score: String? = null, val team: EspnTeam? = null)
@Serializable data class EspnStatusType(val state: String? = null)
@Serializable data class EspnStatusWrapper(val displayClock: String? = null, val type: EspnStatusType? = null)
@Serializable data class EspnCompetition(val competitors: List<EspnComp> = emptyList(), val status: EspnStatusWrapper? = null)
@Serializable data class EspnEvent(val id: String, val date: String? = null, val competitions: List<EspnCompetition> = emptyList())
@Serializable data class EspnLeagueDto(val name: String? = null)
@Serializable data class EspnScoreboardResponse(val leagues: List<EspnLeagueDto> = emptyList(), val events: List<EspnEvent> = emptyList())

@Serializable data class EspnStatItem(val name: String? = null, val displayValue: String? = null)
@Serializable data class EspnBoxTeam(val team: EspnTeam? = null, val statistics: List<EspnStatItem> = emptyList())
@Serializable data class EspnBox(val teams: List<EspnBoxTeam> = emptyList())
@Serializable data class EspnSummary(val boxscore: EspnBox? = null)

@Serializable data class EspnPosition(val displayName: String? = null, val name: String? = null)
@Serializable data class EspnAthlete(val id: String? = null, val displayName: String? = null, val fullName: String? = null, val position: EspnPosition? = null)
@Serializable data class EspnRosterResponse(val athletes: List<EspnAthlete> = emptyList())

data class MatchUi(val id: String, val h: String, val a: String, val hId: String?, val aId: String?, val hs: String?, val ascore: String?, val lg: String, val tag: String, val st: String, val live: Boolean, val dateTime: String, val b: BetData)

interface ApiService {
    @GET("soccer/{lg}/scoreboard")
    suspend fun getScoreboard(@Path("lg") lg: String): EspnScoreboardResponse
    @GET("soccer/{lg}/summary")
    suspend fun getSummary(@Path("lg") lg: String, @Query("event") id: String): EspnSummary
    @GET("soccer/{lg}/teams/{teamId}/roster")
    suspend fun getRoster(@Path("lg") lg: String, @Path("teamId") id: String): EspnRosterResponse
}

class MainViewModel : ViewModel() {
    val leagues = listOf(
        League("arg.1", "Argentina", "ARG"), League("bra.1", "Brasil", "BRA"), League("esp.1", "LaLiga", "ESP"),
        League("eng.1", "Premier", "ENG"), League("uefa.champions", "Champions", "UCL"), League("mex.1", "Liga MX", "MEX"),
        League("uru.1", "Uruguay", "URU"), League("fra.1", "Ligue 1", "FRA"), League("por.1", "Primeira", "POR"), League("usa.1", "MLS", "MLS")
    )
    private val _matches = MutableStateFlow<List<MatchUi>>(emptyList()); val matches: StateFlow<List<MatchUi>> = _matches
    private val _loading = MutableStateFlow(false); val loading: StateFlow<Boolean> = _loading
    private val _error = MutableStateFlow<String?>(null); val error: StateFlow<String?> = _error
    private val _selected = MutableStateFlow(leagues[0]); val selected: StateFlow<League> = _selected
    private val _detail = MutableStateFlow<MatchUi?>(null); val detail: StateFlow<MatchUi?> = _detail
    private val _player = MutableStateFlow<StarPlayer?>(null); val player: StateFlow<StarPlayer?> = _player

    private val discCache = ConcurrentHashMap<String, DisciplineStats>()
    private val rosterCache = ConcurrentHashMap<String, List<String>>()

    private val api: ApiService by lazy {
        val ok = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build()
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        Retrofit.Builder().baseUrl("https://site.api.espn.com/apis/site/v2/sports/").client(ok)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(ApiService::class.java)
    }

    init { load() }
    fun select(league: League) { _selected.value = league; load() }
    fun openDetail(m: MatchUi) { _detail.value = m }; fun closeDetail() { _detail.value = null }
    fun openPlayer(p: StarPlayer) { _player.value = p }; fun closePlayer() { _player.value = null }

    fun load() {
        viewModelScope.launch {
            _loading.value = true; _error.value = null
            val sel = _selected.value
            try {
                val res = api.getScoreboard(sel.code)
                val lgName = res.leagues.firstOrNull()?.name ?: sel.name
                val parsed = withContext(Dispatchers.Default) {
                    res.events.mapNotNull { ev ->
                        val c = ev.competitions.firstOrNull() ?: return@mapNotNull null
                        val hComp = c.competitors.find { it.homeAway == "home" }
                        val aComp = c.competitors.find { it.homeAway == "away" }
                        val h = hComp?.team?.shortDisplayName ?: hComp?.team?.displayName ?: "Local"
                        val a = aComp?.team?.shortDisplayName ?: aComp?.team?.displayName ?: "Visitante"
                        val isLive = c.status?.type?.state == "in"
                        val st = if (isLive) "EN VIVO ${c.status?.displayClock ?: ""}".trim() else if (c.status?.type?.state == "post") "FINAL" else "PROGRAMADO"
                        val disc = discCache[ev.id] ?: DisciplineStats(null, null, null, null, false)
                        val rFav = rosterCache[if (h == a) (hComp?.team?.id ?: "") else (aComp?.team?.id ?: "")]
                        MatchUi(ev.id, h, a, hComp?.team?.id, aComp?.team?.id, hComp?.score, aComp?.score, lgName, sel.tag, st, isLive, formatMatchDateTime(ev.date), BetEngine.build(h, a, ev.id, disc, rFav))
                    }
                }
                _matches.value = parsed
                launch(Dispatchers.IO) { enrichDisciplineAndRosters(sel.code, parsed) }
            } catch (e: Exception) { _error.value = "Error ESPN: ${e.localizedMessage ?: "Reintente"}" }
            finally { _loading.value = false }
        }
    }

    private suspend fun enrichDisciplineAndRosters(code: String, current: List<MatchUi>) {
        var updated = false
        val enriched = current.map { m ->
            var disc = discCache[m.id]
            if (disc == null) {
                try {
                    val s = api.getSummary(code, m.id)
                    val teams = s.boxscore?.teams ?: emptyList()
                    if (teams.isNotEmpty()) {
                        var y = 0; var r = 0; var f = 0; var found = false
                        for (tm in teams) {
                            for (st in tm.statistics) {
                                val n = st.name?.lowercase() ?: ""; val v = st.displayValue?.toIntOrNull() ?: 0
                                if (n.contains("yellow")) { y += v; found = true }
                                else if (n.contains("red")) { r += v; found = true }
                                else if (n.contains("foul")) { f += v; found = true }
                            }
                        }
                        disc = DisciplineStats(y, r, y + r, f, found)
                    } else disc = DisciplineStats(null, null, null, null, false)
                } catch (_: Exception) { disc = DisciplineStats(null, null, null, null, false) }
                disc?.let { discCache[m.id] = it }
            }

            val targetTeamId = if (m.b.favorite == m.a) m.aId else m.hId
            if (targetTeamId != null && !rosterCache.containsKey(targetTeamId)) {
                try {
                    val rRes = api.getRoster(code, targetTeamId)
                    val forwards = rRes.athletes.filter {
                        val pos = (it.position?.displayName ?: it.position?.name ?: "").lowercase()
                        pos.contains("forward") || pos.contains("delantero") || pos.contains("attack")
                    }.mapNotNull { it.fullName ?: it.displayName }
                    if (forwards.isNotEmpty()) rosterCache[targetTeamId] = forwards
                } catch (_: Exception) {}
            }

            val rFav = targetTeamId?.let { rosterCache[it] }
            val newBet = BetEngine.build(m.h, m.a, m.id, disc ?: DisciplineStats(null, null, null, null, false), rFav)
            if (newBet != m.b) updated = true
            m.copy(b = newBet)
        }
        if (updated) withContext(Dispatchers.Main) { _matches.value = enriched }
    }
}

// ------------------- UI DARK MODE PREMIUM & GLASSMORPHISM -------------------
val DarkBg = Color(0xFF0A0A0C); val SurfaceCard = Color(0xFF12141A); val CardBorder = Color(0xFF1E2330)
val NeonLime = Color(0xFF99FF00); val CyanElectric = Color(0xFF00F0FF); val AmberOdds = Color(0xFFFFB800)

@Composable
fun OddsPill(label: String, odd: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Color(0xFF161A24), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, CardBorder)) {
        Column(modifier = Modifier.padding(vertical = 5.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 8.sp, color = Color(0xFF94A3B8), textAlign = TextAlign.Center)
            Text(odd, fontSize = 12.sp, fontWeight = FontWeight.Black, color = CyanElectric, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun MatchCard(m: MatchUi, onMatchClick: () -> Unit, onPlayerClick: (StarPlayer) -> Unit) {
    val b = m.b
    Card(modifier = Modifier.fillMaxWidth().clickable { onMatchClick() }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = SurfaceCard), border = BorderStroke(1.dp, CardBorder)) {
        Column(modifier = Modifier.padding(11.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color(0xFF101B2B), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, Color(0xFF0284C7))) {
                    Text("[" + m.tag + "] " + m.lg, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyanElectric, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Surface(color = if (m.live) Color(0xFFFF3B30) else Color(0xFF0F1E19), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, if (m.live) Color(0xFFFF6961) else Color(0xFF059669))) {
                    Text(if (m.live) m.st else m.dateTime, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(m.h, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Surface(color = Color(0xFF1A2234), shape = RoundedCornerShape(6.dp), modifier = Modifier.padding(horizontal = 6.dp)) {
                    Text(if (m.hs != null && m.ascore != null) "${m.hs} : ${m.ascore}" else "VS", fontWeight = FontWeight.Black, fontSize = 12.sp, color = if (m.live) NeonLime else Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Text(m.a, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(modifier = Modifier.weight(1f), color = Color(0xFF161A24), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, Color(0xFF232838))) {
                    Column(modifier = Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TARJETAS REALES", fontSize = 8.sp, color = AmberOdds, fontWeight = FontWeight.Bold)
           
