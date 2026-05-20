package com.example.ingredient

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ingredient.model.SessionManager
import com.example.ingredient.ui.theme.IngredientTheme
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import java.io.File
import java.io.FileWriter

class MainActivity : ComponentActivity() {

    private lateinit var tesseractManager: EnhancedTesseractManager
    private lateinit var databaseReference: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        try {
            databaseReference = FirebaseDatabase.getInstance().getReference()
            Log.d("MainActivity", "Firebase Database initialized successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing Firebase Database", e)
            Toast.makeText(this, "Firebase initialization failed", Toast.LENGTH_SHORT).show()
        }

        tesseractManager = EnhancedTesseractManager(applicationContext)

        val sessionManager = SessionManager(applicationContext)
        val initialScreen: String = when {
            !sessionManager.isDisclaimerAccepted() -> "disclaimer"
            sessionManager.isLoggedIn() -> if (sessionManager.getUserType() == "Ristoratore") "Ristoratore" else "Cliente"
            else -> "Login"
        }

        setContent {
            IngredientTheme {
                IngredientApp(tesseractManager, databaseReference, initialScreen)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tesseractManager.recycle()
    }
}

@Composable
fun IngredientApp(
    tesseractManager: EnhancedTesseractManager,
    databaseReference: DatabaseReference,
    initialScreen: String = "Login"
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    var currentScreen by remember { mutableStateOf(initialScreen) }
    var currentUserId by remember {
        mutableStateOf<String?>(sessionManager.getUserId().ifEmpty { null })
    }
    var currentUserEmail by remember { mutableStateOf<String?>(null) }
    var userType by remember { mutableStateOf(sessionManager.getUserType()) }

    // Vetrina navigation state
    var vetrinaRestaurantId by remember { mutableStateOf("") }
    var vetrinaRestaurantName by remember { mutableStateOf("") }
    var vetrinaLat by remember { mutableStateOf(0.0) }
    var vetrinaLon by remember { mutableStateOf(0.0) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        when (currentScreen) {
            "disclaimer" -> DisclaimerScreen(
                onAccepted = {
                    val sessionManager = SessionManager(context)
                    sessionManager.setDisclaimerAccepted()
                    currentScreen = "Login"
                },
                modifier = Modifier.padding(innerPadding)
            )
            "Login" -> LoginScreen(
                databaseReference = databaseReference,
                onNavigateToRegister = { currentScreen = "Register" },
                onLoginSuccess = { userId, type ->
                    currentUserId = userId
                    userType = type
                    currentScreen = if (type == "Cliente") "Cliente" else "Ristoratore"
                },
                modifier = Modifier.padding(innerPadding)
            )
            "Register" -> RegistrationScreen(
                databaseReference = databaseReference,
                onNavigateToLogin = { currentScreen = "Login" },
                onRegisterSuccess = { userId, type ->
                    currentUserId = userId
                    userType = type
                    currentScreen = if (type == "Cliente") "Cliente" else "Ristoratore"
                },
                modifier = Modifier.padding(innerPadding)
            )
            "Cliente" -> {
                ClienteScreen(
                    userId = currentUserId,
                    databaseReference = databaseReference,
                    onLogout = {
                        currentUserId = null
                        currentUserEmail = null
                        userType = ""
                        currentScreen = "Login"
                    },
                    onViewVetrina = { restaurantId, restaurantName, lat, lon ->
                        vetrinaRestaurantId = restaurantId
                        vetrinaRestaurantName = restaurantName
                        vetrinaLat = lat ?: 0.0
                        vetrinaLon = lon ?: 0.0
                        currentScreen = "Vetrina"
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            "Vetrina" -> {
                VetrinaScreen(
                    restaurantId = vetrinaRestaurantId,
                    restaurantName = vetrinaRestaurantName,
                    restaurantLat = vetrinaLat,
                    restaurantLon = vetrinaLon,
                    userId = currentUserId,
                    databaseReference = databaseReference,
                    onBack = { currentScreen = "Cliente" },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            "Ristoratore" -> {
                RistoratoreScreen(
                    tesseractManager = tesseractManager,
                    userId = currentUserId,
                    databaseReference = databaseReference,
                    onLogout = {
                        currentUserId = null
                        currentUserEmail = null
                        userType = ""
                        currentScreen = "Login"
                    },
                    onViewMenu = {
                        currentScreen = "MenuEditor"
                    },
                    onGenerateQr = {
                        currentScreen = "QrCode"
                    },
                    onScanQrMenu = {
                        currentScreen = "QrMenuImport"
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            "MenuEditor" -> {
                if (currentUserId != null) {
                    MenuEditorScreen(
                        userId = currentUserId!!,
                        databaseReference = databaseReference,
                        onBack = { currentScreen = "Ristoratore" },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
            "QrCode" -> {
                if (currentUserId != null) {
                    QrCodeScreen(
                        userId = currentUserId!!,
                        databaseReference = databaseReference,
                        onBack = { currentScreen = "Ristoratore" },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
            "QrMenuImport" -> {
                if (currentUserId != null) {
                    QrMenuImportScreen(
                        userId = currentUserId!!,
                        databaseReference = databaseReference,
                        onBack = { currentScreen = "Ristoratore" },
                        onMenuImported = { currentScreen = "Ristoratore" },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}



@Composable
fun RistoratoreScreen(
    tesseractManager: EnhancedTesseractManager,
    userId: String?,
    databaseReference: DatabaseReference,
    onLogout: () -> Unit,
    onViewMenu: () -> Unit,
    onGenerateQr: () -> Unit,
    onScanQrMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var recognizedText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var tesseractReady by remember { mutableStateOf(tesseractManager.isReady()) }

    var showManualSelector by remember { mutableStateOf(false) }
    var manualColumnRegions by remember { mutableStateOf<List<Pair<Int, Int>>?>(null) }
    var useColumnSplit by remember { mutableStateOf(false) }
    var hasMenu by remember { mutableStateOf(false) }
    var appendMode by remember { mutableStateOf(false) }

    // Multi-photo import state
    var photoUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Check if this restaurant has dishes uploaded
    LaunchedEffect(userId) {
        if (userId != null) {
            databaseReference.child("dishes")
                .orderByChild("restaurantId").equalTo(userId)
                .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        hasMenu = snapshot.childrenCount > 0
                    }
                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
                })
        }
    }


    LaunchedEffect(Unit) {
        while (!tesseractReady) {
            delay(1000)
            tesseractReady = tesseractManager.isReady()
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri = uri
        recognizedText = ""
        statusMessage = ""
        manualColumnRegions = null
        useColumnSplit = false
        uri?.let {
            loadedBitmap = uriToBitmap(context, it)
        }
    }

    val menuFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                isProcessing = true
                if (userId != null) {
                    uploadMenuFromFile(
                        context = context,
                        uri = it,
                        userId = userId,
                        databaseReference = databaseReference,
                        onStatusUpdate = { status ->
                            statusMessage = status
                        }
                    )
                } else {
                    statusMessage = "❌ User ID not available"
                }
                isProcessing = false
            }
        }
    }

    val multiImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            photoUris = uris
            statusMessage = "${uris.size} foto selezionate"
        }
    }

    val cameraPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraPhotoUri?.let { uri ->
                photoUris = photoUris + uri
                statusMessage = "${photoUris.size} foto pronte"
            }
        }
    }

    if (showManualSelector && loadedBitmap != null) {
        ManualColumnSelector(
            bitmap = loadedBitmap!!,
            onColumnsConfirmed = { regions ->
                manualColumnRegions = regions
                useColumnSplit = true
                showManualSelector = false
                statusMessage = "Manual columns set: ${regions.size} columns"
            },
            onCancel = {
                showManualSelector = false
                statusMessage = "Manual selection cancelled"
            }
        )
        return
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top
    ) {
        // ── Header ──────────────────────────────────────────────────
        Surface(color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Restaurant,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pannello Ristoratore",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    if (userId != null) {
                        Text(
                            text = "ID: $userId",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        )
                    }
                }
                IconButton(onClick = onLogout) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Logout",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        // ── Processing indicator ─────────────────────────────────────
        if (isProcessing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Column(modifier = Modifier.padding(16.dp)) {

            // ── Quick actions ─────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onViewMenu, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Gestisci Menu")
                }
                if (hasMenu) {
                    OutlinedButton(
                        onClick = onGenerateQr,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Filled.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("QR Code")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── System init card ──────────────────────────────────────
            if (!tesseractReady) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Inizializzazione sistema", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("OCR Engine in caricamento…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Status message ────────────────────────────────────────
            if (statusMessage.isNotEmpty()
                && !statusMessage.contains("loading", ignoreCase = true)
                && !statusMessage.contains("caricamento", ignoreCase = true)) {
                val isSuccess = statusMessage.startsWith("✅")
                val isError = statusMessage.startsWith("❌")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isSuccess -> MaterialTheme.colorScheme.primaryContainer
                            isError -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Text(
                        text = statusMessage,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            isSuccess -> MaterialTheme.colorScheme.onPrimaryContainer
                            isError -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Step 1: Load image ────────────────────────────────────
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(32.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("1", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Carica Immagine Menu", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (imageUri != null) "✓ Immagine caricata" else "Seleziona una foto del menu",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (imageUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { imageLauncher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (imageUri != null) "Cambia Immagine" else "Scegli Immagine")
                    }
                }
            }

            // ── Step 2 + 3: Column split + OCR (only when image loaded) ─
            if (imageUri != null) {
                Spacer(Modifier.height(12.dp))

                // Step 2 – column split
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(32.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("2", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Menu a Colonne", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (useColumnSplit) "${manualColumnRegions?.size ?: 0} colonne configurate" else "Attiva se il menu ha più colonne affiancate",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = useColumnSplit,
                                onCheckedChange = {
                                    if (!useColumnSplit) showManualSelector = true
                                    else { useColumnSplit = false; manualColumnRegions = null; statusMessage = "" }
                                }
                            )
                        }
                        if (useColumnSplit && manualColumnRegions != null) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showManualSelector = true },
                                enabled = !isProcessing,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Modifica Colonne")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Step 3 – OCR
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(32.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("3", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Riconosci Testo (OCR)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (recognizedText.isNotEmpty()) "✓ Testo estratto" else "Avvia il riconoscimento ottico del testo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (recognizedText.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isProcessing = true
                                    if (useColumnSplit && manualColumnRegions != null) {
                                        statusMessage = "Elaborazione ${manualColumnRegions!!.size} colonne…"
                                        processImageWithManualColumns(
                                            context = context,
                                            bitmap = loadedBitmap!!,
                                            tesseractManager = tesseractManager,
                                            columnRegions = manualColumnRegions!!
                                        ) { text -> recognizedText = text; statusMessage = "✅ Testo estratto"; isProcessing = false }
                                    } else {
                                        statusMessage = "Riconoscimento in corso…"
                                        processImageWithTesseract(
                                            context = context,
                                            uri = imageUri!!,
                                            tesseractManager = tesseractManager,
                                            useColumnSplit = false
                                        ) { text -> recognizedText = text; statusMessage = "✅ Testo estratto"; isProcessing = false }
                                    }
                                }
                            },
                            enabled = !isProcessing && tesseractReady,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Avvia OCR")
                        }
                        if (recognizedText.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                                Text(
                                    text = recognizedText.take(300) + if (recognizedText.length > 300) "\n…" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ── Step 4: AI + Firebase ─────────────────────────────────
            if (recognizedText.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("4", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Analisi AI & Pubblicazione", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text("L'AI estrae i piatti e carica su Firebase", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (appendMode)
                                    MaterialTheme.colorScheme.secondaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (appendMode) "➕ Aggiungi al menu esistente" else "🔄 Sostituisci menu esistente",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (appendMode)
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (appendMode)
                                            "I piatti esistenti vengono mantenuti"
                                        else
                                            "I piatti esistenti saranno eliminati",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (appendMode)
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        else
                                            MaterialTheme.colorScheme.error
                                    )
                                }
                                Switch(
                                    checked = appendMode,
                                    onCheckedChange = { appendMode = it }
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isProcessing = true
                                    statusMessage = "Analisi AI in corso…"
                                    val llmClient = LLMApiClient(BuildConfig.GROQ_API_KEY)
                                    val response = llmClient.processMenuText(recognizedText)
                                    saveResponseToFile(context, response, useJson = false)
                                    val menuCategories = MenuParser().parseMenuText(response)
                                    if (menuCategories.isNotEmpty() && userId != null) {
                                        val result = FirebaseMenuUploader(databaseReference).uploadMenuWithMetadata(
                                            userId = userId,
                                            restaurantName = "My Restaurant",
                                            menuCategories = menuCategories,
                                            append = appendMode
                                        )
                                        if (result.isSuccess) { statusMessage = "✅ Menu pubblicato con successo!"; hasMenu = true }
                                        else statusMessage = "❌ Errore: ${result.exceptionOrNull()?.message}"
                                    } else {
                                        statusMessage = "❌ Nessun dato trovato o ID utente mancante"
                                    }
                                    isProcessing = false
                                }
                            },
                            enabled = !isProcessing && userId != null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (appendMode) "Aggiungi al Menu su Firebase" else "Pubblica Menu su Firebase")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    isProcessing = true
                                    statusMessage = "Elaborazione AI…"
                                    val response = LLMApiClient(BuildConfig.GROQ_API_KEY).processMenuText(recognizedText)
                                    saveResponseToFile(context, response, useJson = false)
                                    isProcessing = false
                                    statusMessage = "✅ Risposta AI salvata su file"
                                }
                            },
                            enabled = !isProcessing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Salva Solo Risposta AI")
                        }
                    }
                }
            }

            // ── Import from QR ────────────────────────────────────────
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Il tuo ristorante ha già un QR code?",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onScanQrMenu,
                enabled = !isProcessing && userId != null,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Scansiona QR Menu Esistente")
            }

            // ── Importa da Foto (multi-photo) ─────────────────────────
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Scatta o carica più foto del menu",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(8.dp))
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Foto del Menu",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (photoUris.isEmpty())
                                    "Analisi automatica in background, come per i PDF"
                                else
                                    "✓ ${photoUris.size} foto pronte",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (photoUris.isNotEmpty())
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { multiImageLauncher.launch("image/*") },
                            enabled = !isProcessing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Galleria")
                        }
                        OutlinedButton(
                            onClick = {
                                val tempFile = File.createTempFile("menu_photo_", ".jpg", context.cacheDir)
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    tempFile
                                )
                                cameraPhotoUri = uri
                                cameraPhotoLauncher.launch(uri)
                            },
                            enabled = !isProcessing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Fotocamera")
                        }
                    }
                    if (photoUris.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${photoUris.size} foto selezionate",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(onClick = {
                                photoUris = emptyList()
                                cameraPhotoUri = null
                            }) {
                                Text("Cancella tutto", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isProcessing = true
                                    try {
                                        val ocrText = ImageMenuExtractor.extractText(
                                            context = context,
                                            uris = photoUris,
                                            onProgress = { statusMessage = it }
                                        )
                                        if (ocrText.isBlank()) {
                                            statusMessage = "❌ Impossibile leggere il testo dalle foto."
                                            isProcessing = false
                                            return@launch
                                        }
                                        val estChunks = (ocrText.length / 4000) + 1
                                        statusMessage = if (estChunks > 1)
                                            "Analisi AI (0/$estChunks sezioni)…"
                                        else
                                            "Analisi AI in corso…"
                                        val llmClient = LLMApiClient(BuildConfig.GROQ_API_KEY)
                                        val response = llmClient.processMenuText(
                                            menuText = ocrText,
                                            onProgress = { cur, tot ->
                                                statusMessage = "Analisi AI: sezione $cur/$tot…"
                                            }
                                        )
                                        val menuCategories = MenuParser().parseMenuText(response)
                                        if (menuCategories.isNotEmpty() && userId != null) {
                                            val count = menuCategories.sumOf { it.dishes.size }
                                            statusMessage = "Arricchimento ingredienti…"
                                            val enriched = llmClient.enrichDishes(
                                                categories = menuCategories,
                                                onProgress = { msg -> statusMessage = msg }
                                            )
                                            val result = FirebaseMenuUploader(databaseReference)
                                                .uploadMenuWithMetadata(
                                                    userId = userId,
                                                    restaurantName = "My Restaurant",
                                                    menuCategories = enriched,
                                                    append = appendMode
                                                )
                                            if (result.isSuccess) {
                                                statusMessage = "✅ Menu importato da ${photoUris.size} foto: $count piatti!"
                                                hasMenu = true
                                                photoUris = emptyList()
                                            } else {
                                                statusMessage = "❌ Errore upload: ${result.exceptionOrNull()?.message}"
                                            }
                                        } else {
                                            statusMessage = "❌ Nessun piatto trovato nelle foto."
                                        }
                                    } catch (e: Exception) {
                                        statusMessage = "❌ Errore: ${e.message}"
                                    }
                                    isProcessing = false
                                }
                            },
                            enabled = !isProcessing && userId != null && tesseractReady,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (appendMode)
                                    "Aggiungi al Menu (${photoUris.size} foto)"
                                else
                                    "Analizza e Pubblica (${photoUris.size} foto)"
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

suspend fun uploadMenuFromFile(
    context: Context,
    uri: Uri,
    userId: String,
    databaseReference: DatabaseReference,
    onStatusUpdate: (String) -> Unit
) = withContext(Dispatchers.IO) {
    try {
        onStatusUpdate("Reading menu file...")

        val menuText = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.bufferedReader().use { it.readText() }
        } ?: throw Exception("Could not read file")

        Log.d("MainActivity", "Menu text loaded: ${menuText.length} characters")

        onStatusUpdate("Parsing menu...")
        val parser = MenuParser()
        val menuCategories = parser.parseMenuText(menuText)

        Log.d("MainActivity", "Parsed ${menuCategories.size} categories")

        if (menuCategories.isEmpty()) {
            withContext(Dispatchers.Main) {
                onStatusUpdate("❌ No valid menu data found in file")
            }
            return@withContext
        }

        onStatusUpdate("Uploading to Firebase...")
        val uploader = FirebaseMenuUploader(databaseReference)
        val result = uploader.uploadMenuWithMetadata(
            userId = userId,
            restaurantName = "My Restaurant",
            menuCategories = menuCategories
        )

        withContext(Dispatchers.Main) {
            if (result.isSuccess) {
                onStatusUpdate("✅ ${result.getOrNull()}")
                Toast.makeText(
                    context,
                    "Menu uploaded successfully!",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                onStatusUpdate("❌ Error: ${result.exceptionOrNull()?.message}")
                Toast.makeText(
                    context,
                    "Upload failed: ${result.exceptionOrNull()?.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    } catch (e: Exception) {
        Log.e("MainActivity", "Error uploading menu from file", e)
        withContext(Dispatchers.Main) {
            onStatusUpdate("❌ Error: ${e.message}")
            Toast.makeText(
                context,
                "Error: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

suspend fun processImageWithManualColumns(
    context: Context,
    bitmap: Bitmap,
    tesseractManager: EnhancedTesseractManager,
    columnRegions: List<Pair<Int, Int>>,
    onTextRecognized: (String) -> Unit
) = withContext(Dispatchers.IO) {
    try {
        val text = processImageWithManualColumns(
            tesseract = tesseractManager,
            bitmap = bitmap,
            columnRegions = columnRegions,
            lang = "spa"
        )

        withContext(Dispatchers.Main) {
            onTextRecognized(text)
        }
    } catch (e: Exception) {
        Log.e("MainActivity", "Error with manual columns", e)
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

suspend fun processImageWithTesseract(
    context: Context,
    uri: Uri,
    tesseractManager: EnhancedTesseractManager,
    useColumnSplit: Boolean = false,
    onTextRecognized: (String) -> Unit
) = withContext(Dispatchers.IO) {
    try {
        val bitmap = uriToBitmap(context, uri)
        if (bitmap != null) {
            val text = tesseractManager.processImage(
                image = bitmap,
                lang = "spa",
                useColumnSplit = useColumnSplit,
                addColumnMarkers = false
            )

            withContext(Dispatchers.Main) {
                onTextRecognized(text)
            }
        } else {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error loading image", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        Log.e("MainActivity", "Error recognizing text", e)
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Error recognizing text: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }
    } catch (e: Exception) {
        Log.e("MainActivity", "Error converting URI to Bitmap", e)
        null
    }
}

fun saveResponseToFile(context: Context, content: String, useJson: Boolean = false) {
    try {
        val fileExtension = if (useJson) "json" else "txt"
        val fileName = "Menu_${System.currentTimeMillis()}.$fileExtension"
        val successMessage = "$fileName created successfully in Downloads/Menu"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, if (useJson) "application/json" else "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Menu")
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                Toast.makeText(context, successMessage, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Error: Could not create file.", Toast.LENGTH_SHORT).show()
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val menuDir = File(downloadsDir, "Menu")
            if (!menuDir.exists()) {
                menuDir.mkdirs()
            }
            val file = File(menuDir, fileName)
            FileWriter(file).use { it.write(content) }
            Toast.makeText(context, successMessage, Toast.LENGTH_LONG).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Error creating file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
