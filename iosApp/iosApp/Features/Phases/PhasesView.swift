import SwiftUI

struct PhasesView: View {
    @Environment(CycleStore.self) private var store

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    // No strapline under the large title: iOS titles are left-aligned with
                    // their own metrics, and a centred line beneath opened a dead band before
                    // the first card. The cards carry the voice now.
                    HormoneChartCard()

                    ForEach(phasesFromNow) { p in phaseCard(p) }
                }
                .padding()
            }
            .background(Theme.background.ignoresSafeArea())
            // Large title collapsing to inline — the standard iOS browse-screen behaviour.
            .cyclunaTitle("Your four phases")
        }
    }

    /// The four phases rotated so the one you're in leads.
    ///
    /// Rotated rather than reordered: the cycle runs menstrual → follicular → ovulatory →
    /// luteal and wraps, so starting at "now" and continuing round keeps what comes next
    /// actually next. Plucking the current phase to the top would scramble that.
    private var phasesFromNow: [PhaseContent] {
        let all = PhaseContent.all
        guard let i = all.firstIndex(where: { $0.key == store.phaseLabel }) else { return all }
        return Array(all[i...] + all[..<i])
    }

    /// Which cycle a card's dates belong to.
    ///
    /// The list is rotated to start at the phase you're in, so everything after it is what's
    /// *coming*. A phase that sits earlier in the cycle's natural order has already been and
    /// gone this month, so it's dated from the next cycle — otherwise the cards read forward
    /// (Luteal → Menstrual) while their dates run backwards (Aug 3 → Jul 18).
    private func cycleStart(for p: PhaseContent) -> Date {
        let all = PhaseContent.all
        guard let now = all.firstIndex(where: { $0.key == store.phaseLabel }),
              let i = all.firstIndex(where: { $0.key == p.key }), i < now
        else { return store.currentCycleStart }
        return Calendar.current.date(byAdding: .day, value: store.cycleLength,
                                     to: store.currentCycleStart) ?? store.currentCycleStart
    }

    private func phaseCard(_ p: PhaseContent) -> some View {
        let isNow = p.key == store.phaseLabel
        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 10) {
                // The phase's own symbol in its own colour — the emoji here read as
                // decoration, and drop/leaf/sun/moon are already the app's vocabulary.
                Image(systemName: p.icon)
                    .font(.system(size: 26))
                    .foregroundStyle(p.color)
                Text(p.eyebrow.uppercased())
                    .font(.caption2).tracking(1.2)
                    .foregroundStyle(p.color)
                if isNow {
                    Text("NOW")
                        .font(.caption2).fontWeight(.bold)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 7).padding(.vertical, 2)
                        .background(p.color, in: Capsule())
                }
                Spacer()
                // Real dates only — the "D17–28" cycle-day shorthand under them read as
                // jargon, and the hormone chart above already maps days to phases.
                Text(p.dateRangeText(cycleLength: store.cycleLength, periodLength: store.periodLength,
                                     cycleStart: cycleStart(for: p)))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Theme.ink)
            }
            Text(p.key)
                .font(.cyclunaSerif(30))
                .foregroundStyle(p.color)
            Text(p.blurb)
                .font(.callout)
                .foregroundStyle(Theme.ink.opacity(0.85))
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(20)
        .background(p.color.opacity(0.14), in: RoundedRectangle(cornerRadius: 22))
        .overlay(RoundedRectangle(cornerRadius: 22)
            .stroke(isNow ? p.color.opacity(0.55) : .clear, lineWidth: 1.5))
    }
}
