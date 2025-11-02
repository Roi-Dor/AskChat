package com.example.askchat.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.DocumentId

data class Message(
    @DocumentId val id: String? = null,
    val senderId: String = "",
    val text: String? = null,
    val mediaUrl: String? = null,
    @ServerTimestamp val timestamp: Timestamp? = null
)
