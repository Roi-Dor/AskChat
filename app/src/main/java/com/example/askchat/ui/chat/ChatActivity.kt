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
    var pendingHighlightId: String? = intent.getStringExtra("highlightMessageId")


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

        lifecycleScope.launch {
            vm.messages.collectLatest { msgs ->
                adapter.submit(msgs)
                val list = findViewById<RecyclerView>(R.id.messagesList)
                val targetId = pendingHighlightId
                if (targetId != null) {
                    val idx = msgs.indexOfFirst { it.id == targetId }
                    if (idx >= 0) {
                        list.scrollToPosition(idx)
                        pendingHighlightId = null // consume once
                    } else {
                        list.scrollToPosition(msgs.lastIndex.coerceAtLeast(0))
                    }
                } else {
                    list.scrollToPosition(msgs.lastIndex.coerceAtLeast(0))
                }
            }
        }
    }
}

private class MessagesAdapter : RecyclerView.Adapter<TextVH>() {
    private val items = mutableListOf<Message>()
    override fun onCreateViewHolder(p: android.view.ViewGroup, vType: Int) =
        TextVH(android.widget.TextView(p.context).apply {
            setPadding(24, 12, 24, 12)
            setTextIsSelectable(true)
            movementMethod = android.text.method.LinkMovementMethod.getInstance()
        })
    override fun onBindViewHolder(h: TextVH, i: Int) { h.bind(items[i]) }
    override fun getItemCount() = items.size
    fun submit(newItems: List<Message>) { items.clear(); items.addAll(newItems); notifyDataSetChanged() }
}

private class TextVH(private val tv: android.widget.TextView) : RecyclerView.ViewHolder(tv) {
    fun bind(m: Message) {
        val base = m.text ?: "[media]"
        if (m.senderId == "AskChat" && !m.sources.isNullOrEmpty()) {
            val first = m.sources.first() // "chatId::messageId"
            val label = "\n\n💬 See message"
            val sp = android.text.SpannableString(base + label)
            val start = base.length + 1
            val end = sp.length
            sp.setSpan(object : android.text.style.ClickableSpan() {
                override fun onClick(widget: android.view.View) {
                    val parts = first.split("::")
                    val chatId = parts.getOrNull(0) ?: return
                    val messageId = parts.getOrNull(1) ?: return
                    val ctx = widget.context
                    val i = android.content.Intent(ctx, ChatActivity::class.java)
                        .putExtra("chatId", chatId)
                        .putExtra("highlightMessageId", messageId)
                    ctx.startActivity(i)
                }
            }, start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            tv.text = sp
        } else {
            tv.text = base
        }
    }
}

