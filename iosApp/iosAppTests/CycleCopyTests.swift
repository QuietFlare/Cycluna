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

    func testContextCountsDownToTheFertileWindow() {
        XCTAssertEqual(
            CycleCopy.fertileContext(tracking: .normal, daysLate: 0, daysUntilFertileStart: 4,
                                     daysUntilFertileEnd: 8, daysUntilNextPeriod: 18),
            "Your fertile window opens in 4 days.")
    }

    func testContextUsesSingularFormsOnTheLastDay() {
        // The web app's exact wording — never "in 1 days" / "1 days until".
        XCTAssertEqual(
            CycleCopy.fertileContext(tracking: .normal, daysLate: 0, daysUntilFertileStart: 1,
                                     daysUntilFertileEnd: 5, daysUntilNextPeriod: 15),
            "Your fertile window opens tomorrow.")
        XCTAssertEqual(
            CycleCopy.fertileContext(tracking: .normal, daysLate: 0, daysUntilFertileStart: -9,
                                     daysUntilFertileEnd: -5, daysUntilNextPeriod: 1),
            "1 day until your next period.")
    }

    func testContextRecognisesTheWindowIsOpen() {
        // Start has passed (0 or negative), end has not.
        XCTAssertEqual(
            CycleCopy.fertileContext(tracking: .normal, daysLate: 0, daysUntilFertileStart: 0,
                                     daysUntilFertileEnd: 4, daysUntilNextPeriod: 14),
            "You're in your fertile window.")
        XCTAssertEqual(
            CycleCopy.fertileContext(tracking: .normal, daysLate: 0, daysUntilFertileStart: -2,
                                     daysUntilFertileEnd: 0, daysUntilNextPeriod: 12),
            "You're in your fertile window.",
            "the final day of the window still counts as inside it")
    }

    func testContextFallsBackToTheNextPeriodOnceTheWindowHasPassed() {
        XCTAssertEqual(
            CycleCopy.fertileContext(tracking: .normal, daysLate: 0, daysUntilFertileStart: -9,
                                     daysUntilFertileEnd: -5, daysUntilNextPeriod: 6),
            "6 days until your next period.")
    }

    func testContextStatesLatenessPlainly() {
        XCTAssertEqual(
            CycleCopy.fertileContext(tracking: .late, daysLate: 3, daysUntilFertileStart: 5,
                                     daysUntilFertileEnd: 9, daysUntilNextPeriod: 0),
            "Your period is 3 days late. Log it when it starts.",
            "lateness must win over any fertile-window sentence")
    }

    func testContextAsksForAFreshLogOnceTrackingIsLost() {
        // Plain wording on purpose: this same sentence greets a new user who onboarded with
        // an old date, where anything alarming reads as the app being broken.
        XCTAssertEqual(
            CycleCopy.fertileContext(tracking: .unclear, daysLate: 40, daysUntilFertileStart: 3,
                                     daysUntilFertileEnd: 7, daysUntilNextPeriod: 0),
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
