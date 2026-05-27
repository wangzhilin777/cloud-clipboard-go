from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "icon-sources" / "launcher-source.png"
TARGET = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "ic_launcher_foreground_image.png"
CANVAS_SIZE = 256
SCALE = 0.68


def main() -> None:
    source = Image.open(SOURCE).convert("RGBA")
    canvas = Image.new("RGBA", (CANVAS_SIZE, CANVAS_SIZE), (0, 0, 0, 0))
    size = int(CANVAS_SIZE * SCALE)
    resized = source.resize((size, size), Image.LANCZOS)
    offset = ((CANVAS_SIZE - size) // 2, (CANVAS_SIZE - size) // 2)
    canvas.alpha_composite(resized, offset)
    canvas.save(TARGET)
    print(
        {
            "source": str(SOURCE),
            "target": str(TARGET),
            "scale": SCALE,
            "size": size,
            "offset": offset,
        }
    )


if __name__ == "__main__":
    main()
