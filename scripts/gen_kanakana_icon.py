# Regenerates the launcher icons from docs/branding/KanaKanaIcon.png. Run: python scripts/gen_kanakana_icon.py
from PIL import Image, ImageDraw
import os
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
res = os.path.join(root, "app", "src", "main", "res")
src = Image.open(os.path.join(root, "docs", "branding", "KanaKanaIcon.png")).convert("RGBA")

# Adaptive foreground art (shown at 84dp of the 108dp canvas, see ic_launcher_kanakana_fg.xml).
art = src.resize((768, 768), Image.LANCZOS)
os.makedirs(os.path.join(res, "drawable-nodpi"), exist_ok=True)
art.save(os.path.join(res, "drawable-nodpi", "kanakana_icon_art.png"), optimize=True)

# Legacy icons for API < 26: rounded square and circle.
sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
def masked(size, shape):
    big = src.resize((size * 4, size * 4), Image.LANCZOS)
    mask = Image.new("L", big.size, 0)
    d = ImageDraw.Draw(mask)
    if shape == "round":
        d.ellipse((0, 0, big.size[0] - 1, big.size[1] - 1), fill=255)
    else:
        d.rounded_rectangle((0, 0, big.size[0] - 1, big.size[1] - 1), radius=int(big.size[0] * 0.2), fill=255)
    big.putalpha(mask)
    return big.resize((size, size), Image.LANCZOS)
for dpi, px in sizes.items():
    for name, shape in (("ic_launcher", "square"), ("ic_launcher_round", "round")):
        masked(px, shape).save(os.path.join(res, "mipmap-" + dpi, name + ".png"), optimize=True)
print("done")
