from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SOURCE = RES / "drawable/logo_velum_header.png"
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

# The title logo contains the exact V mark above the wordmark. The source PNG
# has stray transparent-color pixels outside the visible mark, so use the
# documented mark region rather than a global alpha bounding box.
source = Image.open(SOURCE).convert("RGBA")
left, top, right, bottom = 160, 20, 1040, 670
# Add a small transparent safety margin around the exact mark.
pad_x = max(1, int((right - left) * 0.045))
pad_y = max(1, int((bottom - top) * 0.045))
left = max(0, left - pad_x)
top = max(0, top - pad_y)
right = min(source.width, right + pad_x)
bottom = min(source.height, bottom + pad_y)
mark = source.crop((left, top, right, bottom))

# Make the launcher foreground square while preserving the original mark's
# proportions and transparent surroundings. This avoids the rigid hand-drawn V.
def foreground(size: int) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    scale = min((size * 0.78) / mark.width, (size * 0.78) / mark.height)
    resized = mark.resize((round(mark.width * scale), round(mark.height * scale)), Image.Resampling.LANCZOS)
    canvas.alpha_composite(resized, ((size - resized.width) // 2, (size - resized.height) // 2))
    return canvas

for density, size in DENSITIES.items():
    directory = RES / f"mipmap-{density}"
    icon = foreground(size)
    icon.save(directory / "ic_launcher.png", optimize=True)
    icon.save(directory / "ic_launcher_round.png", optimize=True)

# Keep a reusable source crop for visual review and future deterministic rebuilds.
mark.save(RES / "drawable/logo_velum_mark.png", optimize=True)
nodpi = RES / "drawable-nodpi"
nodpi.mkdir(parents=True, exist_ok=True)
foreground(108).save(nodpi / "logo_velum_mark_adaptive.png", optimize=True)

# Android notification icons must be monochrome. Preserve the exact mark alpha
# and replace only its color with opaque white.
notification = foreground(24)
mask = notification.getchannel("A")
white = Image.new("RGBA", notification.size, (255, 255, 255, 0))
white.putalpha(mask)
white.save(nodpi / "logo_velum_mark_notification.png", optimize=True)
adaptive_mask = foreground(108).getchannel("A")
adaptive_white = Image.new("RGBA", (108, 108), (255, 255, 255, 0))
adaptive_white.putalpha(adaptive_mask)
adaptive_white.save(nodpi / "logo_velum_mark_monochrome.png", optimize=True)
print(f"source={SOURCE}")
print(f"mark_bbox=({left},{top},{right},{bottom})")
print(f"mark_size={mark.width}x{mark.height}")
