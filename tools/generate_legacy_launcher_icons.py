from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app/src/main/res"
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

BG_TOP = (11, 15, 21, 255)
BG_BOTTOM = (30, 42, 58, 255)
RING_TOP = (243, 246, 250, 255)
RING_BOTTOM = (107, 119, 138, 255)
V_TOP = (245, 247, 250, 255)
V_BOTTOM = (36, 93, 206, 255)

def lerp(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))

def icon(size):
    im = Image.new("RGBA", (size, size))
    px = im.load()
    for y in range(size):
        t = y / max(1, size - 1)
        c = lerp(BG_TOP, BG_BOTTOM, t)
        for x in range(size):
            px[x, y] = c

    cx = cy = 54 * size / 108.0
    outer = 32.4 * size / 108.0
    inner = 29.6 * size / 108.0
    for y in range(size):
        t = y / max(1, size - 1)
        c = lerp(RING_TOP, RING_BOTTOM, t)
        for x in range(size):
            r = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if inner <= r <= outer:
                px[x, y] = c

    s = size / 108.0
    pts = [(40.5*s,42*s),(47.1*s,42*s),(54*s,60*s),(60.9*s,42*s),(67.5*s,42*s),(58.5*s,68*s),(49.5*s,68*s)]
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).polygon(pts, fill=255)
    v = Image.new("RGBA", (size, size))
    vp = v.load()
    for y in range(size):
        t = max(0.0, min(1.0, (y / max(1, size - 1) - 0.38) / 0.25))
        c = lerp(V_TOP, V_BOTTOM, t)
        for x in range(size):
            vp[x, y] = c
    im.alpha_composite(Image.composite(v, Image.new("RGBA", (size, size)), mask))
    return im

for density, size in DENSITIES.items():
    directory = OUT / f"mipmap-{density}"
    directory.mkdir(parents=True, exist_ok=True)
    rendered = icon(size)
    rendered.save(directory / "ic_launcher.png", optimize=True)
    rendered.save(directory / "ic_launcher_round.png", optimize=True)
    print(density, size)
