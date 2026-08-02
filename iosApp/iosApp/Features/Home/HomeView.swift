import SwiftUI

struct HomeView: View {
    @Environment(CycleStore.self) private var store

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 22) {
                    header
                    heroCard
                    highlightCard
                    MoodStripCard()
                    CalendarCard()
                    CyclesCard()
                    MoonWeekCard()
                    MoonAlignmentCard()
                }
                .padding()
            }
            .background(Theme.background.ignoresSafeArea())
            // A real navigation bar, not a scrolling <header>: the brand stays put and content
            // passes under a translucent bar the way iOS expects. Hiding it was what made this
            // screen read as a web page.
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    HStack(spacing: 6) {
                        Image(systemName: "moon.stars.fill")
                            .font(.footnote)
                            .foregroundStyle(Theme.primary)
                        Text("Cycluna")
                            .font(.cyclunaSerif(17))
                            .foregroundStyle(Theme.ink)
                    }
                    .accessibilityAddTraits(.isHeader)
                }
            }
        }
    }

    private var header: some View {
        VStack(spacing: 4) {
            Text("Hello, \(store.displayName.isEmpty ? "beautiful" : store.displayName)")
                .font(.cyclunaSerif(34))
                .foregroundStyle(Theme.ink)
            Text(store.todayLong)
                .font(.subheadline)
                .foregroundStyle(Theme.inkSoft)
        }
        .padding(.top, 4)
    }

    private var heroCard: some View {
        VStack(spacing: 18) {
            MoonWheel(cycleDay: store.cycleDay,
                      cycleLength: store.cycleLength,
                      phaseLabel: store.phaseLabel,
                      size: 260)

            HStack(spacing: 12) {
                // Once tracking is unclear the fertile window is guesswork — showing a
                // confident date range would be the misleading part, so it goes away.
                if store.showsFertileWindow {
                    infoTile(icon: "heart.fill", tint: Theme.phaseOvulatory,
                             eyebrow: "Fertile window", value: store.fertileWindowText)
                }
                infoTile(icon: "drop.fill", tint: Theme.phaseMenstrual,
                         eyebrow: "Next period", value: store.nextPeriodShort)
            }

            Text(store.fertileContext)
                .font(.subheadline)
                .foregroundStyle(store.tracking == .normal ? Theme.inkSoft : Theme.accentText)
                .multilineTextAlignment(.center)

            Button {
                store.startPeriod()
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "drop.fill")
                    Text("Start period today").fontWeight(.semibold)
                }
                .foregroundStyle(.white)
                .padding(.vertical, 14)
                .padding(.horizontal, 26)
                .background(Theme.primary, in: Capsule())
            }
            .accessibilityHint("Logs a new period starting today")
        }
        .cyclunaCard(padding: 24)
    }

    private func infoTile(icon: String, tint: Color, eyebrow: String, value: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon).foregroundStyle(tint)
            VStack(alignment: .leading, spacing: 2) {
                Text(eyebrow.uppercased())
                    .font(.caption2).tracking(0.6)
                    .foregroundStyle(Theme.inkSoft)
                Text(value)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(Theme.ink)
                    .lineLimit(1).minimumScaleFactor(0.8)
            }
            Spacer(minLength: 0)
        }
        .padding(12)
        .frame(maxWidth: .infinity)
        .background(Theme.background, in: RoundedRectangle(cornerRadius: 14))
    }

    private var highlightCard: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "sparkles")
                .foregroundStyle(Theme.primary)
                .padding(.top, 3)
            Text(store.hormoneHighlight)
                .font(.system(.title3, design: .serif))
                .foregroundStyle(Theme.ink)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .cyclunaCard()
    }

}
