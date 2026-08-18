#!/usr/bin/env python3
"""Generate the "Tanya" demo dataset used for store screenshots.

The captures under store/play/ are of the real app, so they need real data on the device.
This exists so that data is reproducible: before, the persona lived only as a sentence in
store/play/README.md and had to be re-entered by hand, which meant no two capture sessions
matched.

Dates are relative to --today, so a capture session months from now still lands on Day 13 of
an Ovulatory phase rather than whatever the old absolute dates would have drifted into.

Moods stay inside {3, 4}: enough logs for every chart to draw, too little spread for the
insight guardrails to name a pattern (MoodInsights.MIN_GAP is 0.8 on the 1..5 scale). No
headaches at all. No screenshot should assert a health conclusion about a person who does
not exist.

    python3 tools/make_demo_data.py > /tmp/tanya.json
    adb shell am force-stop net.quietflare.cycluna
    adb push /tmp/tanya.json /data/local/tmp/t.json
    adb shell "run-as net.quietflare.cycluna sh -c 'cat /data/local/tmp/t.json > files/cycle-store.json'"
"""
import argparse
import datetime as dt
import json

CYCLE_LENGTH = 28
DAY_IN_CYCLE = 13          # Ovulatory, and the fertile window is open


def build(today):
    anchor = today - dt.timedelta(days=DAY_IN_CYCLE - 1)
    starts = [anchor - dt.timedelta(days=CYCLE_LENGTH * i) for i in range(5)][::-1]

    moods = []
    day = starts[-3]                       # ~2 cycles of history: enough to page back
    while day <= today:
        n = (day - starts[-3]).days
        # Skip roughly every third day so it reads as real logging, but never skip today —
        # the Journal shot needs "Mood · Good" on the Today card.
        if n % 3 != 2 or day == today:
            moods.append({
                "date": day.isoformat(),
                "mood": 4 if n % 5 in (1, 3) or day == today else 3,
                "note": "",
            })
        day += dt.timedelta(days=1)

    return {
        "periodStarts": [s.isoformat() for s in starts],
        "cycleLengthSetting": CYCLE_LENGTH,
        "periodLength": 5,
        "displayName": "Tanya",
        "moods": moods,
        "headaches": [],
        "journal": [],
    }


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--today", help="ISO date to treat as today (default: actual today)")
    args = p.parse_args()
    today = dt.date.fromisoformat(args.today) if args.today else dt.date.today()
    print(json.dumps(build(today), indent=4))
