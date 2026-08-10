import SwiftUI

/// "Your cycles" — real logged history (only what the user actually logged — never
/// fabricated) plus the next predicted cycles with their fertile windows, and the
/// current cycle/period length. "Reset all" returns to onboarding.
struct CyclesCard: View {
    @Environment(CycleStore.self) private var store
    @AppStorage(ReminderSettings.Key.fertility) private var fertilityInsights = true
    private let cal = Calendar.current

    private struct PastRow: Identifiable { let id = UUID(); let date: Date; let label: String }
    private struct PredRow: Identifiable { let id = UUID(); let date: Date; let fertile: String }

    /// Rows shown per section. The past section is otherwise unbounded — it grew one row
    /// per logged period, so a year of tracking buried the rest of Home under it. Both
    /// sections use this so the card stays symmetrical.
    private static let rowsPerSection = 3

    private var past: [PastRow] {
        let starts = store.periodStarts.sorted(by: >)
        // Map first, THEN truncate: each row's length is measured against the start that
        // follows it, so trimming the source list first would mislabel the last row.
        let rows = starts.enumerated().map { i, s -> PastRow in
            if i == 0 { return PastRow(date: s, label: "Current cycle") }
            let len = cal.dateComponents([.day], from: cal.startOfDay(for: s),
                                         to: cal.startOfDay(for: starts[i - 1])).day ?? 0
            return PastRow(date: s, label: "\(len)-day cycle")
        }
        return Array(rows.prefix(Self.rowsPerSection))
    }

    private var predicted: [PredRow] {
        // An overdue or lost cycle makes every downstream date guesswork: these would be
        // measured from a period that hasn't arrived.
        guard store.tracking == .normal else { return [] }
        let base = store.currentCycleStart
        let cl = store.cycleLength
        return (1...Self.rowsPerSection).compactMap { i in
            // Each row pairs a predicted period with the fertile window that PRECEDES it
            // (the web app's pairing) — i.e. the window of the cycle one back from `p`.
            // The dates come from the core so they can't drift from the hero/calendar.
            guard let p = cal.date(byAdding: .day, value: cl * i, to: base),
                  let s = cal.date(byAdding: .day, value: cl * (i - 1), to: base) else { return nil }
            let w = store.fertileWindow(forCycleStarting: s)
            return PredRow(date: p, fertile: "\(fmt(w.start, "MMM d")) – \(fmt(w.end, "MMM d"))")
        }
    }

    var body: some View {
        let pastRows = past
        let predRows = predicted

        VStack(alignment: .leading, spacing: 12) {
            // No "Reset all" here: a destructive everything-eraser on the home screen was
            // one mis-tap from disaster, and Me → "Delete all my data" already owns that job.
            Text("Cycle overview").font(.cyclunaSerif(22)).foregroundStyle(Theme.ink)

            if !pastRows.isEmpty {
                sectionLabel("PAST CYCLES")
                ForEach(Array(pastRows.enumerated()), id: \.element.id) { i, row in
                    cycleRow(date: row.date, subtitle: row.label, drop: false)
                    if i < pastRows.count - 1 { rowDivider }
                }
            }

            sectionLabel("NEXT PREDICTED CYCLES").padding(.top, 4)
            if predRows.isEmpty {
                // Every predicted date is measured from a period that hasn't arrived, so the
                // whole list would be fiction. Say why instead of showing invented dates.
                Text(store.tracking == .late
                     ? "Your period is late. Predictions start again when you log it."
                     : "Predictions start again when you log your period.")
                    .font(.footnote)
                    .foregroundStyle(Theme.inkSoft)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                ForEach(Array(predRows.enumerated()), id: \.element.id) { i, row in
                    // The fertile-window subtitle is a fertility prediction — gated with
                    // the same switch as every other one.
                    cycleRow(date: row.date,
                             subtitle: fertilityInsights ? "Fertile window \(row.fertile)" : "",
                             drop: true)
                    if i < predRows.count - 1 { rowDivider }
                }
            }

            HStack(spacing: 12) {
                summaryTile("CYCLE LENGTH", "\(store.cycleLength) days")
                summaryTile("PERIOD LENGTH", "\(store.periodLength) days")
            }
            .padding(.top, 6)
        }
        .cyclunaCard(padding: 18)
    }

    private func cycleRow(date: Date, subtitle: String, drop: Bool) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(fmt(date, "MMM d, yyyy")).font(.headline).foregroundStyle(Theme.ink)
                if !subtitle.isEmpty {
                    Text(subtitle).font(.subheadline).foregroundStyle(Theme.inkSoft)
                }
            }
            Spacer()
            if drop { Image(systemName: "drop").foregroundStyle(Theme.phaseMenstrual) }
        }
        .padding(.vertical, 6)
    }

    private var rowDivider: some View { Divider().overlay(Theme.inkSoft.opacity(0.12)) }

    private func sectionLabel(_ t: String) -> some View {
        Text(t).font(.caption2).tracking(1.0).foregroundStyle(Theme.inkSoft)
    }

    private func summaryTile(_ label: String, _ value: String) -> some View {
        VStack(spacing: 4) {
            Text(label).font(.caption2).tracking(0.6).foregroundStyle(Theme.inkSoft)
            Text(value).font(.cyclunaSerif(22)).foregroundStyle(Theme.ink)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(Theme.background, in: RoundedRectangle(cornerRadius: 14))
    }

    private func fmt(_ d: Date, _ p: String) -> String {
        let f = DateFormatter(); f.dateFormat = p; return f.string(from: d)
    }
}
