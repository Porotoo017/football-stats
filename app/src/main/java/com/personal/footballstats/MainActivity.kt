package com.personal.footballstats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Header
import java.util.concurrent.TimeUnit
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }
}

@Serializable
data class TeamDto(val name: String)

@Serializable
data class LeagueDto(val name: String)

@Serializable
data class FixtureDto(
    val id: Int,
    @SerialName("start_time") val startTime: String? = null,
    val home_team: String? = null,
    val away_team: String? = null,
    val league: LeagueDto? = null
)

@Serializable
data class FixtureResponse(val response: List<FixtureDto>)

@Serializable
data class PercentDto(val home: String? = null, val draw: String? = null, val away: String? = null)
@Serializable
data class ComparisonDto(val percent: PercentDto? = null)

@Serializable
data class PredictionDataDto(val comparison: ComparisonDto? = null)

@Serializable
data class PredictionResponse(val response: List<PredictionDataDto>)

interface ApiService {
    @GET("fixtures")
    suspend fun getFixtures(
        @Query("date") date: String,
        @Query("league") league: Int,
        @Query("season") season: Int,
        @Header("x-apisports-key") apiKey: String
    ): FixtureResponse

    @GET("predictions")
    suspend fun getPrediction(
        @Query("fixture") fixtureId: Int,
        @Header("x-apisports-key") apiKey: String
    ): PredictionResponse
}

data class MatchUi(
    val id: Int,
    val homeTeam: String,
    val awayTeam: String,
    val league: String,
    val probHome: Double,
    val probDraw: Double,
    val probAway: Double
)

class MainViewModel : ViewModel() {
    private val _apiKey = MutableStateFlow("")
    val apiKey = _apiKey.asStateFlow()

    private val _matches = MutableStateFlow<List<MatchUi>>(emptyList())
    val matches = _matches.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun setApiKey(key: String) { _apiKey.value = key }

    fun loadMatches() {
        if (_apiKey.value.isBlank()) {
            _error.value = "Por favor, ingresa tu API Key"
            return
        }
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val today = "2024-05-20
                val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).build()
                val json = Json { ignoreUnknownKeys = true }
                val retrofit = Retrofit.Builder()
                    .baseUrl("https://v3.football.api-sports.io/")
                    .client(client)
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
                val api = retrofit.create(ApiService::class.java)

                val leagues = listOf(39, 140, 135, 78, 61, 2, 3, 128, 71, 253)
                val allMatches = mutableListOf<MatchUi>()

                for (league in leagues) {
                    val fixtures = api.getFixtures(today, league, 2023, _apiKey.value).response
                    for (fix in fixtures.take(3)) {
                        try {
                            val predRes = api.getPrediction(fix.id, _apiKey.value).response.firstOrNull()
                            val p = predRes?.comparison?.percent
                            if (p != null) {
                                val ph = p.home?.replace("%", "")?.toDoubleOrNull()?.div(100) ?: 0.33
                                val pd = p.draw?.replace("%", "")?.toDoubleOrNull()?.div(100) ?: 0.33
                                val pa = p.away?.replace("%", "")?.toDoubleOrNull()?.div(100) ?: 0.33
                                allMatches.add(
                                    MatchUi(
                                        fix.id,
                                        fix.home_team ?: "Local",
                                        fix.away_team ?: "Visitante",
                                        fix.league?.name ?: "Liga",
                                        ph, pd, pa
                                    )
                                )
                            }
                        } catch (e: Exception) { }
                    }
                }
                _matches.value = allMatches
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val apiKey by viewModel.apiKey.collectAsState()
    val matches by viewModel.matches.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    val selectedMatches = remember { mutableStateListOf<MatchUi>() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Football Stats", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = { viewModel.setApiKey(it) },
            label = { Text("API Key de API-Football") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(onClick = { viewModel.loadMatches() }, modifier = Modifier.fillMaxWidth()) {
            Text("Cargar Partidos de Hoy")
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
        }
        
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(matches) { match ->
                val isSelected = selectedMatches.any { it.id == match.id }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = if(isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(match.league, style = MaterialTheme.typography.labelSmall)
                        Text("${match.homeTeam} vs ${match.awayTeam}", fontWeight = FontWeight.Bold)
                        Text("Local: ${(match.probHome*100).toInt()}% | Empate: ${(match.probDraw*100).toInt()}% | Visita: ${(match.probAway*100).toInt()}%")
                        
                        Button(
                            onClick = {
                                if (isSelected) selectedMatches.removeAll { it.id == match.id }
                                else if (selectedMatches.size < 4) selectedMatches.add(match)
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(if (isSelected) "Quitar" else "Añadir a Combinada")
                        }
                    }
                }
            }
        }

        if (selectedMatches.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Tu Combinada (${selectedMatches.size}/4)", fontWeight = FontWeight.Bold)
                    var jointProb = 1.0
                    selectedMatches.forEach { m ->
                        val maxP = maxOf(m.probHome, m.probDraw, m.probAway)
                        jointProb *= maxP
                    }
                    Text("Probabilidad de acierto estimada: ${String.format("%.2f", jointProb * 100)}%")
                }
            }
        }
    }
}
