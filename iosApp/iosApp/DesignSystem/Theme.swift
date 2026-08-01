import SwiftUI
import UIKit

extension Color {
    init(hex: String) {
        let s = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var v: UInt64 = 0
        Scanner(string: s).scanHexInt64(&v)
        let r = Double((v >> 16) & 0xFF) / 255
        let g = Double((v >> 8) & 0xFF) / 255
        let b = Double(v & 0xFF) / 255
        self.init(.sRGB, red: r, green: g, blue: b, opacity: 1)
    }

    /// Light/dark adaptive color.
    init(light: Color, dark: Color) {
        self = Color(UIColor { tc in
            tc.userInterfaceStyle == .dark ? UIColor(dark) : UIColor(light)
        })
    }
}

/// Brand tokens translated from the web app's design system
/// (cream / mauve / rose / gold, night-sky navy in dark mode).
enum Theme {
    static let background = Color(light: Color(hex: "FAF3EC"), dark: Color(hex: "0F0F2D"))
    static let surface    = Color(light: Color(hex: "FFFDF9"), dark: Color(hex: "181834"))
    static let primary    = Color(light: Color(hex: "6B3FA0"), dark: Color(hex: "B79BE6"))
    static let secondary  = Color(hex: "D4849A")
    static let accent     = Color(hex: "E8C97E")
    static let ink        = Color(light: Color(hex: "2D2D2D"), dark: Color(hex: "F0E9DF"))
    static let inkSoft    = Color(light: Color(hex: "6B6B6B"), dark: Color(hex: "B8B0C4"))
    /// Gold accent tuned for TEXT/icons: deep goldenrod on light cream (readable),
    /// bright gold on the dark night sky. Use this for accent text, not `accent`.
    static let accentText = Color(light: Color(hex: "8A6A1C"), dark: Color(hex: "E8C97E"))

    static let phaseMenstrual  = Color(hex: "D96B6B")
    static let phaseFollicular = Color(hex: "5CB37E")
    static let phaseOvulatory  = Color(hex: "E8B84D")
    static let phaseLuteal     = Color(hex: "8A5FC2")

    static var backgroundGradient: LinearGradient {
        LinearGradient(
            colors: [background, Color(light: Color(hex: "F3E7DA"), dark: Color(hex: "1B1640"))],
            startPoint: .top, endPoint: .bottom
        )
    }
}

extension Font {
    /// Cycluna display serif — Georgia, matching the approved onboarding design.
    /// `relativeTo` makes it scale with the user's Dynamic Type setting (accessibility);
    /// callers additionally scale `size` by screen width for cross-device proportion.
    static func cyclunaSerif(_ size: CGFloat) -> Font {
        .custom("Georgia", size: size, relativeTo: .largeTitle)
    }
}

extension View {
    /// Solid cream card with subtle border + soft mauve shadow (web `Card shadow-soft`).
    func cyclunaCard(padding: CGFloat = 20, radius: CGFloat = 20) -> some View {
        self
            .padding(padding)
            .frame(maxWidth: .infinity)
            .background(Theme.surface, in: RoundedRectangle(cornerRadius: radius))
            .overlay(RoundedRectangle(cornerRadius: radius).stroke(Theme.inkSoft.opacity(0.12)))
            .shadow(color: Theme.primary.opacity(0.10), radius: 16, x: 0, y: 6)
    }
}
