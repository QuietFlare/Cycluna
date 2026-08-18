#!/usr/bin/env python3
"""Compose captioned Play-listing screenshots from the raw app captures.

The raw captures under store/play/{phone,tablet}/ are real screenshots of the running app.
They are also what F-Droid shows (fastlane/metadata/.../phoneScreenshots is a copy of the
phone set), and F-Droid's convention is a plain screenshot with no marketing overlay — so
the captions go ONLY into store/play/captioned/, which is the Play upload. Never write this
output back over the raw captures.

Each frame keeps the source dimensions, so Play's aspect-ratio rules are satisfied by
construction. The device status bar is cropped away: an emulator clock and a half-full
battery read as "someone screenshotted their phone", not as a store asset.

Palette, gradient and serif are the ones in tools/render_feature_graphic.py, so the listing
carousel, the feature graphic and the app itself stay one family.

    python3 tools/render_store_screenshots.py
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

CREAM_IN, CREAM_OUT = (255, 251, 246), (242, 231, 220)
INK, INK_SOFT = (45, 45, 45), (107, 107, 107)
SERIF = "/System/Library/Fonts/Supplemental/Georgia.ttf"
SANS = "/System/Library/Fonts/SFNS.ttf"

ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / "store" / "play"
OUT = ROOT / "store" / "play" / "captioned"

# Fraction of the source height occupied by the status bar. Cropped, not covered: painting
# over it would leave a seam where the app's own background meets the caption band.
STATUS_BAR = 0.052

# Headline, then an optional second line. Shot 1 carries the privacy line because it is the
# real differentiator and the first frame is the only one most people ever see.
CAPTIONS = {
    "01-home": ("Where you are today", "No account. No cloud."),
    "02-calendar": ("Your month, logged and predicted", None),
    "03-phases": ("What each phase is doing", None),
    "04-journal-phase": ("Mood patterns across your cycle", None),
    "05-journal-moon": ("…and through the lunar month", None),
    "04-journal": ("Mood patterns across your cycle", None),  # tablet set's journal shot
}


def cream_wash(size):
    """The app's warm background. Computed small and scaled up — a per-pixel loop at
    1080x2340 costs seconds per frame and the gradient is smooth enough to interpolate."""
    w, h = size
    sw, sh = 96, max(1, round(96 * h / w))
    small = Image.new("RGB", (sw, sh), CREAM_OUT)
    px = small.load()
    cx, cy = sw * 0.5, sh * 0.34
    far = max(
        (cx**2 + cy**2) ** 0.5,
        ((sw - cx) ** 2 + cy**2) ** 0.5,
        (cx**2 + (sh - cy) ** 2) ** 0.5,
        ((sw - cx) ** 2 + (sh - cy) ** 2) ** 0.5,
    )
    for y in range(sh):
        for x in range(sw):
            t = min((((x - cx) ** 2 + (y - cy) ** 2) ** 0.5) / far, 1.0)
            px[x, y] = tuple(round(a + (b - a) * t) for a, b in zip(CREAM_IN, CREAM_OUT))
    return small.resize((w, h), Image.BICUBIC)


def rounded(img, radius):
    """Round the capture's corners so it reads as a device, not a pasted rectangle."""
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.width - 1, img.height - 1],
                                          radius=radius, fill=255)
    out = img.convert("RGBA")
    out.putalpha(mask)
    return out


def fit_text(draw, text, font_path, start_size, max_width):
    """Largest size at or below start_size that fits the caption on one line."""
    size = start_size
    while size > 20:
        font = ImageFont.truetype(font_path, size)
        if draw.textlength(text, font=font) <= max_width:
            return font
        size -= 2
    return ImageFont.truetype(font_path, size)


def compose(src_path, out_path, headline, subline):
    shot = Image.open(src_path).convert("RGB")
    W, H = shot.size

    shot = shot.crop((0, round(H * STATUS_BAR), W, H))

    canvas = cream_wash((W, H))
    draw = ImageDraw.Draw(canvas)

    # Type is sized off the SHORT edge, not the width. A tablet frame is landscape, and
    # sizing off 2560px produced a headline that swallowed the band and cropped the capture.
    ref = min(W, H)
    margin = round(W * 0.075)
    pad = round(ref * 0.085)
    text_width = W - 2 * margin

    head_font = fit_text(draw, headline, SERIF, round(ref * 0.062), text_width)
    head_h = head_font.getbbox(headline)[3]
    sub_font = sub_h = None
    if subline:
        sub_font = fit_text(draw, subline, SANS, round(ref * 0.034), text_width)
        sub_h = sub_font.getbbox(subline)[3]

    gap = round(ref * 0.014) if subline else 0
    text_h = head_h + gap + (sub_h or 0)
    # The band is whatever the text needs plus even padding, so portrait and landscape get
    # the same visual breathing room rather than the same fraction of very different heights.
    band = text_h + 2 * pad

    draw.text((W / 2, pad), headline, font=head_font, fill=INK, anchor="ma")
    if subline:
        draw.text((W / 2, pad + head_h + gap), subline, font=sub_font, fill=INK_SOFT,
                  anchor="ma")

    # The capture fills what the caption leaves, keeping its aspect ratio. It is anchored to
    # the bottom edge: a floating card with cream underneath looked like a rendering bug.
    avail_h = H - band
    scale = min((W - 2 * margin) / shot.width, avail_h / shot.height)
    new = (round(shot.width * scale), round(shot.height * scale))
    radius = round(new[0] * 0.045)
    shot = rounded(shot.resize(new, Image.LANCZOS), radius)
    pos = ((W - new[0]) // 2, H - new[1])

    # The capture's own background is the same cream as the canvas, so without a shadow the
    # frame edge disappears and the screenshot reads as a smudge rather than a phone.
    alpha = Image.new("L", (W, H), 0)
    alpha.paste(shot.getchannel("A"), (pos[0], pos[1] + round(H * 0.005)))
    alpha = alpha.filter(ImageFilter.GaussianBlur(round(W * 0.016)))
    canvas.paste(Image.new("RGB", (W, H), (120, 96, 88)), (0, 0),
                 alpha.point(lambda v: v * 46 // 255))
    canvas.paste(shot, pos, shot)

    # Hairline over the shadow: warm, low-contrast, just enough to hold the corner.
    ImageDraw.Draw(canvas).rounded_rectangle(
        [pos[0], pos[1], pos[0] + new[0] - 1, pos[1] + new[1] - 1],
        radius=radius, outline=(214, 198, 188), width=max(1, round(W * 0.0015)))

    out_path.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(out_path, "PNG", optimize=True)
    print(f"wrote {out_path.relative_to(ROOT)} ({canvas.width}x{canvas.height})")


def main():
    made = 0
    for kind in ("phone", "tablet"):
        for src in sorted((RAW / kind).glob("*.png")):
            caption = CAPTIONS.get(src.stem)
            if caption is None:
                print(f"skipped {src.name} — no caption defined", file=sys.stderr)
                continue
            compose(src, OUT / kind / src.name, *caption)
            made += 1
    if made == 0:
        sys.exit("no screenshots rendered — check store/play/{phone,tablet}/")


if __name__ == "__main__":
    main()
