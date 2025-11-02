package com.example.askchat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.askchat.data.ChatRepository
import com.example.askchat.model.Message
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch



class ChatViewModel(
    private val repo: ChatRepository = ChatRepository(),
    chatId: String
) : ViewModel() {

    val messages: StateFlow<List<Message>> =
        repo.messagesFlow(chatId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun send(chatId: String, text: String) {
        viewModelScope.launch { repo.sendMessage(chatId, text) }
    }
}
