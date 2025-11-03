package com.example.askchat.data

import com.example.askchat.model.Chat
import com.example.askchat.model.LastMessage
import com.example.askchat.model.Message
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await


class ChatRepository {

    private val db = Firebase.firestore
    private val auth get() = FirebaseAuth.getInstance()

    private fun requireUid(): String =
        auth.currentUser?.uid ?: error("Not signed in")

    /**
     * Stream messages of a chat in chronological order.
     * Path: chats/{chatId}/messages
     */
    fun messagesFlow(chatId: String): Flow<List<Message>> = callbackFlow {
        val reg = db.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp") // asc so the UI shows oldest→newest
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                val msgs = snap?.documents.orEmpty().map { d ->
                    Message(
                        id = d.id,
                        senderId = d.getString("senderId") ?: "",
                        text = d.getString("text"),
                        mediaUrl = d.getString("mediaUrl"),
                        timestamp = d.getTimestamp("timestamp")
                    )
                }
                trySend(msgs)
            }
        awaitClose { reg.remove() }
    }

    /**
     * Send a text message to the chat.
     * Writes:
     *   chats/{chatId}/messages/{autoId}:
     *     { senderId, text, timestamp=serverTimestamp() }
     *
     * NOTE: your Cloud Function will update chats/{chatId}.lastMessage + updatedAt
     */
    suspend fun sendMessage(chatId: String, text: String) {
        val uid = requireUid()
        val msgRef = db.collection("chats").document(chatId)
            .collection("messages").document()

        val payload = mapOf(
            "senderId" to uid,
            "text" to text,
            "mediaUrl" to null,
            "timestamp" to FieldValue.serverTimestamp()
        )
        msgRef.set(payload).await()
        // Optional client-side safeguard so the list sorts correctly
        // even before CF runs (your function already does this server-side).
        db.collection("chats").document(chatId)
            .update(
                mapOf(
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "lastMessage" to mapOf(
                        "text" to text,
                        "senderId" to uid,
                        "timestamp" to Timestamp.now()
                    )
                )
            )
            .await()
    }

    fun chatsFlow(): Flow<List<Chat>> = callbackFlow {
        val uid = requireUid()
        val reg = db.collection("chats")
            .whereArrayContains("participants", uid)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                val items = snap?.documents.orEmpty().map { d ->
                    val lm = (d.get("lastMessage") as? Map<*, *>)?.let { m ->
                        LastMessage(
                            text = m["text"] as? String,
                            senderId = m["senderId"] as? String,
                            timestamp = m["timestamp"] as? com.google.firebase.Timestamp
                        )
                    }
                    Chat(
                        id = d.id,
                        participants = (d.get("participants") as? List<*>)?.filterIsInstance<String>()
                            ?: emptyList(),
                        lastMessage = lm,
                        updatedAt = d.getTimestamp("updatedAt")
                    )
                }
                trySend(items)
            }
        awaitClose { reg.remove() }
    }

    suspend fun createOrGetDirectChat(peerUid: String): String {
        val db = FirebaseFirestore.getInstance()
        val myUid = FirebaseAuth.getInstance().currentUser!!.uid

        // Helper to create a deterministic participants key
        fun dmKey(a: String, b: String) = if (a <= b) "${a}_${b}" else "${b}_${a}"
        val key = dmKey(myUid, peerUid)

        // ✅ FIX: Add whereArrayContains("participants", myUid)
        val query = db.collection("chats")
            .whereEqualTo("type", "dm")
            .whereEqualTo("participantsKey", key)
            .whereArrayContains("participants", myUid)
            .limit(1)

        val result = query.get().await()
        if (!result.isEmpty) {
            return result.documents.first().id
        }
// NthosSJOPiTI2AZPSx2mdZ89Nvl1
        // Create chat if none exists
        val chatData = mapOf(
            "type" to "dm",
            "participants" to listOf(myUid, peerUid),
            "participantsKey" to key,
            "updatedAt" to FieldValue.serverTimestamp(),
            "lastMessage" to mapOf(
                "text" to "👋 Say hi!",
                "senderId" to myUid,
                "timestamp" to FieldValue.serverTimestamp()
            )
        )

        val newChat = db.collection("chats").add(chatData).await()
        return newChat.id
    }
}
