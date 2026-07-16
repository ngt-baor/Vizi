import asyncio
import os

from fastapi import FastAPI, File, Header, HTTPException, UploadFile
from rembg import new_session, remove
from starlette.responses import Response


MAX_IMAGE_BYTES = 5 * 1024 * 1024
MODEL = os.getenv("REMBG_MODEL", "u2netp")
SHARED_TOKEN = os.getenv("BACKGROUND_REMOVAL_SHARED_TOKEN", "").strip()
ALLOWED_TYPES = {"image/png", "image/jpeg", "image/webp"}

app = FastAPI(title="Vizi Background Removal", docs_url=None, redoc_url=None)
session = None
session_lock = asyncio.Lock()


def verify_token(value: str | None) -> None:
    if SHARED_TOKEN and value != SHARED_TOKEN:
        raise HTTPException(status_code=401, detail="Background removal authentication failed")


async def get_session():
    global session
    if session is None:
        async with session_lock:
            if session is None:
                session = await asyncio.to_thread(new_session, MODEL)
    return session


@app.get("/api/health")
async def health():
    return {"status": "ok", "model": MODEL}


@app.post("/api/remove")
async def remove_background(
    file: UploadFile = File(...),
    x_vizi_internal_token: str | None = Header(default=None),
):
    verify_token(x_vizi_internal_token)
    if file.content_type not in ALLOWED_TYPES:
        raise HTTPException(status_code=400, detail="Only PNG, JPG, or WebP images are allowed")

    content = await file.read()
    if not content or len(content) > MAX_IMAGE_BYTES:
        raise HTTPException(status_code=400, detail="Image must be between 1 byte and 5 MB")

    try:
        output = await asyncio.to_thread(
            remove,
            content,
            session=await get_session(),
            force_return_bytes=True,
        )
    except Exception as error:
        raise HTTPException(status_code=502, detail="Background removal failed") from error

    if not output or len(output) > MAX_IMAGE_BYTES:
        raise HTTPException(status_code=502, detail="Background removal returned an invalid image")
    return Response(content=output, media_type="image/png")
