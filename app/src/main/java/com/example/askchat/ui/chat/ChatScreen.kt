package com.example.askchat.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.askchat.data.ChatRepository
import com.example.askchat.model.Message
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private class ChatVM(
    private val repo: ChatRepository = ChatRepository(),
    private val chatId: String
) : ViewModel() {
    val messages: StateFlow<List<Message>> =
        repo.messagesFlow(chatId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun send(text: String) = viewModelScope.launch { repo.sendMessage(chatId, text) }
    val me = FirebaseAuth.getInstance().currentUser?.uid
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chatId: String, onBack: () -> Unit) {
    // Simple factory that passes chatId into the VM
    val vm: ChatVM = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ChatVM(chatId = chatId) as T
        }
    })

    val msgs by vm.messages.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.weight(1f)) {
                items(msgs) { m ->
                    val mine = m.senderId == vm.me
                    Row(
                        Modifier.fillMaxWidth().padding(6.dp),
                        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            color = if (mine) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                            tonalElevation = 1.dp,
                            modifier = Modifier.widthIn(max = 320.dp)
                        ) {
                            Text(
                                m.text ?: "📎 Media",
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                color = if (mine) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message") },
                    maxLines = 4
                )
                IconButton(
                    enabled = input.isNotBlank(),
                    onClick = { vm.send(input.trim()); input = "" }
                ) {
                    Icon(Icons.Filled.Send, contentDescription = null)
                }
            }
        }
    }
}
