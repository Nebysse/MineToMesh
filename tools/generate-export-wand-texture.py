from pathlib import Path

from PIL import Image, ImageColor, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/assets/mcgltf/textures/item/export_wand.png"

# Inclusive pixel rectangles. The silhouette reads bottom-left to top-right as a
# calibrated survey rod; orange and blue encode POS1 and POS2.
RECTS = [
    (3, 13, 5, 14, "#151a22"),
    (4, 11, 6, 13, "#3e4854"),
    (5, 8, 6, 11, "#ccd2d7"),
    (6, 6, 7, 8, "#687583"),
    (7, 4, 8, 6, "#d9dee2"),
    (8, 2, 9, 4, "#566371"),
    (9, 1, 10, 2, "#f08a33"),
    (10, 1, 12, 2, "#efefef"),
    (11, 2, 13, 3, "#3c9bec"),
    (9, 5, 11, 5, "#ed741c"),
    (10, 6, 12, 6, "#3488d8"),
    (5, 9, 5, 9, "#f0a25e"),
    (6, 7, 6, 7, "#85c8ff"),
]


def main() -> None:
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    for x0, y0, x1, y1, color in RECTS:
        draw.rectangle((x0, y0, x1, y1), fill=ImageColor.getrgb(color) + (255,))
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUTPUT, optimize=False)


if __name__ == "__main__":
    main()
