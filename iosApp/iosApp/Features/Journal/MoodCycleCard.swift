import SwiftUI
import Shared

/// "Mood & your cycle" — Concept B. Plots the current cycle's logged moods over the phase
/// bands, and shows a plain-language insight ONLY when the shared stats are confident.
/// Fills in as you log: empty invite → dots only → line + insight. Nothing is fabricated.
struct MoodCycleCard: View {
    @Environment(CycleStore.self) private var store

    private var points: [MoodPoint] { store.moodCyclePoints }
    private var cycleLength: Int { store.cycleLength }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Mood & your cycle").font(.cyclunaSerif(22)).foregroundStyle(Theme.ink)
                Text("See how you feel through each phase")
                    .font(.subheadline).italic().foregroundStyle(Theme.inkSoft)
            }

            plot
                .frame(height: 150)

            if points.isEmpty {
                Text("Log your mood a few times and your pattern across the cycle appears here.")
                    .font(.callout).foregroundStyle(Theme.inkSoft)
                    .frame(maxWidth: .infinity).multilineTextAlignment(.center).padding(.top, 4)
            } else if points.count < 4 {
                Label("\(points.count) logged this cycle — keep going and we'll connect your trend.",
                      systemImage: "sparkles")
                    .font(.footnote).foregroundStyle(Theme.inkSoft)
            }

            if let insight = store.moodInsight {
                HStack(alignment: .top, spacing: 10) {
                    Text("💡")
                    Text(insightSentence(insight))
                        .font(.footnote).foregroundStyle(Theme.inkSoft)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.top, 2)
            }

            moodLegend
        }
        .cyclunaCard(padding: 18)
    }

    // MARK: - Plot

    private var plot: some View {
        Canvas { ctx, size in
            let padX: CGFloat = 6, padTop: CGFloat = 10, labelH: CGFloat = 16, padBottom: CGFloat = 4
            let w = size.width - padX * 2
            let h = size.height - padTop - labelH - padBottom
            let bandBottom = padTop + h
            func x(_ day: Int) -> CGFloat {
                padX + (CGFloat(day - 1) / CGFloat(max(1, cycleLength - 1))) * w
            }
            func y(_ mood: Int) -> CGFloat {
                padTop + (CGFloat(5 - mood) / 4.0) * h
            }

            // Phase bands + a label centred under each.
            for phase in PhaseContent.all {
                let r = phase.dayRange(cycleLength: cycleLength, periodLength: store.periodLength)
                let x0 = x(r.lowerBound)
                let x1 = x(min(r.upperBound + 1, cycleLength))
                ctx.fill(Path(CGRect(x: x0, y: padTop, width: max(0, x1 - x0), height: h)),
                         with: .color(phase.color.opacity(0.12)))
                ctx.draw(Text(phase.key).font(.system(size: 9)).foregroundStyle(Theme.inkSoft),
                         at: CGPoint(x: (x0 + x1) / 2, y: bandBottom + labelH / 2 + 1))
            }

            // Trend line (only once there are enough points to mean something).
            if points.count >= 4 {
                var line = Path()
                for (i, p) in points.enumerated() {
                    let pt = CGPoint(x: x(Int(p.cycleDay)), y: y(Int(p.mood)))
                    if i == 0 { line.move(to: pt) } else { line.addLine(to: pt) }
                }
                ctx.stroke(line, with: .color(Theme.primary.opacity(0.6)),
                           style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
            }

            // Mood dots.
            for p in points {
                let c = CGPoint(x: x(Int(p.cycleDay)), y: y(Int(p.mood)))
                ctx.fill(Path(ellipseIn: CGRect(x: c.x - 5, y: c.y - 5, width: 10, height: 10)),
                         with: .color(moodColor(Int(p.mood))))
            }
        }
    }

    private var moodLegend: some View {
        HStack(spacing: 10) {
            ForEach(1...5, id: \.self) { v in
                HStack(spacing: 3) {
                    Circle().fill(moodColor(v)).frame(width: 8, height: 8)
                    Text(MoodScale.label(v).lowercased())
                        .font(.system(size: 10)).foregroundStyle(Theme.inkSoft)
                }
            }
        }
        .padding(.top, 2)
    }

    // MARK: - Helpers

    private func moodColor(_ mood: Int) -> Color { MoodScale.color(mood) }

    /// Words the structured insight — copy lives here, not in the shared core.
    private func insightSentence(_ insight: MoodInsight) -> String {
        "You tend to feel brightest around your \(phaseWord(insight.brightest)), and lower during your \(phaseWord(insight.lowest))."
    }

    private func phaseWord(_ phase: Phase) -> String {
        switch phase.label {
        case "Ovulatory":  return "ovulation window"
        case "Follicular": return "follicular phase"
        case "Luteal":     return "luteal phase"
        default:           return "period"   // Menstrual
        }
    }
}
