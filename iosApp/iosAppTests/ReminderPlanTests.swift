import XCTest
@testable import iosApp

/// Covers what Cycluna decides to schedule — dates, wording, and which reminders exist at
/// all. `ReminderManager.plan` is pure, so none of this touches the notification centre.
final class ReminderPlanTests: XCTestCase {

    private var cal = Calendar(identifier: .gregorian)

    /// 1 Aug 2026, the predicted period start used throughout.
    private var period: Date {
        cal.date(from: DateComponents(year: 2026, month: 8, day: 1))!
    }
    private var fertile: Date {
        cal.date(from: DateComponents(year: 2026, month: 8, day: 12))!
    }

    private func settings(
        periodOn: Bool = false, ovulationOn: Bool = false,
        periodLead: Int = 1, ovulationLead: Int = 0, cycleMinute: Int = 9 * 60,
        mood: Bool = false, headache: Bool = false, checkInMinute: Int = 20 * 60,
        discreet: Bool = false, fertility: Bool = true
    ) -> ReminderSettings {
        ReminderSettings(
            periodOn: periodOn, ovulationOn: ovulationOn,
            periodLeadDays: periodLead, ovulationLeadDays: ovulationLead,
            cycleMinute: cycleMinute,
            moodCheckIn: mood, headacheCheckIn: headache, checkInMinute: checkInMinute,
            discreet: discreet, fertility: fertility)
    }

    private func plan(_ s: ReminderSettings) -> [ReminderManager.Planned] {
        ReminderManager.plan(nextPeriod: period, fertileStart: fertile, settings: s, calendar: cal)
    }

    private func day(_ d: Date?) -> DateComponents? {
        d.map { cal.dateComponents([.year, .month, .day], from: $0) }
    }

    // MARK: - Nothing on by default

    func testNothingIsScheduledWhenEveryToggleIsOff() {
        XCTAssertTrue(plan(settings()).isEmpty)
    }

    // MARK: - Lead time

    func testPeriodLeadTimeShiftsTheFireDateBackwards() {
        for lead in [0, 1, 2] {
            let p = plan(settings(periodOn: true, periodLead: lead))
            let heads = try! XCTUnwrap(p.first { $0.id == ReminderManager.periodID })
            let expected = cal.date(byAdding: .day, value: -lead, to: period)!
            XCTAssertEqual(day(heads.date), day(expected), "lead \(lead)")
        }
    }

    func testPeriodCopyMatchesTheChosenLead() {
        XCTAssertEqual(ReminderManager.periodTitle(leadDays: 0), "Your period may start today")
        XCTAssertEqual(ReminderManager.periodTitle(leadDays: 1), "Your period may start tomorrow")
        XCTAssertEqual(ReminderManager.periodTitle(leadDays: 2), "Your period may start in 2 days")
    }

    func testOvulationCopyMatchesTheChosenLead() {
        XCTAssertEqual(ReminderManager.ovulationTitle(leadDays: 0), "Your fertile window opens today")
        XCTAssertEqual(ReminderManager.ovulationTitle(leadDays: 1), "Your fertile window opens tomorrow")
    }

    func testOvulationUsesTheFertileStartNotThePeriodDate() {
        let p = plan(settings(ovulationOn: true, ovulationLead: 1))
        let ov = try! XCTUnwrap(p.first { $0.id == ReminderManager.ovulationID })
        XCTAssertEqual(day(ov.date), day(cal.date(byAdding: .day, value: -1, to: fertile)!))
    }

    // MARK: - Follow-up when a period doesn't arrive

    func testPeriodRemindersAlsoScheduleAFollowUpAfterTheDueDate() {
        let p = plan(settings(periodOn: true))
        let follow = try! XCTUnwrap(p.first { $0.id == ReminderManager.followUpID })
        let expected = cal.date(byAdding: .day, value: ReminderManager.followUpDelayDays, to: period)!
        XCTAssertEqual(day(follow.date), day(expected))
        XCTAssertFalse(follow.repeats, "the follow-up must ask once, not become a daily nag")
    }

    func testNoFollowUpWhenPeriodRemindersAreOff() {
        let p = plan(settings(periodOn: false, ovulationOn: true))
        XCTAssertNil(p.first { $0.id == ReminderManager.followUpID })
    }

    // MARK: - Daily check-ins

    func testBothCheckInsProduceASingleMergedReminder() {
        let p = plan(settings(mood: true, headache: true))
        let checkIns = p.filter { $0.id == ReminderManager.checkInID }
        XCTAssertEqual(checkIns.count, 1, "two pings at the same minute would read as a duplicate")
        XCTAssertEqual(checkIns[0].body, "Take a moment for yourself and note how today felt, head and heart.")
        XCTAssertTrue(checkIns[0].repeats)
        XCTAssertNil(checkIns[0].date, "a daily check-in repeats rather than landing on one date")
    }

    func testCheckInCopyNamesOnlyWhatIsEnabled() {
        XCTAssertEqual(ReminderManager.checkInBody(mood: true, headache: false),
                       "Take a moment for yourself and note how today felt.")
        XCTAssertEqual(ReminderManager.checkInBody(mood: false, headache: true),
                       "Take a moment to note how your head has been today.")
        XCTAssertEqual(ReminderManager.checkInTitle(mood: false, headache: true),
                       "Any headaches today?")
    }

    func testCheckInUsesItsOwnTimeNotTheCycleReminderTime() {
        let p = plan(settings(periodOn: true, cycleMinute: 9 * 60, mood: true, checkInMinute: 21 * 60))
        XCTAssertEqual(p.first { $0.id == ReminderManager.periodID }?.minuteOfDay, 9 * 60)
        XCTAssertEqual(p.first { $0.id == ReminderManager.checkInID }?.minuteOfDay, 21 * 60)
    }

    // MARK: - Missing predictions

    func testCycleRemindersAreSkippedWithoutPredictedDates() {
        let p = ReminderManager.plan(nextPeriod: nil, fertileStart: nil,
                                     settings: settings(periodOn: true, ovulationOn: true, mood: true),
                                     calendar: cal)
        // Only the check-in survives — it doesn't depend on the cycle.
        XCTAssertEqual(p.map(\.id), [ReminderManager.checkInID])
    }

    // MARK: - Fertility insights

    func testFertilityInsightsOffSuppressesTheOvulationReminder() {
        // The stored ovulation toggle keeps its value; nothing fertility-shaped may fire.
        let p = plan(settings(periodOn: true, ovulationOn: true, fertility: false))
        XCTAssertNil(p.first { $0.id == ReminderManager.ovulationID })
        // The period reminders are untouched.
        XCTAssertNotNil(p.first { $0.id == ReminderManager.periodID })
    }

    // MARK: - Discreet mode

    func testDiscreetModeSwapsTheWordingButKeepsScheduleAndIds() {
        let loud = plan(settings(periodOn: true, ovulationOn: true, mood: true))
        let discreet = plan(settings(periodOn: true, ovulationOn: true, mood: true, discreet: true))

        XCTAssertEqual(loud.map(\.id), discreet.map(\.id))
        XCTAssertEqual(loud.map(\.date), discreet.map(\.date))
        XCTAssertEqual(loud.map(\.minuteOfDay), discreet.map(\.minuteOfDay))
        XCTAssertEqual(loud.map(\.repeats), discreet.map(\.repeats))
        for planned in discreet {
            XCTAssertEqual(planned.title, ReminderManager.discreetTitle)
            XCTAssertEqual(planned.body, ReminderManager.discreetBody)
            let visible = "\(planned.title) \(planned.body)".lowercased()
            for word in ["period", "fertile", "ovulat", "mood", "headache", "cycle"] {
                XCTAssertFalse(visible.contains(word), "\"\(word)\" leaks in discreet copy")
            }
        }
    }

    // MARK: - Tap routing

    func testTappingTheCheckInOpensJournalAndCycleRemindersStayOnToday() {
        XCTAssertTrue(ReminderManager.opensJournal(id: ReminderManager.checkInID))
        XCTAssertFalse(ReminderManager.opensJournal(id: ReminderManager.periodID))
        XCTAssertFalse(ReminderManager.opensJournal(id: ReminderManager.followUpID))
        XCTAssertFalse(ReminderManager.opensJournal(id: ReminderManager.ovulationID))
    }
}
