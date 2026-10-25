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
import retrofit2.HttpException
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import java.time.LocalDate
import java.util.concurrent.TimeUnit

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
data class TeamDto(val name: String? = null)

@Serializable
data class CompetitionDto(val name: String? = null)

@Serializable
data class MatchDto(
    val id: Int,
    val homeTeam: TeamDto? = null,
    val awayTeam: TeamDto? = null,
    val competition: CompetitionDto? = null
)

@Serializable
data class MatchesResponse(val matches: List<MatchDto> = emptyList())

interface ApiService {
    // Ahora le pasamos la fecha de hoy explícitamente
    @GET("matches")
    suspend fun getMatches(
        @Header("X-Auth-Token") token: String,
        @Query("dateFrom") dateFrom: String,
        @Query("dateTo") dateTo: String
    ): MatchesResponse
}

data class MatchUi(
    val id: Int,
    val homeTeam: String,
    val awayTeam: String,
    val league: String
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
    
    private val _message = MutableStateFlow("")
    val message = _message.asStateFlow()

    fun setApiKey(key: String) { _apiKey.value = key }

    fun loadMatches() {
        if (_apiKey.value.isBlank()) {
            _error.value = "Por favor, ingresa tu API Key"
            return
        }
        _isLoading.value = true
        _error.value = null
        _message.value = "Buscando partidos..."
        viewModelScope.launch {
            try {
                // Obtenemos la fecha de hoy en formato YYYY-MM-DD
                val today = "2024-05-19"
                
                val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).build()
                val json = Json { ignoreUnknownKeys = true }
                val retrofit = Retrofit.Builder()
                    .baseUrl("https://api.football-data.org/v4/")
                    .client(client)
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
                val api = retrofit.create(ApiService::class.java)

                val response = api.getMatches(_apiKey.value, today, today)
                val allMatches = response.matches.map { match ->
                    MatchUi(
                        id = match.id,
                        homeTeam = match.homeTeam?.name ?: "Local",
                        awayTeam = match.awayTeam?.name ?: "Visitante",
                        league = match.competition?.name ?: "Liga"
                    )
                }
                _matches.value = allMatches
                
                if (allMatches.isEmpty()) {
                    _message.value = "La API respondió OK, pero no hay partidos hoy en las ligas disponibles."
                } else {
                    _message.value = "¡Se encontraron ${allMatches.size} partidos!"
                }
            } catch (e: HttpException) {
                // Si la API nos rechaza, mostramos el código de error
                _error.value = "Error HTTP ${e.code()}: ${e.message()}"
                _message.value = ""
            } catch (e: Exception) {
                _error.value = "Error de conexión: ${e.localizedMessage}"
                _message.value = ""
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
    val message by viewModel.message.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Football Stats (Prueba API)", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = { viewModel.setApiKey(it) },
            label = { Text("API Key (Football-Data.org)") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(onClick = { viewModel.loadMatches() }, modifier = Modifier.fillMaxWidth()) {
            Text("Buscar Partidos de HOY")
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
        }
        
        if (message.isNotEmpty()) {
            Text(message, modifier = Modifier.padding(8.dp))
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp), fontWeight = FontWeight.Bold)
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(matches) { match ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(match.league, style = MaterialTheme.typography.labelSmall)
                        Text("${match.homeTeam} vs ${match.awayTeam}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
