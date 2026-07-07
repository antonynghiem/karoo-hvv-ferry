package io.hammerhead.hvvferry.ui

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
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import io.hammerhead.hvvferry.data.models.FerryConfig
import io.hammerhead.hvvferry.data.models.ProximityRadius
import io.hammerhead.hvvferry.data.models.RouteFormat
import io.hammerhead.hvvferry.data.models.UpdateInterval
import io.hammerhead.hvvferry.data.preferences.CredentialManager
import io.hammerhead.hvvferry.data.preferences.PreferencesManager
import io.hammerhead.hvvferry.data.repository.FerryRepository
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
                singleLine = true
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
            
            // Display Settings Section
            Text(
                "Display Settings",
                style = MaterialTheme.typography.headlineSmall
            )
            
            // Route Format
            Text("Route Format", style = MaterialTheme.typography.titleMedium)
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
                        Text(
                            text = when (format) {
                                RouteFormat.FULL -> "Full (Landungsbrücken → Finkenwerder)"
                                RouteFormat.ABBREVIATED -> "Abbreviated (Landungsbr. → Finkenwerder)"
                                RouteFormat.DIRECTION_ONLY -> "Direction Only (→ Finkenwerder)"
                            },
                            modifier = Modifier.padding(start = 8.dp)
                        )
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
