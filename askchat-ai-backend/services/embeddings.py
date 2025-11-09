import os
from typing import List
from functools import lru_cache

_OPENAI_KEY = os.getenv("OPENAI_API_KEY")
_MODEL_OPENAI = "text-embedding-3-small"
_USE_OPENAI = bool(_OPENAI_KEY)

if _USE_OPENAI:
    from openai import OpenAI
    _client = OpenAI(api_key=_OPENAI_KEY)
else:
    # Local fallback
    from sentence_transformers import SentenceTransformer
    _local_model_name = "sentence-transformers/all-MiniLM-L6-v2"
    _local_model = SentenceTransformer(_local_model_name)

def embed_texts(texts: List[str]) -> List[List[float]]:
    """
    Returns list of embedding vectors (len(texts) == len(vectors))
    """
    if _USE_OPENAI:
        resp = _client.embeddings.create(model=_MODEL_OPENAI, input=texts)
        return [d.embedding for d in resp.data]
    else:
        # local CPU embeddings
        return _local_model.encode(texts, normalize_embeddings=True).tolist()

def embed_text(text: str) -> List[float]:
    return embed_texts([text])[0]
