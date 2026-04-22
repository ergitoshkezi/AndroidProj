package com.example.ingredient

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

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
                        errorMessage = "Database error: ${error.message}"
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
    var selectedUserType by remember { mutableStateOf("Cliente") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Register",
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
                if (email.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
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
                            errorMessage = "Email already registered"
                            return
                        }

                        // Create new user
                        val userId = databaseReference.child("users").push().key

                        if (userId != null) {
                            val userData = mapOf(
                                "email" to email,
                                "password" to password,
                                "userType" to selectedUserType,
                                "createdAt" to System.currentTimeMillis()
                            )

                            databaseReference.child("users").child(userId).setValue(userData)
                                .addOnSuccessListener {
                                    isLoading = false
                                    Log.d("RegistrationScreen", "User registered: $userId")
                                    Toast.makeText(context, "Registration successful!", Toast.LENGTH_SHORT).show()
                                    onRegisterSuccess(userId, selectedUserType)
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
                        errorMessage = "Database error: ${error.message}"
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