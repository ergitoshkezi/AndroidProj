package com.example.ingredient

import android.location.Location
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.ingredient.model.VetrinaPhoto
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VetrinaScreen(
    restaurantId: String,
    restaurantName: String,
    restaurantLat: Double,
    restaurantLon: Double,
    userId: String?,
    databaseReference: DatabaseReference,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var photos by remember { mutableStateOf<List<VetrinaPhoto>>(emptyList()) }
    var isLoadingPhotos by remember { mutableStateOf(true) }
    var isNearby by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }

    // Temp URI for camera capture
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    // Load photos from /vetrina/{restaurantId}
    LaunchedEffect(restaurantId) {
        databaseReference.child("vetrina").child(restaurantId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = snapshot.children.mapNotNull { child ->
                        val url = child.child("url").getValue(String::class.java) ?: return@mapNotNull null
                        VetrinaPhoto(
                            photoId = child.key ?: "",
                            url = url,
                            uploadedBy = child.child("uploadedBy").getValue(String::class.java) ?: "",
                            restaurantId = restaurantId,
                            timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                        )
                    }.sortedByDescending { it.timestamp }
                    photos = list
                    isLoadingPhotos = false
                }
                override fun onCancelled(error: DatabaseError) {
                    isLoadingPhotos = false
                    Log.e("VetrinaScreen", "Failed to load photos: ${error.message}")
                }
            })
    }

    // Check GPS proximity
    LaunchedEffect(Unit) {
        if (restaurantLat == 0.0 && restaurantLon == 0.0) return@LaunchedEffect
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            fusedClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val restaurantLocation = Location("").apply {
                        latitude = restaurantLat
                        longitude = restaurantLon
                    }
                    isNearby = location.distanceTo(restaurantLocation) < 500f
                }
            }
        } catch (e: SecurityException) {
            Log.w("VetrinaScreen", "Location permission not granted")
        }
    }

    fun uploadPhoto(uri: Uri) {
        if (userId == null) return
        coroutineScope.launch {
            isUploading = true
            try {
                val photoId = System.currentTimeMillis().toString()
                val storageRef = FirebaseStorage.getInstance().reference
                    .child("vetrina/$restaurantId/$photoId.jpg")
                storageRef.putFile(uri).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                databaseReference.child("vetrina").child(restaurantId).child(photoId)
                    .setValue(mapOf(
                        "url" to downloadUrl,
                        "uploadedBy" to userId,
                        "restaurantId" to restaurantId,
                        "timestamp" to System.currentTimeMillis()
                    )).await()

                snackbarHostState.showSnackbar("Foto aggiunta alla Vetrina!")
            } catch (e: Exception) {
                Log.e("VetrinaScreen", "Upload failed", e)
                snackbarHostState.showSnackbar("Errore nell'upload. Riprova.")
            } finally {
                isUploading = false
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { uploadPhoto(it) } }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) cameraImageUri?.let { uploadPhoto(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Vetrina", style = MaterialTheme.typography.titleMedium)
                        Text(
                            restaurantName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        },
        floatingActionButton = {
            if (isNearby && userId != null) {
                FloatingActionButton(
                    onClick = { showSourceDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.AddAPhoto, contentDescription = "Aggiungi foto")
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Proximity banner
            if (!isNearby) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        "📍 Avvicinati al ristorante (< 500m) per aggiungere foto",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Photos section
            if (isLoadingPhotos) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (photos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📸", fontSize = 40.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Nessuna foto ancora.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isNearby && userId != null) {
                            Text(
                                "Sii il primo a condividere!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                Text(
                    "${photos.size} foto dalla community",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(photos, key = { it.photoId }) { photo ->
                        VetrinaPhotoCard(photo)
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp)) // FAB clearance
        }
    }

    // Source picker dialog
    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text("Aggiungi foto") },
            text = { Text("Come vuoi aggiungere la foto?") },
            confirmButton = {
                TextButton(onClick = {
                    showSourceDialog = false
                    galleryLauncher.launch("image/*")
                }) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Galleria")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSourceDialog = false
                    val tempFile = File.createTempFile("vetrina_", ".jpg", context.cacheDir)
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        tempFile
                    )
                    cameraImageUri = uri
                    cameraLauncher.launch(uri)
                }) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Fotocamera")
                }
            }
        )
    }
}

@Composable
private fun VetrinaPhotoCard(photo: VetrinaPhoto) {
    Card(
        modifier = Modifier
            .width(180.dp)
            .height(240.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = photo.url,
                contentDescription = "Foto vetrina",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Timestamp overlay at bottom
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = formatVetrinaTimestamp(photo.timestamp),
                    color = Color.White,
                    fontSize = 11.sp
                )
            }
        }
    }
}

private fun formatVetrinaTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "Adesso"
        diff < 3_600_000 -> "${diff / 60_000} min fa"
        diff < 86_400_000 -> "${diff / 3_600_000} ore fa"
        else -> "${diff / 86_400_000} giorni fa"
    }
}
