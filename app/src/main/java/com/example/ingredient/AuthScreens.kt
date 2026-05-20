package com.example.ingredient

import android.location.Geocoder
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ingredient.model.AllergeneType
import com.example.ingredient.model.SessionManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

@Composable
fun DisclaimerScreen(
    onAccepted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activity = LocalContext.current as? android.app.Activity
    BackHandler { activity?.finish() }
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Important Notice", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Text("This app is a support tool only. Always verify ingredients and allergen information directly with the restaurant before ordering. We do not guarantee the accuracy of the data shown in this app.")
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onAccepted, modifier = Modifier.fillMaxWidth()) {
            Text("I understand")
        }
    }
}

@Composable
fun LoginScreen(
    databaseReference: DatabaseReference,
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: (String, String) -> Unit, // userId, userType
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val context = LocalContext.current
    val sessionManager = SessionManager(context)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Login",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )

        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    errorMessage = "Please fill in all fields"
                    return@Button
                }

                isLoading = true
                errorMessage = ""

                // Search through all users to find matching email
                databaseReference.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        isLoading = false
                        var found = false

                        for (userSnapshot in snapshot.children) {
                            val dbEmail = userSnapshot.child("email").getValue(String::class.java)
                            val dbPassword = userSnapshot.child("password").getValue(String::class.java)
                            val userType = userSnapshot.child("userType").getValue(String::class.java) ?: "Cliente"
                            val userId = userSnapshot.key

                            if (dbEmail == email && dbPassword == password) {
                                found = true
                                Log.d("LoginScreen", "Login successful for user: $userId")
                                Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()
                                sessionManager.saveSession(userId ?: "", userType)
                                onLoginSuccess(userId ?: "", userType)
                                break
                            }
                        }

                        if (!found) {
                            errorMessage = "Invalid email or password"
                            Log.e("LoginScreen", "Login failed - no matching credentials")
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        isLoading = false
                        errorMessage = if (error.code == DatabaseError.NETWORK_ERROR || error.code == DatabaseError.DISCONNECTED) {
                            "Connection error. Check your internet."
                        } else {
                            "Database error: ${error.message}"
                        }
                        Log.e("LoginScreen", "Database error: ${error.message}")
                    }
                })
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoading) "Logging in..." else "Login")
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = onNavigateToRegister,
            enabled = !isLoading
        ) {
            Text("Don't have an account? Register")
        }
    }
}

/** Resolves an address string to (lat, lon, formattedAddress). Returns null if not found. */
private suspend fun geocodeAddress(context: android.content.Context, address: String): Triple<Double, Double, String>? {
    return try {
        val geocoder = Geocoder(context, Locale.getDefault())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocationName(address, 1) { results ->
                    if (results.isNotEmpty()) {
                        val addr = results[0]
                        cont.resume(Triple(addr.latitude, addr.longitude, addr.getAddressLine(0) ?: address))
                    } else {
                        cont.resume(null)
                    }
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocationName(address, 1)
                if (!results.isNullOrEmpty()) {
                    val addr = results[0]
                    Triple(addr.latitude, addr.longitude, addr.getAddressLine(0) ?: address)
                } else null
            }
        }
    } catch (e: Exception) {
        Log.e("Geocoder", "geocodeAddress failed", e)
        null
    }
}

@Composable
fun RegistrationScreen(
    databaseReference: DatabaseReference,
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: (String, String) -> Unit, // userId, userType
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var nome by remember { mutableStateOf("") }
    var cognome by remember { mutableStateOf("") }
    var selectedAllergens by remember { mutableStateOf<List<AllergeneType>>(emptyList()) }
    var selectedUserType by remember { mutableStateOf("Cliente") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val context = LocalContext.current
    val sessionManager = SessionManager(context)
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Restaurant-specific fields (only used when selectedUserType == "Ristoratore")
    var nomeRistorante by remember { mutableStateOf("") }
    var indirizzoInput by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var tipoCucina by remember { mutableStateOf("") }
    var resolvedAddress by remember { mutableStateOf("") }
    var resolvedLat by remember { mutableStateOf(0.0) }
    var resolvedLon by remember { mutableStateOf(0.0) }
    var isResolvingAddress by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Register",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = nome,
            onValueChange = { nome = it },
            label = { Text("Nome") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = cognome,
            onValueChange = { cognome = it },
            label = { Text("Cognome") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Allergens (optional):", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        AllergeneChipSelector(
            selected = selectedAllergens,
            onSelectionChange = { selectedAllergens = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // User Type Selection
        Text(
            text = "I am a:",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FilterChip(
                selected = selectedUserType == "Cliente",
                onClick = { selectedUserType = "Cliente" },
                label = { Text("Cliente") },
                enabled = !isLoading
            )
            FilterChip(
                selected = selectedUserType == "Ristoratore",
                onClick = { selectedUserType = "Ristoratore" },
                label = { Text("Ristoratore") },
                enabled = !isLoading
            )
        }

        // Restaurant fields — only for Ristoratore
        if (selectedUserType == "Ristoratore") {
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Dati del Ristorante", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = nomeRistorante,
                onValueChange = { nomeRistorante = it },
                label = { Text("Nome Ristorante") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = indirizzoInput,
                    onValueChange = {
                        indirizzoInput = it
                        resolvedAddress = ""
                        resolvedLat = 0.0
                        resolvedLon = 0.0
                    },
                    label = { Text("Indirizzo") },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading && !isResolvingAddress,
                    singleLine = true
                )
                Button(
                    onClick = {
                        if (indirizzoInput.isBlank()) return@Button
                        coroutineScope.launch {
                            isResolvingAddress = true
                            errorMessage = ""
                            val result = geocodeAddress(context, indirizzoInput)
                            isResolvingAddress = false
                            if (result != null) {
                                resolvedLat = result.first
                                resolvedLon = result.second
                                resolvedAddress = result.third
                            } else {
                                errorMessage = "Indirizzo non trovato. Riprova."
                            }
                        }
                    },
                    enabled = !isLoading && !isResolvingAddress && indirizzoInput.isNotBlank(),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    if (isResolvingAddress) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Cerca")
                    }
                }
            }

            if (resolvedAddress.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "✓ $resolvedAddress",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = telefono,
                onValueChange = { telefono = it },
                label = { Text("Telefono") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = tipoCucina,
                onValueChange = { tipoCucina = it },
                label = { Text("Tipo di Cucina (es. Italiana, Giapponese)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )
        }

        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (nome.isBlank() || cognome.isBlank() || email.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
                    errorMessage = "Please fill in all fields"
                    return@Button
                }

                if (password != confirmPassword) {
                    errorMessage = "Passwords do not match"
                    return@Button
                }

                if (password.length < 6) {
                    errorMessage = "Password must be at least 6 characters"
                    return@Button
                }

                if (selectedUserType == "Ristoratore") {
                    if (nomeRistorante.isBlank() || indirizzoInput.isBlank() || telefono.isBlank()) {
                        errorMessage = "Compila tutti i dati del ristorante"
                        return@Button
                    }
                    if (resolvedLat == 0.0 && resolvedLon == 0.0) {
                        errorMessage = "Risolvi l'indirizzo prima di procedere"
                        return@Button
                    }
                }

                isLoading = true
                errorMessage = ""

                // Check if email already exists
                databaseReference.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        var emailExists = false

                        for (userSnapshot in snapshot.children) {
                            val dbEmail = userSnapshot.child("email").getValue(String::class.java)
                            if (dbEmail == email) {
                                emailExists = true
                                break
                            }
                        }

                        if (emailExists) {
                            isLoading = false
                            errorMessage = "Email already in use"
                            return
                        }

                        val userId = databaseReference.child("users").push().key

                        if (userId != null) {
                            // DECISION: password stored as plain text in RTDB (MVP).
                            // TODO: migrate to Firebase Authentication or hash (BCrypt) before production.
                            val userData = mapOf(
                                "nome" to nome,
                                "cognome" to cognome,
                                "email" to email,
                                "password" to password,
                                "userType" to selectedUserType,
                                "allergeni" to selectedAllergens.map { it.name },
                                "createdAt" to System.currentTimeMillis()
                            )

                            val restaurantData = if (selectedUserType == "Ristoratore") mapOf(
                                "nomeRistorante" to nomeRistorante,
                                "indirizzo" to resolvedAddress.ifEmpty { indirizzoInput },
                                "telefono" to telefono,
                                "tipoCucina" to tipoCucina,
                                "lat" to resolvedLat,
                                "lon" to resolvedLon,
                                "createdAt" to System.currentTimeMillis()
                            ) else null

                            // Write user first, then restaurant (separate paths = no root permission needed)
                            databaseReference.child("users").child(userId).setValue(userData)
                                .addOnSuccessListener {
                                    if (restaurantData != null) {
                                        databaseReference.child("restaurants").child(userId)
                                            .setValue(restaurantData)
                                            .addOnSuccessListener {
                                                isLoading = false
                                                Log.d("RegistrationScreen", "User + restaurant registered: $userId")
                                                Toast.makeText(context, "Registration successful!", Toast.LENGTH_SHORT).show()
                                                sessionManager.saveSession(userId, selectedUserType)
                                                onRegisterSuccess(userId, selectedUserType)
                                            }
                                            .addOnFailureListener { e ->
                                                isLoading = false
                                                errorMessage = "Registration failed: ${e.message}"
                                                Log.e("RegistrationScreen", "Restaurant write error", e)
                                            }
                                    } else {
                                        isLoading = false
                                        Log.d("RegistrationScreen", "User registered: $userId")
                                        Toast.makeText(context, "Registration successful!", Toast.LENGTH_SHORT).show()
                                        sessionManager.saveSession(userId, selectedUserType)
                                        onRegisterSuccess(userId, selectedUserType)
                                    }
                                }
                                .addOnFailureListener { e ->
                                    isLoading = false
                                    errorMessage = "Registration failed: ${e.message}"
                                    Log.e("RegistrationScreen", "Registration error", e)
                                }
                        } else {
                            isLoading = false
                            errorMessage = "Failed to generate user ID"
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        isLoading = false
                        errorMessage = if (error.code == DatabaseError.NETWORK_ERROR || error.code == DatabaseError.DISCONNECTED) {
                            "Connection error. Check your internet."
                        } else {
                            "Database error: ${error.message}"
                        }
                        Log.e("RegistrationScreen", "Database error: ${error.message}")
                    }
                })
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoading) "Registering..." else "Register")
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = onNavigateToLogin,
            enabled = !isLoading
        ) {
            Text("Already have an account? Login")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllergeneChipSelector(
    selected: List<AllergeneType>,
    onSelectionChange: (List<AllergeneType>) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AllergeneType.entries.forEach { allergen ->
            FilterChip(
                selected = allergen in selected,
                onClick = {
                    onSelectionChange(
                        if (allergen in selected) selected - allergen else selected + allergen
                    )
                },
                label = { Text(allergen.displayName) }
            )
        }
    }
}