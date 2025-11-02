package com.example.askchat.data

object Paths {
    const val USERS = "users"
    const val CHATS = "chats"
    const val MESSAGES = "messages"

    fun chatIdFor(u1: String, u2: String) =
        listOf(u1, u2).sorted().joinToString("_")
}
