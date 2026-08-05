#!/usr/bin/env python3
"""Render the Google Play feature graphic: exactly 1024x500, PNG, opaque.

Play crops this banner differently across its surfaces and can overlay a play button in the
middle, so the mark and wordmark sit left of centre and the right side stays quiet.

The crescent is built the same way the app icon and the Compose `Crescent` are: a disc with a
second disc subtracted, filled with the brand gradient. Colours come from Theme.kt.

    python3 tools/render_feature_graphic.py store/play/feature-graphic-1024x500.png
"""
import sys
from PIL import Image, ImageDraw, ImageFont

W, H = 1024, 500
CREAM_IN, CREAM_OUT = (255, 251, 246), (242, 231, 220)
GRAD = [(140, 107, 196), (107, 63, 160), (212, 132, 154)]   # 8C6BC4 -> 6B3FA0 -> D4849A
INK, INK_SOFT = (45, 45, 45), (107, 107, 107)
SERIF = "/System/Library/Fonts/Supplemental/Georgia.ttf"

SS = 4  # supersample, then downscale — keeps the crescent's edges clean


def radial_cream(size):
    """The app's background wash: warm centre fading outward."""
    w, h = size
    img = Image.new("RGB", (w, h), CREAM_OUT)
    px = img.load()
    cx, cy = w * 0.31, h * 0.60
    far = max((cx * cx + cy * cy) ** 0.5, ((w - cx) ** 2 + (h - cy) ** 2) ** 0.5)
    for y in range(h):
        for x in range(w):
            t = min((((x - cx) ** 2 + (y - cy) ** 2) ** 0.5) / far, 1.0)
            px[x, y] = tuple(round(a + (b - a) * t) for a, b in zip(CREAM_IN, CREAM_OUT))
    return img


def diagonal_gradient(size, stops):
    """Three-stop gradient running top-left to bottom-right, as in the icon."""
    w, h = size
    img = Image.new("RGB", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = (x / max(w - 1, 1) + y / max(h - 1, 1)) / 2
            if t < 0.45:
                a, b, u = stops[0], stops[1], t / 0.45
            else:
                a, b, u = stops[1], stops[2], (t - 0.45) / 0.55
            px[x, y] = tuple(round(p + (q - p) * u) for p, q in zip(a, b))
    return img


def crescent_mask(size, cx, cy, r):
    """Outer disc minus a disc offset up and to the right — the Cycluna mark."""
    mask = Image.new("L", size, 0)
    d = ImageDraw.Draw(mask)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=255)
    cut = r * 0.806
    ox, oy = cx + r * 0.355 * 2, cy - r * 0.274 * 2
    d.ellipse([ox - cut, oy - cut, ox + cut, oy + cut], fill=0)
    return mask


def main(out_path):
    big = (W * SS, H * SS)
    canvas = radial_cream((W, H)).resize(big, Image.BICUBIC)

    # Crescent, left of centre. The gradient is generated across the crescent's OWN bounding
    # box, not the whole banner — spanning it over 1024px meant the mark only sampled the
    # first third and came out flat purple, losing the rose end that the app icon has.
    cx, cy, r = 210 * SS, 250 * SS, 138 * SS
    box = (int(cx - r), int(cy - r), int(cx + r), int(cy + r))
    grad = Image.new("RGB", big, GRAD[0])
    grad.paste(diagonal_gradient((int(r * 2), int(r * 2)), GRAD), (box[0], box[1]))
    canvas.paste(grad, (0, 0), crescent_mask(big, cx, cy, r))

    img = canvas.resize((W, H), Image.LANCZOS)
    draw = ImageDraw.Draw(img)

    # A few sparse stars — dense fields turn to grain once Play scales the banner down.
    for x, y, rr, alpha in [(770, 405, 3.0, 90), (884, 148, 2.2, 70), (668, 92, 2.6, 60)]:
        star = Image.new("RGBA", (int(rr * 4), int(rr * 4)), (0, 0, 0, 0))
        ImageDraw.Draw(star).ellipse([rr, rr, rr * 3, rr * 3], fill=GRAD[0] + (alpha,))
        img.paste(star, (int(x - rr * 2), int(y - rr * 2)), star)

    draw.text((408, 186), "Cycluna", font=ImageFont.truetype(SERIF, 104), fill=INK)
    draw.text((412, 310), "Your rhythm, in tune with the moon",
              font=ImageFont.truetype(SERIF, 31), fill=INK_SOFT)

    img.save(out_path, "PNG", optimize=True)
    print(f"wrote {out_path} ({img.width}x{img.height})")


if __name__ == "__main__":
    main(sys.argv[1])
