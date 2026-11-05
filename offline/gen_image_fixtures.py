"""Writes the image fixtures of the visual diagnostic's instrumented test (F18 T045, research R6).

Three small images the phone's ImageDecoder must handle: a JPEG turned by its EXIF orientation, a PNG
with transparency and a CMYK JPEG. Pillow writes them without dates, so the bytes are deterministic.
Usage: gen_image_fixtures.py [--check].
"""

import io
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "app/src/androidTest/assets/image"
ORIENTATION = 0x0112
ROTATE_90_CW = 6


def _jpeg(image, **options):
    out = io.BytesIO()
    image.save(out, "JPEG", quality=90, optimize=False, progressive=False, **options)
    return out.getvalue()


def exif_rotated():
    """400×200 as stored, 200×400 when shown: the left half is black, so the top must be black once upright."""
    image = Image.new("RGB", (400, 200), (255, 255, 255))
    ImageDraw.Draw(image).rectangle((0, 0, 199, 199), fill=(0, 0, 0))
    exif = Image.Exif()
    exif[ORIENTATION] = ROTATE_90_CW
    return _jpeg(image, exif=exif.tobytes())


def transparent():
    """100×50, fully transparent but for a black square in the middle."""
    image = Image.new("RGBA", (100, 50), (0, 0, 0, 0))
    ImageDraw.Draw(image).rectangle((40, 15, 59, 34), fill=(0, 0, 0, 255))
    out = io.BytesIO()
    image.save(out, "PNG", optimize=False)
    return out.getvalue()


def cmyk():
    """80×40 in CMYK, as some scanners and print workflows save JPEGs."""
    return _jpeg(Image.new("CMYK", (80, 40), (0, 0, 0, 0)))


def fixtures():
    return {"exif_rotated.jpg": exif_rotated(), "alpha.png": transparent(), "cmyk.jpg": cmyk()}


def main():
    files = fixtures()
    if "--check" in sys.argv:
        stale = [name for name, data in files.items() if not (TARGET / name).exists() or (TARGET / name).read_bytes() != data]
        if stale:
            raise SystemExit(f"out of date: {', '.join(stale)}")
        print(f"{len(files)} fixtures up to date")
        return
    TARGET.mkdir(parents=True, exist_ok=True)
    for name, data in files.items():
        (TARGET / name).write_bytes(data)
    print(f"wrote {len(files)} fixtures, {sum(map(len, files.values()))} bytes -> {TARGET}")


if __name__ == "__main__":
    main()
