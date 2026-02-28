package com.privacyshield.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsSection("Model") {
                Text(
                    "Place your GGUF model at:\nAndroid/data/com.privacyshield/files/models/tinyllama-q4.gguf",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = { /* TODO: open file browser */ }, modifier = Modifier.fillMaxWidth()) {
                    Text("Browse for Model File")
                }
            }

            SettingsSection("Privacy Policy") {
                Text(
                    """Privacy Shield is designed with privacy as its core principle:

• No internet connection required or used
• Camera frames are processed in memory only — never saved
• OCR text is classified and immediately discarded
• Chat messages exist only in RAM — cleared on reset or app exit
• No analytics, tracking, or crash reporting
• No data leaves your device under any circumstance
• The app declares no INTERNET permission in its manifest""",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            SettingsSection("About") {
                Text("Privacy Shield v1.0.0", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Open-source components: llama.cpp (MIT), TensorFlow Lite (Apache 2.0), ML Kit (Google APIs ToS), CameraX (Apache 2.0)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            HorizontalDivider()
            content()
        }
    }
}
