FROM ghcr.io/astral-sh/uv:python3.12-bookworm-slim
WORKDIR /app
# T13 A1: PyTorch SOLO CPU, antes que el resto. Si no, sentence-transformers baja la
# variante con CUDA (varios GB de librerías de GPU que un VPS sin GPU nunca usa).
RUN uv pip install --system --no-cache --index-url https://download.pytorch.org/whl/cpu torch
COPY requirements.txt .
RUN uv pip install --system --no-cache -r requirements.txt
COPY . .
# Caché del modelo de embeddings: en producción va a un volumen para no bajarlo en cada deploy.
ENV HF_HOME=/models
EXPOSE 8000
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
