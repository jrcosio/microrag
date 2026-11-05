"""Simulated photos of corpus pages, deterministic by seed (F18 T016, D-073)."""

import io
import sys
import unittest
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval import photos  # noqa: E402


def a_page():
    page = Image.new("RGB", (600, 850), (255, 255, 255))
    draw = ImageDraw.Draw(page)
    for row in range(40, 800, 30):
        draw.rectangle((40, row, 560, row + 10), fill=(30, 30, 30))
    return page


class SimulatedPhotoTest(unittest.TestCase):
    def test_same_seed_same_bytes(self):
        first, params = photos.simulate_photo(a_page(), seed=7)
        second, again = photos.simulate_photo(a_page(), seed=7)
        self.assertEqual(first, second)
        self.assertEqual(params, again)

    def test_another_seed_another_photo(self):
        self.assertNotEqual(photos.simulate_photo(a_page(), seed=7)[0], photos.simulate_photo(a_page(), seed=8)[0])

    def test_recipe_stays_within_its_bounds(self):
        for seed in range(20):
            _, params = photos.simulate_photo(a_page(), seed=seed)
            for key, (low, high) in photos.RECIPE_V1["ranges"].items():
                value = params[key]
                values = value if isinstance(value, list) else [value]
                for item in values:
                    self.assertGreaterEqual(item, low, key)
                    self.assertLessEqual(item, high, key)

    def test_output_is_a_jpeg_of_the_photo_size(self):
        data, params = photos.simulate_photo(a_page(), seed=3)
        image = Image.open(io.BytesIO(data))
        self.assertEqual(image.format, "JPEG")
        self.assertEqual(max(image.size), photos.RECIPE_V1["long_side_px"])
        self.assertEqual(params["recipe"], "v1")

    def test_the_page_is_still_on_the_photo(self):
        data, _ = photos.simulate_photo(a_page(), seed=5)
        image = Image.open(io.BytesIO(data)).convert("L")
        center = image.getpixel((image.width // 2, image.height // 2))
        corner = image.getpixel((2, 2))
        self.assertNotEqual(center, corner)


if __name__ == "__main__":
    unittest.main()
