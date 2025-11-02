package com.example.askchat.model

import com.google.firebase.Timestamp


data class LastMessage(
    val text: String? = null,
    val senderId: String? = null,
    val timestamp: Timestamp? = null
)

data class Chat(
    val id: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: LastMessage? = null,
    val updatedAt: Timestamp? = null
)