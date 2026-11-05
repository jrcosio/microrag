"""Simulated phone photos of corpus pages (F18 D-073, research R9).

A page is put on a desk, seen at an angle, lit unevenly, with sensor noise and JPEG compression.
Everything comes from one seed, so the same page and seed give the same bytes; the parameters are
returned and stored with the photo (manifest `simulated_from`).
"""

import io
import random

from PIL import Image, ImageChops, ImageFilter

RECIPE_V1 = {
    "name": "v1",
    "long_side_px": 2000,
    "margin": 0.08,
    "desks": [(118, 96, 74), (92, 92, 98), (160, 150, 135)],
    "ranges": {
        "corner_jitter": (0.0, 0.05),   # fraction of the page size, per corner coordinate
        "rotation_deg": (-4.0, 4.0),
        "shadow": (0.10, 0.35),         # darkening at the far end of the light gradient
        "noise_sigma": (4.0, 10.0),     # grey levels
        "blur_radius": (0.4, 1.0),
        "jpeg_quality": (72, 85),
    },
}


def _solve(matrix, vector):
    """Gaussian elimination with partial pivoting, for the 8 perspective coefficients."""
    size = len(vector)
    rows = [list(matrix[i]) + [vector[i]] for i in range(size)]
    for column in range(size):
        pivot = max(range(column, size), key=lambda r: abs(rows[r][column]))
        rows[column], rows[pivot] = rows[pivot], rows[column]
        for row in range(size):
            if row != column:
                factor = rows[row][column] / rows[column][column]
                rows[row] = [a - factor * b for a, b in zip(rows[row], rows[column])]
    return [rows[i][size] / rows[i][i] for i in range(size)]


def _perspective(target, source):
    """Coefficients mapping each output point of [target] to the input point of [source] (PIL's convention)."""
    matrix, vector = [], []
    for (x, y), (u, v) in zip(target, source):
        matrix.append([x, y, 1, 0, 0, 0, -u * x, -u * y])
        vector.append(u)
        matrix.append([0, 0, 0, x, y, 1, -v * x, -v * y])
        vector.append(v)
    return _solve(matrix, vector)


def simulate_photo(page, seed, recipe=RECIPE_V1):
    """JPEG bytes of a photo of [page] and the parameters drawn from [seed]."""
    rng = random.Random(seed)
    ranges = recipe["ranges"]
    jitter = [round(rng.uniform(*ranges["corner_jitter"]), 4) for _ in range(8)]
    rotation = round(rng.uniform(*ranges["rotation_deg"]), 3)
    shadow = round(rng.uniform(*ranges["shadow"]), 3)
    sigma = round(rng.uniform(*ranges["noise_sigma"]), 3)
    blur = round(rng.uniform(*ranges["blur_radius"]), 3)
    quality = rng.randint(*ranges["jpeg_quality"])
    desk = recipe["desks"][rng.randrange(len(recipe["desks"]))]
    angle = rng.uniform(0, 360)

    page = page.convert("RGB")
    width, height = page.size
    margin = recipe["margin"]
    canvas_w, canvas_h = round(width * (1 + 2 * margin)), round(height * (1 + 2 * margin))
    left, top = round(width * margin), round(height * margin)
    corners = [(left, top), (left + width, top), (left + width, top + height), (left, top + height)]
    signs = [(1, 1), (-1, 1), (-1, -1), (1, -1)]
    target = [(x + sx * jitter[2 * i] * width, y + sy * jitter[2 * i + 1] * height)
              for i, ((x, y), (sx, sy)) in enumerate(zip(corners, signs))]
    source = [(0, 0), (width, 0), (width, height), (0, height)]
    coefficients = _perspective(target, source)

    mask = Image.new("L", page.size, 255).transform((canvas_w, canvas_h), Image.Transform.PERSPECTIVE, coefficients,
                                                    Image.Resampling.BILINEAR)
    warped = page.transform((canvas_w, canvas_h), Image.Transform.PERSPECTIVE, coefficients, Image.Resampling.BILINEAR)
    photo = Image.new("RGB", (canvas_w, canvas_h), desk)
    photo.paste(warped, mask=mask)
    photo = photo.rotate(rotation, resample=Image.Resampling.BILINEAR, expand=False, fillcolor=desk)

    # The rotated gradient's centre has no filled corners: a smooth light from one side.
    turned = Image.linear_gradient("L").rotate(angle, resample=Image.Resampling.BILINEAR, expand=True)
    inner = round(256 / 2 ** 0.5) // 2
    cx, cy = turned.width // 2, turned.height // 2
    gradient = turned.crop((cx - inner, cy - inner, cx + inner, cy + inner)).resize(photo.size, Image.Resampling.BILINEAR)
    light = gradient.point(lambda level: round(255 * (1 - shadow * level / 255)))
    photo = Image.composite(photo, Image.new("RGB", photo.size, (0, 0, 0)), light)

    small = (max(1, photo.width // 4), max(1, photo.height // 4))
    noise = Image.new("L", small)
    noise.putdata([max(0, min(255, round(128 + rng.gauss(0, sigma)))) for _ in range(small[0] * small[1])])
    noise = noise.resize(photo.size, Image.Resampling.BILINEAR)
    photo = Image.merge("RGB", [ImageChops.add(channel, noise, scale=1.0, offset=-128) for channel in photo.split()])
    photo = photo.filter(ImageFilter.GaussianBlur(blur))

    scale = recipe["long_side_px"] / max(photo.size)
    photo = photo.resize((round(photo.width * scale), round(photo.height * scale)), Image.Resampling.LANCZOS)
    out = io.BytesIO()
    photo.save(out, "JPEG", quality=quality, optimize=False, progressive=False)
    params = {"recipe": recipe["name"], "seed": seed, "corner_jitter": jitter, "rotation_deg": rotation,
              "shadow": shadow, "noise_sigma": sigma, "blur_radius": blur, "jpeg_quality": quality,
              "desk": list(desk), "light_angle_deg": round(angle, 3)}
    return out.getvalue(), params
