import XCTest
@testable import iosApp

/// The wording Home shows for a normal, late, or lost cycle. These are the sentences that
/// decide whether the app tells the truth about an overdue period, so each branch is pinned.
final class CycleCopyTests: XCTestCase {

    // MARK: - "Next period" tile

    func testNextPeriodCountsDownWhileOnTrack() {
        XCTAssertEqual(
            CycleCopy.nextPeriodShort(tracking: .normal, daysLate: 0, daysUntilNextPeriod: 14),
            "in 14 days")
    }

    func testNextPeriodSaysTodayRatherThanInZeroDays() {
        XCTAssertEqual(
            CycleCopy.nextPeriodShort(tracking: .normal, daysLate: 0, daysUntilNextPeriod: 0),
            "Today")
    }

    func testNextPeriodSaysTomorrowRatherThanInOneDays() {
        XCTAssertEqual(
            CycleCopy.nextPeriodShort(tracking: .normal, daysLate: 0, daysUntilNextPeriod: 1),
            "Tomorrow")
    }

    func testNextPeriodReportsLatenessInsteadOfACountdown() {
        XCTAssertEqual(
            CycleCopy.nextPeriodShort(tracking: .late, daysLate: 3, daysUntilNextPeriod: 0),
            "3 days late")
    }

    func testLatenessIsSingularOnTheFirstDay() {
        XCTAssertEqual(
            CycleCopy.nextPeriodShort(tracking: .late, daysLate: 1, daysUntilNextPeriod: 0),
            "1 day late")
    }

    func testNextPeriodNamesNoDateOnceTrackingIsLost() {
        XCTAssertEqual(
            CycleCopy.nextPeriodShort(tracking: .unclear, daysLate: 40, daysUntilNextPeriod: 0),
            "Unknown")
    }

    // MARK: - Hero card sentence

    func testContextIsSilentWhileTrackingIsNormal() {
        // Everything it used to say in this state duplicated the fertile/next-period tiles
        // directly above it, so it deliberately says nothing.
        XCTAssertEqual(CycleCopy.fertileContext(tracking: .normal, daysLate: 0), "")
    }

    func testContextStatesLatenessPlainly() {
        XCTAssertEqual(
            CycleCopy.fertileContext(tracking: .late, daysLate: 3),
            "Your period is 3 days late. Log it when it starts.")
    }

    func testContextAsksForAFreshLogOnceTrackingIsLost() {
        // Plain wording on purpose: this same sentence greets a new user who onboarded with
        // an old date, where anything alarming reads as the app being broken.
        XCTAssertEqual(
            CycleCopy.fertileContext(tracking: .unclear, daysLate: 40),
            "It's been a while. Log your period when it starts.")
    }

    // MARK: - Mapping from the shared core

    func testTrackingStateParsesEveryValueTheCoreCanReturn() {
        XCTAssertEqual(TrackingState(rawValue: "normal"), .normal)
        XCTAssertEqual(TrackingState(rawValue: "late"), .late)
        XCTAssertEqual(TrackingState(rawValue: "unclear"), .unclear)
        XCTAssertNil(TrackingState(rawValue: "something-else"))
    }
}
