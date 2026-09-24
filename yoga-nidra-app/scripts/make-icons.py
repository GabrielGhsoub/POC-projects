"""Generate PWA / Android icons. Run: python3 scripts/make-icons.py (needs Pillow)."""
from PIL import Image, ImageDraw
import os, math

OUT = os.path.join(os.path.dirname(__file__), "..", "www", "icons")
os.makedirs(OUT, exist_ok=True)

def render(size, maskable):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    pad = 0 if maskable else int(size * 0.06)
    r = int(size * (0 if maskable else 0.22))
    d.rounded_rectangle([pad, pad, size - pad, size - pad], radius=r, fill=(15, 17, 23, 255))
    # crescent moon
    cx, cy = size * 0.52, size * 0.5
    R = size * (0.26 if maskable else 0.3)
    d.ellipse([cx - R, cy - R, cx + R, cy + R], fill=(179, 157, 219, 255))
    ox = cx + R * 0.45
    d.ellipse([ox - R * 0.85, cy - R * 0.85 - R * 0.15, ox + R * 0.85, cy + R * 0.85 - R * 0.15], fill=(15, 17, 23, 255))
    # small stars
    for (fx, fy, s) in [(0.24, 0.3, 0.035), (0.3, 0.68, 0.025), (0.74, 0.26, 0.022)]:
        x, y, rr = size * fx, size * fy, size * s
        d.polygon([(x, y - rr), (x + rr * 0.3, y - rr * 0.3), (x + rr, y), (x + rr * 0.3, y + rr * 0.3),
                   (x, y + rr), (x - rr * 0.3, y + rr * 0.3), (x - rr, y), (x - rr * 0.3, y - rr * 0.3)],
                  fill=(242, 193, 78, 255))
    return img

render(192, False).save(os.path.join(OUT, "icon-192.png"))
render(512, False).save(os.path.join(OUT, "icon-512.png"))
render(512, True).save(os.path.join(OUT, "icon-maskable-512.png"))
print("icons written to", os.path.abspath(OUT))
