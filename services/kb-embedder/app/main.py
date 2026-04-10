import os
from typing import List, Optional

from fastapi import FastAPI
from pydantic import BaseModel

# Lazy import to keep startup errors readable.
from sentence_transformers import SentenceTransformer


class EmbedRequest(BaseModel):
    texts: List[str]


class EmbedResponse(BaseModel):
    vectors: List[List[float]]
    model: str


def _get_device() -> str:
    # sentence-transformers will auto-pick when device is None, but we keep it explicit for clarity.
    return os.getenv("EMBED_DEVICE", "cpu")


MODEL_NAME = os.getenv("EMBED_MODEL", "BAAI/bge-small-zh-v1.5")

app = FastAPI(title="kb-embedder", version="1.0")

_model: Optional[SentenceTransformer] = None


def get_model() -> SentenceTransformer:
    global _model
    if _model is None:
        _model = SentenceTransformer(MODEL_NAME, device=_get_device())
    return _model


@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL_NAME}


@app.post("/embed", response_model=EmbedResponse)
def embed(req: EmbedRequest):
    texts = [t if t is not None else "" for t in req.texts]
    # normalize_embeddings=True -> cosine similarity equals dot product
    vecs = get_model().encode(texts, normalize_embeddings=True)
    # vecs is numpy array
    return EmbedResponse(vectors=vecs.tolist(), model=MODEL_NAME)
