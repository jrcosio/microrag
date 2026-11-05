"""Page rasterization, encodings, tiles and image scaling on the desktop (F18 T016, research R7)."""

import io
import sys
import tempfile
import unittest
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval import raster  # noqa: E402

FIXTURES = Path(__file__).resolve().parents[2] / "app" / "src" / "androidTest" / "assets" / "pdf"


def a_pdf():
    """The F06 fixture with three text pages."""
    return FIXTURES / "text_3_pages.pdf"


class RenderTest(unittest.TestCase):
    def test_long_side_and_white_background(self):
        image = raster.render_page(a_pdf(), 1, 768)
        self.assertEqual(max(image.size), 768)
        self.assertEqual(image.mode, "RGB")
        self.assertEqual(image.getpixel((0, 0)), (255, 255, 255))

    def test_pages_are_one_based(self):
        with self.assertRaises(IndexError):
            raster.render_page(a_pdf(), 0, 768)
        self.assertGreaterEqual(raster.page_count(a_pdf()), 1)

    def test_rendering_is_deterministic(self):
        first = raster.encode(raster.render_page(a_pdf(), 1, 512), "png")
        second = raster.encode(raster.render_page(a_pdf(), 1, 512), "png")
        self.assertEqual(first, second)


class EncodeTest(unittest.TestCase):
    def test_formats_decode_back(self):
        image = Image.new("RGB", (300, 200), (255, 255, 255))
        for fmt, name in (("png", "PNG"), ("webp", "WEBP"), ("jpeg", "JPEG")):
            data = raster.encode(image, fmt, quality=85)
            self.assertEqual(Image.open(io.BytesIO(data)).format, name)

    def test_unknown_format(self):
        with self.assertRaises(ValueError):
            raster.encode(Image.new("RGB", (4, 4)), "gif")


class TilesTest(unittest.TestCase):
    def test_portrait_halves_overlap_ten_percent(self):
        top, bottom = raster.tiles(Image.new("RGB", (100, 200)), overlap=0.10)
        self.assertEqual(top.size, (100, 110))
        self.assertEqual(bottom.size, (100, 110))

    def test_landscape_is_split_left_and_right(self):
        left, right = raster.tiles(Image.new("RGB", (200, 100)), overlap=0.10)
        self.assertEqual(left.size, (110, 100))
        self.assertEqual(right.size, (110, 100))


class ScaleImageTest(unittest.TestCase):
    def test_exif_rotation_transparency_and_cmyk(self):
        with tempfile.TemporaryDirectory() as tmp:
            rotated = Path(tmp, "rotated.jpg")
            exif = Image.Exif()
            exif[0x0112] = 6  # rotate 90° clockwise when shown
            Image.new("RGB", (400, 200), (10, 20, 30)).save(rotated, "JPEG", exif=exif)
            upright = raster.scale_image(rotated, 300)
            self.assertEqual(upright.size, (150, 300))

            transparent = Path(tmp, "alpha.png")
            Image.new("RGBA", (100, 50), (0, 0, 0, 0)).save(transparent)
            self.assertEqual(raster.scale_image(transparent, 100).getpixel((5, 5)), (255, 255, 255))

            cmyk = Path(tmp, "cmyk.jpg")
            Image.new("CMYK", (80, 40), (0, 0, 0, 0)).save(cmyk, "JPEG")
            self.assertEqual(raster.scale_image(cmyk, 80).mode, "RGB")


class CacheTest(unittest.TestCase):
    def test_cached_page_is_rendered_once(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = raster.cached_page(Path(tmp), "abc", a_pdf(), 1, 512)
            stamp = path.stat().st_mtime_ns
            again = raster.cached_page(Path(tmp), "abc", a_pdf(), 1, 512)
            self.assertEqual(path, again)
            self.assertEqual(again.stat().st_mtime_ns, stamp)
            self.assertEqual(path.relative_to(tmp).parts[:3], ("abc", "512", "full"))


if __name__ == "__main__":
    unittest.main()
