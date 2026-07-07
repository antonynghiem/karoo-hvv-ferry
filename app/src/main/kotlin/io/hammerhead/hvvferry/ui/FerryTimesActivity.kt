package io.hammerhead.hvvferry.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import io.hammerhead.hvvferry.data.preferences.PreferencesManager
import io.hammerhead.hvvferry.data.repository.FerryRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FerryTimesActivity : ComponentActivity() {
    
    @Inject
    lateinit var repository: FerryRepository
    
    @Inject
    lateinit var preferencesManager: PreferencesManager
    
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
                        onClose = { finish() }
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
    onClose: () -> Unit
) {
    var departures by remember { mutableStateOf<List<io.hammerhead.hvvferry.data.models.Departure>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            errorMessage = null
            
            // Get manual stop if set
            val stopId = preferencesManager.getManualStopId()
            if (stopId != null) {
                val stop = repository.getStopById(stopId)
                if (stop != null) {
                    val result = repository.getDepartures(stop, 10)
                    result.fold(
                        onSuccess = { departures = it },
                        onFailure = { errorMessage = it.message }
                    )
                }
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
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                                Text(
                                    text = "${departure.line.name} → ${departure.line.direction}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = departure.getDisplayTime(),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (departure.hasDelay()) {
                                    Text(
                                        text = "Delayed +${departure.delay} min",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
