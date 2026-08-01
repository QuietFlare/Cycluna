import SwiftUI

/// Single source of truth for phase display copy + colours. UI copy lives in the native
/// layer (per the architecture — the KMP core stays copy-free), and BOTH Home and Phases
/// read from here so the wording can never drift between screens.
struct PhaseContent: Identifiable {
    let id = UUID()
    let key: String        // matches the core's phase label: "Menstrual", "Follicular", …
    let emoji: String
    let eyebrow: String
    let blurb: String
    let color: Color

    static let all: [PhaseContent] = [
        .init(key: "Menstrual",  emoji: "🔴", eyebrow: "Rest & restore",  blurb: "Hormones at their lowest — rest, restore, reflect.",     color: Theme.phaseMenstrual),
        .init(key: "Follicular", emoji: "🌱", eyebrow: "Rising energy",   blurb: "Oestrogen rising — you're entering your power week.",     color: Theme.phaseFollicular),
        .init(key: "Ovulatory",  emoji: "✨", eyebrow: "Peak & magnetic", blurb: "Peak oestrogen, LH surging — you're magnetic right now.", color: Theme.phaseOvulatory),
        .init(key: "Luteal",     emoji: "🌙", eyebrow: "Slow & tend",     blurb: "Progesterone leading — slow down and tend to yourself.",   color: Theme.phaseLuteal),
    ]

    static func blurb(for phase: String) -> String {
        all.first { $0.key == phase }?.blurb ?? ""
    }

    /// 1-based day range for this phase, given the user's own cycle + period length.
    func dayRange(cycleLength: Int, periodLength: Int) -> ClosedRange<Int> {
        let folEnd = max(periodLength + 1, cycleLength / 2 - 2)
        let ovEnd = cycleLength / 2 + 2
        switch key {
        case "Menstrual":  return 1...max(1, periodLength)
        case "Follicular": return (periodLength + 1)...folEnd
        case "Ovulatory":  return (folEnd + 1)...ovEnd
        default:           return (ovEnd + 1)...cycleLength   // Luteal
        }
    }

    func rangeText(cycleLength: Int, periodLength: Int) -> String {
        let r = dayRange(cycleLength: cycleLength, periodLength: periodLength)
        return r.lowerBound == r.upperBound ? "D\(r.lowerBound)" : "D\(r.lowerBound)–\(r.upperBound)"
    }

    /// The phase's real calendar dates for the current cycle (compact: "Aug 25–28",
    /// or "Aug 30 – Sep 3" across a month boundary).
    func dateRangeText(cycleLength: Int, periodLength: Int, cycleStart: Date) -> String {
        let r = dayRange(cycleLength: cycleLength, periodLength: periodLength)
        let cal = Calendar.current
        let start = cal.date(byAdding: .day, value: r.lowerBound - 1, to: cycleStart) ?? cycleStart
        let end = cal.date(byAdding: .day, value: r.upperBound - 1, to: cycleStart) ?? cycleStart
        let mmmd = DateFormatter(); mmmd.dateFormat = "MMM d"
        if r.lowerBound == r.upperBound { return mmmd.string(from: start) }
        if cal.isDate(start, equalTo: end, toGranularity: .month) {
            let d = DateFormatter(); d.dateFormat = "d"
            return "\(mmmd.string(from: start))–\(d.string(from: end))"
        }
        return "\(mmmd.string(from: start)) – \(mmmd.string(from: end))"
    }
}
