import SwiftUI

/// App appearance preference (persisted). Defaults to following the system.
enum AppTheme: String, CaseIterable {
    case system, light, dark

    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light:  return .light
        case .dark:   return .dark
        }
    }

    var icon: String {
        switch self {
        case .system: return "circle.lefthalf.filled"
        case .light:  return "sun.max"
        case .dark:   return "moon.stars.fill"
        }
    }

    var next: AppTheme {
        let all = AppTheme.allCases
        return all[(all.firstIndex(of: self)! + 1) % all.count]
    }
}
