# In Step 1 we only stub this; Step 2 will call GPT-4o with context.
# For now, we return a placeholder that echoes the top sources.
from typing import List, Dict, Any

def draft_answer(question: str, sources: List[Dict[str, Any]]) -> str:
    if not sources:
        return "I couldn't find anything relevant in your chats."
    bullets = "\n".join(
        f"- [{s['chatId']}:{s['messageId']}] {s['text'][:120]}{'...' if len(s['text'])>120 else ''}"
        for s in sources
    )
    return f"**Draft (no LLM yet):** You asked: “{question}”. Relevant snippets:\n{bullets}"
