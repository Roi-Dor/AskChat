from pydantic import BaseModel, Field
from typing import List, Dict, Any, Optional

class EmbedMessageIn(BaseModel):
    chatId: str
    messageId: str
    text: str
    senderId: Optional[str] = None
    timestamp: Optional[int] = None  # epoch ms (optional)

class EmbedMessageOut(BaseModel):
    status: str = "ok"
    upserted: int = 1
    collection: str

class AskIn(BaseModel):
    question: str
    userId: Optional[str] = None
    top_k: int = Field(default=6, ge=1, le=50)
    ctx_window: int = Field(default=2, ge=0, le=10)  # how many msgs around each hit

class Source(BaseModel):
    chatId: str
    messageId: str
    text: str
    score: float
    timestamp: Optional[int] = None

class AskOut(BaseModel):
    answer: str
    sources: List[Source]
