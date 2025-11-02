package com.example.askchat.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.askchat.data.ChatRepository
import com.example.askchat.model.Chat
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Color


class ChatListVM(
    private val repo: ChatRepository = ChatRepository()
) : ViewModel() {

    val chats: StateFlow<List<Chat>> =
        repo.chatsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val me get() = FirebaseAuth.getInstance().currentUser?.uid

    fun titleFor(chat: Chat): String {
        val other = chat.participants.firstOrNull { it != me } ?: "Unknown"
        return if (other == "askchat") "AskChat" else other.takeLast(6)
    }

    fun subtitleFor(chat: Chat): String =
        chat.lastMessage?.text ?: " " // non-breaking space to keep row height stable
}

@Composable
fun ChatListScreen(
    onOpenChat: (String) -> Unit
) {
    val vm = androidx.lifecycle.viewmodel.compose.viewModel<ChatListVM>()
    val chats by vm.chats.collectAsStateWithLifecycle()

    Scaffold { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(chats, key = { it.id ?: it.participants.joinToString("_") }) { chat ->
                val chatId = chat.id ?: chat.participants.sorted().joinToString("_")
                ListItem(
                    headlineContent = { Text(vm.titleFor(chat)) },
                    supportingContent = { Text(chat.lastMessage?.text ?: "Media") },
                    modifier = Modifier.clickable { onOpenChat(chatId) }.padding(horizontal = 4.dp, vertical = 2.dp)
                )
                HorizontalDivider(thickness = 1.dp, color = Color(0x11000000))
            }
        }
    }
}
