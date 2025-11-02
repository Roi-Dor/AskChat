package com.example.askchat.model

import com.google.firebase.firestore.DocumentId

data class User(
    @DocumentId val id: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val fcmTokens: List<String>? = null
)
