package com.example.askchat

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.lifecycleScope
import com.example.askchat.data.FcmTokenManager
import com.example.askchat.ui.AppNav
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object { const val CHANNEL_ID = "chat_messages" }

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, "Notifications disabled", Toast.LENGTH_SHORT).show()
        }
        registerFcmTokenIfSignedIn()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createNotificationChannel()
        askNotificationPermissionIfNeeded()

        // 1) Ensure Firebase Auth user exists
        ensureSignedIn {                      // <- runs after a user object exists
            // 2) Now it’s safe to render Compose & start token registration
            setContent {
                AppNav(initialDeepLinkChatId = intent?.getStringExtra("chatId"))
            }
            registerFcmTokenIfSignedIn()
        }
    }

    private fun ensureSignedIn(onReady: () -> Unit) {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user != null) {
            onReady()
            return
        }
        auth.signInAnonymously()
            .addOnSuccessListener { onReady() }
            .addOnFailureListener {
                android.widget.Toast.makeText(this, "Auth failed: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
            }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }


    private fun handleDeepLink(intent: Intent?) {
        val chatId = intent?.getStringExtra("chatId")
        if (!chatId.isNullOrBlank()) {
            Toast.makeText(this, "Open chat: $chatId", Toast.LENGTH_SHORT).show()
        }
    }

    private fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            )
            if (status != PermissionChecker.PERMISSION_GRANTED) {
                requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID, "Chat messages", NotificationManager.IMPORTANCE_DEFAULT
            )
            nm.createNotificationChannel(channel)
        }
    }

    private fun registerFcmTokenIfSignedIn() {
        lifecycleScope.launch {
            FcmTokenManager.ensureUserDocAndRegisterToken()
        }
    }

    // If you haven't created ChatRepository/ChatActivity yet, keep this removed or commented:
    // private fun openAskChat() { ... }
}
