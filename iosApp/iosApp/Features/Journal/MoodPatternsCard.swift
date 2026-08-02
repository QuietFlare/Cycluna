import SwiftUI
import Shared

/// "Mood patterns" — the same logged moods seen through three lenses: by day, by cycle phase,
/// and by moon phase. Swipe the chart to page back through history.
///
/// Two rules this card exists to keep:
///  • Every page states the span it covers. The chart and the sentence beneath it must never
///    describe different timeframes.
///  • A page shows a *claim* only when its own data clears the core's guardrails; otherwise it
///    describes what's there ("6 logs · average good"). Descriptions need no guardrails,
///    conclusions do.
struct MoodPatternsCard: View {
    @Environment(CycleStore.self) private var store

    /// A paging TabView needs one fixed height, so all three lenses share it.
    private static let chartHeight: CGFloat = 150
    /// Tall enough for the phase axis, which carries two rows (phase names + real dates).
    private static let axisHeight: CGFloat = 38

    var body: some View {
        @Bindable var store = store
        return VStack(alignment: .leading, spacing: 12) {
            header

            Picker("View", selection: $store.moodLens) {
                Text("Daily").tag(CycleStore.MoodLens.daily)
                Text("Phase").tag(CycleStore.MoodLens.phase)
                Text("Moon").tag(CycleStore.MoodLens.moon)
            }
            .pickerStyle(.segmented)

            if store.moodPages.isEmpty {
                emptyState
            } else {
                pager
                pageFooter
            }

            moodLegend
        }
        .cyclunaCard(padding: 18)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("Mood patterns").font(.cyclunaSerif(22)).foregroundStyle(Theme.ink)
            Text(subtitle).font(.subheadline).italic().foregroundStyle(Theme.inkSoft)
        }
    }

    private var subtitle: String {
        switch store.moodLens {
        case .daily: return "How you've felt day by day"
        case .phase: return "How you feel through each cycle phase"
        case .moon:  return "How you feel through the lunar month"
        }
    }

    private var emptyState: some View {
        Text("Log your mood a few times and your patterns appear here.")
            .font(.callout).foregroundStyle(Theme.inkSoft)
            .frame(maxWidth: .infinity).multilineTextAlignment(.center)
            .padding(.vertical, 24)
    }

    // MARK: - Pager

    private var pager: some View {
        @Bindable var store = store
        return TabView(selection: $store.moodPageIndex) {
            ForEach(Array(store.moodPages.enumerated()), id: \.element.id) { index, page in
                VStack(spacing: 5) {
                    chart(for: page)
                        .frame(height: Self.chartHeight)
                    axis(for: page)
                        .frame(height: Self.axisHeight)
                }
                .tag(index)
            }
        }
        .tabViewStyle(.page(indexDisplayMode: .never))
        .frame(height: Self.chartHeight + Self.axisHeight + 5)
    }

    /// Just the span this page covers. The chevrons say whether there's more to swipe to.
    private var pageFooter: some View {
        let page = currentPage
        return HStack(spacing: 8) {
            Spacer(minLength: 0)
            Text(page?.title ?? "")
                .font(.caption2).foregroundStyle(Theme.inkSoft)
            Spacer(minLength: 0)
        }
        .overlay(alignment: .leading) {
            if store.moodPageIndex > 0 {
                Image(systemName: "chevron.left")
                    .font(.caption2).foregroundStyle(Theme.inkSoft.opacity(0.5))
            }
        }
        .overlay(alignment: .trailing) {
            if !store.isOnCurrentMoodPage {
                Image(systemName: "chevron.right")
                    .font(.caption2).foregroundStyle(Theme.inkSoft.opacity(0.5))
            }
        }
    }

    private var currentPage: CycleStore.MoodPage? {
        guard store.moodPages.indices.contains(store.moodPageIndex) else { return store.moodPages.last }
        return store.moodPages[store.moodPageIndex]
    }

    // MARK: - Charts

    @ViewBuilder
    private func chart(for page: CycleStore.MoodPage) -> some View {
        switch store.moodLens {
        case .daily: DailyMoodChart(page: page)
        case .phase: PhaseMoodChart(page: page, periodLength: store.periodLength)
        case .moon:  MoonMoodChart(page: page)
        }
    }

    @ViewBuilder
    private func axis(for page: CycleStore.MoodPage) -> some View {
        switch store.moodLens {
        case .daily: DailyMoodAxis(page: page)
        case .phase: PhaseMoodAxis(page: page, periodLength: store.periodLength)
        case .moon:  MoonMoodAxis(page: page, store: store)
        }
    }

    // MARK: - The sentence under the chart

    private var moodLegend: some View {
        VStack(alignment: .leading, spacing: 8) {
            narrative
            HStack(spacing: 10) {
                ForEach(1...5, id: \.self) { v in
                    HStack(spacing: 3) {
                        Circle().fill(MoodScale.color(v)).frame(width: 8, height: 8)
                        Text(MoodScale.label(v).lowercased())
                            .font(.system(size: 10)).foregroundStyle(Theme.inkSoft)
                    }
                }
            }
        }
        .padding(.top, 2)
    }

    /// A claim only when the page's own data earns one, and only about the span on screen.
    ///
    /// There is deliberately nothing to say otherwise: a running "n logs · averaging mid"
    /// restated what the chart already showed. The empty-page line stays, because a blank
    /// chart with no explanation reads as broken rather than as "nothing here".
    @ViewBuilder
    private var narrative: some View {
        if let page = currentPage {
            if page.summary.count == 0 {
                row("🌙", emptyPageText)
            } else if let insight = page.insight {
                row("💡", insightSentence(insight))
            }

            // Required disclaimer — this view invites a causal reading the evidence doesn't
            // support, so it stays regardless of what else is on screen.
            if store.moodLens == .moon {
                Text("Research hasn't found a strong moon–mood link — your own pattern is yours to discover. Educational only.")
                    .font(.caption2).foregroundStyle(Theme.inkSoft)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func row(_ icon: String, _ text: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Text(icon)
            Text(text)
                .font(.footnote).foregroundStyle(Theme.inkSoft)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var emptyPageText: String {
        switch store.moodLens {
        case .daily: return "Nothing logged in these two weeks."
        case .phase: return "Nothing logged during this cycle."
        case .moon:  return "Nothing logged during this lunar month."
        }
    }

    private func insightSentence(_ insight: MoodInsight) -> String {
        switch store.moodLens {
        case .moon:
            return "On this page you felt brightest around your \(phaseWord(insight.brightest))."
        default:
            return "On this page you felt brightest around your \(phaseWord(insight.brightest)), and lower during your \(phaseWord(insight.lowest))."
        }
    }

    private func phaseWord(_ phase: Phase) -> String {
        switch phase.label {
        case "Ovulatory":  return "ovulation window"
        case "Follicular": return "follicular phase"
        case "Luteal":     return "luteal phase"
        default:           return "period"
        }
    }
}
