package dev.rohans.taskwarrior.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.rohans.taskwarrior.viewmodel.TaskViewModel
import dev.rohans.taskwarrior.viewmodel.TaskViewModelFactory

@Composable
fun SyncSettingsScreen(
    navController: NavController,
    viewModel: TaskViewModel = viewModel(
        factory = TaskViewModelFactory(
            LocalContext.current.filesDir,
            LocalContext.current.getSharedPreferences("taskwarrior_prefs", Context.MODE_PRIVATE)
        )
    )
) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val encryptionSecret by viewModel.encryptionSecret.collectAsState()
    val clientId by viewModel.clientId.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var urlInput by remember { mutableStateOf("") }
    var secretInput by remember { mutableStateOf("") }
    var isSecretVisible by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }

    if (!initialized && serverUrl.isNotEmpty()) {
        urlInput = serverUrl
        secretInput = encryptionSecret
        initialized = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Sync Configuration",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("Server URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            OutlinedTextField(
                value = secretInput,
                onValueChange = { secretInput = it },
                label = { Text("Encryption Secret") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (isSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { isSecretVisible = !isSecretVisible }) {
                        Text(if (isSecretVisible) "Hide" else "Show")
                    }
                }
            )

            OutlinedTextField(
                value = clientId,
                onValueChange = {},
                label = { Text("Client ID") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                singleLine = true
            )

            Button(
                onClick = { viewModel.saveSyncConfig(urlInput, secretInput) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text("Save Configuration")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Sync Actions",
                style = MaterialTheme.typography.titleMedium
            )

            Button(
                onClick = { viewModel.sync() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && urlInput.isNotEmpty() && secretInput.isNotEmpty()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Text("Syncing...")
                } else {
                    Text("Sync Now")
                }
            }

            if (syncStatus != null) {
                Text(
                    text = syncStatus ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (syncStatus?.contains("failed", ignoreCase = true) == true || syncStatus?.contains("error", ignoreCase = true) == true) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
