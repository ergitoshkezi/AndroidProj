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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ingredient.model.SessionManager
import com.example.ingredient.ui.theme.IngredientTheme
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
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
fun IngredientApp(tesseractManager: EnhancedTesseractManager, databaseReference: DatabaseReference) {
    var currentScreen by remember { mutableStateOf("Login") }
    var currentUserId by remember { mutableStateOf<String?>(null) }
    var currentUserEmail by remember { mutableStateOf<String?>(null) }
    var userType by remember { mutableStateOf("") }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        when (currentScreen) {
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
    modifier: Modifier = Modifier
) {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var recognizedText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var modelReady by remember { mutableStateOf(LocalAiParser.isReady()) }
    var tesseractReady by remember { mutableStateOf(tesseractManager.isReady()) }

    var showManualSelector by remember { mutableStateOf(false) }
    var manualColumnRegions by remember { mutableStateOf<List<Pair<Int, Int>>?>(null) }
    var useColumnSplit by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (!modelReady) {
            delay(1000)
            modelReady = LocalAiParser.isReady()
            if (LocalAiParser.isInitializing()) {
                statusMessage = "AI Model is loading... Please wait."
            } else if (modelReady) {
                statusMessage = "AI Model ready!"
            }
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
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Ristoratore",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "User ID: ${userId ?: "Unknown"}",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = onViewMenu) {
                Text("View & Edit Menu")
            }

            Button(onClick = onLogout) {
                Text("Logout")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (!modelReady || !tesseractReady) {
            Text(
                text = "⏳ Loading...",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (!tesseractReady) {
                Text(
                    text = "Initializing Tesseract OCR...",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!modelReady) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        } else if (statusMessage == "AI Model ready!") {
            Text(
                text = "✅ AI Model Ready",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(onClick = { imageLauncher.launch("image/*") }) {
            Text("Load Menu Image")
        }

        Spacer(modifier = Modifier.height(16.dp))

        imageUri?.let {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = useColumnSplit,
                    onCheckedChange = {
                        if (!useColumnSplit) {
                            showManualSelector = true
                        } else {
                            useColumnSplit = false
                            manualColumnRegions = null
                            statusMessage = ""
                        }
                    }
                )
                Text("Split into columns")
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (useColumnSplit && manualColumnRegions != null) {
                Text(
                    text = "✓ ${manualColumnRegions!!.size} columns configured",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Button(
                    onClick = { showManualSelector = true },
                    enabled = !isProcessing
                ) {
                    Text("Adjust Columns")
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        isProcessing = true

                        if (useColumnSplit && manualColumnRegions != null) {
                            statusMessage = "Processing ${manualColumnRegions!!.size} columns..."

                            processImageWithManualColumns(
                                context = context,
                                bitmap = loadedBitmap!!,
                                tesseractManager = tesseractManager,
                                columnRegions = manualColumnRegions!!
                            ) { text ->
                                recognizedText = text
                                statusMessage = "Text recognized successfully!"
                                isProcessing = false
                            }
                        } else {
                            statusMessage = "Recognizing text..."
                            processImageWithTesseract(
                                context = context,
                                uri = it,
                                tesseractManager = tesseractManager,
                                useColumnSplit = false
                            ) { text ->
                                recognizedText = text
                                statusMessage = "Text recognized successfully!"
                                isProcessing = false
                            }
                        }
                    }
                },
                enabled = !isProcessing && tesseractReady
            ) {
                Text(if (isProcessing) "Processing..." else "Recognize Text")
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (recognizedText.isNotEmpty()) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        isProcessing = true
                        statusMessage = "Processing with LLM..."

                        val apiKey = "SIEMENS_API_KEY_REMOVED"
                        val llmClient = LLMApiClient(apiKey)
                        val response = llmClient.processMenuText(recognizedText)

                        saveResponseToFile(context, response, useJson = false)

                        isProcessing = false
                        statusMessage = "Menu saved as TXT!"
                    }
                },
                enabled = !isProcessing
            ) {
                Text(if (isProcessing) "Processing..." else "Save Menu Response")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    coroutineScope.launch {
                        isProcessing = true
                        statusMessage = "Processing with LLM and uploading to Firebase..."

                        val apiKey = "SIEMENS_API_KEY_REMOVED"
                        val llmClient = LLMApiClient(apiKey)
                        val response = llmClient.processMenuText(recognizedText)

                        saveResponseToFile(context, response, useJson = false)

                        val parser = MenuParser()
                        val menuCategories = parser.parseMenuText(response)

                        if (menuCategories.isNotEmpty() && userId != null) {
                            val uploader = FirebaseMenuUploader(databaseReference)
                            val result = uploader.uploadMenuWithMetadata(
                                userId = userId,
                                restaurantName = "My Restaurant",
                                menuCategories = menuCategories
                            )

                            if (result.isSuccess) {
                                statusMessage = "✅ ${result.getOrNull()}"
                            } else {
                                statusMessage = "❌ Error: ${result.exceptionOrNull()?.message}"
                            }
                        } else {
                            statusMessage = "❌ No menu data to upload or missing user ID"
                        }

                        isProcessing = false
                    }
                },
                enabled = !isProcessing && userId != null
            ) {
                Text(if (isProcessing) "Processing..." else "Process & Upload to Firebase")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = { menuFileLauncher.launch("text/*") },
            enabled = !isProcessing && userId != null
        ) {
            Text("Upload Existing Menu File")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (statusMessage.isNotEmpty()) {
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = if (statusMessage.startsWith("✅")) {
                    MaterialTheme.colorScheme.primary
                } else if (statusMessage.startsWith("❌")) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (recognizedText.isNotEmpty()) {
            Text(
                text = "Recognized Text:",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = recognizedText.take(500) + if (recognizedText.length > 500) "..." else "",
                style = MaterialTheme.typography.bodySmall
            )
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