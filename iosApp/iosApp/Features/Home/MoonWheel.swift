import SwiftUI

/// Circular cycle ring — SwiftUI port of the web app's MoonWheel SVG.
/// Phase arcs (active one thicker), predicted ovulation/period markers, the
/// current-day dot, and a serif "Day N" center.
struct MoonWheel: View {
    let cycleDay: Int
    let cycleLength: Int
    let phaseLabel: String            // "Menstrual" / "Follicular" / "Ovulatory" / "Luteal"
    var size: CGFloat = 280

    private var segments: [(from: Double, to: Double, name: String, color: Color)] {
        let half = Double(cycleLength / 2)
        let len = Double(cycleLength)
        return [
            (0,        5,        "Menstrual",  Theme.phaseMenstrual),
            (5,        half - 2, "Follicular", Theme.phaseFollicular),
            (half - 2, half + 2, "Ovulatory",  Theme.phaseOvulatory),
            (half + 2, len,      "Luteal",     Theme.phaseLuteal),
        ]
    }

    private var activeColor: Color {
        segments.first { $0.name == phaseLabel }?.color ?? Theme.primary
    }

    var body: some View {
        ZStack {
            Canvas { ctx, sz in
                let r = sz.width / 2 - 20
                let c = CGPoint(x: sz.width / 2, y: sz.height / 2)

                // Center glow in the active phase colour.
                let glowR = r - 30
                ctx.fill(
                    Path(ellipseIn: CGRect(x: c.x - glowR, y: c.y - glowR, width: glowR * 2, height: glowR * 2)),
                    with: .radialGradient(
                        Gradient(colors: [activeColor.opacity(0.28), activeColor.opacity(0.06), activeColor.opacity(0)]),
                        center: c, startRadius: 0, endRadius: glowR
                    )
                )

                // Outer track.
                ctx.stroke(
                    Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)),
                    with: .color(Theme.inkSoft.opacity(0.22)), lineWidth: 1
                )

                // Phase arcs (active thicker + opaque).
                for s in segments {
                    let active = s.name == phaseLabel
                    ctx.stroke(
                        arc(from: s.from, to: s.to, center: c, radius: r),
                        with: .color(s.color.opacity(active ? 1 : 0.35)),
                        style: StrokeStyle(lineWidth: active ? 8 : 4, lineCap: .round)
                    )
                }

                // Predicted ovulation + period markers.
                marker(ctx, day: Double(cycleLength / 2), center: c, radius: r, color: Theme.phaseOvulatory)
                marker(ctx, day: Double(cycleLength),     center: c, radius: r, color: Theme.phaseMenstrual)

                // Current-day dot (accent) with a soft outer ring.
                let p = point(((Double(cycleDay) - 1) / Double(cycleLength)) * 360 - 90, center: c, radius: r)
                ctx.stroke(Path(ellipseIn: CGRect(x: p.x - 14, y: p.y - 14, width: 28, height: 28)),
                           with: .color(Theme.accent.opacity(0.5)), lineWidth: 1)
                ctx.fill(Path(ellipseIn: CGRect(x: p.x - 10.5, y: p.y - 10.5, width: 21, height: 21)),
                         with: .color(Theme.background))
                ctx.fill(Path(ellipseIn: CGRect(x: p.x - 9, y: p.y - 9, width: 18, height: 18)),
                         with: .color(Theme.accent))
            }

            VStack(spacing: 6) {
                Text("Day \(cycleDay)")
                    .font(.system(size: 48, weight: .regular, design: .serif))
                    .foregroundStyle(Theme.ink)
                Text(phaseLabel.uppercased())
                    .font(.caption).tracking(1.5)
                    .foregroundStyle(Theme.inkSoft)
            }
        }
        .frame(width: size, height: size)
        // Canvas is invisible to VoiceOver — expose one meaningful element.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Cycle day \(cycleDay) of \(cycleLength), \(phaseLabel) phase")
    }

    private func point(_ deg: Double, center: CGPoint, radius: CGFloat) -> CGPoint {
        let rad = deg * .pi / 180
        return CGPoint(x: center.x + radius * CGFloat(cos(rad)), y: center.y + radius * CGFloat(sin(rad)))
    }

    private func arc(from: Double, to: Double, center: CGPoint, radius: CGFloat) -> Path {
        var path = Path()
        let steps = max(2, Int((to - from) * 4))
        for i in 0...steps {
            let day = from + (to - from) * Double(i) / Double(steps)
            let pt = point((day / Double(cycleLength)) * 360 - 90, center: center, radius: radius)
            if i == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
        }
        return path
    }

    private func marker(_ ctx: GraphicsContext, day: Double, center: CGPoint, radius: CGFloat, color: Color) {
        let p = point(((day - 1) / Double(cycleLength)) * 360 - 90, center: center, radius: radius)
        let ring = CGRect(x: p.x - 6, y: p.y - 6, width: 12, height: 12)
        ctx.fill(Path(ellipseIn: ring), with: .color(Theme.background))
        ctx.stroke(Path(ellipseIn: ring), with: .color(color), lineWidth: 2)
        ctx.fill(Path(ellipseIn: CGRect(x: p.x - 2.5, y: p.y - 2.5, width: 5, height: 5)), with: .color(color))
    }
}
