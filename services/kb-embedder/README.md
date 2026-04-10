# kb-embedder (Python)

本服务提供 embedding HTTP 接口（方案 A）。

## API
- `GET /health`
- `POST /embed`

Request:
```json
{ "texts": ["你好", "企业知识库"] }
```

Response:
```json
{ "model": "...", "vectors": [[...],[...]] }
```

## 本机启动

推荐使用仓库脚本（会优先用 `uv` 创建 `3.12` 的 venv，避免新版本 Python 缺少依赖 wheel 导致编译失败）：

```powershell
cd .\kb-assistant-ms
.\scripts\setup-python.ps1 -Service embedder
.\services\kb-embedder\.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8090
```

也可以手动创建 venv（注意：Python 3.13/3.14 可能无法直接安装 `pydantic-core` / `torch` 等依赖，建议用 3.11/3.12）：

```powershell
cd .\services\kb-embedder
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
python -m uvicorn app.main:app --host 0.0.0.0 --port 8090
```

可用环境变量：
- `EMBED_MODEL`：默认 `BAAI/bge-small-zh-v1.5`
- `EMBED_DEVICE`：默认 `cpu`（如有 CUDA 可设 `cuda`）
