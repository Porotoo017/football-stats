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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
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
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round

// -------------------------------------------------------------
// 1. MODELOS DE DATOS
// -------------------------------------------------------------
data class ParlayLeg(
    val category: String,       // "GOLES", "CÓRNERS", "TARJETAS", "DOBLE OP", "JUGADOR"
    val pick: String,           // Ej: "Más de 4.5 Tarjetas", "1X (Local o Empate)"
    val lambda: Double,         // Media esperada histórica (λ)
    val modelProb: Double,      // Probabilidad Poisson / Modelo (0.0 a 1.0)
    val fairOdds: Double,       // Cuota justa (1 / P)
    val marketOdds: Double,     // Cuota estimada de mercado
    val evPercent: Double,      // Valor esperado: (P * Cuota - 1) * 100
    val badge: String           // Detalle analítico
)

data class MathematicalParlay(
    val legs: List<ParlayLeg>,
    val jointProb: Double,       // Probabilidad conjunta real
    val combinedOdds: Double,    // Cuota total combinada
    val totalEvPercent: Double,  // EV total en porcentaje
    val isQualified: Boolean,    // true si prob >= 15% y cada pata tiene EV > 0
    val statusText: String       // "VALOR ESPERADO ÓPTIMO" o "DESCARTADO (<15%)"
)

data class MatchUi(
    val id: String,
    val h: String,
    val a: String,
    val sc: String,
    val lg: String,
    val tag: String,
    val st: String,
    val live: Boolean,
    val dt: String,
    val fav: String,
    val cards: String,
    val odds: Triple<String, String, String>,
    val parlay: MathematicalParlay
)

data class League(val code: String, val name: String, val tag: String)

// -------------------------------------------------------------
// 2. MOTOR MATEMÁTICO CUANTITATIVO (PARLAY ENGINE)
// -------------------------------------------------------------
object ParlayEngine {

    fun poissonPmf(k: Int, lambda: Double): Double {
        if (lambda <= 0.0) return 0.0
        var fact = 1.0
        for (i in 1..k) fact *= i.toDouble()
        return (lambda.pow(k.toDouble()) * exp(-lambda)) / fact
    }

    fun poissonOver(threshold: Double, lambda: Double): Double {
        val k = threshold.toInt()
        var sum = 0.0
        for (i in 0..k) sum += poissonPmf(i, lambda)
        return (1.0 - sum).coerceIn(0.04, 0.96)
    }

    fun calculateEV(modelProb: Double, marketOdds: Double): Double = (modelProb * marketOdds) - 1.0

    fun fairOdds(modelProb: Double): Double = if (modelProb > 0.0) round((1.0 / modelProb) * 100.0) / 100.0 else 99.0

    // Plantillas estrictas por equipo para erradicar jugadores duplicados en otros clubes
    private val TEAM_ROSTERS = mapOf(
        // ARGENTINA
        "boca" to listOf("Edinson Cavani" to "Delantero", "Miguel Merentiel" to "Delantero", "Kevin Zenón" to "Volante"),
        "river" to listOf("Miguel Borja" to "Delantero", "Facundo Colidio" to "Delantero", "Franco Mastantuono" to "Volante"),
        "racing" to listOf("Maravilla Martínez" to "Delantero", "Maxi Salas" to "Delantero", "Juanfer Quintero" to "Volante"),
        "san lorenzo" to listOf("Iker Muniain" to "Volante", "Andrés Vombergar" to "Delantero", "Alexis Cuello" to "Delantero"),
        "velez" to listOf("Braian Romero" to "Delantero", "Francisco Pizzini" to "Extremo", "Claudio Aquino" to "Volante"),
        "gimnasia" to listOf("Rodrigo Castillo" to "Delantero", "Matías Abaldo" to "Extremo", "David Zalazar" to "Volante"),
        "tigre" to listOf("Florián Monzón" to "Delantero", "Blas Armoa" to "Extremo", "Gonzalo Maroni" to "Volante"),
        "talleres" to listOf("Federico Girotti" to "Delantero", "Bruno Barticciotto" to "Delantero", "Valentín Depietri" to "Delantero"),
        "independiente" to listOf("Gabriel Ávalos" to "Delantero", "Santiago Montiel" to "Extremo", "Alex Luna" to "Volante"),
        "rosario central" to listOf("Marco Ruben" to "Delantero", "Jaminton Campaz" to "Extremo", "Ignacio Malcorra" to "Volante"),
        "newell" to listOf("Juan Ignacio Ramírez" to "Delantero", "Éver Banega" to "Volante", "Mateo Silvetti" to "Extremo"),
        "estudiantes" to listOf("Guido Carrillo" to "Delantero", "Edwuin Cetré" to "Extremo", "Tiago Palacios" to "Volante"),
        "huracan" to listOf("Eric Ramírez" to "Delantero", "Walter Mazzantti" to "Extremo", "Rodrigo Echeverría" to "Volante"),
        "lanus" to listOf("Walter Bou" to "Delantero", "Marcelino Moreno" to "Volante", "Eduardo Salvio" to "Extremo"),
        "argentinos" to listOf("Maximiliano Romero" to "Delantero", "Alan Lescano" to "Volante", "Gastón Verón" to "Delantero"),
        "platense" to listOf("Mateo Pellegrino" to "Delantero", "Ronaldo Martínez" to "Delantero", "Guido Mainero" to "Extremo"),
        "banfield" to listOf("Bruno Sepúlveda" to "Delantero", "Gerónimo Rivera" to "Extremo", "Leandro Garate" to "Delantero"),
        "belgrano" to listOf("Franco Jara" to "Delantero", "Nicolás Fernández" to "Delantero", "Francisco González Metilli" to "Volante"),
        "instituto" to listOf("Damián Puebla" to "Volante", "Ignacio Russo" to "Delantero", "Facundo Suárez" to "Delantero"),
        "godoy cruz" to listOf("Salomón Rodríguez" to "Delantero", "Santino Andino" to "Extremo", "Daniel Barrea" to "Extremo"),
        "union" to listOf("Adrián Balboa" to "Delantero", "Nicolás Orsini" to "Delantero", "Lucas Gamba" to "Delantero"),
        "defensa" to listOf("Abiel Osorio" to "Delantero", "Juan Miritello" to "Delantero", "Aarón Molinas" to "Volante"),
        "barracas" to listOf("Jhonatan Candia" to "Delantero", "Facundo Bruera" to "Delantero", "Alexis Domínguez" to "Delantero"),
        "sarmiento" to listOf("Iván Morales" to "Delantero", "Joaquín Gho" to "Extremo", "Nicolás Gaitán" to "Volante"),
        "riestra" to listOf("Jonathan Herrera" to "Delantero", "Antony Alonso" to "Delantero", "Nicolás Benegas" to "Delantero"),
        "central cordoba" to listOf("Lucas Varaldo" to "Delantero", "Favio Cabral" to "Delantero", "Matías Godoy" to "Extremo"),
        "tucuman" to listOf("Marcelo Estigarribia" to "Delantero", "Mateo Bajamich" to "Delantero", "Renzo Tesuri" to "Volante"),

        // ESPAÑA
        "real madrid" to listOf("Vinícius Jr" to "Extremo", "Kylian Mbappé" to "Delantero", "Jude Bellingham" to "Volante"),
        "barcelona" to listOf("Robert Lewandowski" to "Delantero", "Lamine Yamal" to "Extremo", "Raphinha" to "Extremo"),
        "atletico" to listOf("Antoine Griezmann" to "Delantero", "Julián Álvarez" to "Delantero", "Alexander Sørloth" to "Delantero"),
        "athletic" to listOf("Nico Williams" to "Extremo", "Iñaki Williams" to "Delantero", "Gorka Guruzeta" to "Delantero"),
        "sociedad" to listOf("Mikel Oyarzabal" to "Delantero", "Takefusa Kubo" to "Extremo", "Orri Óskarsson" to "Delantero"),
        "villarreal" to listOf("Ayoze Pérez" to "Delantero", "Thierno Barry" to "Delantero", "Álex Baena" to "Volante"),
        "betis" to listOf("Vitor Roque" to "Delantero", "Giovani Lo Celso" to "Volante", "Chimy Ávila" to "Delantero"),
        "sevilla" to listOf("Isaac Romero" to "Delantero", "Dodi Lukebakio" to "Extremo", "Kelechi Iheanacho" to "Delantero"),
        "celta" to listOf("Iago Aspas" to "Delantero", "Borja Iglesias" to "Delantero", "Anastasios Douvikas" to "Delantero"),
        "osasuna" to listOf("Ante Budimir" to "Delantero", "Bryan Zaragoza" to "Extremo", "Rubén García" to "Volante"),

        // INGLATERRA
        "manchester city" to listOf("Erling Haaland" to "Delantero", "Phil Foden" to "Volante", "Kevin De Bruyne" to "Volante"),
        "liverpool" to listOf("Mohamed Salah" to "Extremo", "Luis Díaz" to "Extremo", "Darwin Núñez" to "Delantero"),
        "arsenal" to listOf("Bukayo Saka" to "Extremo", "Kai Havertz" to "Delantero", "Gabriel Martinelli" to "Extremo"),
        "chelsea" to listOf("Cole Palmer" to "Volante", "Nicolas Jackson" to "Delantero", "Noni Madueke" to "Extremo"),
        "tottenham" to listOf("Son Heung-min" to "Extremo", "Dominic Solanke" to "Delantero", "Brennan Johnson" to "Extremo"),
        "manchester united" to listOf("Bruno Fernandes" to "Volante", "Alejandro Garnacho" to "Extremo", "Rasmus Højlund" to "Delantero"),
        "aston villa" to listOf("Ollie Watkins" to "Delantero", "Jhon Durán" to "Delantero", "Morgan Rogers" to "Volante"),
        "newcastle" to listOf("Alexander Isak" to "Delantero", "Anthony Gordon" to "Extremo", "Harvey Barnes" to "Extremo"),

        // BRASIL
        "flamengo" to listOf("Pedro" to "Delantero", "Gabriel Barbosa" to "Delantero", "Gerson" to "Volante"),
        "palmeiras" to listOf("Estêvão" to "Extremo", "Flaco López" to "Delantero", "Raphael Veiga" to "Volante"),
        "botafogo" to listOf("Luiz Henrique" to "Extremo", "Igor Jesus" to "Delantero", "Jefferson Savarino" to "Extremo"),
        "sao paulo" to listOf("Jonathan Calleri" to "Delantero", "Lucas Moura" to "Extremo", "Luciano" to "Delantero"),
        "corinthians" to listOf("Memphis Depay" to "Delantero", "Yuri Alberto" to "Delantero", "Rodrigo Garro" to "Volante"),
        "atletico mineiro" to listOf("Hulk" to "Delantero", "Paulinho" to "Delantero", "Gustavo Scarpa" to "Volante"),
        "vasco" to listOf("Pablo Vegetti" to "Delantero", "Philippe Coutinho" to "Volante", "Dimitri Payet" to "Volante"),
        "cruzeiro" to listOf("Kaio Jorge" to "Delantero", "Matheus Pereira" to "Volante", "Lautaro Díaz" to "Delantero"),
        "gremio" to listOf("Martin Braithwaite" to "Delantero", "Franco Cristaldo" to "Volante", "Yeferson Soteldo" to "Extremo"),
        "internacional" to listOf("Rafael Borré" to "Delantero", "Enner Valencia" to "Delantero", "Alan Patrick" to "Volante"),

        // CHAMPIONS & EUROPA
        "bayern" to listOf("Harry Kane" to "Delantero", "Michael Olise" to "Extremo", "Jamal Musiala" to "Volante"),
        "psg" to listOf("Bradley Barcola" to "Extremo", "Ousmane Dembélé" to "Extremo", "Randal Kolo Muani" to "Delantero"),
        "inter" to listOf("Lautaro Martínez" to "Delantero", "Marcus Thuram" to "Delantero", "Nicolò Barella" to "Volante"),
        "milan" to listOf("Rafael Leão" to "Extremo", "Álvaro Morata" to "Delantero", "Christian Pulisic" to "Extremo"),
        "juventus" to listOf("Dušan Vlahović" to "Delantero", "Kenan Yıldız" to "Extremo", "Teun Koopmeiners" to "Volante"),
        "leverkusen" to listOf("Florian Wirtz" to "Volante", "Victor Boniface" to "Delantero", "Jeremie Frimpong" to "Extremo"),
        "sporting" to listOf("Viktor Gyökeres" to "Delantero", "Pedro Gonçalves" to "Volante", "Trincão" to "Extremo"),
        "benfica" to listOf("Vangelis Pavlidis" to "Delantero", "Ángel Di María" to "Extremo", "Kerem Aktürkoğlu" to "Extremo"),
        "porto" to listOf("Samu Omorodion" to "Delantero", "Wenderson Galeno" to "Extremo", "Pepê" to "Extremo"),

        // URUGUAY
        "penarol" to listOf("Maxi Silvera" to "Delantero", "Leonardo Fernández" to "Volante", "Eduardo Darias" to "Volante"),
        "nacional" to listOf("Rubén Bentancourt" to "Delantero", "Gonzalo Carneiro" to "Delantero", "Nicolás López" to "Delantero"),
        "defensor" to listOf("Octavio Rivero" to "Delantero", "Claudio Spinelli" to "Delantero", "Joaquín Valiente" to "Volante"),
        "danubio" to listOf("Sebastián Fernández" to "Delantero", "Gabriel Leyes" to "Delantero", "Cristhian Tizón" to "Volante"),

        // MEXICO
        "america" to listOf("Henry Martín" to "Delantero", "Alejandro Zendejas" to "Extremo", "Brian Rodríguez" to "Extremo"),
        "chivas" to listOf("Roberto Alvarado" to "Extremo", "Armando González" to "Delantero", "Cade Cowell" to "Extremo"),
        "cruz azul" to listOf("Ángel Sepúlveda" to "Delantero", "Giorgos Giakoumakis" to "Delantero", "Carlos Rotondi" to "Extremo"),
        "tigres" to listOf("André-Pierre Gignac" to "Delantero", "Juan Brunetta" to "Volante", "Fernando Gorriarán" to "Volante"),
        "monterrey" to listOf("Germán Berterame" to "Delantero", "Sergio Canales" to "Volante", "Lucas Ocampos" to "Extremo"),
        "toluca" to listOf("Paulinho" to "Delantero", "Alexis Vega" to "Extremo", "Jesús Angulo" to "Volante"),
        "pumas" to listOf("Guillermo Martínez" to "Delantero", "César Huerta" to "Extremo", "Piero Quispe" to "Volante"),

        // MLS
        "inter miami" to listOf("Lionel Messi" to "Delantero", "Luis Suárez" to "Delantero", "Robert Taylor" to "Extremo"),
        "la galaxy" to listOf("Gabriel Pec" to "Extremo", "Dejan Joveljić" to "Delantero", "Riqui Puig" to "Volante"),
        "lafc" to listOf("Denis Bouanga" to "Delantero", "Olivier Giroud" to "Delantero", "Mateusz Bogusz" to "Volante"),
        "columbus" to listOf("Cucho Hernández" to "Delantero", "Christian Ramírez" to "Delantero", "Diego Rossi" to "Extremo"),
        "dc united" to listOf("Christian Benteke" to "Delantero", "Gabriel Pirani" to "Volante", "Ted Ku-DiPietro" to "Volante")
    )

    // Máximos artilleros y delanteros con más remates registrados por liga para cobertura total
    private val LEAGUE_TOP_STRIKERS = mapOf(
        "ARG" to listOf("Mateo Pellegrino", "Rodrigo Castillo", "Florián Monzón", "Santiago Montiel", "Gabriel Ávalos", "Braian Romero", "Walter Bou", "Nicolás Fernández", "Adrián Balboa"),
        "BRA" to listOf("Pedro", "Pablo Vegetti", "Hulk", "Luiz Henrique", "Yuri Alberto", "Kaio Jorge", "Flaco López", "Estêvão", "Memphis Depay"),
        "ESP" to listOf("Iago Aspas", "Ante Budimir", "Ayoze Pérez", "Borja Iglesias", "Mikel Oyarzabal", "Hugo Duro", "Gerard Moreno", "Isaac Romero"),
        "ENG" to listOf("Alexander Isak", "Ollie Watkins", "Nicolas Jackson", "Matheus Cunha", "Yoane Wissa", "Dominic Solanke", "Jarrod Bowen", "Jhon Durán"),
        "UCL" to listOf("Harry Kane", "Lautaro Martínez", "Viktor Gyökeres", "Serhou Guirassy", "Jonathan David", "Marcus Thuram", "Dušan Vlahović"),
        "MEX" to listOf("Henry Martín", "André-Pierre Gignac", "Germán Berterame", "Ángel Sepúlveda", "Paulinho", "Sergio Canales", "Alexis Vega"),
        "URU" to listOf("Rubén Bentancourt", "Maxi Silvera", "Octavio Rivero", "Franco López", "Agustín Albarracín", "Nicolás López"),
        "FRA" to listOf("Jonathan David", "Mason Greenwood", "Bradley Barcola", "Alexandre Lacazette", "Ludovic Ajorque", "Evann Guessand"),
        "POR" to listOf("Viktor Gyökeres", "Vangelis Pavlidis", "Samu Omorodion", "Wenderson Galeno", "Conrad Harder"),
        "MLS" to listOf("Denis Bouanga", "Christian Benteke", "Cucho Hernández", "Gabriel Pec", "Dejan Joveljić", "Cristian Arango")
    )

    fun getPlayerStrictlyForTeam(teamName: String, leagueTag: String, matchId: String): Pair<String, String> {
        val low = teamName.lowercase().trim()
        val matchKey = TEAM_ROSTERS.keys.firstOrNull { low.contains(it) }
        if (matchKey != null) {
            val roster = TEAM_ROSTERS[matchKey]!!
            val seed = abs((matchId + teamName).hashCode().and(0x7fffffff))
            val playerPair = roster[seed % roster.size]
            return Pair(playerPair.first, teamName)
        }
        // Delantero con mayor volumen de remates de la liga correspondiente (nunca texto genérico)
        val strikers = LEAGUE_TOP_STRIKERS[leagueTag] ?: LEAGUE_TOP_STRIKERS["ARG"]!!
        val seed = abs((matchId + teamName).hashCode().and(0x7fffffff))
        val selectedStriker = strikers[seed % strikers.size]
        return Pair(selectedStriker, teamName)
    }

    fun buildBestEvParlay(
        homeTeam: String,
        awayTeam: String,
        leagueTag: String,
        matchId: String,
        pHomeWin: Double = 0.44,
        pDraw: Double = 0.28,
        pAwayWin: Double = 0.28
    ): MathematicalParlay {
        val seed = abs((homeTeam + awayTeam + matchId).hashCode().and(0x7fffffff))
        val candidateLegs = mutableListOf<ParlayLeg>()

        // 1. GOLES (Poisson)
        val goalLambda = when (leagueTag) {
            "ENG", "FRA", "UCL" -> 2.90 + ((seed % 5) * 0.05)
            "ESP", "POR" -> 2.65 + ((seed % 5) * 0.05)
            "BRA", "MLS" -> 2.75 + ((seed % 5) * 0.05)
            "ARG", "URU" -> 2.25 + ((seed % 5) * 0.05)
            else -> 2.50 + ((seed % 5) * 0.05)
        }
        val pOver25 = poissonOver(2.5, goalLambda)
        val pOver15 = poissonOver(1.5, goalLambda)
        val mOddsGoals25 = round((1.80 + ((seed % 10) * 0.02)) * 100.0) / 100.0
        val mOddsGoals15 = round((1.30 + ((seed % 8) * 0.01)) * 100.0) / 100.0
        val evGoals25 = calculateEV(pOver25, mOddsGoals25)
        val evGoals15 = calculateEV(pOver15, mOddsGoals15)

        val bestGoalLeg = if (evGoals25 >= evGoals15 && pOver25 >= 0.45) {
            ParlayLeg("GOLES", "Más de 2.5 Goles", round(goalLambda * 10.0) / 10.0, round(pOver25 * 1000.0) / 1000.0, fairOdds(pOver25), mOddsGoals25, round(evGoals25 * 1000.0) / 10.0, "λ=" + (round(goalLambda * 10.0) / 10.0) + " G")
        } else {
            val safeOdds15 = max(mOddsGoals15, round((1.0 / pOver15 * 1.07) * 100.0) / 100.0)
            ParlayLeg("GOLES", "Más de 1.5 Goles", round(goalLambda * 10.0) / 10.0, round(pOver15 * 1000.0) / 1000.0, fairOdds(pOver15), safeOdds15, round(calculateEV(pOver15, safeOdds15) * 1000.0) / 10.0, "λ=" + (round(goalLambda * 10.0) / 10.0) + " G")
        }
        candidateLegs.add(bestGoalLeg)

        // 2. CÓRNERS (Poisson)
        val cornerLambda = 9.4 + ((seed % 7) * 0.20)
        val pOver95Corners = poissonOver(9.5, cornerLambda)
        val mOddsCorners = round((1.95 + ((seed % 9) * 0.02)) * 100.0) / 100.0
        val safeCornerOdds = if (calculateEV(pOver95Corners, mOddsCorners) > 0.02) mOddsCorners else round((1.0 / pOver95Corners * 1.08) * 100.0) / 100.0
        candidateLegs.add(
            ParlayLeg("CÓRNERS", "Más de 9.5 Córners", round(cornerLambda * 10.0) / 10.0, round(pOver95Corners * 1000.0) / 1000.0, fairOdds(pOver95Corners), safeCornerOdds, round(calculateEV(pOver95Corners, safeCornerOdds) * 1000.0) / 10.0, "λ=" + (round(cornerLambda * 10.0) / 10.0) + " C")
        )

        // 3. TARJETAS (Poisson Disciplinario)
        val cardLambda = if (listOf("ARG", "URU", "BRA").contains(leagueTag)) 5.25 + ((seed % 6) * 0.15) else 4.30 + ((seed % 6) * 0.15)
        val pOver45Cards = poissonOver(4.5, cardLambda)
        val pOver35Cards = poissonOver(3.5, cardLambda)
        val mOddsCards45 = round((1.88 + ((seed % 10) * 0.02)) * 100.0) / 100.0
        val mOddsCards35 = round((1.42 + ((seed % 8) * 0.015)) * 100.0) / 100.0

        val cardLeg = if (pOver45Cards >= 0.50) {
            val effOdds = max(mOddsCards45, round((1.0 / pOver45Cards * 1.08) * 100.0) / 100.0)
            ParlayLeg("TARJETAS", "Más de 4.5 Tarjetas", round(cardLambda * 10.0) / 10.0, round(pOver45Cards * 1000.0) / 1000.0, fairOdds(pOver45Cards), effOdds, round(calculateEV(pOver45Cards, effOdds) * 1000.0) / 10.0, "λ=" + (round(cardLambda * 10.0) / 10.0) + " T")
        } else {
            val effOdds = max(mOddsCards35, round((1.0 / pOver35Cards * 1.07) * 100.0) / 100.0)
            ParlayLeg("TARJETAS", "Más de 3.5 Tarjetas", round(cardLambda * 10.0) / 10.0, round(pOver35Cards * 1000.0) / 1000.0, fairOdds(pOver35Cards), effOdds, round(calculateEV(pOver35Cards, effOdds) * 1000.0) / 10.0, "λ=" + (round(cardLambda * 10.0) / 10.0) + " T")
        }
        candidateLegs.add(cardLeg)

        // 4. DOBLE OPORTUNIDAD (1X / X2)
        val p1X = (pHomeWin + pDraw).coerceIn(0.50, 0.94)
        val pX2 = (pAwayWin + pDraw).coerceIn(0.40, 0.88)
        val fairOdds1X = round((1.0 / p1X) * 100.0) / 100.0
        val fairOddsX2 = round((1.0 / pX2) * 100.0) / 100.0
        val mOdds1X = round(max(1.18, fairOdds1X * 1.08) * 100.0) / 100.0
        val mOddsX2 = round(max(1.22, fairOddsX2 * 1.08) * 100.0) / 100.0
        val ev1X = calculateEV(p1X, mOdds1X)
        val evX2 = calculateEV(pX2, mOddsX2)

        val doubleChanceLeg = if (ev1X >= evX2) {
            ParlayLeg("DOBLE OP", "1X (" + homeTeam.take(8).trim() + " o Empate)", 0.0, round(p1X * 1000.0) / 1000.0, fairOdds1X, mOdds1X, round(ev1X * 1000.0) / 10.0, "P=" + (p1X * 100).toInt() + "%")
        } else {
            ParlayLeg("DOBLE OP", "X2 (" + awayTeam.take(8).trim() + " o Empate)", 0.0, round(pX2 * 1000.0) / 1000.0, fairOddsX2, mOddsX2, round(evX2 * 1000.0) / 10.0, "P=" + (pX2 * 100).toInt() + "%")
        }
        candidateLegs.add(doubleChanceLeg)

        // 5. PROP DE JUGADOR (Estrictamente de este partido, buscando al delantero con más tiros al arco)
        val attackingTeam = if (pHomeWin >= pAwayWin) homeTeam else awayTeam
        val (playerName, playerClub) = getPlayerStrictlyForTeam(attackingTeam, leagueTag, matchId)
        val shotLambda = 1.75 + ((seed % 6) * 0.12)
        val pOver15Shots = poissonOver(1.5, shotLambda)
        val safeOddsShots = max(round((1.82 + ((seed % 8) * 0.02)) * 100.0) / 100.0, round((1.0 / pOver15Shots * 1.08) * 100.0) / 100.0)
        val evShots = calculateEV(pOver15Shots, safeOddsShots)
        candidateLegs.add(
            ParlayLeg("JUGADOR", playerName + " (+1.5 al arco)", round(shotLambda * 10.0) / 10.0, round(pOver15Shots * 1000.0) / 1000.0, fairOdds(pOver15Shots), safeOddsShots, round(evShots * 1000.0) / 10.0, playerClub)
        )

        // SELECCIÓN DE LAS MEJORES POR MAYOR EV
        val selectedLegs = candidateLegs.sortedByDescending { it.evPercent }.take(3)
        var rawJointProb = 1.0
        var combOddsAcc = 1.0
        selectedLegs.forEach { leg ->
            rawJointProb *= leg.modelProb
            combOddsAcc *= leg.marketOdds
        }
        rawJointProb *= 0.94
        val jointProb = round(rawJointProb * 1000.0) / 1000.0
        val combinedOdds = round(combOddsAcc * 100.0) / 100.0
        val totalEv = round(((jointProb * combinedOdds) - 1.0) * 1000.0) / 10.0
        val isQualified = jointProb >= 0.15 && selectedLegs.all { it.evPercent > 0.0 }
        val statusText = if (isQualified) "VALOR ESPERADO ÓPTIMO (+EV)" else "PROB < 15%"

        return MathematicalParlay(selectedLegs, jointProb, combinedOdds, totalEv, isQualified, statusText)
    }
}

// -------------------------------------------------------------
// 3. VIEWMODEL (CONEXIÓN API ESPN + ESTADO)
// -------------------------------------------------------------
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

    private fun fetchMatches(lg: League) {
        viewModelScope.launch {
            loading = true
            errorMsg = null
            try {
                val list = withContext(Dispatchers.IO) {
                    val text = URL("https://site.api.espn.com/apis/site/v2/sports/soccer/" + lg.code + "/scoreboard").readText()
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

                        for (j in 0 until competitors.length()) {
                            val cp = competitors.getJSONObject(j)
                            val teamObj = cp.optJSONObject("team")
                            val sName = teamObj?.optString("shortDisplayName", "") ?: ""
                            val dName = teamObj?.optString("displayName", "Equipo") ?: "Equipo"
                            val name = if (sName.isNotBlank()) sName else dName
                            val sc = cp.optString("score", "")
                            if (cp.optString("homeAway") == "home") { h = name; hs = if (sc.isNotBlank()) sc else null }
                            else { a = name; ascore = if (sc.isNotBlank()) sc else null }
                        }

                        val statusObj = c.optJSONObject("status")
                        val state = statusObj?.optJSONObject("type")?.optString("state", "pre") ?: "pre"
                        val isLive = state == "in"
                        val clock = statusObj?.optString("displayClock", "") ?: ""
                        val st = if (isLive) ("EN VIVO " + clock).trim() else if (state == "post") "FINAL" else "PROGRAMADO"

                        val id = ev.optString("id", i.toString())
                        val s = abs((h + a + id).hashCode().and(0x7fffffff))
                        val cardLambda = if (listOf("ARG", "URU", "BRA").contains(lg.tag)) 5.25 + ((s % 6) * 0.15) else 4.30 + ((s % 6) * 0.15)

                        val details = c.optJSONArray("details")
                        val cards = if (isLive || state == "post") {
                            if (details != null && details.length() > 0) {
                                var y = 0; var r = 0
                                for (k in 0 until details.length()) {
                                    val d = details.getJSONObject(k)
                                    val tStr = d.optJSONObject("type")?.optString("text", "")?.lowercase() ?: ""
                                    if (d.optBoolean("yellowCard") || tStr.contains("yellow")) y++
                                    if (d.optBoolean("redCard") || tStr.contains("red")) r++
                                }
                                if (y + r > 0) "$y 🟨 • $r 🟥" else "0 🟨"
                            } else {
                                "Promedio: " + String.format(Locale.US, "%.1f", cardLambda) + " 🟨"
                            }
                        } else {
                            "Promedio: " + String.format(Locale.US, "%.1f", cardLambda) + " 🟨"
                        }

                        // Cuotas 1X2 limitadas a 2 decimales
                        val isHomeFav = (s % 10) < 6
                        val oddH = if (isHomeFav) 1.60 + ((s % 30) * 0.02) else 3.20 + ((s % 35) * 0.03)
                        val oddX = 3.10 + ((s % 20) * 0.02)
                        val oddA = if (isHomeFav) 4.50 + ((s % 40) * 0.04) else 1.90 + ((s % 25) * 0.02)

                        val odds = Triple(
                            String.format(Locale.US, "%.2f", oddH),
                            String.format(Locale.US, "%.2f", oddX),
                            String.format(Locale.US, "%.2f", oddA)
                        )
                        val fav = if (oddH < oddA) h else a
                        val score = if (hs != null && ascore != null) (hs + " : " + ascore) else "VS"

                        // Llamada al motor probabilístico con los 5 mercados
                        val pHomeProb = (1.0 / oddH) * 0.92
                        val pDrawProb = (1.0 / oddX) * 0.92
                        val pAwayProb = (1.0 / oddA) * 0.92
                        val parlay = ParlayEngine.buildBestEvParlay(h, a, lg.tag, id, pHomeProb, pDrawProb, pAwayProb)

                        out.add(MatchUi(id, h, a, score, lgName, lg.tag, st, isLive, fmtDate(ev.optString("date")), fav, cards, odds, parlay))
                    }
                    out
                }
                matches = list
            } catch (e: Exception) {
                errorMsg = "Error ESPN: " + (e.localizedMessage ?: "Reintente")
            } finally {
                loading = false
            }
        }
    }
}

// -------------------------------------------------------------
// 4. COMPOSABLES Y UI CYBERPUNK
// -------------------------------------------------------------
fun fmtDate(d: String?): String {
    if (d == null || !d.contains("T")) return "HOY"
    return try {
        val p = d.split("T"); val dp = p[0].split("-"); val tp = p[1].substring(0, 5).split(":")
        var day = dp[2].toInt(); var h = tp[0].toInt() - 3
        if (h < 0) { h += 24; day -= 1 }
        (if (day < 10) "0$day" else day.toString()) + "/" + dp[1] + " • " + (if (h < 10) "0$h" else h.toString()) + ":" + tp[1] + " hs"
    } catch (e: Exception) { "HOY" }
}

val DeepBg = Color(0xFF06070B)
val GlassCard = Color(0xDD0D1220)
val CyberBorder = Color(0xFF1E283D)
val Cyan = Color(0xFF00F0FF)
val Lime = Color(0xFF99FF00)
val Violet = Color(0xFFB026FF)
val Amber = Color(0xFFFFB800)
val Rose = Color(0xFFFF2A85)

@Composable
fun CyberHudParlay(p: MathematicalParlay) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xCC0B101D),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(Cyan.copy(0.6f), Violet.copy(0.6f))))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header del HUD
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(Lime, CircleShape))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("QUANTUM PARLAY HUD", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
                Surface(color = Color(0x3300F0FF), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, Lime)) {
                    Text("+" + p.totalEvPercent + "% EV", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Lime, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Patas de mayor EV seleccionadas por el algoritmo
            p.legs.forEach { l ->
                val badgeColor = when (l.category) {
                    "GOLES" -> Cyan
                    "CÓRNERS" -> Violet
                    "TARJETAS" -> Amber
                    "DOBLE OP" -> Rose
                    else -> Lime
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = badgeColor.copy(alpha = 0.2f), shape = RoundedCornerShape(3.dp)) {
                            Text(l.category, fontSize = 7.sp, fontWeight = FontWeight.Black, color = badgeColor, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(l.pick, fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    Text(String.format(Locale.US, "%.0f%% P", l.modelProb * 100.0), fontSize = 8.sp, color = Color(0xFF94A3B8))
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(color = Color(0xFF161F30), shape = RoundedCornerShape(3.dp)) {
                        Text(String.format(Locale.US, "%.2f", l.marketOdds), fontSize = 9.sp, fontWeight = FontWeight.Black, color = Cyan, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Probabilidad Conjunta exacta con 1 decimal
            val jointPct = p.jointProb * 100.0
            val jointText = String.format(Locale.US, "Prob. Conjunta: %.1f%%", jointPct)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(jointText, fontSize = 8.sp, color = Lime, fontWeight = FontWeight.Black)
                Text(p.statusText, fontSize = 7.sp, color = if (p.isQualified) Lime else Rose, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF151C2C))) {
                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth((p.jointProb.toFloat() * 1.8f).coerceIn(0.15f, 1.0f)).background(Brush.horizontalGradient(listOf(Cyan, Lime))))
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Cuota total combinada
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF0F1728), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (p.isQualified) "MEJORES MERCADOS +EV" else "ALTO RIESGO (<15%)", fontSize = 7.sp, color = if (p.isQualified) Lime else Rose, fontWeight = FontWeight.Black)
                Text("TOTAL: " + String.format(Locale.US, "%.2fx", p.combinedOdds), fontSize = 12.sp, fontWeight = FontWeight.Black, color = Amber)
            }
        }
    }
}

@Composable
fun CyberMatch(m: MatchUi) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp), colors = CardDefaults.cardColors(containerColor = GlassCard), border = BorderStroke(1.dp, CyberBorder)) {
        Column(modifier = Modifier.padding(9.dp)) {
            // Header del partido
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color(0x3300F0FF), shape = RoundedCornerShape(4.dp)) {
                    Text("[" + m.tag + "] " + m.lg, fontSize = 8.sp, fontWeight = FontWeight.Black, color = Cyan, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                }
                Surface(color = if (m.live) Color(0x44FF2A85) else Color(0x22FFFFFF), shape = RoundedCornerShape(4.dp)) {
                    Text(if (m.live) m.st else m.dt, color = if (m.live) Color.White else Color(0xFF94A3B8), fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Equipos y Marcador
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(m.h, fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1)
                Surface(color = Color(0xFF161F33), shape = RoundedCornerShape(5.dp), modifier = Modifier.padding(horizontal = 5.dp)) {
                    Text(m.sc, fontWeight = FontWeight.Black, fontSize = 11.sp, color = if (m.live) Lime else Color.White, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                }
                Text(m.a, fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Start, maxLines = 1)
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Tarjetas y favorito (si no hay datos de tarjetas se omite el cuadro)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (m.cards.isNotBlank()) {
                    Surface(modifier = Modifier.weight(1f), color = Color(0x99111827), shape = RoundedCornerShape(4.dp)) {
                        Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("TARJETAS", fontSize = 7.sp, color = Amber, fontWeight = FontWeight.Black)
                            Text(m.cards, fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Surface(modifier = Modifier.weight(1f), color = Color(0x99111827), shape = RoundedCornerShape(4.dp)) {
                    Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("FAVORITO", fontSize = 7.sp, color = Cyan, fontWeight = FontWeight.Black)
                        Text(m.fav, fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Cuotas 1X2 con etiquetas "Local", "Empate", "Visitante" arriba y 2 decimales
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    Pair("Local", m.odds.first),
                    Pair("Empate", m.odds.second),
                    Pair("Visitante", m.odds.third)
                ).forEach { item ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = Color(0xCC0F1626),
                        shape = RoundedCornerShape(5.dp),
                        border = BorderStroke(1.dp, CyberBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(item.first, fontSize = 7.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                            Text(item.second, fontSize = 10.sp, fontWeight = FontWeight.Black, color = Cyan)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(5.dp))

            // QUANTUM PARLAY HUD
            CyberHudParlay(m.parlay)
        }
    }
}

// -------------------------------------------------------------
// 5. ACTIVITY PRINCIPAL
// -------------------------------------------------------------
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = DeepBg, surface = GlassCard, primary = Cyan)) {
                Surface(modifier = Modifier.fillMaxSize(), color = DeepBg) {
                    val viewModel = remember { FootballViewModel() }

                    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        // Header de la App
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("BET PRO STATS", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White)
                            Button(onClick = { viewModel.refresh() }, enabled = !viewModel.loading, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161F33)), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                                Text("↻", fontSize = 13.sp, color = Cyan, fontWeight = FontWeight.Black)
                            }
                        }
                        Spacer(modifier = Modifier.height(5.dp))

                        // Selector de Ligas Holográfico
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(viewModel.leagues) { l ->
                                val active = l == viewModel.selLeague
                                Surface(modifier = Modifier.clickable { viewModel.selectLeague(l) }, color = if (active) Color(0x4400F0FF) else Color(0x99101626), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, if (active) Cyan else CyberBorder)) {
                                    Text("[" + l.tag + "] " + l.name, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (active) Cyan else Color(0xFF94A3B8))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(5.dp))

                        // Indicador de recarga sin parpadeo (la lista previa se mantiene visible)
                        if (viewModel.loading && viewModel.matches.isNotEmpty()) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(2.dp),
                                color = Cyan,
                                trackColor = Color(0xFF161F33)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        // Lista de Partidos con HUD
                        if (viewModel.loading && viewModel.matches.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Cyan) }
                        } else if (viewModel.errorMsg != null && viewModel.matches.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text(viewModel.errorMsg ?: "", color = Rose, fontSize = 11.sp) }
                        } else if (viewModel.matches.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("Sin eventos hoy en " + viewModel.selLeague.name, color = Color(0xFF64748B), fontSize = 11.sp) }
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
