package dev.antonyng.hvvferry.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import dev.antonyng.hvvferry.data.preferences.PreferencesManager
import dev.antonyng.hvvferry.data.repository.FerryRepository
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.LaunchPinDrop
import io.hammerhead.karooext.models.Symbol
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FerryTimesActivity : ComponentActivity() {
    
    @Inject
    lateinit var repository: FerryRepository

    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    lateinit var karooSystemService: KarooSystemService
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FerryTimesScreen(
                        repository = repository,
                        preferencesManager = preferencesManager,
                        karooSystem = karooSystemService,
                        onClose = { finish() },
                        onOpenConfig = {
                            startActivity(
                                Intent(this, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FerryTimesScreen(
    repository: FerryRepository,
    preferencesManager: PreferencesManager,
    karooSystem: KarooSystemService,
    onClose: () -> Unit,
    onOpenConfig: () -> Unit = {}
) {
    var departures by remember { mutableStateOf<List<dev.antonyng.hvvferry.data.models.Departure>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            errorMessage = null
            
            // Get manual stop from config
            val config = preferencesManager.getConfig()
            val stopId = config.manualStopId
            if (stopId != null) {
                // Try DB cache first, fall back to building from saved config
                val stop = repository.getStopById(stopId)
                    ?: dev.antonyng.hvvferry.data.models.FerryStop(
                        stationId = stopId,
                        name = config.manualStopName ?: stopId,
                        latitude = 0.0,
                        longitude = 0.0,
                        ferryLines = emptyList()
                    )
                // Show all departures (ignore connection filter) for the overview
                val result = repository.getDepartures(stop, 20, ignoreConnectionFilter = true)
                result.fold(
                    onSuccess = { departures = it },
                    onFailure = { errorMessage = it.message }
                )
            } else {
                errorMessage = "No ferry stop selected"
            }
            
            isLoading = false
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ferry Departures") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Text("←")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenConfig) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Configure"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator()
                }
                errorMessage != null -> {
                    Text(
                        text = "Error: $errorMessage",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                departures.isEmpty() -> {
                    Text("No ferry departures found")
                }
                else -> {
                    departures.forEach { departure ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${departure.line.name} → ${departure.line.direction}",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        if (departure.line.origin.isNotBlank()) {
                                            Text(
                                                text = departure.line.origin,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = departure.getDisplayTime(),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        if (departure.hasDelay()) {
                                            Text(
                                                text = "+${departure.delay} min delay",
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                    // Navigate button — only shown when pier coordinates are available
                                    if (departure.originLat != null && departure.originLon != null) {
                                        IconButton(onClick = {
                                            karooSystem.dispatch(
                                                LaunchPinDrop(
                                                    Symbol.POI(
                                                        id = "ferry-${departure.serviceId}",
                                                        lat = departure.originLat,
                                                        lng = departure.originLon,
                                                        type = Symbol.POI.Types.FERRY,
                                                        name = departure.line.origin.ifBlank { "Ferry Stop" }
                                                    )
                                                )
                                            )
                                        }) {
                                            Text("🧭", style = MaterialTheme.typography.titleLarge)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
