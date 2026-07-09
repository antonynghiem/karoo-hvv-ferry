package dev.antonyng.hvvferry.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import dagger.hilt.android.AndroidEntryPoint
import dev.antonyng.hvvferry.data.models.FerryConfig
import dev.antonyng.hvvferry.data.models.FerryStop
import dev.antonyng.hvvferry.data.models.GpsLookupInterval
import dev.antonyng.hvvferry.data.models.ProximityRadius
import dev.antonyng.hvvferry.data.models.RouteFormat
import dev.antonyng.hvvferry.data.models.UpdateInterval
import dev.antonyng.hvvferry.data.preferences.CredentialManager
import dev.antonyng.hvvferry.data.preferences.PreferencesManager
import dev.antonyng.hvvferry.data.repository.FerryRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var credentialManager: CredentialManager
    
    @Inject
    lateinit var preferencesManager: PreferencesManager
    
    @Inject
    lateinit var repository: FerryRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ConfigurationScreen(
                        credentialManager = credentialManager,
                        preferencesManager = preferencesManager,
                        repository = repository
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationScreen(
    credentialManager: CredentialManager,
    preferencesManager: PreferencesManager,
    repository: FerryRepository
) {
    var username by remember { mutableStateOf(credentialManager.getUsername() ?: "") }
    var password by remember { mutableStateOf(credentialManager.getPassword() ?: "") }
    var connectionStatus by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    var config by remember { mutableStateOf(preferencesManager.getConfig()) }

    // Stop picker state
    var stopQuery by remember { mutableStateOf("") }
    var stopSearchResults by remember { mutableStateOf<List<FerryStop>>(emptyList()) }
    var isSearchingStops by remember { mutableStateOf(false) }
    var stopSearchError by remember { mutableStateOf<String?>(null) }
    var stopSearchAttempted by remember { mutableStateOf(false) }

    // Connection filter state
    var availableConnections by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isLoadingConnections by remember { mutableStateOf(false) }
    var connectionsError by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HVV Ferry Configuration") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Credentials Section
            Text(
                "Geofox API Credentials",
                style = MaterialTheme.typography.headlineSmall
            )
            
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        credentialManager.saveCredentials(username, password)
                        connectionStatus = "Credentials saved"
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
                
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            connectionStatus = null
                            
                            // Save credentials first
                            credentialManager.saveCredentials(username, password)
                            
                            // Test connection
                            val result = repository.testConnection()
                            connectionStatus = result.fold(
                                onSuccess = { "✓ $it" },
                                onFailure = { "✗ ${it.message}" }
                            )
                            
                            isLoading = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading && username.isNotBlank() && password.isNotBlank()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Test Connection")
                    }
                }
            }
            
            connectionStatus?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (status.startsWith("✓")) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Ferry Stop Section
            Text("Ferry Stop", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Used when GPS auto-detection is off or finds no nearby stops.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Current stop summary + clear
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (config.manualStopName != null) "Selected: ${config.manualStopName}"
                           else "No stop selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (config.manualStopName != null) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (config.manualStopName != null) {
                    TextButton(onClick = {
                        config = config.copy(manualStopId = null, manualStopName = null)
                        preferencesManager.saveConfig(config)
                    }) {
                        Text("Clear")
                    }
                }
            }

            // Quick picks
            Text("Quick picks", style = MaterialTheme.typography.titleMedium)

            data class KnownStop(val id: String, val name: String, val lines: String)
            val knownStops = listOf(
                KnownStop("Master:80950", "Landungsbrücken",     "62, 72, 73, 75"),
                KnownStop("Master:51014", "Finkenwerder (Fähre)", "62, 64"),
                KnownStop("Master:80981", "Teufelsbrück",         "62, 64"),
                KnownStop("Master:52982", "Neumühlen/Övelgönne", "62, 64"),
                KnownStop("Master:80989", "Dockland",             "62, 64"),
KnownStop("Master:81054", "Blankenese (Fähre)",   "62, 64"),
                KnownStop("Master:51981", "Rüschpark",             "62, 64"),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                knownStops.forEach { stop ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = config.manualStopId == stop.id,
                            onClick = {
                                config = config.copy(manualStopId = stop.id, manualStopName = stop.name)
                                preferencesManager.saveConfig(config)
                                repository.invalidateCache()
                                stopSearchResults = emptyList()
                            }
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(stop.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Lines ${stop.lines}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Search
            Text("Search stops", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = stopQuery,
                    onValueChange = { stopQuery = it },
                    label = { Text("Stop name") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(
                    onClick = {
                        scope.launch {
                            isSearchingStops = true
                            stopSearchError = null
                            stopSearchResults = emptyList()
                            stopSearchAttempted = true
                            val result = repository.searchStopsByName(stopQuery.trim())
                            result.fold(
                                onSuccess = { stopSearchResults = it },
                                onFailure = { stopSearchError = it.message }
                            )
                            isSearchingStops = false
                        }
                    },
                    enabled = !isSearchingStops && stopQuery.isNotBlank()
                ) {
                    if (isSearchingStops) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Search")
                    }
                }
            }

            stopSearchError?.let { error ->
                Text(
                    text = "✗ $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (stopSearchResults.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    stopSearchResults.forEach { stop ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = config.manualStopId == stop.stationId,
                                onClick = {
                                    config = config.copy(manualStopId = stop.stationId, manualStopName = stop.name)
                                    preferencesManager.saveConfig(config)
                                    repository.invalidateCache()
                                }
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(stop.name, style = MaterialTheme.typography.bodyMedium)
                                if (stop.ferryLines.isNotEmpty()) {
                                    Text(
                                        "Lines ${stop.ferryLines.joinToString(", ")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (stopSearchAttempted && !isSearchingStops && stopSearchError == null) {
                Text(
                    "No stops found",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Routes section — shown when a stop is selected
            if (config.manualStopId != null) {
                Divider(modifier = Modifier.padding(vertical = 4.dp))

                Text("Routes", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Only show departures for selected routes. Leave all checked to show everything.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Fetch available connections whenever the stop changes
                LaunchedEffect(config.manualStopId) {
                    val stopId = config.manualStopId ?: return@LaunchedEffect
                    val stopName = config.manualStopName ?: stopId
                    isLoadingConnections = true
                    connectionsError = null
                    availableConnections = emptyList()
                    // Clear connection filter and invalidate cache when stop changes
                    if (config.enabledConnections.isNotEmpty()) {
                        config = config.copy(enabledConnections = emptySet())
                        preferencesManager.saveConfig(config)
                    }
                    repository.invalidateCache()
                    try {
                        val stop = dev.antonyng.hvvferry.data.models.FerryStop(
                            stationId = stopId,
                            name = stopName,
                            latitude = 0.0,
                            longitude = 0.0,
                            ferryLines = emptyList()
                        )
                        val result = repository.getDepartures(stop, 20, ignoreConnectionFilter = true)
                        result.fold(
                            onSuccess = { departures ->
                                availableConnections = departures
                                    .map { Pair(it.line.name, it.line.direction) }
                                    .distinct()
                                    .sortedWith(compareBy({ it.first }, { it.second }))
                            },
                            onFailure = { connectionsError = it.message }
                        )
                    } catch (e: Exception) {
                        connectionsError = e.message
                    }
                    isLoadingConnections = false
                }

                when {
                    isLoadingConnections -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Loading routes...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    connectionsError != null -> {
                        Text(
                            "Could not load routes: $connectionsError",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    availableConnections.isNotEmpty() -> {
                        val allKeys = availableConnections
                            .map { FerryConfig.connectionKey(it.first, it.second) }
                            .toSet()

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            availableConnections.forEach { (line, direction) ->
                                val key = FerryConfig.connectionKey(line, direction)
                                // Empty enabledConnections = all shown; treat as all checked
                                val isChecked = config.enabledConnections.isEmpty() ||
                                    key in config.enabledConnections
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            val current = if (config.enabledConnections.isEmpty()) allKeys
                                                          else config.enabledConnections
                                            val updated = if (checked) current + key else current - key
                                            // If all selected again, go back to empty (= show all)
                                            config = config.copy(
                                                enabledConnections = if (updated == allKeys) emptySet() else updated
                                            )
                                            preferencesManager.saveConfig(config)
                                        }
                                    )
                                    Text(
                                        "$line → $direction",
                                        modifier = Modifier.padding(start = 8.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        // Show "Select all" only when some are filtered out
                        if (config.enabledConnections.isNotEmpty()) {
                            TextButton(onClick = {
                                config = config.copy(enabledConnections = emptySet())
                                preferencesManager.saveConfig(config)
                            }) { Text("Select all") }
                        }
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Display Settings Section
            Text(
                "Display Settings",
                style = MaterialTheme.typography.headlineSmall
            )
            
            // Route Format
            Text("Display Mode", style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RouteFormat.entries.forEach { format ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = config.routeFormat == format,
                            onClick = {
                                config = config.copy(routeFormat = format)
                                preferencesManager.saveConfig(config)
                            }
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = when (format) {
                                    RouteFormat.ABBREVIATED -> "Abbreviated"
                                    RouteFormat.DIRECTION_ONLY -> "Full destination"
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = when (format) {
                                    RouteFormat.ABBREVIATED -> "62 → Finkenw.  ·  Brücke 3"
                                    RouteFormat.DIRECTION_ONLY -> "62 → Finkenwerder  ·  Landungsbrücken Brücke 3"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            // Two Departures Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show Two Departures", style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = config.showTwoDepartures,
                    onCheckedChange = {
                        config = config.copy(showTwoDepartures = it)
                        preferencesManager.saveConfig(config)
                    }
                )
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // GPS Settings Section
            Text(
                "GPS Settings",
                style = MaterialTheme.typography.headlineSmall
            )
            
            // GPS Auto-Detection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto-Detect Nearby Stops", style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = config.gpsAutoDetectionEnabled,
                    onCheckedChange = {
                        config = config.copy(gpsAutoDetectionEnabled = it)
                        preferencesManager.saveConfig(config)
                    }
                )
            }
            
            // Proximity Radius
            Text("Detection Radius", style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ProximityRadius.entries.forEach { radius ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = config.proximityRadiusMeters == radius.meters,
                            onClick = {
                                config = config.copy(proximityRadiusMeters = radius.meters)
                                preferencesManager.saveConfig(config)
                            },
                            enabled = config.gpsAutoDetectionEnabled
                        )
                        Text(
                            text = radius.displayName,
                            modifier = Modifier.padding(start = 8.dp),
                            color = if (config.gpsAutoDetectionEnabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            }
                        )
                    }
                }
            }
            
            // GPS Lookup Interval
            Text("GPS Stop Lookup Interval", style = MaterialTheme.typography.titleMedium)
            Text(
                "How often to search for nearby ferry stops based on your location",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GpsLookupInterval.entries.forEach { interval ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = config.gpsLookupIntervalSeconds == interval.seconds,
                            onClick = {
                                config = config.copy(gpsLookupIntervalSeconds = interval.seconds)
                                preferencesManager.saveConfig(config)
                            },
                            enabled = config.gpsAutoDetectionEnabled
                        )
                        Text(
                            text = interval.displayName,
                            modifier = Modifier.padding(start = 8.dp),
                            color = if (config.gpsAutoDetectionEnabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            }
                        )
                    }
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Update Settings Section
            Text(
                "Update Settings",
                style = MaterialTheme.typography.headlineSmall
            )
            
            Text("Update Interval", style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                UpdateInterval.entries.forEach { interval ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = config.updateIntervalSeconds == interval.seconds,
                            onClick = {
                                config = config.copy(updateIntervalSeconds = interval.seconds)
                                preferencesManager.saveConfig(config)
                            }
                        )
                        Text(
                            text = interval.displayName,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Ferry Lines Section
            Text(
                "Ferry Lines",
                style = MaterialTheme.typography.headlineSmall
            )
            
            Text("Show departures for these lines:", style = MaterialTheme.typography.bodyMedium)
            
            val allFerryLines = listOf("62", "64", "68", "72", "73", "75")
            allFerryLines.forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = config.enabledFerryLines.contains(line),
                        onCheckedChange = { checked ->
                            val newLines = if (checked) {
                                config.enabledFerryLines + line
                            } else {
                                config.enabledFerryLines - line
                            }
                            config = config.copy(enabledFerryLines = newLines)
                            preferencesManager.saveConfig(config)
                        }
                    )
                    Text(
                        text = "Ferry Line $line",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Instructions Section
            Text(
                "Setup Instructions",
                style = MaterialTheme.typography.headlineSmall
            )
            
            Text(
                """
                1. Enter your Geofox API credentials above
                2. Click 'Test Connection' to verify
                3. Configure your display and GPS settings
                4. Add the 'Next Ferry' data field to your Karoo profile
                5. The extension will automatically show nearby ferry departures
                """.trimIndent(),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
