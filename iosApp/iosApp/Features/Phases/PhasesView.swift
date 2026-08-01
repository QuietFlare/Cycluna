import SwiftUI

struct PhasesView: View {
    @Environment(CycleStore.self) private var store

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    VStack(spacing: 4) {
                        Text("Your four phases")
                            .font(.cyclunaSerif(34))
                            .foregroundStyle(Theme.ink)
                        Text("A monthly weather pattern, not a flaw.")
                            .font(.subheadline).italic()
                            .foregroundStyle(Theme.inkSoft)
                    }
                    .padding(.top, 8)

                    HormoneChartCard()

                    ForEach(PhaseContent.all) { p in phaseCard(p) }
                }
                .padding()
            }
            .background(Theme.background.ignoresSafeArea())
            .toolbar(.hidden, for: .navigationBar)
        }
    }

    private func phaseCard(_ p: PhaseContent) -> some View {
        let isNow = p.key == store.phaseLabel
        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 10) {
                Text(p.emoji).font(.system(size: 30))
                Text(p.eyebrow.uppercased())
                    .font(.caption2).tracking(1.2)
                    .foregroundStyle(p.color)
                if isNow {
                    Text("NOW")
                        .font(.caption2).fontWeight(.bold)
                        .foregroundStyle(Color(light: .white, dark: Color(hex: "1B0F34")))
                        .padding(.horizontal, 7).padding(.vertical, 2)
                        .background(p.color, in: Capsule())
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text(p.dateRangeText(cycleLength: store.cycleLength, periodLength: store.periodLength,
                                         cycleStart: store.currentCycleStart))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Theme.ink)
                    Text(p.rangeText(cycleLength: store.cycleLength, periodLength: store.periodLength))
                        .font(.caption2).monospacedDigit()
                        .foregroundStyle(Theme.inkSoft)
                }
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
