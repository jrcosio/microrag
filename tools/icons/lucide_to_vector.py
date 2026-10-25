#!/usr/bin/env python3
"""Convert Lucide SVG icons into Android VectorDrawable XML resources.

Usage:
    python3 -I tools/icons/lucide_to_vector.py <lucide-icons-dir> <icons.txt> <output-res-drawable-dir>

<lucide-icons-dir> is the `icons/` folder of the `lucide-static` npm package
(downloaded outside the repository, see specs/001-foundations-design-system/quickstart.md).
<icons.txt> maps resource names to Lucide names, one `ic_name = lucide-name` per line.

Only standard SVG geometry is read (path, circle, ellipse, rect, line, polyline, polygon);
nothing from the downloaded package is executed. Output icons keep Lucide's look:
24x24 viewport, stroke width 2, round caps and joins, no fill, black stroke tinted at use.
"""

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

SVG_NS = "{http://www.w3.org/2000/svg}"
LUCIDE_VERSION = "1.61.0"

NUMBER = re.compile(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")
COMMAND = re.compile(r"[MmZzLlHhVvCcSsQqTtAa]")
PARAM_COUNT = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "T": 2, "A": 7, "Z": 0}


def fmt(value: float) -> str:
    text = f"{value:.4f}".rstrip("0").rstrip(".")
    return "0" if text in ("-0", "") else text


def normalize_path(d: str) -> str:
    """Re-serialize path data with explicit separators (handles compact arc flags)."""
    out = []
    i = 0
    command = None
    while i < len(d):
        ch = d[i]
        if ch.isspace() or ch == ",":
            i += 1
            continue
        if COMMAND.match(ch):
            command = ch
            out.append(ch)
            i += 1
            if command in "Zz":
                command = None
            continue
        if command is None:
            raise ValueError(f"Number without command in path: {d!r}")
        upper = command.upper()
        params = []
        for index in range(PARAM_COUNT[upper]):
            while i < len(d) and (d[i].isspace() or d[i] == ","):
                i += 1
            if upper == "A" and index in (3, 4):
                flag = d[i]
                if flag not in "01":
                    raise ValueError(f"Invalid arc flag {flag!r} in {d!r}")
                params.append(flag)
                i += 1
                continue
            match = NUMBER.match(d, i)
            if not match:
                raise ValueError(f"Expected number at {i} in {d!r}")
            params.append(fmt(float(match.group())))
            i = match.end()
        out.append(" ".join(params))
        # Implicit repetition: a moveto followed by more pairs is a lineto.
        if command == "M":
            command = "L"
        elif command == "m":
            command = "l"
    return " ".join(out)


def num(element, name, default=0.0):
    value = element.get(name)
    return float(value) if value is not None else default


def points_path(points: str, close: bool) -> str:
    values = [float(v) for v in NUMBER.findall(points)]
    pairs = [f"{fmt(values[k])} {fmt(values[k + 1])}" for k in range(0, len(values), 2)]
    data = "M " + pairs[0] + "".join(f" L {p}" for p in pairs[1:])
    return data + (" Z" if close else "")


def ellipse_path(cx, cy, rx, ry) -> str:
    return (
        f"M {fmt(cx - rx)} {fmt(cy)} "
        f"A {fmt(rx)} {fmt(ry)} 0 1 0 {fmt(cx + rx)} {fmt(cy)} "
        f"A {fmt(rx)} {fmt(ry)} 0 1 0 {fmt(cx - rx)} {fmt(cy)} Z"
    )


def rect_path(x, y, w, h, rx, ry) -> str:
    if rx is None and ry is None:
        return f"M {fmt(x)} {fmt(y)} H {fmt(x + w)} V {fmt(y + h)} H {fmt(x)} Z"
    rx = ry if rx is None else rx
    ry = rx if ry is None else ry
    rx = min(rx, w / 2)
    ry = min(ry, h / 2)
    return (
        f"M {fmt(x + rx)} {fmt(y)} H {fmt(x + w - rx)} "
        f"A {fmt(rx)} {fmt(ry)} 0 0 1 {fmt(x + w)} {fmt(y + ry)} V {fmt(y + h - ry)} "
        f"A {fmt(rx)} {fmt(ry)} 0 0 1 {fmt(x + w - rx)} {fmt(y + h)} H {fmt(x + rx)} "
        f"A {fmt(rx)} {fmt(ry)} 0 0 1 {fmt(x)} {fmt(y + h - ry)} V {fmt(y + ry)} "
        f"A {fmt(rx)} {fmt(ry)} 0 0 1 {fmt(x + rx)} {fmt(y)} Z"
    )


def element_path(element) -> str:
    tag = element.tag.replace(SVG_NS, "")
    if tag == "path":
        return normalize_path(element.get("d"))
    if tag == "circle":
        r = num(element, "r")
        return ellipse_path(num(element, "cx"), num(element, "cy"), r, r)
    if tag == "ellipse":
        return ellipse_path(num(element, "cx"), num(element, "cy"), num(element, "rx"), num(element, "ry"))
    if tag == "rect":
        rx = element.get("rx")
        ry = element.get("ry")
        return rect_path(
            num(element, "x"), num(element, "y"), num(element, "width"), num(element, "height"),
            float(rx) if rx is not None else None,
            float(ry) if ry is not None else None,
        )
    if tag == "line":
        return (
            f"M {fmt(num(element, 'x1'))} {fmt(num(element, 'y1'))} "
            f"L {fmt(num(element, 'x2'))} {fmt(num(element, 'y2'))}"
        )
    if tag in ("polyline", "polygon"):
        return points_path(element.get("points"), close=tag == "polygon")
    raise ValueError(f"Unsupported SVG element <{tag}>")


def convert(svg_file: Path, resource: str, lucide_name: str) -> str:
    root = ET.parse(svg_file).getroot()
    if root.get("viewBox") != "0 0 24 24":
        raise ValueError(f"{svg_file.name}: unexpected viewBox {root.get('viewBox')}")
    paths = [element_path(child) for child in root]
    body = "\n".join(
        "    <path\n"
        f"        android:pathData=\"{data}\"\n"
        "        android:fillColor=\"#00000000\"\n"
        "        android:strokeColor=\"#FF000000\"\n"
        "        android:strokeWidth=\"2\"\n"
        "        android:strokeLineCap=\"round\"\n"
        "        android:strokeLineJoin=\"round\" />"
        for data in paths
    )
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f"<!-- Generated by tools/icons/lucide_to_vector.py from lucide-static {LUCIDE_VERSION} "
        f"icon '{lucide_name}'. ISC License: tools/icons/LICENSE-lucide.txt -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp"\n'
        '    android:height="24dp"\n'
        '    android:viewportWidth="24"\n'
        '    android:viewportHeight="24">\n'
        f"{body}\n"
        "</vector>\n"
    )


def read_mapping(mapping_file: Path):
    for line in mapping_file.read_text(encoding="utf-8").splitlines():
        line = line.split("#", 1)[0].strip()
        if not line:
            continue
        resource, lucide_name = (part.strip() for part in line.split("=", 1))
        yield resource, lucide_name


def main(argv):
    if len(argv) != 4:
        print(__doc__)
        return 2
    icons_dir, mapping_file, out_dir = Path(argv[1]), Path(argv[2]), Path(argv[3])
    out_dir.mkdir(parents=True, exist_ok=True)
    count = 0
    for resource, lucide_name in read_mapping(mapping_file):
        svg_file = icons_dir / f"{lucide_name}.svg"
        if not svg_file.is_file():
            raise FileNotFoundError(f"Lucide icon not found: {svg_file}")
        (out_dir / f"{resource}.xml").write_text(convert(svg_file, resource, lucide_name), encoding="utf-8")
        count += 1
    print(f"Generated {count} vector drawables in {out_dir}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
