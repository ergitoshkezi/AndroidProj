package com.example.ingredient

import android.util.Log
import com.google.firebase.database.DatabaseReference
import kotlinx.coroutines.tasks.await

class FirebaseMenuUploader(private val databaseReference: DatabaseReference) {
    private val TAG = "FirebaseMenuUploader"

    /**
     * Upload dishes to /dishes/{dishId} root level (approved schema).
     * Deletes existing dishes for this restaurant first, then re-uploads.
     * Field names: Italian (nome, ingredienti, cucina, regione, paese, prezzo, offerta, prezzoOfferta).
     */
    /**
     * @param append When true, existing dishes are kept and new ones are added.
     *               When false (default), existing dishes are replaced entirely.
     */
    suspend fun uploadMenu(
        userId: String,
        menuCategories: List<MenuCategory>,
        append: Boolean = false
    ): Result<String> {
        return try {
            // Read restaurant profile from /restaurants/{userId} (fallback to /users for legacy accounts)
            val restaurantSnapshot = databaseReference.child("restaurants").child(userId).get().await()
            val restaurantName: String
            val restaurantLat: Double
            val restaurantLon: Double

            if (restaurantSnapshot.exists()) {
                restaurantName = restaurantSnapshot.child("nomeRistorante").getValue(String::class.java)
                    ?.takeIf { it.isNotBlank() } ?: "Unknown Restaurant"
                restaurantLat = restaurantSnapshot.child("lat").getValue(Double::class.java) ?: 0.0
                restaurantLon = restaurantSnapshot.child("lon").getValue(Double::class.java) ?: 0.0
            } else {
                // Fallback: legacy account without /restaurants node
                val userSnapshot = databaseReference.child("users").child(userId).get().await()
                val nome = userSnapshot.child("nome").getValue(String::class.java) ?: ""
                val cognome = userSnapshot.child("cognome").getValue(String::class.java) ?: ""
                restaurantName = "$nome $cognome".trim().ifEmpty { "Unknown Restaurant" }
                restaurantLat = 0.0
                restaurantLon = 0.0
            }

            if (!append) {
                // Replace mode: atomically remove all existing dishes for this restaurant
                val existing = databaseReference.child("dishes")
                    .orderByChild("restaurantId").equalTo(userId).get().await()
                val deletions = mutableMapOf<String, Any?>()
                for (dish in existing.children) {
                    deletions["dishes/${dish.key}"] = null
                }
                if (deletions.isNotEmpty()) {
                    databaseReference.updateChildren(deletions).await()
                    Log.d(TAG, "Replace mode: deleted ${deletions.size} existing dishes for $userId")
                }
            } else {
                Log.d(TAG, "Append mode: keeping existing dishes for $userId")
            }

            var totalDishes = 0

            for (category in menuCategories) {
                if (category.dishes.isEmpty()) continue
                for (dish in category.dishes) {
                    val dishId = databaseReference.child("dishes").push().key ?: continue
                    val dishData = mapOf(
                        "nome" to dish.name,
                        "descrizione" to dish.description,
                        "ingredienti" to dish.allergens,
                        "cucina" to dish.cucina.ifEmpty { category.categoryName },
                        "regione" to dish.region,
                        "paese" to dish.country,
                        "prezzo" to dish.price,
                        "offerta" to dish.isOffer,
                        "prezzoOfferta" to dish.originalPrice,
                        "calorie" to dish.calories,
                        "restaurantId" to userId,
                        "restaurantName" to restaurantName,
                        "restaurantLat" to restaurantLat,
                        "restaurantLon" to restaurantLon
                    )
                    databaseReference.child("dishes").child(dishId).setValue(dishData).await()
                    totalDishes++
                }
            }

            val message = "Successfully uploaded $totalDishes dishes in ${menuCategories.size} categories"
            Log.d(TAG, message)
            Result.success(message)

        } catch (e: Exception) {
            Log.e(TAG, "Error uploading menu to Firebase", e)
            Result.failure(e)
        }
    }

    /**
     * Upload menu (restaurantName param kept for API compat; name now read from user profile).
     */
    suspend fun uploadMenuWithMetadata(
        userId: String,
        restaurantName: String,
        menuCategories: List<MenuCategory>,
        append: Boolean = false
    ): Result<String> {
        return uploadMenu(userId, menuCategories, append)
    }

    /**
     * Get all dishes for this restaurant from /dishes, filtered by restaurantId.
     * Groups by cucina into MenuCategory list.
     */
    suspend fun getMenu(userId: String): Result<List<MenuCategory>> {
        return try {
            val snapshot = databaseReference.child("dishes")
                .orderByChild("restaurantId").equalTo(userId).get().await()

            val dishMap = mutableMapOf<String, MutableList<MenuItem>>()

            for (dishSnapshot in snapshot.children) {
                val allergens = dishSnapshot.child("ingredienti").children
                    .mapNotNull { it.getValue(String::class.java) }
                val dish = MenuItem(
                    name = dishSnapshot.child("nome").getValue(String::class.java) ?: "",
                    description = dishSnapshot.child("descrizione").getValue(String::class.java) ?: "",
                    allergens = allergens,
                    price = dishSnapshot.child("prezzo").getValue(Double::class.java) ?: 0.0,
                    originalPrice = dishSnapshot.child("prezzoOfferta").getValue(Double::class.java),
                    isOffer = dishSnapshot.child("offerta").getValue(Boolean::class.java) ?: false,
                    country = dishSnapshot.child("paese").getValue(String::class.java) ?: "",
                    region = dishSnapshot.child("regione").getValue(String::class.java) ?: "",
                    cucina = dishSnapshot.child("cucina").getValue(String::class.java) ?: "",
                    calories = (dishSnapshot.child("calorie").getValue(Long::class.java) ?: 0L).toInt()
                )
                val category = dish.cucina.ifEmpty { "Other" }
                dishMap.getOrPut(category) { mutableListOf() }.add(dish)
            }

            val categories = dishMap.map { (cat, dishes) ->
                MenuCategory(cat, dishes)
            }.sortedBy { it.categoryName }

            Log.d(TAG, "Retrieved ${categories.size} categories from /dishes for $userId")
            Result.success(categories)

        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving menu from Firebase", e)
            Result.failure(e)
        }
    }

    /**
     * Sanitize category name to use as Firebase key (kept for legacy compat).
     */
    private fun sanitizeKey(key: String): String {
        return key
            .replace("/", "_").replace(".", "_").replace("$", "_")
            .replace("#", "_").replace("[", "_").replace("]", "_")
            .trim().lowercase().replace(" ", "_")
    }
}