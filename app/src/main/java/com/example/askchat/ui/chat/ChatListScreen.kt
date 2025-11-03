package com.example.askchat.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.askchat.data.ChatRepository
import com.example.askchat.model.Chat
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

class ChatListVM(
    private val repo: ChatRepository = ChatRepository()
) : ViewModel() {

    private val _navigateToChat = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigateToChat = _navigateToChat.asSharedFlow()

    val chats: StateFlow<List<Chat>> =
        repo.chatsFlow().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    private val me get() = FirebaseAuth.getInstance().currentUser?.uid

    fun titleFor(chat: Chat): String {
        val other = chat.participants.firstOrNull { it != me } ?: "Unknown"
        return if (other == "askchat") "AskChat" else "User • ${other.takeLast(6)}"
    }

    fun subtitleFor(chat: Chat): String = chat.lastMessage?.text.orEmpty()

    fun startDmWith(otherUid: String) {
        val cleaned = otherUid.trim()
        if (cleaned.isEmpty()) return
        viewModelScope.launch {
            val chatId = repo.createOrGetDirectChat(cleaned)
            _navigateToChat.tryEmit(chatId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onOpenChat: (String) -> Unit,             // <-- lift navigation out
    vm: ChatListVM = viewModel()
) {
    val chats by vm.chats.collectAsStateWithLifecycle(initialValue = emptyList())

    var showNewDmDialog by remember { mutableStateOf(false) }
    var otherUid by remember { mutableStateOf("") }

    // When VM asks to navigate, call the callback
    LaunchedEffect(Unit) {
        vm.navigateToChat.collectLatest { chatId ->
            onOpenChat(chatId)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("AskChat") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewDmDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "New conversation")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(chats) { chat ->
                val title = vm.titleFor(chat)
                val subtitle = vm.subtitleFor(chat)

                ListItem(
                    headlineContent = { Text(title) },
                    supportingContent = { if (subtitle.isNotEmpty()) Text(subtitle) },
                    modifier = Modifier
                        .clickable { onOpenChat(chat.id) }   // <-- use callback here
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                HorizontalDivider()
            }
        }
    }

    if (showNewDmDialog) {
        AlertDialog(
            onDismissRequest = { showNewDmDialog = false },
            title = { Text("Start a direct chat") },
            text = {
                OutlinedTextField(
                    value = otherUid,
                    onValueChange = { otherUid = it },
                    singleLine = true,
                    label = { Text("Other user's UID") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.startDmWith(otherUid)
                    showNewDmDialog = false
                    otherUid = ""
                }) { Text("Start") }
            },
            dismissButton = {
                TextButton(onClick = { showNewDmDialog = false }) { Text("Cancel") }
            }
        )
    }
}
