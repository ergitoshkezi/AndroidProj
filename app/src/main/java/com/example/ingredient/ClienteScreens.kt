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
            0 -> SearchTab(databaseReference, userLocation, onLogout, currentModifier)
            1 -> OffersTab(databaseReference, userLocation, currentModifier)
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
            filtered = filtered.sortedBy { it.dish.price.toDoubleOrNull() ?: 0.0 }
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
            Text(
                text = "Discover Food",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onLogout) {
                Text("Logout")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search ingredients...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { showFilters = !showFilters }) {
                Icon(
                    Icons.Default.FilterList, 
                    contentDescription = "Filters",
                    tint = if (showFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
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

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                performSearch(searchQuery, databaseReference, userLocation) { results ->
                    searchResults = results
                    isSearching = false
                }
                isSearching = true
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = searchQuery.isNotBlank() && !isSearching
        ) {
            if (isSearching) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Text("Search Dishes")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (filteredResults.isEmpty() && !isSearching && searchQuery.isNotBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No matching dishes found with current filters.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filteredResults) { result ->
                    DishResultItem(result)
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Filters", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text("Max Distance: ${maxDistance.toInt()} km", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = maxDistance,
                onValueChange = onMaxDistanceChange,
                valueRange = 1f..100f
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = selectedCountry,
                    onValueChange = onCountryChange,
                    label = { Text("Country") },
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = selectedRegion,
                    onValueChange = onRegionChange,
                    label = { Text("Region") },
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = sortByPriceAsc, onCheckedChange = onSortByPriceChange)
                Text("Sort by price (Low to High)", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun OffersTab(
    databaseReference: DatabaseReference,
    userLocation: Location?,
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
            text = "Best Offers Nearby",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (offerResults.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No active offers found nearby.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(offerResults) { result ->
                    DishResultItem(result)
                }
            }
        }
    }
}

@Composable
fun DishResultItem(result: SearchResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.dish.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = result.restaurantName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    result.distance?.let {
                        Text(
                            text = "${String.format("%.1f", it / 1000f)} km away",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    if (result.dish.isOffer) {
                        Text(
                            text = "€${result.dish.originalPrice}",
                            style = MaterialTheme.typography.bodySmall,
                            textDecoration = TextDecoration.LineThrough,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "€${result.dish.price}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD32F2F) // Offer red
                        )
                    } else {
                        Text(
                            text = "€${result.dish.price}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = result.dish.description,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (result.dish.country.isNotEmpty() || result.dish.region.isNotEmpty()) {
                    val locationText = listOfNotNull(
                        result.dish.country.takeIf { it.isNotBlank() },
                        result.dish.region.takeIf { it.isNotBlank() }
                    ).joinToString(", ")
                    
                    Text(
                        text = "Origin: $locationText",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }

                if (result.dish.isOffer) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFFEBEE))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "OFFER", 
                            color = Color(0xFFD32F2F), 
                            fontSize = 10.sp, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
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

    databaseReference.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val allResults = mutableListOf<SearchResult>()

            for (userSnapshot in snapshot.children) {
                val userType = userSnapshot.child("userType").getValue(String::class.java)
                if (userType == "Ristoratore") {
                    val restaurantId = userSnapshot.key ?: ""
                    val restaurantName = userSnapshot.child("menuMetadata/restaurantName").getValue(String::class.java) ?: "Unknown Restaurant"
                    
                    // Simulate/Read location
                    val lat = userSnapshot.child("lat").getValue(Double::class.java)
                    val lon = userSnapshot.child("lon").getValue(Double::class.java)
                    
                    var distance: Float? = null
                    if (userLocation != null && lat != null && lon != null) {
                        val restLoc = Location("").apply {
                            latitude = lat
                            longitude = lon
                        }
                        distance = userLocation.distanceTo(restLoc)
                    }

                    val menuSnapshot = userSnapshot.child("menu")
                    for (categorySnapshot in menuSnapshot.children) {
                        val dishesSnapshot = categorySnapshot.child("dishes")
                        for (dishSnapshot in dishesSnapshot.children) {
                            val dish = parseDish(dishSnapshot)
                            val fullText = (dish.name + " " + dish.description).lowercase()

                            val matched = mutableListOf<String>()
                            var score = 0
                            for (ingredient in searchIngredients) {
                                if (fullText.contains(ingredient)) {
                                    score++
                                    matched.add(ingredient)
                                }
                            }

                            if (score > 0) {
                                allResults.add(
                                    SearchResult(
                                        restaurantId = restaurantId,
                                        restaurantName = restaurantName,
                                        dish = dish,
                                        matchScore = score,
                                        matchedIngredients = matched,
                                        distance = distance,
                                        restaurantLat = lat,
                                        restaurantLon = lon
                                    )
                                )
                            }
                        }
                    }
                }
            }
            onResult(allResults)
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
    databaseReference.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val offers = mutableListOf<SearchResult>()

            for (userSnapshot in snapshot.children) {
                val userType = userSnapshot.child("userType").getValue(String::class.java)
                if (userType == "Ristoratore") {
                    val restaurantId = userSnapshot.key ?: ""
                    val restaurantName = userSnapshot.child("menuMetadata/restaurantName").getValue(String::class.java) ?: "Unknown Restaurant"
                    
                    val lat = userSnapshot.child("lat").getValue(Double::class.java)
                    val lon = userSnapshot.child("lon").getValue(Double::class.java)
                    
                    var distance: Float? = null
                    if (userLocation != null && lat != null && lon != null) {
                        val restLoc = Location("").apply {
                            latitude = lat
                            longitude = lon
                        }
                        distance = userLocation.distanceTo(restLoc)
                    }

                    val menuSnapshot = userSnapshot.child("menu")
                    for (categorySnapshot in menuSnapshot.children) {
                        val dishesSnapshot = categorySnapshot.child("dishes")
                        for (dishSnapshot in dishesSnapshot.children) {
                            val dish = parseDish(dishSnapshot)
                            
                            if (dish.isOffer) {
                                offers.add(
                                    SearchResult(
                                        restaurantId = restaurantId,
                                        restaurantName = restaurantName,
                                        dish = dish,
                                        matchScore = 0,
                                        matchedIngredients = emptyList(),
                                        distance = distance,
                                        restaurantLat = lat,
                                        restaurantLon = lon
                                    )
                                )
                            }
                        }
                    }
                }
            }
            // Sort by distance if available, otherwise just return
            onResult(offers.sortedBy { it.distance ?: Float.MAX_VALUE })
        }

        override fun onCancelled(error: DatabaseError) {
            onResult(emptyList())
        }
    })
}

private fun parseDish(snapshot: DataSnapshot): MenuItem {
    return MenuItem(
        name = snapshot.child("name").getValue(String::class.java) ?: "",
        description = snapshot.child("description").getValue(String::class.java) ?: "",
        allergens = snapshot.child("allergens").getValue(String::class.java) ?: "",
        price = snapshot.child("price").getValue(String::class.java) ?: "0.00",
        originalPrice = snapshot.child("originalPrice").getValue(String::class.java) ?: "",
        isOffer = snapshot.child("isOffer").getValue(Boolean::class.java) ?: false,
        country = snapshot.child("country").getValue(String::class.java) ?: "",
        region = snapshot.child("region").getValue(String::class.java) ?: ""
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
