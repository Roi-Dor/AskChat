package com.example.askchat.ui.chat

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Button
import android.widget.EditText
import com.example.askchat.R
import com.example.askchat.model.Message
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var vm: ChatViewModel
    private lateinit var adapter: MessagesAdapter
    private lateinit var chatId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat)

        chatId = intent.getStringExtra("chatId") ?: error("chatId missing")

        // very small RecyclerView adapter
        val list = findViewById<RecyclerView>(R.id.messagesList)
        adapter = MessagesAdapter()
        list.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        list.adapter = adapter

        vm = ChatViewModel(chatId = chatId) // default ctor uses ChatRepository()

        // collect messages in real time
        lifecycleScope.launch {
            vm.messages.collectLatest { msgs ->
                adapter.submit(msgs)
                list.scrollToPosition(msgs.lastIndex.coerceAtLeast(0))
            }
        }

        val input = findViewById<EditText>(R.id.input)
        findViewById<Button>(R.id.sendBtn).setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                vm.send(chatId, text)
                input.setText("")
            }
        }
    }
}

// extremely small adapter just to show text; style later
private class MessagesAdapter : RecyclerView.Adapter<TextVH>() {
    private val items = mutableListOf<Message>()
    override fun onCreateViewHolder(p: android.view.ViewGroup, vType: Int) =
        TextVH(android.widget.TextView(p.context).apply {
            setPadding(24, 12, 24, 12)
        })
    override fun onBindViewHolder(h: TextVH, i: Int) { h.bind(items[i]) }
    override fun getItemCount() = items.size
    fun submit(newItems: List<Message>) { items.clear(); items.addAll(newItems); notifyDataSetChanged() }
}
private class TextVH(private val tv: android.widget.TextView) : RecyclerView.ViewHolder(tv) {
    fun bind(m: Message) { tv.text = m.text ?: "[media]" }
}
