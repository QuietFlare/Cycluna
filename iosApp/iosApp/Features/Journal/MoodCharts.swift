import SwiftUI
import Shared

/// The three chart bodies behind "Mood patterns". Each takes one prepared page and draws it —
/// no aggregation here, that all lives in the shared core.
///
/// Shared conventions: y is mood 1..5 (bottom to top), a dot per real log, and a connecting
/// line only once there are enough points for the shape to mean something.
private enum Plot {
    /// Wide enough that a dot on the first or last position isn't clipped by the Canvas
    /// edge. At 6pt a day-1 log was drawn half outside the chart and read as missing.
    static let padX: CGFloat = 12
    static let padTop: CGFloat = 12
    static let padBottom: CGFloat = 10
    /// Below this, joining dots implies a trend the data doesn't show.
    static let minPointsForLine = 4

    static func y(_ mood: Double, _ size: CGSize) -> CGFloat {
        let h = size.height - padTop - padBottom
        return padTop + CGFloat((5 - mood) / 4) * h
    }

    /// One mood dot, ringed in the card colour so overlapping dots stay countable —
    /// consecutive days sit only ~11pt apart on the moon lens, closer than a dot is wide.
    static func dot(_ ctx: GraphicsContext, at p: CGPoint, color: Color) {
        let r: CGFloat = 5
        ctx.fill(Path(ellipseIn: CGRect(x: p.x - r - 1.5, y: p.y - r - 1.5,
                                        width: (r + 1.5) * 2, height: (r + 1.5) * 2)),
                 with: .color(Theme.surface))
        ctx.fill(Path(ellipseIn: CGRect(x: p.x - r, y: p.y - r, width: r * 2, height: r * 2)),
                 with: .color(color))
    }

    static func line(_ pts: [CGPoint]) -> Path {
        var p = Path()
        for (i, pt) in pts.enumerated() { i == 0 ? p.move(to: pt) : p.addLine(to: pt) }
        return p
    }
}

// MARK: - Daily

struct DailyMoodChart: View {
    let page: CycleStore.MoodPage

    var body: some View {
        Canvas { ctx, size in
            let w = size.width - Plot.padX * 2
            let span = max(1, page.spanDays - 1)
            func x(_ dayOffset: Int) -> CGFloat {
                Plot.padX + (CGFloat(dayOffset) / CGFloat(span)) * w
            }

            // Faint gridlines at each mood level, so height is readable without an axis.
            for level in 1...5 {
                let ly = Plot.y(Double(level), size)
                var g = Path()
                g.move(to: CGPoint(x: Plot.padX, y: ly))
                g.addLine(to: CGPoint(x: size.width - Plot.padX, y: ly))
                ctx.stroke(g, with: .color(Theme.inkSoft.opacity(level == 3 ? 0.14 : 0.07)),
                           lineWidth: 1)
            }

            let pts = page.daily.compactMap { d -> CGPoint? in
                guard let offset = dayOffset(d.dateIso) else { return nil }
                return CGPoint(x: x(offset), y: Plot.y(Double(d.mood), size))
            }

            if pts.count >= Plot.minPointsForLine {
                ctx.stroke(Plot.line(pts), with: .color(Theme.primary.opacity(0.55)),
                           style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
            }
            for (i, pt) in pts.enumerated() {
                Plot.dot(ctx, at: pt, color: MoodScale.color(Int(page.daily[i].mood)))
            }
        }
        .accessibilityHidden(true)
    }

    private func dayOffset(_ iso: String) -> Int? {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"
        guard let start = f.date(from: page.startIso), let d = f.date(from: iso) else { return nil }
        return Calendar.current.dateComponents([.day], from: start, to: d).day
    }
}

struct DailyMoodAxis: View {
    let page: CycleStore.MoodPage

    /// The phase axis's tick density — enough to place any dot in the month, so the page
    /// needs no separate caption naming its range.
    private static let dateTicks = 5

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .topLeading) {
                ForEach(tickOffsets, id: \.self) { offset in
                    let span = CGFloat(max(1, page.spanDays - 1))
                    let x = geo.size.width * CGFloat(offset) / span
                    Text(dateLabel(offset))
                        .font(.system(size: 8))
                        .foregroundStyle(Theme.inkSoft)
                        .frame(width: 44)
                        .offset(x: min(max(0, x - 22), geo.size.width - 44))
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(page.title), \(Int(page.summary.count)) logs")
    }

    private var tickOffsets: [Int] {
        let span = max(1, page.spanDays - 1)
        let step = max(1, span / (Self.dateTicks - 1))
        return Array(stride(from: 0, through: span, by: step))
    }

    private func dateLabel(_ offset: Int) -> String {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"
        guard let start = f.date(from: page.startIso),
              let d = Calendar.current.date(byAdding: .day, value: offset, to: start) else { return "" }
        let out = DateFormatter(); out.dateFormat = "d MMM"
        return out.string(from: d)
    }
}

// MARK: - Phase

struct PhaseMoodChart: View {
    let page: CycleStore.MoodPage
    let periodLength: Int

    var body: some View {
        Canvas { ctx, size in
            let w = size.width - Plot.padX * 2
            let h = size.height - Plot.padTop - Plot.padBottom
            let length = max(1, page.spanDays)
            func x(_ day: Int) -> CGFloat {
                Plot.padX + (CGFloat(day - 1) / CGFloat(max(1, length - 1))) * w
            }

            // Phase bands sized to THIS cycle's real length, not the predicted one.
            for phase in PhaseContent.all {
                let r = phase.dayRange(cycleLength: length, periodLength: periodLength)
                let x0 = x(r.lowerBound)
                let x1 = x(min(r.upperBound + 1, length))
                ctx.fill(Path(CGRect(x: x0, y: Plot.padTop, width: max(0, x1 - x0), height: h)),
                         with: .color(phase.color.opacity(0.12)))
            }

            let pts = page.cycle.map {
                CGPoint(x: x(Int($0.cycleDay)), y: Plot.y(Double($0.mood), size))
            }
            if pts.count >= Plot.minPointsForLine {
                ctx.stroke(Plot.line(pts), with: .color(Theme.primary.opacity(0.6)),
                           style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
            }
            for (i, pt) in pts.enumerated() {
                Plot.dot(ctx, at: pt, color: MoodScale.color(Int(page.cycle[i].mood)))
            }
        }
        .accessibilityHidden(true)
    }
}

struct PhaseMoodAxis: View {
    let page: CycleStore.MoodPage
    let periodLength: Int

    /// Roughly this many date ticks across the cycle — enough to locate yourself, few enough
    /// that they don't collide on a narrow screen.
    private static let dateTicks = 5

    var body: some View {
        VStack(spacing: 2) {
            GeometryReader { geo in
                ZStack(alignment: .topLeading) {
                    ForEach(PhaseContent.all, id: \.key) { phase in
                        let r = phase.dayRange(cycleLength: max(1, page.spanDays), periodLength: periodLength)
                        let width = geo.size.width * CGFloat(r.count) / CGFloat(max(1, page.spanDays))
                        let offset = geo.size.width * CGFloat(r.lowerBound - 1) / CGFloat(max(1, page.spanDays))
                        // The ovulatory band is only a few days wide, so its label must
                        // shrink rather than wrap — "Ovulator / y" over two lines was
                        // colliding with the row of dates beneath.
                        Text(phase.key)
                            .font(.system(size: 9)).foregroundStyle(Theme.inkSoft)
                            .lineLimit(1)
                            .minimumScaleFactor(0.6)
                            .frame(width: width)
                            .offset(x: offset)
                    }
                }
            }
            .frame(height: 13)

            // Real calendar dates as well as cycle phases — "day 14" is hard to place in a
            // month without them.
            GeometryReader { geo in
                ZStack(alignment: .topLeading) {
                    ForEach(dateTickDays, id: \.self) { day in
                        let offset = geo.size.width * CGFloat(day - 1) / CGFloat(max(1, page.spanDays))
                        Text(dateLabel(dayOffset: day - 1))
                            .font(.system(size: 8))
                            .foregroundStyle(Theme.inkSoft.opacity(0.8))
                            .frame(width: 44)
                            .offset(x: min(max(0, offset - 22), geo.size.width - 44))
                    }
                }
            }
            .frame(height: 12)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(page.title), \(page.spanDays) days, \(Int(page.summary.count)) logs")
    }

    private var dateTickDays: [Int] {
        let span = max(1, page.spanDays)
        let step = max(1, span / (Self.dateTicks - 1))
        return Array(stride(from: 1, through: span, by: step))
    }

    private func dateLabel(dayOffset: Int) -> String {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"
        guard let start = f.date(from: page.startIso),
              let d = Calendar.current.date(byAdding: .day, value: dayOffset, to: start) else { return "" }
        let out = DateFormatter(); out.dateFormat = "d MMM"
        return out.string(from: d)
    }
}

// MARK: - Moon

struct MoonMoodChart: View {
    let page: CycleStore.MoodPage
    private static let bands = 8

    var body: some View {
        Canvas { ctx, size in
            let w = size.width - Plot.padX * 2
            let h = size.height - Plot.padTop - Plot.padBottom
            let bandW = w / CGFloat(Self.bands)

            // Lit like the month itself: darkest at the new moon, brightest at full.
            for i in 0..<Self.bands {
                let centre = (Double(i) + 0.5) / Double(Self.bands)
                let illum = (1 - cos(2 * .pi * centre)) / 2
                let rect = CGRect(x: Plot.padX + CGFloat(i) * bandW, y: Plot.padTop,
                                  width: bandW - 1, height: h)
                ctx.fill(Path(roundedRect: rect, cornerRadius: 4),
                         with: .color(Theme.accent.opacity(0.06 + 0.16 * illum)))
            }

            // Within one page every dot belongs to the same lunation, so lunar order is date
            // order — the line means exactly what it means in the daily view. (Per-band
            // average ticks lived here once and read as floating noise.)
            let pts = page.moon.map {
                CGPoint(x: Plot.padX + CGFloat($0.phaseFraction) * w, y: Plot.y(Double($0.mood), size))
            }
            if pts.count >= Plot.minPointsForLine {
                ctx.stroke(Plot.line(pts), with: .color(Theme.primary.opacity(0.55)),
                           style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
            }
            for (i, pt) in pts.enumerated() {
                Plot.dot(ctx, at: pt, color: MoodScale.color(Int(page.moon[i].mood)))
            }
        }
        .accessibilityHidden(true)
    }
}

struct MoonMoodAxis: View {
    let page: CycleStore.MoodPage
    let store: CycleStore

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(page.moonAverages.enumerated()), id: \.element.bucketKey) { index, band in
                VStack(spacing: 2) {
                    MoonDisc(illumination: store.moonBucketIllumination(band.bucketKey),
                             waxing: store.moonBucketIsWaxing(band.bucketKey),
                             glow: false)
                        .frame(width: 14, height: 14)
                    // Dates only, under the principal phases — the discs already picture
                    // the stages, so naming them was noise, and the dates are what place
                    // the lunation in the month (so the page needs no caption). VoiceOver
                    // still names every bucket in full.
                    if let date = bucketDate(index) {
                        Text(date)
                            .font(.system(size: 8))
                            .foregroundStyle(Theme.inkSoft.opacity(0.8))
                            .lineLimit(1).minimumScaleFactor(0.6)
                    }
                }
                .frame(maxWidth: .infinity)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(description(band))
            }
        }
        .padding(.horizontal, Plot.padX)
    }

    /// The day this bucket begins, for every other bucket (new, first quarter, full, last
    /// quarter). Within one lunation the buckets are date-ordered eighths of its span.
    private func bucketDate(_ index: Int) -> String? {
        guard index % 2 == 0 else { return nil }
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"
        guard let start = f.date(from: page.startIso),
              let d = Calendar.current.date(byAdding: .day, value: index * page.spanDays / 8, to: start)
        else { return nil }
        let out = DateFormatter(); out.dateFormat = "d MMM"
        return out.string(from: d)
    }

    private func description(_ band: MoonMood) -> String {
        let count = Int(band.count)
        guard count > 0 else { return "\(MoonNames.full(band.bucketKey)), no logs" }
        let level = MoodScale.label(max(1, min(5, Int(band.average.rounded()))))
        return "\(MoonNames.full(band.bucketKey)), average mood \(level), \(count) log\(count == 1 ? "" : "s")"
    }
}

/// Bucket slugs → display names. Copy lives in the native layer, never in the shared core.
enum MoonNames {
    static func full(_ key: String) -> String {
        switch key {
        case "new":             return "New moon"
        case "waxing-crescent": return "Waxing crescent"
        case "first-quarter":   return "First quarter"
        case "waxing-gibbous":  return "Waxing gibbous"
        case "full":            return "Full moon"
        case "waning-gibbous":  return "Waning gibbous"
        case "last-quarter":    return "Last quarter"
        default:                return "Waning crescent"
        }
    }

}
