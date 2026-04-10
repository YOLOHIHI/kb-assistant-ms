# kb-ocr-service (Python)

为 `kb-doc-service` 提供可选 OCR 能力（图片 -> 文本）。

## API

- `GET /health`
- `POST /ocr`

Request:
```json
{
  "filename": "a.png",
  "contentType": "image/png",
  "dataBase64": "..."
}
```

Response:
```json
{ "text": "...", "engine": "tesseract", "lang": "chi_sim+eng" }
```

## Docker

该镜像会在构建时安装 `tesseract-ocr` + 简体中文语言包。

## 环境变量

- `OCR_LANG`：默认 `chi_sim+eng`
