import SwiftUI

/// "Your cycles" — real logged history (only what the user actually logged — never
/// fabricated) plus the next predicted cycles with their fertile windows, and the
/// current cycle/period length. "Reset all" returns to onboarding.
struct CyclesCard: View {
    @Environment(CycleStore.self) private var store
    @State private var confirmReset = false
    private let cal = Calendar.current

    private struct PastRow: Identifiable { let id = UUID(); let date: Date; let label: String }
    private struct PredRow: Identifiable { let id = UUID(); let date: Date; let fertile: String }

    private var past: [PastRow] {
        let starts = store.periodStarts.sorted(by: >)
        return starts.enumerated().map { i, s in
            if i == 0 { return PastRow(date: s, label: "Current cycle") }
            let len = cal.dateComponents([.day], from: cal.startOfDay(for: s),
                                         to: cal.startOfDay(for: starts[i - 1])).day ?? 0
            return PastRow(date: s, label: "\(len)-day cycle")
        }
    }

    private var predicted: [PredRow] {
        let base = store.currentCycleStart
        let cl = store.cycleLength
        return (1...3).compactMap { i in
            guard let p = cal.date(byAdding: .day, value: cl * i, to: base) else { return nil }
            let fs = cal.date(byAdding: .day, value: -(cl / 2 + 3), to: p) ?? p
            let fe = cal.date(byAdding: .day, value: -(cl / 2 - 1), to: p) ?? p
            return PredRow(date: p, fertile: "\(fmt(fs, "MMM d")) – \(fmt(fe, "MMM d"))")
        }
    }

    var body: some View {
        let pastRows = past
        let predRows = predicted

        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Your cycles").font(.cyclunaSerif(22)).foregroundStyle(Theme.ink)
                Spacer()
                Button("Reset all") { confirmReset = true }
                    .font(.subheadline).tint(Theme.inkSoft)
            }

            if !pastRows.isEmpty {
                sectionLabel("PAST CYCLES")
                ForEach(Array(pastRows.enumerated()), id: \.element.id) { i, row in
                    cycleRow(date: row.date, subtitle: row.label, drop: false)
                    if i < pastRows.count - 1 { rowDivider }
                }
            }

            sectionLabel("NEXT PREDICTED CYCLES").padding(.top, 4)
            ForEach(Array(predRows.enumerated()), id: \.element.id) { i, row in
                cycleRow(date: row.date, subtitle: "Fertile window \(row.fertile)", drop: true)
                if i < predRows.count - 1 { rowDivider }
            }

            HStack(spacing: 12) {
                summaryTile("CYCLE LENGTH", "\(store.cycleLength) days")
                summaryTile("PERIOD LENGTH", "\(store.periodLength) days")
            }
            .padding(.top, 6)
        }
        .cyclunaCard(padding: 18)
        .confirmationDialog("Reset all cycle data? This erases everything and returns to setup.",
                            isPresented: $confirmReset, titleVisibility: .visible) {
            Button("Reset everything", role: .destructive) { store.deleteAllData() }
            Button("Cancel", role: .cancel) {}
        }
    }

    private func cycleRow(date: Date, subtitle: String, drop: Bool) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(fmt(date, "MMM d, yyyy")).font(.headline).foregroundStyle(Theme.ink)
                Text(subtitle).font(.subheadline).foregroundStyle(Theme.inkSoft)
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
