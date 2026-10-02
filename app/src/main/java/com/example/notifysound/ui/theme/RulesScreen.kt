package com.example.notifysound

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete

@Composable
fun RulesScreen(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).soundRuleDao() }
    val coroutineScope = rememberCoroutineScope()

    val rules by dao.getAllRulesFlow().collectAsState(initial = emptyList())

    var emailInput by remember { mutableStateOf("") }
    var selectedSound by remember { mutableStateOf("fahh") }
    val availableSounds = listOf("fahh", "bruh", "fornite")

    Column(modifier = modifier.padding(16.dp)) {

        Text("Sound Rules", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        // ---- Existing Rules List ----
        if (rules.isEmpty()) {
            Text("No rules yet. Add one below.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(rules) { rule ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(rule.identifierMatch, style = MaterialTheme.typography.bodyLarge)
                                Text("Sound: ${rule.soundFileName}", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    dao.deleteRule(rule)
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete rule"
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        // ---- Add New Rule Form ----
        Text("Add New Rule", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = emailInput,
            onValueChange = { emailInput = it },
            label = { Text("Email address") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text("Sound:", style = MaterialTheme.typography.bodyMedium)
        Row {
            availableSounds.forEach { sound ->
                FilterChip(
                    selected = selectedSound == sound,
                    onClick = { selectedSound = sound },
                    label = { Text(sound) },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (emailInput.isNotBlank()) {
                    coroutineScope.launch {
                        dao.insertRule(
                            SoundRule(
                                packageName = "com.google.android.gm",
                                identifierMatch = emailInput.trim(),
                                soundFileName = selectedSound
                            )
                        )
                        emailInput = ""
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add Rule")
        }
    }
}