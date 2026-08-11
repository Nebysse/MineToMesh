#!/usr/bin/env python3
"""Deterministic MineToMesh workstation asset processor.

Reproduces the production GUI atlas from the approved green-key source using
explicit crop rectangles stored in tools/workstation-asset-crops.json. Sprites
are packed into a compact 384x120 atlas on a 24-pixel grid with nearest-neighbor
sampling, matching the committed production atlas.

The script refuses to run when the source background contains near-green pixels
outside exact #00FF00, because those require manual cleanup. Fully transparent
sources (already cleaned) pass through unchanged.

Usage:
    pip install -r tools/requirements-assets.txt
    python tools/process-workstation-assets.py --source <design-assets> --output <dir>
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from PIL import Image

GREEN = (0, 255, 0)
CELL = 24
COLUMNS = 16


def key_to_alpha(image: Image.Image) -> Image.Image:
    rgba = image.convert("RGBA")
    pixels = []
    for red, green, blue, alpha in rgba.getdata():
        pixels.append((0, 0, 0, 0) if (red, green, blue) == GREEN
                      else (red, green, blue, 255))
    rgba.putdata(pixels)
    return rgba


def assert_clean_source(image: Image.Image) -> None:
    rgba = image.convert("RGBA")
    near_green = 0
    for red, green, blue, alpha in rgba.getdata():
        if alpha == 0:
            continue
        if green > max(red, blue) and (red, green, blue) != GREEN:
            near_green += 1
    if near_green > 0:
        raise SystemExit(
            f"Source contains {near_green} near-green pixels outside exact #00FF00; "
            "manual cleanup is required before processing.")


def crop_sprite(source: Image.Image, rect: list[int]) -> Image.Image:
    x, y, width, height = rect
    return source.crop((x, y, x + width, y + height)).convert("RGBA")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path,
                        help="design-assets/minetomesh-0.3.0 directory")
    parser.add_argument("--output", required=True, type=Path,
                        help="output directory for production assets")
    parser.add_argument("--crops", type=Path,
                        default=Path(__file__).parent / "workstation-asset-crops.json",
                        help="named integer crop rectangles")
    args = parser.parse_args()

    crops = json.loads(args.crops.read_text(encoding="utf-8"))
    source_image = Image.open(args.source / "gui-greenkey-atlas.png")
    assert_clean_source(source_image)
    gui_atlas = key_to_alpha(source_image)

    out = args.output
    gui_out = out / "assets" / "minetomesh" / "textures" / "gui"
    gui_out.mkdir(parents=True, exist_ok=True)

    gui_sprites = crops["gui"]["sprites"]
    atlas_width, atlas_height = crops["gui"]["atlasSize"]
    atlas = Image.new("RGBA", (atlas_width, atlas_height), (0, 0, 0, 0))
    for index, name in enumerate(sorted(gui_sprites.keys())):
        sprite = crop_sprite(gui_atlas, gui_sprites[name])
        column = index % COLUMNS
        row = index // COLUMNS
        atlas.alpha_composite(sprite, (column * CELL, row * CELL))
    atlas.save(gui_out / "export_workstation.png")

    print("Processed GUI atlas:", atlas.size,
          "sprites:", len(gui_sprites))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
