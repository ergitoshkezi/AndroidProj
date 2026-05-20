package com.example.ingredient

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.database.DatabaseReference
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

private val GROQ_API_KEY get() = BuildConfig.GROQ_API_KEY

@Composable
fun QrMenuImportScreen(
    userId: String,
    databaseReference: DatabaseReference,
    onBack: () -> Unit,
    onMenuImported: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scannedUrl by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var parsedMenu by remember { mutableStateOf<List<MenuCategory>>(emptyList()) }
    var appendMode by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    fun processUrl(url: String) {
        parsedMenu = emptyList()
        coroutineScope.launch {
            isProcessing = true
            try {
                val content: String
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    statusMessage = "QR con testo diretto, analisi AI…"
                    content = url
                } else if (PdfMenuExtractor.isPdfUrl(url)) {
                    statusMessage = "PDF rilevato, download in corso…"
                    val pdfText = PdfMenuExtractor.extractText(
                        context = context,
                        url = url,
                        onProgress = { statusMessage = it }
                    )
                    if (pdfText.isBlank()) {
                        statusMessage = "❌ Impossibile leggere il PDF."
                        isProcessing = false
                        return@launch
                    }
                    content = pdfText
                } else {
                    statusMessage = "Caricamento pagina con JavaScript…"
                    val extracted = WebViewMenuExtractor.extract(
                        context = context,
                        url = url,
                        onProgress = { statusMessage = it }
                    )
                    content = extracted.bestContent().let { raw ->
                        val preprocessed = MenuContentPreprocessor.preprocess(raw)
                        if (preprocessed.isNotBlank()) preprocessed else extracted.visibleText.take(20000)
                    }
                    if (content.isBlank()) {
                        statusMessage = "❌ Nessun contenuto trovato nella pagina."
                        isProcessing = false
                        return@launch
                    }
                }

                val estChunks = (content.length / 4000) + 1
                statusMessage = if (estChunks > 1) "Analisi AI (0/$estChunks sezioni)…" else "Analisi AI in corso…"
                val response = LLMApiClient(GROQ_API_KEY).processMenuText(
                    menuText = content,
                    onProgress = { cur, tot -> statusMessage = "Analisi AI: sezione $cur/$tot…" }
                )

                val menuCategories = MenuParser().parseMenuText(response)
                if (menuCategories.isNotEmpty()) {
                    val count = menuCategories.sumOf { it.dishes.size }
                    menuCategories.forEach { cat ->
                        android.util.Log.d("QrMenuImport", "Category '${cat.categoryName}' → ${cat.dishes.size} dishes")
                        cat.dishes.take(3).forEach { d ->
                            android.util.Log.d("QrMenuImport", "  dish: '${d.name}' | desc: '${d.description.take(40)}'")
                        }
                    }

                    // Enrichment pass: fill ingredients, calories, country, region for any dish missing them
                    statusMessage = "Arricchimento ingredienti e info…"
                    val enriched = LLMApiClient(GROQ_API_KEY).enrichDishes(
                        categories = menuCategories,
                        onProgress = { msg -> statusMessage = msg }
                    )

                    statusMessage = "✅ Trovati $count piatti in ${enriched.size} categorie. Verifica e conferma."
                    parsedMenu = enriched
                } else {
                    statusMessage = "❌ Nessun piatto trovato. Il sito potrebbe non contenere un menu leggibile."
                }
            } catch (e: Exception) {
                statusMessage = "❌ Errore: ${e.message}"
            }
            isProcessing = false
        }
    }

    fun uploadMenu() {
        coroutineScope.launch {
            isProcessing = true
            statusMessage = "Salvataggio su Firebase…"
            try {
                val result = FirebaseMenuUploader(databaseReference).uploadMenuWithMetadata(
                    userId = userId,
                    restaurantName = "My Restaurant",
                    menuCategories = parsedMenu,
                    append = appendMode
                )
                if (result.isSuccess) {
                    val count = parsedMenu.sumOf { it.dishes.size }
                    val modeLabel = if (appendMode) "aggiunto al menu" else "importato"
                    statusMessage = "✅ Menu $modeLabel! $count piatti, ${parsedMenu.size} categorie."
                    parsedMenu = emptyList()
                    onMenuImported()
                } else {
                    statusMessage = "❌ Errore upload: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                statusMessage = "❌ Errore: ${e.message}"
            }
            isProcessing = false
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val url = result.contents
        if (url != null) {
            scannedUrl = url
            statusMessage = "QR scansionato"
            processUrl(url)
        } else {
            statusMessage = "Scansione annullata"
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 3.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back")
                }
                Text(
                    text = "Importa Menu da QR",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (isProcessing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }

            if (statusMessage.isNotEmpty()) {
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

            // Scan card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("1", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Scansiona QR del Menu",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (scannedUrl.isNotEmpty()) "✓ URL acquisito" else "Punta la fotocamera al QR code del ristorante",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (scannedUrl.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val options = ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setPrompt("Scansiona il QR code del menu")
                                setBeepEnabled(true)
                                setOrientationLocked(false)
                            }
                            scanLauncher.launch(options)
                        },
                        enabled = !isProcessing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (scannedUrl.isNotEmpty()) "Scansiona di Nuovo" else "Scansiona QR")
                    }

                    if (scannedUrl.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = scannedUrl,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Re-process button
            if (scannedUrl.isNotEmpty() && parsedMenu.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("2", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Analisi AI",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Rendering JS → estrazione testo → LLM",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { processUrl(scannedUrl) },
                            enabled = !isProcessing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Analizza Menu")
                        }
                    }
                }
            }

            // Preview del menu estratto
            if (parsedMenu.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    "📋 Anteprima Menu — ${parsedMenu.sumOf { it.dishes.size }} piatti totali",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))

                parsedMenu.forEach { category ->
                    // Categoria
                    Text(
                        text = "━━ ${category.categoryName.uppercase()} ━━",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )

                    if (category.dishes.isEmpty()) {
                        Text(
                            "(nessun piatto)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                        )
                    } else {
                        category.dishes.forEach { dish ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp, bottom = 10.dp)
                            ) {
                                // Nome + Prezzo
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = dish.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "€ %.2f".format(dish.price),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                // Ingredienti
                                Text(
                                    text = if (dish.description.isNotEmpty()) dish.description else "—",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                // Allergeni
                                if (dish.allergens.isNotEmpty()) {
                                    Text(
                                        text = "⚠️ ${dish.allergens.joinToString(", ")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                // Calorie + Origine
                                val extra = buildList {
                                    if (dish.calories > 0) add("🔥 ${dish.calories} kcal")
                                    if (dish.country.isNotEmpty()) add("📍 ${dish.country}")
                                    if (dish.region.isNotEmpty()) add(dish.region)
                                }.joinToString("  ")
                                if (extra.isNotEmpty()) {
                                    Text(
                                        text = extra,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }

                // Import mode toggle
                Spacer(Modifier.height(8.dp))
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

                // Confirm button
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { uploadMenu() },
                    enabled = !isProcessing,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (appendMode)
                            "Aggiungi ${parsedMenu.sumOf { it.dishes.size }} Piatti al Menu"
                        else
                            "Conferma e Importa ${parsedMenu.sumOf { it.dishes.size }} Piatti",
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { processUrl(scannedUrl) },
                    enabled = !isProcessing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Rianalizza")
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
