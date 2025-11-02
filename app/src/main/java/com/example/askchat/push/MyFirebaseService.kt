package com.example.askchat.push

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .update("fcmTokens", FieldValue.arrayUnion(token))
    }

    // Optional: handle data notifications to deep-link into a chat
    override fun onMessageReceived(msg: RemoteMessage) {
        val chatId = msg.data["chatId"] // we send this from the Function
        // If you use FCM "notification" payloads, the system shows the UI.
        // If you want custom UI, build a Notification here and open ChatActivity(chatId).
    }
}
