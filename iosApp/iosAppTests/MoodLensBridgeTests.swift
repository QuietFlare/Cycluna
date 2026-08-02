import XCTest
import Shared
@testable import iosApp

/// Exercises the exact core calls `CycleStore.rebuildMoodPages` makes, across the KMP bridge.
///
/// The shared-core tests already prove the Kotlin is right for this data. If these fail, the
/// fault is in the Swift↔Kotlin call — wrong argument, wrong overload, silently empty result —
/// which is invisible to the Kotlin tests.
final class MoodLensBridgeTests: XCTestCase {

    /// Four moods on consecutive days, all inside one cycle and one lunation.
    private func makeData() -> CycleData {
        CycleData(
            periodStarts: ["2026-07-30"],
            cycleLengthSetting: 28,
            periodLength: 5,
            displayName: "",
            moods: [
                MoodLog(date: "2026-07-30", mood: 1, note: ""),
                MoodLog(date: "2026-07-31", mood: 4, note: ""),
                MoodLog(date: "2026-08-01", mood: 3, note: ""),
                MoodLog(date: "2026-08-02", mood: 4, note: ""),
            ],
            headaches: [],
            journal: []
        )
    }

    func testCycleSpansCrossTheBridge() {
        let spans = MoodInsights.shared.cycleSpans(data: makeData())
        XCTAssertEqual(spans.count, 1)
        XCTAssertEqual(spans.first?.startIso, "2026-07-30")
    }

    func testPhaseLensGetsAllFourPoints() {
        let data = makeData()
        let span = MoodInsights.shared.cycleSpans(data: data).last!
        let pts = MoodInsights.shared.cyclePoints(data: data,
                                                  startIso: span.startIso,
                                                  endIso: span.endIso)
        XCTAssertEqual(pts.count, 4, "phase lens lost points crossing the bridge")
        XCTAssertEqual(pts.map { Int($0.cycleDay) }, [1, 2, 3, 4])
    }

    func testMoonLensGetsAllFourPoints() {
        let data = makeData()
        let span = MoonMoodInsights.shared.lunationSpans(data: data, count: 12).last!
        let pts = MoonMoodInsights.shared.moonPointsInRange(data: data,
                                                           fromIso: span.startIso,
                                                           toIso: span.endIso)
        XCTAssertEqual(pts.count, 4, "moon lens lost points crossing the bridge")
    }

    func testDailyLensGetsAllFourPoints() {
        let pts = MoodInsights.shared.moodsInRange(data: makeData(),
                                                   fromIso: "2026-07-20",
                                                   toIso: "2026-08-02")
        XCTAssertEqual(pts.count, 4)
    }
}
