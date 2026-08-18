# Google Play listing assets

Generated from the running app, not mocked up. Regenerate by staging `cycle-store.json`
with the "Tanya" persona (Day 13, Ovulatory) and screenshotting the emulator.

| Asset | Spec | File |
|---|---|---|
| App icon | 512×512 PNG, no alpha, ≤1 MB | `play-icon-512.png` (the shipping icon, resized) |
| Feature graphic | exactly 1024×500, ≤15 MB, opaque | `feature-graphic-1024x500.png` |
| Phone screenshots | 2–8, each side 320–3840 px | `phone/` — 1080×2340 |
| Tablet screenshots | 2–8, each side 320–3840 px | `tablet/` — 2560×1600 |

The demo data deliberately contains **no headache logs and low mood variance**, so the app's
own guardrails decline to state any pattern. No screenshot asserts a health conclusion about
a person who does not exist — the charts show data, not claims.

The feature graphic is rendered by `tools/render_feature_graphic.py` (Pillow).

## Captioned set — upload these to Play

`captioned/{phone,tablet}/` is built from the raw captures by
`python3 tools/render_store_screenshots.py`: caption band on the cream wash, status bar
cropped, capture set below it. **Those are the files to upload to Play Console**; the raw
captures stay as the source and are never overwritten.

⚠️ `fastlane/metadata/android/en-US/images/phoneScreenshots/` (what F-Droid shows) is a copy
of the **raw** `phone/` set, deliberately WITHOUT captions — F-Droid's convention is a plain
app screenshot, not a marketing frame. The two sets are supposed to differ. Do not "fix" the
drift by copying the captioned files over the fastlane ones.

Caption text lives in `CAPTIONS` at the top of the render script, keyed by file stem; a
capture with no entry there is skipped with a warning rather than silently shipped bare.
