import base64
import io
import os
import platform
import shutil
import subprocess
import threading

from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel

from PIL import Image

app = FastAPI(title="kb-ocr-service")

# ---------------------------------------------------------------------------
# Tesseract availability check
# ---------------------------------------------------------------------------

def _check_tesseract() -> bool:
    """Return True if tesseract binary is callable."""
    try:
        result = subprocess.run(
            ["tesseract", "--version"],
            capture_output=True, timeout=5
        )
        return result.returncode == 0
    except Exception:
        return False


_tesseract_available = _check_tesseract()
_install_lock = threading.Lock()
_install_status = {"running": False, "last_result": None}


# ---------------------------------------------------------------------------
# Models
# ---------------------------------------------------------------------------

class OcrRequest(BaseModel):
    filename: str = ""
    contentType: str = ""
    dataBase64: str


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------

@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/ocr/status")
def ocr_status():
    """Report whether Tesseract is available and ready."""
    global _tesseract_available
    _tesseract_available = _check_tesseract()
    return {
        "tesseract": _tesseract_available,
        "platform": platform.system(),
        "install_running": _install_status["running"],
        "last_install_result": _install_status["last_result"],
    }


@app.post("/ocr/install")
def ocr_install(x_internal_token: str = Header(None)):
    _expected = os.environ.get("KB_INTERNAL_TOKEN", "")
    if not _expected or x_internal_token != _expected:
        raise HTTPException(status_code=403, detail="Forbidden")
    """
    Trigger a background Tesseract installation.
    Supported automatically on Debian/Ubuntu (apt) and Alpine (apk).
    On other systems returns instructions for manual installation.
    """
    if _check_tesseract():
        return {"ok": True, "message": "Tesseract is already installed."}

    sys = platform.system()
    if sys != "Linux":
        return {
            "ok": False,
            "message": f"Automatic installation is not supported on {sys}. "
                       "Please install Tesseract manually and restart the container.",
        }

    # Detect package manager
    if shutil.which("apt-get"):
        cmd = [
            "bash", "-c",
            "apt-get update -qq && "
            "apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-chi-sim tesseract-ocr-eng"
        ]
    elif shutil.which("apk"):
        cmd = [
            "apk", "add", "--no-cache",
            "tesseract-ocr", "tesseract-ocr-data-chi_sim", "tesseract-ocr-data-eng"
        ]
    else:
        return {
            "ok": False,
            "message": "Could not detect apt-get or apk. Install Tesseract manually.",
        }

    if _install_status["running"]:
        return {"ok": False, "message": "Installation is already in progress."}

    def _run():
        global _tesseract_available
        with _install_lock:
            _install_status["running"] = True
            _install_status["last_result"] = None
            try:
                result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
                if result.returncode == 0:
                    _tesseract_available = _check_tesseract()
                    _install_status["last_result"] = {
                        "success": True,
                        "message": "Tesseract installed successfully.",
                    }
                else:
                    _install_status["last_result"] = {
                        "success": False,
                        "message": result.stderr[:500] if result.stderr else "Unknown error",
                    }
            except Exception as e:
                _install_status["last_result"] = {"success": False, "message": str(e)}
            finally:
                _install_status["running"] = False

    threading.Thread(target=_run, daemon=True).start()
    return {"ok": True, "message": "Installation started in background. Poll /ocr/status to check progress."}


@app.post("/ocr")
def ocr(req: OcrRequest):
    global _tesseract_available
    if not _tesseract_available:
        _tesseract_available = _check_tesseract()
    if not _tesseract_available:
        return {
            "text": "",
            "engine": "tesseract",
            "error": "Tesseract is not installed. POST /ocr/install to install it, or check /ocr/status.",
        }

    try:
        import pytesseract
        raw = base64.b64decode(req.dataBase64)
        img = Image.open(io.BytesIO(raw))
        lang = os.environ.get("OCR_LANG", "chi_sim+eng")
        text = pytesseract.image_to_string(img, lang=lang)
        return {"text": text or "", "engine": "tesseract", "lang": lang}
    except Exception as e:
        return {"text": "", "engine": "tesseract", "error": str(e)}
