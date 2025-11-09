import os
import chromadb
from chromadb.config import Settings
from typing import List, Dict, Any
from services.embeddings import _USE_OPENAI


_PERSIST_DIR = os.getenv("CHROMA_PERSIST_DIR", ".chromadb")
_COLLECTION = os.getenv(
    "CHROMA_COLLECTION",
    f"askchat_{'openai' if _USE_OPENAI else 'minilm'}"
)

_client = chromadb.PersistentClient(
    path=_PERSIST_DIR,
    settings=Settings(allow_reset=False)
)

def get_collection():
    return _client.get_or_create_collection(
        name=_COLLECTION,
        metadata={"hnsw:space": "cosine"}  # cosine similarity
    )

def upsert_messages(vectors: List[List[float]],
                    ids: List[str],
                    metadatas: List[Dict[str, Any]],
                    documents: List[str]):
    col = get_collection()
    col.upsert(embeddings=vectors, ids=ids, metadatas=metadatas, documents=documents)

def query_similar(vector: List[float], n_results: int = 6):
    col = get_collection()
    return col.query(query_embeddings=[vector], n_results=n_results)
