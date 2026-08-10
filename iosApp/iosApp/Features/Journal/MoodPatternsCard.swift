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

    /// A paging TabView needs one fixed height, so all pages of a lens share it.
    private static let chartHeight: CGFloat = 150

    /// The axis differs per lens — one row of dates (daily), phase names + dates (phase),
    /// disc + name + date (moon). Sizing all three to the tallest left the daily page with
    /// a band of dead space between its dates and whatever came next.
    private var axisHeight: CGFloat {
        switch store.moodLens {
        case .daily: return 14
        case .phase: return 28
        case .moon:  return 40
        }
    }

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
                        .frame(height: axisHeight)
                }
                .tag(index)
            }
        }
        .tabViewStyle(.page(indexDisplayMode: .never))
        .frame(height: Self.chartHeight + axisHeight + 5)
        // The paging affordance lives ON the chart — a row of its own was just a gap. The
        // chevrons sit in the plot's own edge padding, level with the chart's midline.
        .overlay(alignment: .leading) {
            if store.moodPageIndex > 0 { pagingChevron("chevron.left") }
        }
        .overlay(alignment: .trailing) {
            if !store.isOnCurrentMoodPage { pagingChevron("chevron.right") }
        }
    }

    private func pagingChevron(_ symbol: String) -> some View {
        Image(systemName: symbol)
            .font(.footnote.weight(.semibold))
            .foregroundStyle(Theme.inkSoft)
            // A small surface chip: the daily line's first dot lands exactly here, and a
            // bare glyph vanished into the data.
            .padding(6)
            .background(Theme.surface.opacity(0.92), in: Circle())
            .padding(.horizontal, 2)
            // Centre on the chart, not on chart + axis.
            .offset(y: -axisHeight / 2)
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
    /// restated what the chart already showed, and an empty page stays visually quiet —
    /// the blank chart is the empty state. Plain text, no emoji prefix.
    @ViewBuilder
    private var narrative: some View {
        if let page = currentPage, page.summary.count > 0, let insight = page.insight {
            Text(insightSentence(insight))
                .font(.footnote).foregroundStyle(Theme.inkSoft)
                .fixedSize(horizontal: false, vertical: true)
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
