package com.example.ingredient

import android.annotation.SuppressLint
import android.location.Location
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.example.ingredient.model.AllergeneType
import com.example.ingredient.model.SessionManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

data class SearchResult(
    val restaurantId: String,
    val restaurantName: String,
    val dish: MenuItem,
    val matchScore: Int,
    val matchedIngredients: List<String>,
    val distance: Float?, // in meters
    val restaurantLat: Double?,
    val restaurantLon: Double?
)

@Composable
fun ClienteScreen(
    userId: String?,
    databaseReference: DatabaseReference,
    onLogout: () -> Unit,
    onViewVetrina: (restaurantId: String, restaurantName: String, lat: Double?, lon: Double?) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    var userLocation by remember { mutableStateOf<Location?>(null) }
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    LaunchedEffect(Unit) {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                userLocation = location
            }
        } catch (e: SecurityException) {
            // Handle permission not granted
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    label = { Text("Search") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.LocalOffer, contentDescription = "Offers") },
                    label = { Text("Offers") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profilo") }
                )
            }
        }
    ) { innerPadding ->
        val currentModifier = modifier.padding(innerPadding)
        
        when (selectedTab) {
            0 -> SearchTab(databaseReference, userLocation, onLogout, onViewVetrina, currentModifier)
            1 -> OffersTab(databaseReference, userLocation, onViewVetrina, currentModifier)
            2 -> ProfileScreen(
                userId = userId ?: "",
                databaseReference = databaseReference,
                onLogout = onLogout,
                modifier = currentModifier
            )
        }
    }
}

@Composable
fun SearchTab(
    databaseReference: DatabaseReference,
    userLocation: Location?,
    onLogout: () -> Unit,
    onViewVetrina: (restaurantId: String, restaurantName: String, lat: Double?, lon: Double?) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var filteredResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }

    // Filter states
    var maxDistance by remember { mutableStateOf(50f) } // km
    var selectedCountry by remember { mutableStateOf("") }
    var selectedRegion by remember { mutableStateOf("") }
    var sortByPriceAsc by remember { mutableStateOf(false) }

    // Apply filters locally
    LaunchedEffect(searchResults, maxDistance, selectedCountry, selectedRegion, sortByPriceAsc) {
        var filtered = searchResults.filter { result ->
            val distanceMatch = if (userLocation != null && result.distance != null) {
                result.distance / 1000f <= maxDistance
            } else true
            
            val countryMatch = if (selectedCountry.isNotBlank()) {
                result.dish.country.equals(selectedCountry, ignoreCase = true)
            } else true
            
            val regionMatch = if (selectedRegion.isNotBlank()) {
                result.dish.region.equals(selectedRegion, ignoreCase = true)
            } else true
            
            distanceMatch && countryMatch && regionMatch
        }

        if (sortByPriceAsc) {
            filtered = filtered.sortedBy { it.dish.price }
        } else {
            filtered = filtered.sortedByDescending { it.matchScore }
        }
        
        filteredResults = filtered
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "🍴 Discover Food",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Cerca piatti per ingredienti",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onLogout) {
                Text("Logout", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Es: mozzarella, tonno...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null,
                         tint = MaterialTheme.colorScheme.primary)
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (showFilters) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            ) {
                IconButton(onClick = { showFilters = !showFilters }) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = "Filtri",
                        tint = if (showFilters) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (showFilters) {
            FilterPanel(
                maxDistance = maxDistance,
                onMaxDistanceChange = { maxDistance = it },
                selectedCountry = selectedCountry,
                onCountryChange = { selectedCountry = it },
                selectedRegion = selectedRegion,
                onRegionChange = { selectedRegion = it },
                sortByPriceAsc = sortByPriceAsc,
                onSortByPriceChange = { sortByPriceAsc = it }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                performSearch(searchQuery, databaseReference, userLocation) { results ->
                    searchResults = results
                    isSearching = false
                }
                isSearching = true
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            enabled = searchQuery.isNotBlank() && !isSearching
        ) {
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cercando...")
            } else {
                Icon(Icons.Default.Search, contentDescription = null,
                     modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Cerca Piatti")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredResults.isNotEmpty()) {
            Text(
                text = "${filteredResults.size} piatti trovati",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (filteredResults.isEmpty() && !isSearching && searchQuery.isNotBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("😕", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Nessun piatto trovato con i filtri correnti.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredResults) { result ->
                    DishResultItem(result, onViewVetrina)
                }
            }
        }
    }
}

@Composable
fun FilterPanel(
    maxDistance: Float,
    onMaxDistanceChange: (Float) -> Unit,
    selectedCountry: String,
    onCountryChange: (String) -> Unit,
    selectedRegion: String,
    onRegionChange: (String) -> Unit,
    sortByPriceAsc: Boolean,
    onSortByPriceChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Filtri",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Distance slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Distanza max", style = MaterialTheme.typography.bodySmall)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                ) {
                    Text(
                        "${maxDistance.toInt()} km",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Slider(
                value = maxDistance,
                onValueChange = onMaxDistanceChange,
                valueRange = 1f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Country / Region fields
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = selectedCountry,
                    onValueChange = onCountryChange,
                    label = { Text("Paese", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )
                OutlinedTextField(
                    value = selectedRegion,
                    onValueChange = onRegionChange,
                    label = { Text("Regione", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sort by price toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.SortByAlpha,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ordina per prezzo (↑)", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = sortByPriceAsc,
                    onCheckedChange = onSortByPriceChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    }
}

@Composable
fun OffersTab(
    databaseReference: DatabaseReference,
    userLocation: Location?,
    onViewVetrina: (restaurantId: String, restaurantName: String, lat: Double?, lon: Double?) -> Unit,
    modifier: Modifier = Modifier
) {
    var offerResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(userLocation) {
        isLoading = true
        fetchOffers(databaseReference, userLocation) { results ->
            offerResults = results
            isLoading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "🏷 Offerte Vicino a Te",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "I piatti in promozione nelle vicinanze",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (offerResults.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎉", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Nessuna offerta attiva nelle vicinanze.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(offerResults) { result ->
                    DishResultItem(result, onViewVetrina)
                }
            }
        }
    }
}

@Composable
fun DishResultItem(
    result: SearchResult,
    onViewVetrina: (restaurantId: String, restaurantName: String, lat: Double?, lon: Double?) -> Unit = { _, _, _, _ -> }
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
        ) {
            // Accent left bar
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(
                        if (result.dish.isOffer) Color(0xFFE53935)
                        else MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    )
            )

            Column(modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .weight(1f)
            ) {
                // Top row: name + price
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = result.dish.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "🍽 ${result.restaurantName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {
                                onViewVetrina(
                                    result.restaurantId,
                                    result.restaurantName,
                                    result.restaurantLat,
                                    result.restaurantLon
                                )
                            }
                        )
                    }
                    // Price block
                    Column(horizontalAlignment = Alignment.End) {
                        if (result.dish.isOffer && result.dish.originalPrice != null) {
                            Text(
                                text = "€${"%.2f".format(result.dish.originalPrice)}",
                                style = MaterialTheme.typography.bodySmall,
                                textDecoration = TextDecoration.LineThrough,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (result.dish.isOffer) Color(0xFFFFEBEE)
                                    else MaterialTheme.colorScheme.primaryContainer
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "€${"%.2f".format(result.dish.price)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (result.dish.isOffer) Color(0xFFE53935)
                                        else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (result.dish.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = result.dish.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tag chips row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    result.distance?.let {
                        InfoChip("📍 ${"%.1f".format(it / 1000f)} km")
                    }
                    if (result.dish.calories > 0) {
                        InfoChip(
                            text = "🔥 ${result.dish.calories} kcal",
                            containerColor = Color(0xFFFFF3E0),
                            textColor = Color(0xFFE65100)
                        )
                    }
                    if (result.dish.isOffer) {
                        InfoChip(
                            text = "OFFERTA",
                            containerColor = Color(0xFFFFEBEE),
                            textColor = Color(0xFFE53935)
                        )
                    }
                    if (result.dish.country.isNotBlank()) {
                        InfoChip("🌍 ${result.dish.country}")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoChip(
    text: String,
    containerColor: Color = Color(0xFFF0F0F0),
    textColor: Color = Color(0xFF555555)
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, fontSize = 11.sp, color = textColor, fontWeight = FontWeight.Medium)
    }
}

private fun performSearch(
    query: String,
    databaseReference: DatabaseReference,
    userLocation: Location?,
    onResult: (List<SearchResult>) -> Unit
) {
    val searchIngredients = query.split(",")
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }

    // REQ-SEARCH-004: query /dishes directly — no O(n) /users scan
    databaseReference.child("dishes").addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val allResults = mutableListOf<SearchResult>()

            for (dishSnapshot in snapshot.children) {
                val dish = parseDish(dishSnapshot)
                val dishIngredients = dish.allergens.map { it.lowercase() }

                val matched = searchIngredients.filter { searchTerm ->
                    dishIngredients.any { it.contains(searchTerm) } ||
                    dish.name.lowercase().contains(searchTerm)
                }
                val score = matched.size

                if (score > 0) {
                    val restaurantLat = dishSnapshot.child("restaurantLat").getValue(Double::class.java)
                    val restaurantLon = dishSnapshot.child("restaurantLon").getValue(Double::class.java)
                    var distance: Float? = null
                    if (userLocation != null && restaurantLat != null && restaurantLon != null) {
                        val restLoc = Location("").apply {
                            latitude = restaurantLat
                            longitude = restaurantLon
                        }
                        distance = userLocation.distanceTo(restLoc)
                    }
                    allResults.add(
                        SearchResult(
                            restaurantId = dishSnapshot.child("restaurantId").getValue(String::class.java) ?: "",
                            restaurantName = dishSnapshot.child("restaurantName").getValue(String::class.java) ?: "Unknown Restaurant",
                            dish = dish,
                            matchScore = score,
                            matchedIngredients = matched,
                            distance = distance,
                            restaurantLat = restaurantLat,
                            restaurantLon = restaurantLon
                        )
                    )
                }
            }
            onResult(allResults.sortedByDescending { it.matchScore })
        }

        override fun onCancelled(error: DatabaseError) {
            onResult(emptyList())
        }
    })
}

private fun fetchOffers(
    databaseReference: DatabaseReference,
    userLocation: Location?,
    onResult: (List<SearchResult>) -> Unit
) {
    // REQ-SEARCH-004: query /dishes directly — no O(n) /users scan
    databaseReference.child("dishes").addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val offers = mutableListOf<SearchResult>()

            for (dishSnapshot in snapshot.children) {
                val dish = parseDish(dishSnapshot)
                if (dish.isOffer) {
                    val restaurantLat = dishSnapshot.child("restaurantLat").getValue(Double::class.java)
                    val restaurantLon = dishSnapshot.child("restaurantLon").getValue(Double::class.java)
                    var distance: Float? = null
                    if (userLocation != null && restaurantLat != null && restaurantLon != null) {
                        val restLoc = Location("").apply {
                            latitude = restaurantLat
                            longitude = restaurantLon
                        }
                        distance = userLocation.distanceTo(restLoc)
                    }
                    offers.add(
                        SearchResult(
                            restaurantId = dishSnapshot.child("restaurantId").getValue(String::class.java) ?: "",
                            restaurantName = dishSnapshot.child("restaurantName").getValue(String::class.java) ?: "Unknown Restaurant",
                            dish = dish,
                            matchScore = 0,
                            matchedIngredients = emptyList(),
                            distance = distance,
                            restaurantLat = restaurantLat,
                            restaurantLon = restaurantLon
                        )
                    )
                }
            }
            onResult(offers.sortedBy { it.distance ?: Float.MAX_VALUE })
        }

        override fun onCancelled(error: DatabaseError) {
            onResult(emptyList())
        }
    })
}

private fun parseDish(snapshot: DataSnapshot): MenuItem {
    // Reads from approved /dishes/{dishId} schema (Italian field names)
    val ingredients = snapshot.child("ingredienti").children
        .mapNotNull { it.getValue(String::class.java) }
    return MenuItem(
        name = snapshot.child("nome").getValue(String::class.java) ?: "",
        description = snapshot.child("descrizione").getValue(String::class.java) ?: "",
        allergens = ingredients,
        price = snapshot.child("prezzo").getValue(Double::class.java) ?: 0.0,
        originalPrice = snapshot.child("prezzoOfferta").getValue(Double::class.java),
        isOffer = snapshot.child("offerta").getValue(Boolean::class.java) ?: false,
        country = snapshot.child("paese").getValue(String::class.java) ?: "",
        region = snapshot.child("regione").getValue(String::class.java) ?: "",
        cucina = snapshot.child("cucina").getValue(String::class.java) ?: "",
        calories = (snapshot.child("calorie").getValue(Long::class.java) ?: 0L).toInt()
    )
}

@Composable
fun ProfileScreen(
    userId: String,
    databaseReference: DatabaseReference,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var nome by remember { mutableStateOf("") }
    var cognome by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var selectedAllergens by remember { mutableStateOf<List<AllergeneType>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val sessionManager = SessionManager(context)

    // Load user data once
    LaunchedEffect(userId) {
        databaseReference.child("users").child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    nome = snapshot.child("nome").getValue(String::class.java) ?: ""
                    cognome = snapshot.child("cognome").getValue(String::class.java) ?: ""
                    email = snapshot.child("email").getValue(String::class.java) ?: ""
                    val rawAllergens = snapshot.child("allergeni")
                        .children.mapNotNull { it.getValue(String::class.java) }
                    selectedAllergens = rawAllergens.mapNotNull {
                        runCatching { AllergeneType.valueOf(it) }.getOrNull()
                    }
                    isLoading = false
                }
                override fun onCancelled(error: DatabaseError) {
                    isLoading = false
                    Log.e("ProfileScreen", "Failed to load profile: ${error.message}")
                }
            })
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Profilo", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(24.dp))

                // Non-editable profile fields
                Text("Nome: $nome", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Cognome: $cognome", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Email: $email", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(24.dp))

                // Allergen chip selector (editable)
                Text("I miei allergeni:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                AllergeneChipSelector(
                    selected = selectedAllergens,
                    onSelectionChange = { selectedAllergens = it }
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Save allergens button
                Button(
                    onClick = {
                        databaseReference.child("users").child(userId)
                            .child("allergeni")
                            .setValue(selectedAllergens.map { it.name })
                            .addOnSuccessListener {
                                coroutineScope.launch { snackbarHostState.showSnackbar("Saved!") }
                            }
                            .addOnFailureListener { e ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Could not save. Try again.")
                                }
                                Log.e("ProfileScreen", "Failed to save allergens", e)
                            }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Salva allergeni")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Logout button
                OutlinedButton(
                    onClick = {
                        sessionManager.logout()
                        onLogout()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Logout")
                }
            }
        }
    }
}
