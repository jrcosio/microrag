"""Page rasterization and image scaling on the desktop (F18 research R7, data-model §1.2).

pypdfium2 renders with its own PDFium build, the engine family of Android's PdfRenderer; the
difference between both sides is measured on the phone (R13), not assumed. Pages are 1-based and
always on white; the encoded size and quality match the phone's (R6).
"""

import io
from pathlib import Path

import pypdfium2 as pdfium
from PIL import Image, ImageOps

RESOLUTIONS = (768, 1024, 1536)
QUALITY = 85
FORMATS = {"png": "PNG", "webp": "WEBP", "jpeg": "JPEG"}
WHITE = (255, 255, 255)


def page_count(pdf_path):
    document = pdfium.PdfDocument(str(pdf_path))
    try:
        return len(document)
    finally:
        document.close()


def render_page(pdf_path, page, long_side_px):
    """Page [page] (1-based) on white, scaled so its long side is [long_side_px]."""
    document = pdfium.PdfDocument(str(pdf_path))
    try:
        if not 1 <= page <= len(document):
            raise IndexError(f"page {page} of {len(document)}")
        pdf_page = document[page - 1]
        width, height = pdf_page.get_size()
        scale = long_side_px / max(width, height)
        bitmap = pdf_page.render(scale=scale, fill_color=(255, 255, 255, 255))
        image = bitmap.to_pil().convert("RGB")
    finally:
        document.close()
    return _fit(image, long_side_px)


def _fit(image, long_side_px):
    """Exact long side, since PDFium rounds the scaled size."""
    width, height = image.size
    if max(width, height) == long_side_px:
        return image
    factor = long_side_px / max(width, height)
    return image.resize((max(1, round(width * factor)), max(1, round(height * factor))), Image.Resampling.LANCZOS)


def encode(image, fmt, quality=QUALITY):
    """Encoded bytes, without metadata, so the same image always gives the same bytes."""
    if fmt not in FORMATS:
        raise ValueError(f"unknown format {fmt}")
    out = io.BytesIO()
    if fmt == "png":
        image.save(out, "PNG", optimize=False)
    elif fmt == "webp":
        image.save(out, "WEBP", quality=quality, method=4)
    else:
        image.save(out, "JPEG", quality=quality, optimize=False, progressive=False)
    return out.getvalue()


def tiles(image, overlap=0.10):
    """Two halves along the long side, each with half the [overlap] past the middle (R11)."""
    width, height = image.size
    if height >= width:
        part = round(height * (0.5 + overlap / 2))
        return image.crop((0, 0, width, part)), image.crop((0, height - part, width, height))
    part = round(width * (0.5 + overlap / 2))
    return image.crop((0, 0, part, height)), image.crop((width - part, 0, width, height))


def scale_image(path, long_side_px):
    """A JPG or PNG upright (EXIF), in RGB on white, scaled down so its long side is at most [long_side_px]."""
    with Image.open(path) as opened:
        image = ImageOps.exif_transpose(opened)
        if image.mode in ("RGBA", "LA") or (image.mode == "P" and "transparency" in image.info):
            rgba = image.convert("RGBA")
            background = Image.new("RGB", rgba.size, WHITE)
            background.paste(rgba, mask=rgba.getchannel("A"))
            image = background
        else:
            image = image.convert("RGB")
    if max(image.size) > long_side_px:
        image = _fit(image, long_side_px)
    return image


def cached_page(cache_root, doc_sha, pdf_path, page, long_side_px, tile="full"):
    """PNG of the page (or one of its tiles) in [cache_root]/<sha>/<px>/<tile>/<page>.png, rendered once."""
    target = Path(cache_root) / doc_sha / str(long_side_px) / tile / f"{page}.png"
    if not target.exists():
        image = render_page(pdf_path, page, long_side_px)
        if tile != "full":
            top, bottom = tiles(image)
            image = top if tile == "top" else bottom
        target.parent.mkdir(parents=True, exist_ok=True)
        partial = target.with_suffix(".part")
        partial.write_bytes(encode(image, "png"))
        partial.replace(target)
    return target
