# from fastapi import FastAPI
# from pydantic import BaseModel
# from sentence_transformers import SentenceTransformer

# # Tải mô hình embedding
# model = SentenceTransformer("all-MiniLM-L6-v2")

# # Tạo app FastAPI
# app = FastAPI()

# # Định nghĩa kiểu dữ liệu đầu vào
# class EmbeddingRequest(BaseModel):
#     text: str

# # API chính: POST /embedding
# @app.post("/embedding")
# def get_embedding(request: EmbeddingRequest):
#     embedding = model.encode(request.text)
#     return embedding.tolist()

from fastapi import FastAPI
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer

# Tải mô hình tiếng Việt
model = SentenceTransformer("VoVanPhuc/sup-SimCSE-VietNamese-phobert-base")

# Tạo FastAPI app
app = FastAPI()

# Định nghĩa schema cho dữ liệu đầu vào
class EmbeddingRequest(BaseModel):
    text: str

# API endpoint để lấy embedding
@app.post("/embedding")
def get_embedding(request: EmbeddingRequest):
    embedding = model.encode(request.text)
    return embedding.tolist()
