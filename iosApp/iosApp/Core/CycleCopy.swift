import Foundation

/// How far Cycluna trusts its own prediction — the Swift mirror of the shared core's
/// `CycleTracking`. Named differently from the Kotlin enum on purpose: `CycleStore` imports
/// `Shared`, where `CycleTracking` already exists, and two identical names would be ambiguous.
enum TrackingState: String {
    case normal, late, unclear
}

/// The user-facing sentences derived from cycle state.
///
/// Kept as pure functions of plain values — no store, no disk, no `Date.now` — so the
/// branching can be tested directly. `CycleStore` supplies the numbers; this decides wording.
enum CycleCopy {
    static func lateText(daysLate: Int) -> String {
        daysLate == 1 ? "1 day late" : "\(daysLate) days late"
    }

    /// Value for the "Next period" tile.
    static func nextPeriodShort(tracking: TrackingState, daysLate: Int, daysUntilNextPeriod: Int) -> String {
        switch tracking {
        case .late:
            return lateText(daysLate: daysLate)
        case .unclear:
            // Nothing honest to show: the prediction is too stale to name a date.
            return "Unknown"
        case .normal:
            return daysUntilNextPeriod == 0 ? "Today" : "in \(daysUntilNextPeriod) days"
        }
    }

    /// The sentence under the hero card.
    ///
    /// `daysUntilFertileStart` / `daysUntilFertileEnd` are whole days from today — negative
    /// once the date has passed.
    static func fertileContext(tracking: TrackingState,
                               daysLate: Int,
                               daysUntilFertileStart: Int,
                               daysUntilFertileEnd: Int,
                               daysUntilNextPeriod: Int) -> String {
        switch tracking {
        case .late:
            return "Your period is \(lateText(daysLate: daysLate)). Log it when it starts."
        case .unclear:
            return "We've lost track of your cycle. Log your last period to resume predictions."
        case .normal:
            if daysUntilFertileStart > 0 {
                return "Your fertile window opens in \(daysUntilFertileStart) days."
            }
            if daysUntilFertileEnd >= 0 {
                return "You're in your fertile window."
            }
            return daysUntilNextPeriod == 0
                ? "Your period may start today."
                : "\(daysUntilNextPeriod) days until your next period."
        }
    }
}
