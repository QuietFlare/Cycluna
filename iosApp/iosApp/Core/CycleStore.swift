import SwiftUI
import Shared

/// SwiftUI-facing adapter over the KMP `CycleData`. The shared core owns the state model,
/// the persistence/export FORMATS, and the period-logging RULES — this class only holds the
/// observable copy, bridges to SwiftUI bindings, and does the platform-specific bits:
/// debounced/off-main file writes with iOS file protection, and the share-sheet export file.
///
/// Everything lives on-device: no accounts, no server. Android will wrap the same `CycleData`
/// in its own ViewModel, reusing this exact logic.
@Observable
final class CycleStore {
    /// Canonical state, straight from the shared core. Any change schedules a save and
    /// refreshes the derived aggregates below.
    private var data: CycleData { didSet { save(); refreshDerived() } }

    private let core = CyclunaCore.shared

    init() {
        data = Self.load() ?? CycleData.companion.EMPTY
        // `didSet` does not fire for assignments inside `init`, so prime the caches here.
        refreshDerived()
    }

    // MARK: - Derived aggregates (computed once per data change, not per render)
    //
    // These each walk the whole log history across the KMP bridge, parsing a date per entry.
    // As computed properties they ran on every SwiftUI body evaluation. `data`'s `didSet` is
    // the single choke point through which state changes, so it is the only place they need
    // to be invalidated — do not add a cache here without refreshing it there.

    private(set) var moodCyclePoints: [MoodPoint] = []
    private(set) var moodInsight: MoodInsight?
    private(set) var headacheInsight: HeadacheInsight?
    private(set) var moonMoodPoints: [MoonMoodPoint] = []
    private(set) var moonMoodAverages: [MoonMood] = []
    private(set) var moonMoodInsight: MoonMoodInsight?
    /// Whether there's enough spread to say anything at all — including "steady".
    private(set) var moonMoodReady = false
    private(set) var cycleMoonAligned = false

    private func refreshDerived() {
        moodCyclePoints = MoodInsights.shared.currentCyclePoints(data: data)
        moodInsight = MoodInsights.shared.insight(data: data)
        headacheInsight = HeadacheInsights.shared.insight(data: data)
        moonMoodPoints = MoonMoodInsights.shared.moonPoints(data: data)
        moonMoodAverages = MoonMoodInsights.shared.moonAverages(data: data)
        moonMoodInsight = MoonMoodInsights.shared.moonInsight(data: data)
        moonMoodReady = MoonMoodInsights.shared.hasEnoughForClaim(data: data)
        cycleMoonAligned = MoonMoodInsights.shared.cycleMoonAligned(data: data)
        rebuildDayIndex()
        rebuildMoodPages()
    }

    // MARK: - Mood patterns: lenses and pageable history
    //
    // Each lens pages by a different unit — days, cycles, lunations — so pages are rebuilt
    // whenever the lens or the data changes, never per render. Page state lives here rather
    // than in the view precisely so it shares that one invalidation point.

    enum MoodLens: String, CaseIterable { case daily, phase, moon }

    /// How far back each lens offers. Cycles are bounded by real logged starts; the other two
    /// are capped for sanity, not by knowledge.
    private static let dailyPageDays = 14
    private static let dailyPageCount = 12
    private static let lunationPageCount = 12

    var moodLens: MoodLens = .phase {
        didSet { if oldValue != moodLens { rebuildMoodPages() } }
    }

    /// Index into `moodPages`; the last page is the present.
    var moodPageIndex = 0

    private(set) var moodPages: [MoodPage] = []

    var isOnCurrentMoodPage: Bool { moodPageIndex >= moodPages.count - 1 }

    /// Identifiable only — deliberately NOT Equatable.
    ///
    /// It used to declare `==` as "same id", which is true of a page's *identity* but false
    /// of its contents: a page keeps its id (the span's start date) while the logs inside it
    /// change. SwiftUI compares a view's inputs to decide whether to re-render, so it read
    /// "equal" and skipped redrawing the chart — the new points only appeared after the view
    /// was rebuilt from scratch, i.e. after relaunching the app.
    struct MoodPage: Identifiable {
        let id: String              // startIso — stable across rebuilds
        let startIso: String
        let endIso: String          // exclusive
        let title: String
        let spanDays: Int
        var daily: [DailyMood] = []
        var cycle: [MoodPoint] = []
        var moon: [MoonMoodPoint] = []
        var moonAverages: [MoonMood] = []
        var insight: MoodInsight?
        var summary: MoodSummary = MoodSummary(count: 0, average: 0)
    }

    private func rebuildMoodPages() {
        let mi = MoodInsights.shared
        var pages: [MoodPage] = []

        switch moodLens {
        case .daily:
            let cal = Calendar.current
            let today = cal.startOfDay(for: .now)
            for back in stride(from: Self.dailyPageCount - 1, through: 0, by: -1) {
                let end = cal.date(byAdding: .day, value: -back * Self.dailyPageDays, to: today)!
                let start = cal.date(byAdding: .day, value: -(Self.dailyPageDays - 1), to: end)!
                let s = iso(start), e = iso(end)
                var page = MoodPage(id: s, startIso: s, endIso: iso(cal.date(byAdding: .day, value: 1, to: end)!),
                                    title: "\(fmt(start, "d MMM")) – \(fmt(end, "d MMM"))",
                                    spanDays: Self.dailyPageDays)
                page.daily = mi.moodsInRange(data: data, fromIso: s, toIso: e)
                page.summary = mi.summaryForRange(data: data, fromIso: s, toIso: e)
                page.insight = mi.insightForRange(data: data, fromIso: s, toIso: e)
                pages.append(page)
            }

        case .phase:
            for span in mi.cycleSpans(data: data) {
                // Spans are half-open [start, end) but the summary/insight range is inclusive,
                // so the last day has to be stepped back — otherwise a log on a cycle boundary
                // counts in both the cycle that ends there and the one that begins.
                let last = dayBefore(span.endIso)
                var page = MoodPage(id: span.startIso, startIso: span.startIso, endIso: span.endIso,
                                    title: "Cycle of \(pretty(span.startIso))",
                                    spanDays: Int(span.length))
                page.cycle = mi.cyclePoints(data: data, startIso: span.startIso, endIso: span.endIso)
                page.summary = mi.summaryForRange(data: data, fromIso: span.startIso, toIso: last)
                page.insight = mi.insightForRange(data: data, fromIso: span.startIso, toIso: last)
                pages.append(page)
            }

        case .moon:
            let mm = MoonMoodInsights.shared
            for span in mm.lunationSpans(data: data, count: Int32(Self.lunationPageCount)) {
                var page = MoodPage(id: span.startIso, startIso: span.startIso, endIso: span.endIso,
                                    title: "Moon from \(pretty(span.startIso))",
                                    spanDays: Int(span.length))
                page.moon = mm.moonPointsInRange(data: data, fromIso: span.startIso, toIso: span.endIso)
                page.moonAverages = mm.moonAveragesInRange(data: data, fromIso: span.startIso, toIso: span.endIso)
                // Half-open span, inclusive summary range — see the phase case above.
                page.summary = mi.summaryForRange(data: data, fromIso: span.startIso,
                                                  toIso: dayBefore(span.endIso))
                pages.append(page)
            }
        }

        moodPages = pages
        // Land on the present, and never leave the index dangling past a shorter list.
        moodPageIndex = max(0, pages.count - 1)
    }

    /// The day before an exclusive end date, for APIs whose range is inclusive.
    private func dayBefore(_ isoDate: String) -> String {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; f.timeZone = .current
        guard let d = f.date(from: isoDate),
              let prev = Calendar.current.date(byAdding: .day, value: -1, to: d) else { return isoDate }
        return iso(prev)
    }

    private func pretty(_ iso: String) -> String {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"
        guard let d = f.date(from: iso) else { return iso }
        return fmt(d, "d MMM")
    }

    /// The eight moon buckets in synodic order — stable slugs from the core.
    let moonBucketOrder: [String] = CyclunaCore.shared.moonBucketOrder()

    func moonBucketIllumination(_ key: String) -> Double { core.moonBucketIllumination(bucketKey: key) }
    func moonBucketIsWaxing(_ key: String) -> Bool { core.moonBucketIsWaxing(bucketKey: key) }

    // MARK: - First-run gate

    /// True once the user has logged a real period. Until then the app shows only Welcome.
    var hasLoggedPeriod: Bool { data.hasLoggedPeriod }

    // MARK: - Settings (SwiftUI-bindable)

    var cycleLengthSetting: Int {
        get { Int(data.cycleLengthSetting) }
        set { data = data.withCycleLength(days: Int32(newValue)) }
    }
    var periodLength: Int {
        get { Int(data.periodLength) }
        set { data = data.withPeriodLength(days: Int32(newValue)) }
    }
    var displayName: String {
        get { data.displayName }
        set { data = data.withDisplayName(name: newValue) }
    }

    // MARK: - History & anchor

    /// Logged period start dates (for the calendar).
    var periodStarts: [Date] { data.periodStarts.map { parseISO($0) } }

    /// Most recent period start (the current cycle anchor). Settable — edits the latest entry.
    var lastPeriodStart: Date {
        get { data.lastPeriodStartIso.map { parseISO($0) } ?? .now }
        set { data = data.withLastPeriodStart(iso: iso(newValue)) }
    }

    /// Log a new period start — appends to history via the shared rule (dedup + sorted).
    func startPeriod(on date: Date = .now) {
        data = data.logPeriod(iso: iso(Calendar.current.startOfDay(for: date)))
    }

    /// Finish onboarding: set the chosen cycle length and log the user's ACTUAL selected
    /// last-period date as truth (it's an explicitly logged period → shown solid on the
    /// calendar). Handling an old date is done at read-time by the core, which rolls the
    /// anchor into the current cycle for "today" values without fabricating a stored date.
    func completeOnboarding(lastPeriod date: Date, cycleLength length: Int, periodLength pLen: Int) {
        data = data.withCycleLength(days: Int32(length))
                   .withPeriodLength(days: Int32(pLen))
                   .logPeriod(iso: iso(Calendar.current.startOfDay(for: date)))
    }

    // MARK: - Bridging helpers

    private func iso(_ d: Date) -> String { fmt(d, "yyyy-MM-dd") }
    private var startISO: String { data.lastPeriodStartIso ?? iso(.now) }
    private var periodsCsv: String { data.periodStartsCsv }

    private func parseISO(_ s: String) -> Date {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; f.timeZone = .current
        return f.date(from: s) ?? .now
    }
    private func fmt(_ d: Date, _ pattern: String) -> String {
        let f = DateFormatter(); f.dateFormat = pattern; f.timeZone = .current
        return f.string(from: d)
    }
    private func daysBetween(_ a: Date, _ b: Date) -> Int {
        let cal = Calendar.current
        return cal.dateComponents([.day], from: cal.startOfDay(for: a), to: cal.startOfDay(for: b)).day ?? 0
    }

    /// The current cycle's start, rolled forward from the logged anchor (for the cycles
    /// list + predictions). Falls back to today when nothing is logged.
    var currentCycleStart: Date {
        guard let raw = data.lastPeriodStartIso else { return Calendar.current.startOfDay(for: .now) }
        return parseISO(core.mostRecentPeriodStartIso(selectedIso: raw, cycleLength: Int32(cycleLength)))
    }

    /// Effective cycle length — recomputed from recent history when available.
    var cycleLength: Int {
        Int(core.predictedCycleLength(periodStartsCsv: periodsCsv, cycleLengthFallback: Int32(cycleLengthSetting)))
    }

    // MARK: - Derived cycle values

    var phaseLabel: String { core.cyclePhaseLabel(lastPeriodStartIso: startISO, cycleLength: Int32(cycleLength), periodLength: Int32(periodLength)) }
    var phaseEmoji: String { core.cyclePhaseEmoji(lastPeriodStartIso: startISO, cycleLength: Int32(cycleLength), periodLength: Int32(periodLength)) }
    var cycleDay: Int { Int(core.cycleDay(lastPeriodStartIso: startISO, cycleLength: Int32(cycleLength), periodLength: Int32(periodLength))) }
    var daysUntilNextPeriod: Int { Int(core.daysUntilNextPeriod(lastPeriodStartIso: startISO, cycleLength: Int32(cycleLength), periodLength: Int32(periodLength))) }

    // Per-date (calendar)
    func linearCycleDay(_ dateIso: String) -> Int {
        Int(core.projectedCycleDay(lastPeriodStartIso: startISO, cycleLength: Int32(cycleLength), dateIso: dateIso))
    }
    func dayMarker(_ dateIso: String) -> String {
        core.dayMarker(periodStartsCsv: periodsCsv, cycleLength: Int32(cycleLength), periodLength: Int32(periodLength), dateIso: dateIso)
    }
    func phaseForDate(_ dateIso: String) -> String {
        core.phaseLabelForDate(lastPeriodStartIso: startISO, cycleLength: Int32(cycleLength), periodLength: Int32(periodLength), dateIso: dateIso)
    }

    /// On-device principal moon phase for a date: "", "new", "first-quarter", "full",
    /// "last-quarter", "blue-moon", "black-moon". Special events (eclipses) come from
    /// `MoonEventCatalog`, not this.
    func moonPhaseMarker(_ dateIso: String) -> String { core.moonPhaseMarker(dateIso: dateIso) }

    // MARK: - Dates & fertility

    var todayLong: String { fmt(.now, "EEEE, d MMMM") }
    var fertileStartDate: Date { parseISO(core.fertileStartIso(lastPeriodStartIso: startISO, cycleLength: Int32(cycleLength))) }
    var fertileEndDate: Date { parseISO(core.fertileEndIso(lastPeriodStartIso: startISO, cycleLength: Int32(cycleLength))) }
    var nextPeriodDate: Date { parseISO(core.nextPeriodIso(lastPeriodStartIso: startISO, cycleLength: Int32(cycleLength))) }
    var fertileWindowText: String {
        let s = fertileStartDate, e = fertileEndDate
        // Compact so it fits the tile: "Aug 12–16" within a month, else "Aug 30 – Sep 3".
        if Calendar.current.isDate(s, equalTo: e, toGranularity: .month) {
            return "\(fmt(s, "MMM d"))–\(fmt(e, "d"))"
        }
        return "\(fmt(s, "MMM d")) – \(fmt(e, "MMM d"))"
    }
    // MARK: - Late / missed periods

    /// How far the core trusts its own prediction (see `CycleTracking` in the shared core).
    var tracking: TrackingState {
        TrackingState(rawValue: core.trackingState(lastPeriodStartIso: startISO,
                                                   cycleLength: Int32(cycleLength),
                                                   periodLength: Int32(periodLength))) ?? .normal
    }

    /// Days past the predicted start with nothing logged; 0 when not overdue.
    var daysLate: Int {
        Int(core.daysLate(lastPeriodStartIso: startISO, cycleLength: Int32(cycleLength),
                          periodLength: Int32(periodLength)))
    }

    var nextPeriodShort: String {
        CycleCopy.nextPeriodShort(tracking: tracking, daysLate: daysLate,
                                  daysUntilNextPeriod: daysUntilNextPeriod)
    }

    /// Fertile-window predictions are only meaningful while the cycle is on track. Once a
    /// period is overdue, ovulation has either not happened on schedule or happened late —
    /// either way the next window can't be dated until the period actually starts.
    var showsFertileWindow: Bool { tracking == .normal }

    var fertileContext: String {
        CycleCopy.fertileContext(
            tracking: tracking,
            daysLate: daysLate,
            daysUntilFertileStart: daysBetween(.now, fertileStartDate),
            daysUntilFertileEnd: daysBetween(.now, fertileEndDate),
            daysUntilNextPeriod: daysUntilNextPeriod)
    }

    // MARK: - Moon

    var moonSymbol: String { core.todayMoonSymbol() }
    var moonLabel: String { core.todayMoonLabel() }
    var moonIllumination: Int { Int(core.todayMoonIlluminationPercent()) }

    // Per-date moon values (for the "moon this week" strip).
    func moonIllumination(on dateIso: String) -> Double { core.moonIlluminationForDate(dateIso: dateIso) }
    func moonWaxing(on dateIso: String) -> Bool { core.moonIsWaxingForDate(dateIso: dateIso) }
    func moonLabel(on dateIso: String) -> String { core.moonLabelForDate(dateIso: dateIso) }

    // Cycle ↔ moon alignment (the signature insight).
    var lastPeriodMoonLabel: String {
        guard let last = data.lastPeriodStartIso else { return "" }
        return core.moonLabelForDate(dateIso: last)
    }
    var daysUntilNextFullMoon: Int { Int(core.daysUntilNextFullMoon(fromIso: iso(.now))) }
    var nextFullMoonDate: Date { parseISO(core.nextFullMoonIso(fromIso: iso(.now))) }
    var phaseAtNextFullMoon: String {
        core.phaseLabelForDate(lastPeriodStartIso: startISO, cycleLength: Int32(cycleLength),
                               periodLength: Int32(periodLength), dateIso: core.nextFullMoonIso(fromIso: iso(.now)))
    }

    // MARK: - Phase copy (from the web app's PHASES)

    // Single source: the same phase copy Phases shows, so Home can't drift from it.
    var hormoneHighlight: String { PhaseContent.blurb(for: phaseLabel) }

    // MARK: - Persistence (on-device only)
    //
    // The FORMAT lives in the shared core (`CyclePersistence`). This layer only decides
    // WHEN to write and applies iOS file protection — both platform concerns.

    private static let fileURL: URL = {
        let dir = FileManager.default
            .urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("cycle-store.json")
    }()

    private static func load() -> CycleData? {
        guard let text = try? String(contentsOf: fileURL, encoding: .utf8) else { return nil }
        return CyclePersistence.shared.decode(text: text)
    }

    /// Serial queue so writes never touch the main thread and never overlap.
    private static let ioQueue = DispatchQueue(label: "net.quietflare.cycluna.cyclestore.io", qos: .utility)
    private var pendingSave: DispatchWorkItem?

    /// Debounced save. Bindings that fire many times in quick succession — a name text
    /// field being typed into, a stepper held down — collapse into a SINGLE disk write
    /// ~0.4s after edits settle. Never one write per keystroke. Runs off the main thread.
    private func save() {
        pendingSave?.cancel()
        let text = CyclePersistence.shared.encode(data: data)
        let work = DispatchWorkItem { Self.write(text) }
        pendingSave = work
        Self.ioQueue.asyncAfter(deadline: .now() + 0.4, execute: work)
    }

    /// Force any pending change to disk immediately. Call when the app leaves the
    /// foreground so a debounced write is never lost to suspension. Synchronous by design.
    func flush() {
        pendingSave?.cancel()
        pendingSave = nil
        Self.write(CyclePersistence.shared.encode(data: data))
    }

    private static func write(_ text: String) {
        guard let bytes = text.data(using: .utf8) else { return }
        // `.completeFileProtection` keeps the file encrypted at rest while the device is
        // locked — appropriate for health data. `.atomic` avoids partial writes.
        try? bytes.write(to: fileURL, options: [.atomic, .completeFileProtection])
    }

    // MARK: - Data portability & deletion (on-device, user-initiated)

    /// Writes the shared-core export (`cycluna.export.v1`) to a temporary file for the
    /// share sheet; returns its URL. The data stays on-device until the user picks a
    /// destination — that is the one moment it can leave the phone.
    func exportFileURL() -> URL? {
        let text = CyclePersistence.shared.exportJson(
            data: data,
            exportedAtIso: ISO8601DateFormatter().string(from: .now)
        )
        guard let bytes = text.data(using: .utf8) else { return nil }
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("cycluna-export-\(iso(.now)).json")
        // Even the transient share file gets complete protection (it holds health data);
        // MeView deletes it once the share sheet closes.
        return (try? bytes.write(to: url, options: [.atomic, .completeFileProtection])) == nil ? nil : url
    }

    // MARK: - Daily logs (mood / headache / journal) — on-device only

    /// ISO day string (start of day) for a Date.
    func isoDay(_ date: Date) -> String { iso(Calendar.current.startOfDay(for: date)) }

    // Day lookups are indexed rather than scanned. A month grid asks about 42 days at a
    // time, several times each; scanning the whole history per question is ~250 passes over
    // every log to draw one calendar.
    private(set) var moodByDay: [String: MoodLog] = [:]
    private(set) var headacheDays: Set<String> = []
    private(set) var noteDays: Set<String> = []

    private func rebuildDayIndex() {
        moodByDay = Dictionary(data.moods.map { ($0.date, $0) }, uniquingKeysWith: { _, latest in latest })
        // Headaches are timestamped `yyyy-MM-dd'T'HH:mm`; the day is the leading 10 characters.
        headacheDays = Set(data.headaches.map { String($0.at.prefix(10)) })
        noteDays = Set(data.journal.map { $0.date })
    }

    func mood(on iso: String) -> MoodLog? { moodByDay[iso] }
    func headaches(on iso: String) -> [HeadacheLog] { data.headachesOn(iso: iso) }
    func journalEntries(on iso: String) -> [JournalEntry] { data.journal.filter { $0.date == iso } }

    func hasHeadache(on iso: String) -> Bool { headacheDays.contains(iso) }
    func hasNote(on iso: String) -> Bool { noteDays.contains(iso) }

    // Mood / headache aggregates now live in the cached properties above.

    /// True if anything at all is logged on the given day.
    func hasLog(on iso: String) -> Bool {
        moodByDay[iso] != nil || headacheDays.contains(iso) || noteDays.contains(iso)
    }

    func logMood(_ mood: Int, note: String = "", on iso: String) {
        data = data.withMood(iso: iso, mood: Int32(mood), note: note)
    }
    func clearMood(on iso: String) { data = data.clearingMood(iso: iso) }

    // Headaches: multiple episodes per day, each with its own id + time.
    private func isoDateTime(_ d: Date) -> String {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd'T'HH:mm"; f.timeZone = .current
        return f.string(from: d)
    }
    func addHeadache(intensity: Int, symptoms: [String], triggers: [String], note: String, at date: Date) {
        data = data.addingHeadache(entry: HeadacheLog(
            id: UUID().uuidString, at: isoDateTime(date), intensity: Int32(intensity),
            symptoms: symptoms, triggers: triggers, note: note))
    }
    func updateHeadache(id: String, intensity: Int, symptoms: [String], triggers: [String], note: String, at date: Date) {
        data = data.removingHeadache(id: id).addingHeadache(entry: HeadacheLog(
            id: id, at: isoDateTime(date), intensity: Int32(intensity),
            symptoms: symptoms, triggers: triggers, note: note))
    }
    func deleteHeadache(id: String) { data = data.removingHeadache(id: id) }

    func addJournalEntry(text: String, on iso: String) {
        data = data.addingJournal(entry: JournalEntry(id: UUID().uuidString, date: iso, text: text))
    }
    func updateJournalEntry(id: String, text: String, on iso: String) {
        data = data.removingJournal(id: id)
            .addingJournal(entry: JournalEntry(id: id, date: iso, text: text))
    }
    func deleteJournalEntry(id: String) { data = data.removingJournal(id: id) }

    /// Permanently erases all stored data and returns the app to a fresh first-run state
    /// (empty), which drops the UI back to the Welcome screen — no fabricated cycle.
    func deleteAllData() {
        pendingSave?.cancel()
        pendingSave = nil
        try? FileManager.default.removeItem(at: Self.fileURL)
        data = CycleData.companion.EMPTY
        // The assignment above scheduled a debounced save — cancel it and delete again so
        // "delete" truly leaves no file until the user next changes something.
        pendingSave?.cancel()
        pendingSave = nil
        try? FileManager.default.removeItem(at: Self.fileURL)
    }
}
