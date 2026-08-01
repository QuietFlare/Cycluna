import Foundation
import Shared

/// Loads the bundled `moon-events.json` once and indexes it by ISO date. Fully offline —
/// the file ships in the app bundle; the shared core (`MoonEvents`) owns the format. These
/// are the special events (eclipses/"blood moons") that can't be derived from phase math;
/// everyday phases come from `CyclunaCore.moonPhaseMarker`.
enum MoonEventCatalog {
    static let byDate: [String: MoonEvent] = {
        guard let url = Bundle.main.url(forResource: "moon-events", withExtension: "json"),
              let text = try? String(contentsOf: url, encoding: .utf8) else { return [:] }
        let events = MoonEvents.shared.parse(text: text)
        return Dictionary(events.map { ($0.date, $0) }, uniquingKeysWith: { first, _ in first })
    }()

    static func event(on iso: String) -> MoonEvent? { byDate[iso] }
}
