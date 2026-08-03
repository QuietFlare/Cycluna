import SwiftUI

/// A real moon-phase disc: a lit gold sphere with the shaded portion drawn from the true
/// illumination fraction. The terminator is a proper half-ellipse (not a flat symbol), so
/// crescents and gibbous phases read correctly. Waxing lights the right limb, waning the left.
struct MoonDisc: View {
    var illumination: Double        // 0 (new) .. 1 (full)
    var waxing: Bool
    var lit: Color = Color(hex: "EAD59B")
    var shadow: Color = Color(hex: "20152A")
    var glow: Bool = true

    var body: some View {
        Canvas { ctx, size in
            let r = min(size.width, size.height) / 2
            let c = CGPoint(x: size.width / 2, y: size.height / 2)
            let disc = Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r))

            // Soft sphere shading on the lit base.
            ctx.fill(disc, with: .radialGradient(
                Gradient(colors: [Color(hex: "FFF6E0"), lit, Color(hex: "C9A24A")]),
                center: CGPoint(x: c.x - r * 0.28, y: c.y - r * 0.3),
                startRadius: 1, endRadius: r * 1.15))

            // Overlay the shadow region.
            let k = max(0, min(1, illumination))
            ctx.fill(shadowPath(c: c, r: r, k: k, waxing: waxing),
                     with: .color(shadow.opacity(0.9)))
            ctx.stroke(disc, with: .color(Color(hex: "B48C32").opacity(0.35)), lineWidth: 0.75)
        }
        .background(
            glow ? Circle().fill(lit).blur(radius: 8).opacity(0.35) : nil
        )
        .aspectRatio(1, contentMode: .fit)
    }

    /// The unlit region: the dark limb semicircle plus/minus the terminator ellipse.
    private func shadowPath(c: CGPoint, r: CGFloat, k: Double, waxing: Bool) -> Path {
        let kappa: CGFloat = 0.5523
        // Terminator horizontal offset at mid-height; +r at new, -r at full.
        let a = CGFloat(1 - 2 * k) * r * (waxing ? 1 : -1)
        let top = CGPoint(x: c.x, y: c.y - r)

        var p = Path()
        p.move(to: top)
        // Dark limb: the semicircle on the shadow side (left if waxing).
        p.addArc(center: c, radius: r,
                 startAngle: .degrees(-90), endAngle: .degrees(90),
                 clockwise: waxing)
        // Terminator ellipse back to top (two cubic quarter-ellipse segments).
        p.addCurve(to: CGPoint(x: c.x + a, y: c.y),
                   control1: CGPoint(x: c.x + a * kappa, y: c.y + r),
                   control2: CGPoint(x: c.x + a, y: c.y + r * kappa))
        p.addCurve(to: top,
                   control1: CGPoint(x: c.x + a, y: c.y - r * kappa),
                   control2: CGPoint(x: c.x + a * kappa, y: c.y - r))
        p.closeSubpath()
        return p
    }
}
