package com.example.ingredient

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.database.DatabaseReference
import kotlinx.coroutines.launch

@Composable
fun MenuEditorScreen(
    userId: String,
    databaseReference: DatabaseReference,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuCategories by remember { mutableStateOf<List<MenuCategory>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var editingItem by remember { mutableStateOf<EditingMenuItemState?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val uploader = remember { FirebaseMenuUploader(databaseReference) }

    // Load menu on first composition
    LaunchedEffect(userId) {
        isLoading = true
        val result = uploader.getMenu(userId)
        if (result.isSuccess) {
            menuCategories = result.getOrNull() ?: emptyList()
            errorMessage = null
        } else {
            errorMessage = "Failed to load menu: ${result.exceptionOrNull()?.message}"
        }
        isLoading = false
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Custom top bar to avoid experimental APIs
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back")
                }
                Text(
                    text = "Edit Menu",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBack) {
                            Text("Go Back")
                        }
                    }
                }
                menuCategories.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No menu found")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBack) {
                            Text("Go Back")
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        menuCategories.forEachIndexed { categoryIndex, category ->
                            MenuCategoryCard(
                                category = category,
                                onEditDish = { dishIndex, dish ->
                                    editingItem = EditingMenuItemState(
                                        categoryIndex = categoryIndex,
                                        dishIndex = dishIndex,
                                        item = dish
                                    )
                                },
                                onDeleteDish = { dishIndex ->
                                    coroutineScope.launch {
                                        val updatedCategories = menuCategories.toMutableList()
                                        val updatedDishes = category.dishes.toMutableList()
                                        updatedDishes.removeAt(dishIndex)

                                        if (updatedDishes.isEmpty()) {
                                            updatedCategories.removeAt(categoryIndex)
                                        } else {
                                            updatedCategories[categoryIndex] = category.copy(dishes = updatedDishes)
                                        }

                                        val result = uploader.uploadMenu(userId, updatedCategories)
                                        if (result.isSuccess) {
                                            menuCategories = updatedCategories
                                        }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }

    // Edit dialog
    editingItem?.let { editState ->
        EditDishDialog(
            dish = editState.item,
            onDismiss = { editingItem = null },
            onSave = { updatedDish ->
                coroutineScope.launch {
                    val updatedCategories = menuCategories.toMutableList()
                    val category = updatedCategories[editState.categoryIndex]
                    val updatedDishes = category.dishes.toMutableList()
                    updatedDishes[editState.dishIndex] = updatedDish
                    updatedCategories[editState.categoryIndex] = category.copy(dishes = updatedDishes)

                    val result = uploader.uploadMenu(userId, updatedCategories)
                    if (result.isSuccess) {
                        menuCategories = updatedCategories
                        editingItem = null
                    } else {
                        errorMessage = "Failed to save: ${result.exceptionOrNull()?.message}"
                    }
                }
            }
        )
    }
}

@Composable
fun MenuCategoryCard(
    category: MenuCategory,
    onEditDish: (Int, MenuItem) -> Unit,
    onDeleteDish: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = category.categoryName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            category.dishes.forEachIndexed { index, dish ->
                MenuItemCard(
                    dish = dish,
                    onEdit = { onEditDish(index, dish) },
                    onDelete = { onDeleteDish(index) }
                )

                if (index < category.dishes.size - 1) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
fun MenuItemCard(
    dish: MenuItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dish.name,
                    style = MaterialTheme.typography.titleMedium
                )

                if (dish.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = dish.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (dish.allergens.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Allergens: ${dish.allergens.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (dish.isOffer) {
                        Text(
                            text = "€${"%.2f".format(dish.originalPrice ?: 0.0)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "€${"%.2f".format(dish.price)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge(containerColor = MaterialTheme.colorScheme.errorContainer) {
                            Text("OFFER", color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    } else {
                        Text(
                            text = "€${"%.2f".format(dish.price)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (dish.country.isNotEmpty() || dish.region.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = listOfNotNull(dish.country.takeIf { it.isNotEmpty() }, dish.region.takeIf { it.isNotEmpty() }).joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                if (dish.calories > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "🔥 ${dish.calories} kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Edit")
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Default.Delete,
                        "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Dish") },
            text = { Text("Are you sure you want to delete \"${dish.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun EditDishDialog(
    dish: MenuItem,
    onDismiss: () -> Unit,
    onSave: (MenuItem) -> Unit
) {
    var name by remember { mutableStateOf(dish.name) }
    var description by remember { mutableStateOf(dish.description) }
    var allergens by remember { mutableStateOf(dish.allergens.joinToString(", ")) }
    var price by remember { mutableStateOf("%.2f".format(dish.price)) }
    var originalPrice by remember { mutableStateOf(dish.originalPrice?.let { "%.2f".format(it) } ?: "") }
    var isOffer by remember { mutableStateOf(dish.isOffer) }
    var country by remember { mutableStateOf(dish.country) }
    var region by remember { mutableStateOf(dish.region) }
    var calories by remember { mutableStateOf(if (dish.calories > 0) dish.calories.toString() else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Dish") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = allergens,
                    onValueChange = { allergens = it },
                    label = { Text("Allergens") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = country,
                    onValueChange = { country = it },
                    label = { Text("Country") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = region,
                    onValueChange = { region = it },
                    label = { Text("Region") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = calories,
                    onValueChange = {
                        if (it.isEmpty() || it.matches(Regex("^\\d{0,5}$"))) {
                            calories = it
                        }
                    },
                    label = { Text("Calories (kcal)") },
                    modifier = Modifier.fillMaxWidth(),
                    suffix = { Text("kcal") }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isOffer,
                        onCheckedChange = {
                            isOffer = it
                            if (it && originalPrice.isEmpty()) {
                                originalPrice = price
                            }
                        }
                    )
                    Text("Is Offer?")
                }

                if (isOffer) {
                    OutlinedTextField(
                        value = originalPrice,
                        onValueChange = {
                            if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                originalPrice = it
                            }
                        },
                        label = { Text("Original Price (€)") },
                        modifier = Modifier.fillMaxWidth(),
                        prefix = { Text("€") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = price,
                    onValueChange = {
                        // Allow only numbers and decimal point
                        if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            price = it
                        }
                    },
                    label = { Text(if (isOffer) "Offer Price (€)" else "Price (€)") },
                    modifier = Modifier.fillMaxWidth(),
                    prefix = { Text("€") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updatedDish = MenuItem(
                        name = name.trim(),
                        description = description.trim(),
                        allergens = allergens.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        price = price.toDoubleOrNull() ?: 0.0,
                        originalPrice = if (isOffer && originalPrice.isNotEmpty()) originalPrice.toDoubleOrNull() else null,
                        isOffer = isOffer,
                        country = country.trim(),
                        region = region.trim(),
                        calories = calories.toIntOrNull() ?: 0
                    )
                    onSave(updatedDish)
                },
                enabled = name.isNotEmpty()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

data class EditingMenuItemState(
    val categoryIndex: Int,
    val dishIndex: Int,
    val item: MenuItem
)