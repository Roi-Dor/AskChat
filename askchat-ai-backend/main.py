import os
import re
from typing import List
import time
from fastapi import Request
from fastapi import FastAPI, Header, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from dotenv import load_dotenv

# Models & services
from models import EmbedMessageIn, EmbedMessageOut, AskIn, AskOut, Source
from services.embeddings import embed_text, embed_texts
from services.vector_store import upsert_messages, query_similar
from services.llm import draft_answer

try:
    # Optional: if implemented in your codebase
    from services.llm import answer_with_citations  # type: ignore
except Exception:  # pragma: no cover
    answer_with_citations = None  # fallback to draft_answer later

load_dotenv()

app = FastAPI(title="AskChat AI Backend", version="0.2.0")

# ------- Embedding filters & chunking config -------
MIN_CHARS = 8            # minimum length to consider
MIN_NONSPACE = 6         # after stripping spaces/newlines
MIN_ALNUM = 3            # require at least N letters/digits (reduces emoji-only)
MAX_CHARS_PER_CHUNK = 1800
CHUNK_OVERLAP = 200      # overlapping chars to preserve context across chunks

_ALNUM_RE = re.compile(r"[0-9A-Za-z\u0590-\u05FF]")  # latin + Hebrew letters/digits

# CORS for local dev (tighten in prod)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
def health():
    return {"status": "ok"}


# -----------------------
# /embed-message (Phase 2A)
# -----------------------
@app.post("/embed-message", response_model=EmbedMessageOut)
def embed_message(
    payload: EmbedMessageIn,
    x_askchat_token: str | None = Header(None)  # optional shared-secret
):
    # Optional shared secret (set ASKCHAT_BACKEND_TOKEN in env to enable)
    expected = os.getenv("ASKCHAT_BACKEND_TOKEN")
    if expected and x_askchat_token != expected:
        raise HTTPException(status_code=401, detail="Unauthorized")

    text = (payload.text or "").strip()

    # 1) Min-length filter (skip low-signal messages like "👍", "ok")
    skip, reason = _should_skip_text(text)
    if skip:
        return EmbedMessageOut(
            status=f"skipped:{reason}",
            upserted=0,
            collection=os.getenv("CHROMA_COLLECTION", "askchat_messages"),
        )

    # 2) Chunking for long messages
    parts = chunk_text(text)
    vectors = embed_texts(parts)  # batch embed all chunks

    ids, metas, docs = [], [], []
    for idx, (chunk, vec) in enumerate(zip(parts, vectors)):
        ids.append(f"{payload.chatId}::{payload.messageId}#c{idx}")
        metas.append({
            "chatId": payload.chatId,
            "messageId": payload.messageId,
            "senderId": payload.senderId or "",
            "timestamp": payload.timestamp or 0,
            "isChunk": len(parts) > 1,
            "chunkIndex": idx,
            "totalChunks": len(parts),
            "origLength": len(text),
        })
        docs.append(chunk)

    # 3) Upsert all chunks
    upsert_messages(vectors=vectors, ids=ids, metadatas=metas, documents=docs)

    return EmbedMessageOut(
        status="ok" if len(parts) == 1 else f"ok:chunked({len(parts)})",
        upserted=len(parts),
        collection=os.getenv("CHROMA_COLLECTION", "askchat_messages"),
    )


# -----------------------
# /ask (Phase 2B - Step 4)
# -----------------------
@app.post("/ask", response_model=AskOut)
def ask(
    payload: AskIn,
    x_askchat_token: str | None = Header(None)  # optional shared-secret (same as /embed-message)
):
    expected = os.getenv("ASKCHAT_BACKEND_TOKEN")
    if expected and x_askchat_token != expected:
        raise HTTPException(status_code=401, detail="Unauthorized")

    # 1) Embed the question
    qvec = embed_text(payload.question)

    # 2) Retrieve more than needed, then collapse chunks → distinct messages
    n_overfetch = max(1, int((payload.top_k or 5) * 3))
    res = query_similar(qvec, n_results=n_overfetch)

    flat = []
    if res and res.get("ids"):
        ids = res["ids"][0]
        docs = res["documents"][0]
        metas = res["metadatas"][0]
        dists = res.get("distances", [[0.0] * len(ids)])[0]
        for i in range(len(ids)):
            m = metas[i] or {}
            flat.append({
                "chatId": m.get("chatId", ""),
                "messageId": m.get("messageId", ""),
                "text": docs[i] or "",
                "score": float(dists[i]) if dists else 0.0,  # lower = more similar (cosine distance)
                "chunkIndex": m.get("chunkIndex"),
                "totalChunks": m.get("totalChunks"),
                "timestamp": m.get("timestamp"),
            })

    # Collapse multiple chunks of same message → keep best (lowest distance)
    best_by_msg: dict[tuple[str, str], dict] = {}
    for item in flat:
        key = (item["chatId"], item["messageId"])
        if key not in best_by_msg or item["score"] < best_by_msg[key]["score"]:
            best_by_msg[key] = item

    # Take top_k distinct messages by score (ascending)
    top_k = payload.top_k or 5
    collapsed = sorted(best_by_msg.values(), key=lambda x: x["score"])[:top_k]

    # Map to Pydantic Source for response & for LLM input
    sources: List[Source] = [
        Source(
            chatId=i["chatId"],
            messageId=i["messageId"],
            text=i["text"],
            score=i["score"],
            timestamp=i.get("timestamp"),
        )
        for i in collapsed
    ]

    # 3) Ask the LLM to answer with citations
    llm_in = [{"chatId": s.chatId, "messageId": s.messageId, "text": s.text} for s in sources]

    if answer_with_citations:
        llm_out = answer_with_citations(payload.question, llm_in)
        answer_text = (llm_out or {}).get("answer", "")
    else:
        # Fallback: simple drafting without enforced citation format
        answer_text = draft_answer(payload.question, [s.dict() for s in sources])

    return AskOut(answer=answer_text, sources=sources)


# -----------------------
# Helpers: signal check & chunking
# -----------------------

def _signal_counts(s: str) -> tuple[int, int, int]:
    stripped = s.strip()
    nonspace = len("".join(ch for ch in stripped if not ch.isspace()))
    alnum = len(_ALNUM_RE.findall(stripped))
    return len(stripped), nonspace, alnum


def _should_skip_text(s: str) -> tuple[bool, str]:
    L, NS, AN = _signal_counts(s)
    if L < MIN_CHARS or NS < MIN_NONSPACE or AN < MIN_ALNUM:
        return True, f"too_short(L={L},NS={NS},AN={AN})"
    return False, ""


def chunk_text(text: str, max_chars: int = MAX_CHARS_PER_CHUNK, overlap: int = CHUNK_OVERLAP) -> list[str]:
    """Greedy chunker with soft sentence boundary preference + overlap."""
    text = text.strip()
    if len(text) <= max_chars:
        return [text]
    chunks: list[str] = []
    i = 0
    while i < len(text):
        j = min(i + max_chars, len(text))
        chunk = text[i:j]
        # try to end on a sentence boundary if possible
        k = chunk.rfind(". ")
        if j < len(text) and k != -1 and k > max_chars * 0.5:
            chunk = chunk[: k + 1]
            j = i + len(chunk)
        chunks.append(chunk)
        i = max(0, j - overlap)
    return chunks
