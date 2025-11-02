package com.example.askchat.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

object FcmTokenManager {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    /**
     * Ensures users/{uid} exists and registers the current FCM token
     * into users/{uid}.fcmTokens (array-Union).
     */
    suspend fun ensureUserDocAndRegisterToken(displayName: String? = null) {
        val user = auth.currentUser ?: return

        // 1) Upsert basic user doc so updates never fail
        val base = hashMapOf(
            "displayName" to (displayName ?: user.displayName ?: "User"),
            "photoUrl" to (user.photoUrl?.toString()),
        )
        db.collection("users").document(user.uid)
            .set(base, SetOptions.merge())
            .await()

        // 2) Save current FCM token to array
        val token = FirebaseMessaging.getInstance().token.await()
        db.collection("users").document(user.uid)
            .update("fcmTokens", FieldValue.arrayUnion(token))
            .await()
    }
}
