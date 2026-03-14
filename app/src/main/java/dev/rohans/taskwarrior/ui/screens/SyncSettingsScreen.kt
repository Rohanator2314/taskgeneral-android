package dev.rohans.taskwarrior.ui.screens

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.rohans.taskwarrior.viewmodel.TaskViewModel
import dev.rohans.taskwarrior.navigation.Route

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun SyncSettingsScreen(
    navController: NavController,
    viewModel: TaskViewModel
) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val encryptionSecret by viewModel.encryptionSecret.collectAsState()
    val clientId by viewModel.clientId.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var urlInput by remember { mutableStateOf("") }
    var secretInput by remember { mutableStateOf("") }
    var clientIdInput by remember { mutableStateOf("") }
    var isSecretVisible by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }

    if (!initialized && (serverUrl.isNotEmpty() || clientId.isNotEmpty())) {
        urlInput = serverUrl
        secretInput = encryptionSecret
        clientIdInput = clientId
        initialized = true
    }

    if (showClearDataDialog) {
        AlertDialog(
            shape = RectangleShape,
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Clear Local Data?") },
            text = { Text("This will delete all local tasks. This cannot be undone. You can re-sync from the server afterward.") },
            confirmButton = {
                TextButton(
                    shape = RectangleShape,
                    onClick = {
                        viewModel.clearLocalData()
                        showClearDataDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(
                    shape = RectangleShape,
                    onClick = { showClearDataDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { scaffoldPadding ->
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(scaffoldPadding)
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
                    TextButton(
                        shape = RectangleShape,
                        onClick = { isSecretVisible = !isSecretVisible }
                    ) {
                        Text(if (isSecretVisible) "Hide" else "Show")
                    }
                }
            )

            OutlinedTextField(
                value = clientIdInput,
                onValueChange = { clientIdInput = it },
                label = { Text("Client ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    TextButton(
                        shape = RectangleShape,
                        onClick = { 
                            clientIdInput = java.util.UUID.randomUUID().toString()
                        }
                    ) {
                        Text("New")
                    }
                }
            )

            Button(
                shape = RectangleShape,
                onClick = { viewModel.saveSyncConfig(urlInput, secretInput, clientIdInput) },
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
                shape = RectangleShape,
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

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "App Settings",
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedButton(
                shape = RectangleShape,
                onClick = { navController.navigate(Route.AppSettings.route) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open App Settings")
            }
            Spacer(modifier = Modifier.height(16.dp))

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Danger Zone",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )

            OutlinedButton(
                shape = RectangleShape,
                onClick = { showClearDataDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Text("Clear Local Data")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    }
}
