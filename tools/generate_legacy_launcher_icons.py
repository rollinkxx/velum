from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app/src/main/res"
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

BG_TOP = (11, 15, 21, 255)
BG_BOTTOM = (30, 42, 58, 255)
SILVER_TOP = (247, 248, 250, 255)
SILVER_BOTTOM = (83, 100, 123, 255)
BLUE_TOP = (24, 35, 51, 255)
BLUE_MID = (11, 85, 216, 255)
BLUE_BOTTOM = (94, 155, 255, 255)

def lerp(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))

def vertical_gradient(size, top, bottom, start=0.0, end=1.0):
    image = Image.new("RGBA", (size, size))
    px = image.load()
    for y in range(size):
        t = max(0.0, min(1.0, (y / max(1, size - 1) - start) / max(0.001, end - start)))
        c = lerp(top, bottom, t)
        for x in range(size):
            px[x, y] = c
    return image

def icon(size):
    im = vertical_gradient(size, BG_TOP, BG_BOTTOM)
    s = size / 108.0
    silver_pts = [(22*s,25*s),(37*s,25*s),(54*s,61*s),(60*s,73*s),(56*s,83*s),(49*s,85*s),(43*s,74*s)]
    blue_pts = [(69*s,25*s),(86*s,25*s),(65*s,68*s),(61*s,77*s),(57*s,82*s),(51*s,83*s),(56*s,79*s),(58*s,74*s),(60*s,68*s)]
    silver_mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(silver_mask).polygon(silver_pts, fill=255)
    im.alpha_composite(Image.composite(vertical_gradient(size, SILVER_TOP, SILVER_BOTTOM, 0.22, 0.78), Image.new("RGBA", (size, size)), silver_mask))
    blue_mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(blue_mask).polygon(blue_pts, fill=255)
    blue = vertical_gradient(size, BLUE_TOP, BLUE_BOTTOM, 0.22, 0.78)
    # Electric-blue middle highlight, kept restrained at small densities.
    bp = blue.load()
    for y in range(size):
        t = max(0.0, min(1.0, (y / max(1, size - 1) - 0.38) / 0.28))
        c = lerp(BLUE_TOP, BLUE_MID, t) if t < 0.7 else lerp(BLUE_MID, BLUE_BOTTOM, (t - 0.7) / 0.3)
        for x in range(size):
            bp[x, y] = c
    im.alpha_composite(Image.composite(blue, Image.new("RGBA", (size, size)), blue_mask))
    return im

for density, size in DENSITIES.items():
    directory = OUT / f"mipmap-{density}"
    directory.mkdir(parents=True, exist_ok=True)
    rendered = icon(size)
    rendered.save(directory / "ic_launcher.png", optimize=True)
    rendered.save(directory / "ic_launcher_round.png", optimize=True)
    print(density, size)
