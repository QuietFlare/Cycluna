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
