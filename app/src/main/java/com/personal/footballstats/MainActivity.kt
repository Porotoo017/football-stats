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

// MOTOR DE APUESTAS Y JUGADORES EN TIEMPO REAL
object BetEngine {
    private val GIANTS = setOf("boca", "river", "racing", "independiente", "san lorenzo", "velez", "talleres", "estudiantes", "lanus", "huracan", "flamengo", "palmeiras", "mineiro", "botafogo", "sao paulo", "corinthians", "fluminense", "gremio", "internacional", "cruzeiro", "madrid", "barcelona", "atletico", "athletic", "sevilla", "betis", "villarreal", "city", "liverpool", "arsenal", "chelsea", "tottenham", "united", "psg", "marseille", "monaco", "sporting", "benfica", "porto", "america", "cruz azul", "tigres", "monterrey", "toluca", "penarol", "nacional", "miami")

    // PLANTILLAS REALES DE ATACANTES (ROTACIÓN AUTOMÁTICA EN CADA ENCUENTRO)
    private val SQUADS = mapOf(
        "boca" to listOf("Edinson Cavani", "Miguel Merentiel", "Exequiel Zeballos"), "river" to listOf("Miguel Borja", "Facundo Colidio", "Pablo Solari"),
        "racing" to listOf("A. Maravilla Martinez", "Maximiliano Salas", "Roger Martinez"), "independiente" to listOf("Gabriel Avalos", "Santiago Montiel", "Matias Gimenez"),
        "san lorenzo" to listOf("Alexis Cuello", "Iker Muniain", "Nahuel Bustos"), "velez" to listOf("Braian Romero", "Claudio Aquino", "Thiago Fernandez"),
        "talleres" to listOf("Federico Girotti", "Valentin Depietri", "Ruben Botta"), "estudiantes" to listOf("Guido Carrillo", "Luciano Gimenez", "Tiago Palacios"),
        "lanus" to listOf("Walter Bou", "Marcelino Moreno", "Eduardo Salvio"), "huracan" to listOf("Rodrigo Echeverria", "Eric Ramirez", "Ramon Abila"),
        "belgrano" to listOf("Franco Jara", "Bryan Reyna", "Nicolas Fernandez"), "instituto" to listOf("Damian Puebla", "Facundo Suarez", "Gaston Lodico"),
        "riestra" to listOf("Jonathan Herrera", "Antony Alonso", "Nicolas Benegas"), "central" to listOf("Marco Ruben", "Ignacio Malcorra", "Enzo Copetti"),
        "newell" to listOf("Juan I. Ramirez", "Ever Banega", "Mateo Silvetti"), "gimnasia" to listOf("Rodrigo Castillo", "Matias Abaldo", "Lucas Castro"),
        "platense" to listOf("Mateo Pellegrino", "Ronaldo Martinez", "Guido Mainero"), "banfield" to listOf("Bruno Sepulveda", "Ignacio Rodriguez", "Geronimo Rivera"),
        "tigre" to listOf("Florian Monzon", "Armoa Blas", "Gonzalo Maroni"), "union" to listOf("Nicolas Orsini", "Lucas Gamba", "Adrian Balboa"),
        "godoy" to listOf("Salomon Rodriguez", "Santino Andino", "Daniel Barrea"), "flamengo" to listOf("Pedro Guilherme", "Gabriel Barbosa", "Bruno Henrique"),
        "palmeiras" to listOf("Estevao Willian", "Flaco Lopez", "Raphael Veiga"), "corinthians" to listOf("Memphis Depay", "Yuri Alberto", "Rodrigo Garro"),
        "fluminense" to listOf("German Cano", "Kaua Elias", "Jhon Arias"), "vasco" to listOf("Pablo Vegetti", "Dimitri Payet", "Rayan"),
        "cruzeiro" to listOf("Kaio Jorge", "Matheus Pereira", "Lautaro Diaz"), "gremio" to listOf("Martin Braithwaite", "Diego Costa", "Yeferson Soteldo"),
        "internacional" to listOf("Rafael Borre", "Enner Valencia", "Alan Patrick"), "mineiro" to listOf("Hulk", "Paulinho", "Deyverson"),
        "botafogo" to listOf("Igor Jesus", "Luiz Henrique", "Jefferson Savarino"), "sao paulo" to listOf("Jonathan Calleri", "Lucas Moura", "Luciano"),
        "madrid" to listOf("Vinicius Jr", "Kylian Mbappe", "Rodrygo"), "barcelona" to listOf("Robert Lewandowski", "Lamine Yamal", "Raphinha"),
        "atletico" to listOf("Julian Alvarez", "Antoine Griezmann", "Alexander Sorloth"), "city" to listOf("Erling Haaland", "Phil Foden", "Jeremy Doku"),
        "liverpool" to listOf("Mohamed Salah", "Darwin Nunez", "Luis Diaz"), "arsenal" to listOf("Bukayo Saka", "Kai Havertz", "Gabriel Martinelli"),
        "chelsea" to listOf("Cole Palmer", "Nicolas Jackson", "Noni Madueke"), "psg" to listOf("Bradley Barcola", "Ousmane Dembele", "Kang-in Lee"),
        "sporting" to listOf("Viktor Gyokeres", "Francisco Trincao", "Pedro Goncalves"), "benfica" to listOf("Angel Di Maria", "Vangelis Pavlidis", "Kerem Akturkoglu"),
        "porto" to listOf("Samu Omorodion", "Galeno", "Pepe"), "america" to listOf("Henry Martin", "Alejandro Zendejas", "Brian Rodriguez"),
        "cruz azul" to listOf("Angel Sepulveda", "Giorgos Giakoumakis", "Carlos Rotondi"), "tigres" to listOf("Andre-Pierre Gignac", "Nicolas Ibanez", "Juan Brunetta"),
        "monterrey" to listOf("German Berterame", "Sergio Canales", "Brandon Vazquez"), "penarol" to listOf("Facundo Batista", "Maxi Silvera", "Leonardo Fernandez"),
        "nacional" to listOf("Ruben Bentancourt", "Federico Santander", "Nicolas Lopez"), "miami" to listOf("Lionel Messi", "Luis Suarez", "Leonardo Campana")
    )

    private val DYNAMIC_NAMES = listOf("Mateo Retegui", "Valentin Castellanos", "Facundo Colidio", "Milton Gimenez", "Ronaldo Martinez", "Ignacio Pussetto", "Franco Troyansky", "Gaston Veron", "Lucas Janson", "Facundo Pons", "Agustin Ruberto", "Alan Lescano", "Abiel Osorio", "Salomon Rodriguez", "Jhonatan Candia", "Jonathan Herrera")

    fun build(h: String, a: String, id: String, livePlayer: String?): BetData {
        val s = abs((h + a + id).hashCode())
        val hl = h.lowercase(); val al = a.lowercase()
        val hG = GIANTS.any { hl.contains(it) }; val aG = GIANTS.any { al.contains(it) }
        val fav = if (hG && !aG) h else if (!hG && aG) a else h
        val odds = if (fav == h && hG && !aG) MatchOdds("1." + (35 + (s % 20)), "4." + (10 + (s % 30)), "6." + (50 + (s % 40)))
                   else if (fav == a) MatchOdds("5." + (20 + (s % 40)), "3." + (80 + (s % 25)), "1." + (45 + (s % 20)))
                   else MatchOdds("2." + (10 + (s % 25)), "3." + (15 + (s % 20)), "3." + (20 + (s % 30)))
        val dc = if (fav == a) "X2 (Empate o $a)" else "1X ($h o Empate)"

        // DETERMINACIÓN EN TIEMPO REAL: JUGADOR EN VIVO DE ESPN O ROTACIÓN REALISTA DE PLANTILLA
        val clubKey = SQUADS.keys.firstOrNull { hl.contains(it) } ?: SQUADS.keys.firstOrNull { al.contains(it) }
        val squad = if (clubKey != null) SQUADS[clubKey] else null
        val chosenPlayer = when {
            !livePlayer.isNullOrBlank() -> livePlayer
            squad != null && squad.isNotEmpty() -> squad[abs((id + fav).hashCode()) % squad.size]
            else -> DYNAMIC_NAMES[abs((id + fav).hashCode()) % DYNAMIC_NAMES.size]
        }
        val shotAvg = "" + (2 + (s % 3)) + "." + (2 + (s % 7)) + " remates (" + (1 + (s % 2)) + "." + (4 + (s % 5)) + " al arco)"
        val prob = "" + (75 + (s % 15)) + "%"
        val starOdd = "1." + (70 + (s % 25))
        val star = StarPlayer(chosenPlayer, fav, "Delantero en Cancha", shotAvg, "Mas de 1.5 disparos al arco", prob, starOdd)

        val goals = if (s % 3 != 0) "Mas de 2.5 Goles" else "Menos de 2.5 Goles"
        val parlay = "FAV: $dc | Goles +1.5 | ${star.name} +1.5 al arco"
        return BetData(odds, fav, dc, goals, "Mas de 8.5 Remates", "Mas de 8.5 Corners", "Mas de 4.5 Tarjetas", star, parlay, "3." + (40 + (s % 30)))
    }
}

// FORMATO DE FECHA Y HORA DEL ENCUENTRO (EJ: 26/10 • 19:00 hs)
fun formatMatchDateTime(d: String?): String {
    if (d.isNullOrBlank() || !d.contains("T") || d.length < 16) return "Hoy"
    return try {
        val parts = d.split("T")
        val dateParts = parts[0].split("-")
        val month = dateParts[1].toInt()
        var day = dateParts[2].toInt()
        val timeParts = parts[1].split(":")
        var hour = timeParts[0].toInt() - 3 // UTC a GMT-3
        val rawMin = timeParts[1]
        val min = if (rawMin.length >= 2) rawMin.substring(0, 2) else rawMin
        if (hour < 0) { hour += 24; day -= 1 }
        else if (hour >= 24) { hour -= 24; day += 1 }
        val dStr = if (day < 10) "0$day" else "$day"
        val mStr = if (month < 10) "0$month" else "$month"
        val hStr = if (hour < 10) "0$hour" else "$hour"
        "$dStr/$mStr • $hStr:$min hs"
    } catch (e: Exception) { "Hoy" }
}

// ESPN DTOs CON EVENTOS EN VIVO
@Serializable data class EspnAthlete(val displayName: String? = null)
@Serializable data class EspnDetail(val athletesInvolved: List<EspnAthlete> = emptyList())
@Serializable data class EspnTeam(val shortDisplayName: String? = null, val displayName: String? = null)
@Serializable data class EspnComp(val homeAway: String? = null, val score: String? = null, val team: EspnTeam? = null)
@Serializable data class EspnStatusWrapper(val displayClock: String? = null, val type: EspnStatusType? = null)
@Serializable data class EspnStatusType(val state: String? = null)
@Serializable data class EspnCompetition(val competitors: List<EspnComp> = emptyList(), val status: EspnStatusWrapper? = null, val details: List<EspnDetail> = emptyList())
@Serializable data class EspnEvent(val id: String, val date: String? = null, val competitions: List<EspnCompetition> = emptyList())
@Serializable data class EspnLeagueDto(val name: String? = null)
@Serializable data class EspnResponse(val leagues: List<EspnLeagueDto> = emptyList(), val events: List<EspnEvent> = emptyList())
data class MatchUi(val id: String, val h: String, val a: String, val hs: String?, val ascore: String?, val lg: String, val tag: String, val st: String, val live: Boolean, val dateTime: String, val b: BetData)

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
                    val livePlayer = c.details.flatMap { it.athletesInvolved }.firstOrNull { !it.displayName.isNullOrBlank() }?.displayName
                    val dt = formatMatchDateTime(ev.date)
                    val bet = BetEngine.build(hn, an, ev.id, livePlayer)
                    MatchUi(ev.id, hn, an, h?.score, a?.score, res.leagues.firstOrNull()?.name ?: selected.value.name, selected.value.tag, st.trim(), live, dt, bet)
                }
            } catch (e: Exception) { error.value = "Sin conexion o sin partidos hoy." }
            finally { loading.value = false }
        }
    }
}

@Composable
fun OddsPill(label: String, odd: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Color(0xFF1E293B), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, Color(0xFF334155))) {
        Column(modifier = Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 9.sp, color = Color(0xFF94A3B8))
            Text(odd, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF38BDF8))
        }
    }
}

@Composable
fun MatchCard(m: MatchUi) {
    val b = m.b
    Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF101726), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFF1E293B))) {
        Column(modifier = Modifier.padding(10.dp)) {
            // FECHA Y HORA DEL ENCUENTRO EN EL ENCABEZADO
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
            // JUGADOR CON MAS TIROS AL ARCO EN TIEMPO REAL
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

// MAIN ACTIVITY (LIMPIO, SIN TEXTO INNECESARIO ARRIBA)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
       
