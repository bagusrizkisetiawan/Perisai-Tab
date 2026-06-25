package id.co.alphanusa.perisaitab.ui.screen.setting

import id.co.alphanusa.perisaitab.data.local.AppSettingsManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.co.alphanusa.perisaitab.BuildConfig


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: AppSettingsManager,
    onBackPressed: () -> Unit
) {
    var baseUrl by remember { mutableStateOf(settingsManager.getBaseUrl()) }
    var centrifugoUrl by remember { mutableStateOf(settingsManager.getCentrifugoWebSocketUrl()) }
    var rtmpUrl by remember { mutableStateOf(settingsManager.getRtmpUrl()) }
    var livekitUrl by remember { mutableStateOf(settingsManager.getLivekitUrl()) }
    var showSaveSnackbar by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Server Configuration",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Configure the server URLs for the application. Changes will take effect after restarting the app.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Base URL Setting
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                placeholder = { Text("e.g., http://localhost:3000") },
                supportingText = { Text("API server base URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Centrifugo WebSocket URL Setting
            OutlinedTextField(
                value = centrifugoUrl,
                onValueChange = { centrifugoUrl = it },
                label = { Text("Centrifugo WebSocket URL") },
                placeholder = { Text("e.g., ws://localhost:8000/connection/websocket") },
                supportingText = { Text("WebSocket URL for real-time communication") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // RTMP URL Setting
            OutlinedTextField(
                value = rtmpUrl,
                onValueChange = { rtmpUrl = it },
                label = { Text("RTMP URL") },
                placeholder = { Text("e.g., rtmp://localhost/live") },
                supportingText = { Text("Base RTMP URL for video streaming") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // LIVEKIT URL Setting
            OutlinedTextField(
                value = livekitUrl,
                onValueChange = { livekitUrl = it },
                label = { Text("Livekit URL") },
                placeholder = { Text("e.g., rtmp://localhost/livekit") },
                supportingText = { Text("Base RTMP URL for comunication streaming") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Default Values Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Default Values",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Base URL: ${BuildConfig.BASE_URL}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Centrifugo URL: ${BuildConfig.CENTRIFUGO_WEBSOCKET_URL}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "RTMP URL: ${BuildConfig.RTMP_URL}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "RTMP URL: ${BuildConfig.LIVEKIT_URL}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Save Button
            Button(
                onClick = {
                    settingsManager.setBaseUrl(baseUrl)
                    settingsManager.setCentrifugoWebSocketUrl(centrifugoUrl)
                    settingsManager.setRtmpUrl(rtmpUrl)
                    settingsManager.seLivekitUrl(livekitUrl)
                    showSaveSnackbar = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Settings")
            }

            // Reset Button
            OutlinedButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset to Defaults")
            }
        }
    }

    // Show snackbar when settings are saved
    LaunchedEffect(showSaveSnackbar) {
        if (showSaveSnackbar) {
            snackbarHostState.showSnackbar(
                message = "Settings saved successfully. Please restart the app for changes to take effect.",
                duration = SnackbarDuration.Long
            )
            showSaveSnackbar = false
        }
    }

    // Reset confirmation dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Settings") },
            text = { Text("Are you sure you want to reset all settings to default values?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        settingsManager.resetToDefaults()
                        baseUrl = settingsManager.getBaseUrl()
                        centrifugoUrl = settingsManager.getCentrifugoWebSocketUrl()
                        rtmpUrl = settingsManager.getRtmpUrl()
                        livekitUrl = settingsManager.getLivekitUrl()
                        showResetDialog = false
                        showSaveSnackbar = true
                    }
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
