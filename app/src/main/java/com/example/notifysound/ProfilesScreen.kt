package com.example.notifysound

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.provider.ContactsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.runtime.derivedStateOf
val supportedApps = listOf(
    AppOption("Gmail", "com.google.android.gm"),
    AppOption("Instagram", "com.instagram.android"),
    AppOption("WhatsApp", "com.whatsapp"),
)

data class AppOption(val label: String, val packageName: String)

val bundledSounds = listOf(
    "fahh",
    "bruh",
    "fornite",
    "phub",
    "italian_brainrot_rington",
    "taco_bell_bond",
    "bing_chilling",
    "lego_breaking",
    "bonk"
)

fun resolveSoundRes(fileName: String): Int {
    return when (fileName) {
        "fahh" -> R.raw.fahh
        "bruh" -> R.raw.bruh
        "fornite" -> R.raw.fornite
        "phub" -> R.raw.phub
        "italian_brainrot_rington" -> R.raw.italian_brainrot_ringtone
        "taco_bell_bond" -> R.raw.taco_bell_bong
        "bing_chilling" -> R.raw.bing_chilling
        "lego_breaking" -> R.raw.lego_breaking
        "bonk" -> R.raw.bonk
        else -> R.raw.fahh
    }
}
fun isCustomSound(soundFileName: String): Boolean {
    return soundFileName.startsWith("content://") ||
            soundFileName.startsWith("file://")
}

fun getDisplayNameForSound(context: android.content.Context, soundFileName: String): String {
    if (!isCustomSound(soundFileName)) return soundFileName
    return try {
        val uri = Uri.parse(soundFileName)
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) return it.getString(nameIndex) ?: "Custom sound"
            }
        }
        "Custom sound"
    } catch (e: Exception) {
        "Custom sound"
    }
}

fun previewSound(context: android.content.Context, soundFileName: String) {
    try {
        val mp = MediaPlayer()
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        if (isCustomSound(soundFileName)) {
            mp.setDataSource(context, Uri.parse(soundFileName))
        } else {
            val afd = context.resources.openRawResourceFd(resolveSoundRes(soundFileName))
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
        }
        mp.setOnCompletionListener { it.release() }
        mp.prepare()
        mp.start()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun resolveContactFromUri(
    context: android.content.Context,
    uri: Uri
): Pair<String, String> {
    var name = ""
    var phoneNumber = ""
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(
                    cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
                ) ?: ""
                val contactId = cursor.getString(
                    cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                )
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId),
                    null
                )?.use { phoneCursor ->
                    if (phoneCursor.moveToFirst()) {
                        phoneNumber = phoneCursor.getString(0)
                            .replace(" ", "")
                            .replace("-", "")
                            .replace("(", "")
                            .replace(")", "")
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return Pair(name, phoneNumber)
}

@Composable
fun ProfilesScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).contactDao() }
    val coroutineScope = rememberCoroutineScope()
    val profiles by dao.getAllContactsFlow().collectAsState(initial = emptyList())
    var showAddProfileDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Profiles", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            if (profiles.isEmpty()) {
                Text(
                    "No profiles yet. Tap + to add one.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(profiles) { profile ->
                        ProfileCard(
                            profile = profile,
                            dao = dao,
                            onDelete = {
                                coroutineScope.launch { dao.deleteContact(profile) }
                            }
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { showAddProfileDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Profile")
        }
    }

    if (showAddProfileDialog) {
        AddProfileDialog(dao = dao, onDismiss = { showAddProfileDialog = false })
    }
}

@Composable
fun ProfileCard(
    profile: Contact,
    dao: ContactDao,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val identifiers by dao.getIdentifiersForContact(profile.id)
        .collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    var showAddIdentifierDialog by remember { mutableStateOf(false) }
    var editingIdentifier by remember { mutableStateOf<ContactIdentifier?>(null) }
    var showEditNameDialog by remember { mutableStateOf(false) }

    // Contact picker — hoisted so it survives dialog recomposition
    var pickedContactName by remember { mutableStateOf("") }
    var pickedContactNumber by remember { mutableStateOf("") }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri ->
        uri?.let {
            val (name, number) = resolveContactFromUri(context, it)
            pickedContactName = name
            pickedContactNumber = number
        }
    }

    // Sound file picker — hoisted so it survives dialog recomposition
    var pickedSoundUri by remember { mutableStateOf("") }
    var pickedSoundName by remember { mutableStateOf("") }

    val soundPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            // Persist permission so we can access file later
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            pickedSoundUri = it.toString()
            pickedSoundName = getDisplayNameForSound(context, it.toString())
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                Row {
                    IconButton(onClick = { showEditNameDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit name")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }

            if (identifiers.isEmpty()) {
                Text("No app identifiers yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                identifiers.forEach { id ->
                    val appLabel = supportedApps.find {
                        it.packageName == id.packageName
                    }?.label ?: id.packageName
                    val displayText = if (id.displayLabel.isNotEmpty())
                        id.displayLabel else id.identifier
                    val soundDisplayName = getDisplayNameForSound(context, id.soundFileName)

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "$appLabel: $displayText",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (id.packageName == "com.whatsapp" &&
                                id.displayLabel.isNotEmpty()) {
                                Text(
                                    id.identifier,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Sound: $soundDisplayName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)  // ← key fix
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { previewSound(context, id.soundFileName) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Preview sound",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        Row {
                            IconButton(onClick = { editingIdentifier = id }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = {
                                coroutineScope.launch { dao.deleteIdentifier(id) }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { showAddIdentifierDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add app identifier")
            }
        }
    }

    if (showAddIdentifierDialog) {
        AddIdentifierDialog(
            profile = profile,
            dao = dao,
            pickedContactName = pickedContactName,
            pickedContactNumber = pickedContactNumber,
            pickedSoundUri = pickedSoundUri,
            pickedSoundName = pickedSoundName,
            onPickContact = {
                pickedContactName = ""
                pickedContactNumber = ""
                contactPickerLauncher.launch(null)
            },
            onClearContact = {
                pickedContactName = ""
                pickedContactNumber = ""
            },
            onPickSound = {
                pickedSoundUri = ""
                pickedSoundName = ""
                soundPickerLauncher.launch(arrayOf("audio/*"))
            },
            onClearSound = {
                pickedSoundUri = ""
                pickedSoundName = ""
            },
            onDismiss = {
                showAddIdentifierDialog = false
                pickedContactName = ""
                pickedContactNumber = ""
                pickedSoundUri = ""
                pickedSoundName = ""
            }
        )
    }

    editingIdentifier?.let { identifier ->
        EditIdentifierDialog(
            identifier = identifier,
            dao = dao,
            pickedContactName = pickedContactName,
            pickedContactNumber = pickedContactNumber,
            pickedSoundUri = pickedSoundUri,
            pickedSoundName = pickedSoundName,
            onPickContact = {
                pickedContactName = ""
                pickedContactNumber = ""
                contactPickerLauncher.launch(null)
            },
            onClearContact = {
                pickedContactName = ""
                pickedContactNumber = ""
            },
            onPickSound = {
                pickedSoundUri = ""
                pickedSoundName = ""
                soundPickerLauncher.launch(arrayOf("audio/*"))
            },
            onClearSound = {
                pickedSoundUri = ""
                pickedSoundName = ""
            },
            onDismiss = {
                editingIdentifier = null
                pickedContactName = ""
                pickedContactNumber = ""
                pickedSoundUri = ""
                pickedSoundName = ""
            }
        )
    }

    if (showEditNameDialog) {
        EditProfileNameDialog(
            profile = profile,
            dao = dao,
            onDismiss = { showEditNameDialog = false }
        )
    }
}

@Composable
fun EditProfileNameDialog(profile: Contact, dao: ContactDao, onDismiss: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    var nameInput by remember { mutableStateOf(profile.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile Name") },
        text = {
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = {
                if (nameInput.isNotBlank()) {
                    coroutineScope.launch {
                        dao.updateContact(profile.copy(name = nameInput.trim()))
                    }
                    onDismiss()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddProfileDialog(dao: ContactDao, onDismiss: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    var nameInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Profile") },
        text = {
            Column {
                Text("Enter a name for this profile:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (nameInput.isNotBlank()) {
                    coroutineScope.launch {
                        dao.insertContact(Contact(name = nameInput.trim()))
                    }
                    onDismiss()
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SoundRow(
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(
            1.5.dp, MaterialTheme.colorScheme.primary
        ) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = isSelected, onClick = onSelect)

            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onPreview) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Preview",
                    tint = if (isSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun SoundPicker(
    selectedSound: String,
    pickedSoundUri: String,
    pickedSoundName: String,
    onSoundSelected: (String) -> Unit,
    onPickSound: () -> Unit,
    onClearSound: () -> Unit
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).userSoundDao() }
    val coroutineScope = rememberCoroutineScope()
    val userSounds by dao.getAllSoundsFlow().collectAsState(initial = emptyList())

    // Auto-add newly picked sound to Room database
    LaunchedEffect(pickedSoundUri) {
        if (pickedSoundUri.isNotEmpty() && pickedSoundName.isNotEmpty()) {
            val existing = dao.getSoundByUri(pickedSoundUri)
            if (existing == null) {
                dao.insertSound(
                    UserSound(
                        uri = pickedSoundUri,
                        displayName = pickedSoundName
                    )
                )
            }
            onSoundSelected(pickedSoundUri)
        }
    }

    Column {
        // Bundled sounds
        Text(
            "Bundled sounds",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))

        bundledSounds.forEach { sound ->
            SoundRow(
                label = sound,
                isSelected = selectedSound == sound,
                onSelect = { onSoundSelected(sound) },
                onPreview = { previewSound(context, sound) }
            )
        }

        // User library sounds from Room
        if (userSounds.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Your sounds",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            userSounds.forEach { userSound ->
                SoundRow(
                    label = userSound.displayName,
                    isSelected = selectedSound == userSound.uri,
                    onSelect = { onSoundSelected(userSound.uri) },
                    onPreview = { previewSound(context, userSound.uri) },
                    onDelete = {
                        coroutineScope.launch {
                            dao.deleteSound(userSound)
                        }
                        if (selectedSound == userSound.uri) {
                            onSoundSelected(bundledSounds.first())
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onPickSound,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.AudioFile, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add sound from device")
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Tip: Download any MP3 to your phone first, then pick it here",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AddIdentifierDialog(
    profile: Contact,
    dao: ContactDao,
    pickedContactName: String,
    pickedContactNumber: String,
    pickedSoundUri: String,
    pickedSoundName: String,
    onPickContact: () -> Unit,
    onClearContact: () -> Unit,
    onPickSound: () -> Unit,
    onClearSound: () -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var selectedApp by remember { mutableStateOf(supportedApps.first()) }
    var identifierInput by remember { mutableStateOf("") }
    var displayLabel by remember { mutableStateOf("") }
    var selectedSound by remember { mutableStateOf(bundledSounds.first()) }

    LaunchedEffect(pickedContactName, pickedContactNumber) {
        if (pickedContactName.isNotEmpty() && pickedContactNumber.isNotEmpty()) {
            displayLabel = pickedContactName
            identifierInput = pickedContactNumber
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(when (step) {
                0 -> "Step 1 of 3 — Choose App"
                1 -> "Step 2 of 3 — Enter Contact"
                else -> "Step 3 of 3 — Choose Sound"
            })
        },
        text = {
            val scrollState = rememberScrollState()
            val showScrollHint by remember {
                derivedStateOf { scrollState.canScrollForward }
            }

            Box {
                Column(
                    modifier = Modifier
                        .verticalScroll(scrollState)
                        .padding(bottom = if (showScrollHint) 24.dp else 0.dp)
                ) {
                    when (step) {

                        0 -> {
                            Text(
                                "Which app is this profile for?",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            supportedApps.forEach { app ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selectedApp == app)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    border = if (selectedApp == app)
                                        androidx.compose.foundation.BorderStroke(
                                            1.5.dp, MaterialTheme.colorScheme.primary
                                        )
                                    else null,
                                    onClick = { selectedApp = app }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selectedApp == app,
                                            onClick = { selectedApp = app }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            app.label,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        }

                        1 -> {
                            when (selectedApp.packageName) {
                                "com.whatsapp" -> {
                                    Text(
                                        "Find ${profile.name}'s WhatsApp contact:",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = onPickContact,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Contacts, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Pick from Contacts ★ Recommended")
                                    }
                                    if (displayLabel.isNotEmpty() && identifierInput.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    displayLabel,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                Text(
                                                    identifierInput,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                        TextButton(
                                            onClick = {
                                                displayLabel = ""
                                                identifierInput = ""
                                                onClearContact()
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("Clear selection") }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider()
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        "Or enter phone number manually:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = if (displayLabel.isEmpty()) identifierInput else "",
                                        onValueChange = {
                                            displayLabel = ""
                                            identifierInput = it
                                            onClearContact()
                                        },
                                        label = { Text("Phone number (e.g. +447911223344)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Include country code for best results",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                "com.google.android.gm" -> {
                                    Text(
                                        "Enter ${profile.name}'s Gmail address:",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = identifierInput,
                                        onValueChange = { identifierInput = it },
                                        label = { Text("Email address") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                "com.instagram.android" -> {
                                    Text(
                                        "Enter ${profile.name}'s Instagram display name:",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = identifierInput,
                                        onValueChange = { identifierInput = it },
                                        label = { Text("Display name (as shown in notifications)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Open Instagram, go to their profile and copy their display name exactly",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                else -> {
                                    OutlinedTextField(
                                        value = identifierInput,
                                        onValueChange = { identifierInput = it },
                                        label = { Text("Identifier") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        2 -> {
                            val displayName = if (displayLabel.isNotEmpty())
                                displayLabel else identifierInput
                            Text(
                                "Choose a sound for $displayName on ${selectedApp.label}:",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            SoundPicker(
                                selectedSound = selectedSound,
                                pickedSoundUri = pickedSoundUri,
                                pickedSoundName = pickedSoundName,
                                onSoundSelected = { selectedSound = it },
                                onPickSound = onPickSound,
                                onClearSound = onClearSound
                            )
                        }
                    }
                }

                // Scroll hint
                if (showScrollHint) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                        MaterialTheme.colorScheme.surface
                                    )
                                )
                            )
                    )
                    Text(
                        "↓ scroll for more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                when (step) {
                    0 -> step = 1
                    1 -> { if (identifierInput.isNotBlank()) step = 2 }
                    2 -> {
                        coroutineScope.launch {
                            dao.insertIdentifier(
                                ContactIdentifier(
                                    contactId = profile.id,
                                    packageName = selectedApp.packageName,
                                    identifier = identifierInput.trim(),
                                    soundFileName = selectedSound,
                                    displayLabel = displayLabel.trim()
                                )
                            )
                        }
                        onDismiss()
                    }
                }
            }) {
                Text(if (step == 2) "Save" else "Next →")
            }
        },
        dismissButton = {
            TextButton(onClick = { if (step == 0) onDismiss() else step-- }) {
                Text(if (step == 0) "Cancel" else "← Back")
            }
        }
    )
}

@Composable
fun EditIdentifierDialog(
    identifier: ContactIdentifier,
    dao: ContactDao,
    pickedContactName: String,
    pickedContactNumber: String,
    pickedSoundUri: String,
    pickedSoundName: String,
    onPickContact: () -> Unit,
    onClearContact: () -> Unit,
    onPickSound: () -> Unit,
    onClearSound: () -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var identifierInput by remember { mutableStateOf(identifier.identifier) }
    var displayLabel by remember { mutableStateOf(identifier.displayLabel) }
    var selectedSound by remember { mutableStateOf(identifier.soundFileName) }
    val currentApp = supportedApps.find { it.packageName == identifier.packageName }
        ?: supportedApps.first()
    var step by remember { mutableIntStateOf(0) }

    LaunchedEffect(pickedContactName, pickedContactNumber) {
        if (pickedContactName.isNotEmpty() && pickedContactNumber.isNotEmpty()) {
            displayLabel = pickedContactName
            identifierInput = pickedContactNumber
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (step == 0) "Step 1 of 2 — Edit Contact"
                else "Step 2 of 2 — Choose Sound"
            )
        },
        text = {
            val scrollState = rememberScrollState()
            val showScrollHint by remember {
                derivedStateOf { scrollState.canScrollForward }
            }

            Box {
                Column(
                    modifier = Modifier
                        .verticalScroll(scrollState)
                        .padding(bottom = if (showScrollHint) 24.dp else 0.dp)
                ) {
                    when (step) {
                        0 -> {
                            Text(
                                "App: ${currentApp.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            when (identifier.packageName) {
                                "com.whatsapp" -> {
                                    Button(
                                        onClick = onPickContact,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Contacts, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Pick from Contacts")
                                    }
                                    if (displayLabel.isNotEmpty() && identifierInput.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    displayLabel,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                Text(
                                                    identifierInput,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                        TextButton(
                                            onClick = {
                                                displayLabel = ""
                                                identifierInput = ""
                                                onClearContact()
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("Clear selection") }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider()
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Or enter phone number manually:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = if (displayLabel.isEmpty()) identifierInput else "",
                                        onValueChange = {
                                            displayLabel = ""
                                            identifierInput = it
                                            onClearContact()
                                        },
                                        label = { Text("Phone number (e.g. +447911223344)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                "com.google.android.gm" -> {
                                    OutlinedTextField(
                                        value = identifierInput,
                                        onValueChange = { identifierInput = it },
                                        label = { Text("Email address") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                "com.instagram.android" -> {
                                    OutlinedTextField(
                                        value = identifierInput,
                                        onValueChange = { identifierInput = it },
                                        label = { Text("Display name (as shown in notifications)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                else -> {
                                    OutlinedTextField(
                                        value = identifierInput,
                                        onValueChange = { identifierInput = it },
                                        label = { Text("Identifier") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        1 -> {
                            Text(
                                "Choose a sound for ${currentApp.label}:",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            SoundPicker(
                                selectedSound = selectedSound,
                                pickedSoundUri = pickedSoundUri,
                                pickedSoundName = pickedSoundName,
                                onSoundSelected = { selectedSound = it },
                                onPickSound = onPickSound,
                                onClearSound = onClearSound
                            )
                        }
                    }
                }

                // Scroll hint
                if (showScrollHint) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                        MaterialTheme.colorScheme.surface
                                    )
                                )
                            )
                    )
                    Text(
                        "↓ scroll for more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                when (step) {
                    0 -> { if (identifierInput.isNotBlank()) step = 1 }
                    1 -> {
                        coroutineScope.launch {
                            dao.updateIdentifier(
                                identifier.copy(
                                    identifier = identifierInput.trim(),
                                    soundFileName = selectedSound,
                                    displayLabel = displayLabel.trim()
                                )
                            )
                        }
                        onDismiss()
                    }
                }
            }) { Text(if (step == 1) "Save" else "Next →") }
        },
        dismissButton = {
            TextButton(onClick = { if (step == 0) onDismiss() else step-- }) {
                Text(if (step == 0) "Cancel" else "← Back")
            }
        }
    )
}