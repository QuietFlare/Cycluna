import SwiftUI

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
}

/// Brand tokens translated from the web app's design system
/// (cream / mauve / rose / gold). The app is light-only — see RootView.
enum Theme {
    static let background = Color(hex: "FAF3EC")
    static let surface    = Color(hex: "FFFDF9")
    static let primary    = Color(hex: "6B3FA0")
    static let secondary  = Color(hex: "D4849A")
    static let accent     = Color(hex: "E8C97E")
    static let ink        = Color(hex: "2D2D2D")
    static let inkSoft    = Color(hex: "6B6B6B")
    /// Gold accent tuned for TEXT/icons: deep goldenrod, readable on cream.
    /// Use this for accent text, not `accent`, which is too pale to read.
    static let accentText = Color(hex: "8A6A1C")

    static let phaseMenstrual  = Color(hex: "D96B6B")
    static let phaseFollicular = Color(hex: "5CB37E")
    static let phaseOvulatory  = Color(hex: "E8B84D")
    static let phaseLuteal     = Color(hex: "8A5FC2")

    static var backgroundGradient: LinearGradient {
        LinearGradient(
            colors: [background, Color(hex: "F3E7DA")],
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
