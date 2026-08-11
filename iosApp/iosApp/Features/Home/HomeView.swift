import SwiftUI

struct HomeView: View {
    @Environment(CycleStore.self) private var store
    @State private var adjustOpen = false
    @AppStorage(ReminderSettings.Key.fertility) private var fertilityInsights = true

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
            // The wheel is the way in to fixing the dates it draws: tap it (or the chip,
            // which is the visible hint that this is possible) to adjust the last period
            // start in place, and watch the dot sweep to where it belongs.
            Button {
                UIImpactFeedbackGenerator(style: .soft).impactOccurred()
                adjustOpen = true
            } label: {
                VStack(spacing: 12) {
                    MoonWheel(cycleDay: store.cycleDay,
                              cycleLength: store.cycleLength,
                              periodLength: store.periodLength,
                              phaseLabel: store.phaseLabel,
                              size: 260,
                              showsOvulation: fertilityInsights)
                        .animation(.easeInOut(duration: 0.8), value: store.cycleDay)

                    Label("Adjust dates", systemImage: "pencil")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(Theme.primary)
                        .padding(.vertical, 6)
                        .padding(.horizontal, 12)
                        .background(Theme.primary.opacity(0.10), in: Capsule())
                }
            }
            .buttonStyle(PressScaleStyle())
            .accessibilityHint("Opens a calendar to adjust when your last period started")

            HStack(spacing: 12) {
                // Once tracking is unclear the fertile window is guesswork — showing a
                // confident date range would be the misleading part, so it goes away. It
                // also hides with fertility insights switched off.
                if store.showsFertileWindow && fertilityInsights {
                    infoTile(icon: "heart.fill", tint: Theme.phaseOvulatory,
                             eyebrow: "Fertile window", value: store.fertileWindowText)
                }
                infoTile(icon: "drop.fill", tint: Theme.phaseMenstrual,
                         eyebrow: "Next period", value: store.nextPeriodShort)
            }

            // Speaks only when the tiles can't: a late period, or tracking gone unclear.
            if !store.fertileContext.isEmpty {
                Text(store.fertileContext)
                    .font(.subheadline)
                    .foregroundStyle(Theme.accentText)
                    .multilineTextAlignment(.center)
            }

            // While the logged period is on, offering "Start period today" only invites an
            // accidental second log that corrupts the cycle history — a quiet confirmation
            // replaces it. The button returns the moment it's needed again, including the
            // late/unclear states, which is exactly when logging matters most.
            if store.isInLoggedPeriod {
                HStack(spacing: 8) {
                    Image(systemName: "drop.fill").foregroundStyle(Theme.phaseMenstrual)
                    Text(store.periodStartedText)
                        .fontWeight(.medium)
                        .foregroundStyle(Theme.inkSoft)
                }
                .padding(.vertical, 14)
            } else {
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
        }
        .cyclunaCard(padding: 24)
        .sheet(isPresented: $adjustOpen) {
            PeriodDateSheet(mode: .adjustLast, initialDate: store.lastPeriodStart)
        }
    }

    /// A soft press-down so the wheel feels touchable the moment a finger lands on it.
    private struct PressScaleStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .scaleEffect(configuration.isPressed ? 0.97 : 1)
                .animation(.easeOut(duration: 0.15), value: configuration.isPressed)
        }
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
        // The phase's own icon and colour, not "sparkles" — the AI-glitter glyph made this
        // hand-written phase copy read as a generated blurb. Same source as the phase cards.
        let phase = PhaseContent.content(for: store.phaseLabel)
        return HStack(alignment: .top, spacing: 12) {
            Image(systemName: phase?.icon ?? "moon.fill")
                .foregroundStyle(phase?.color ?? Theme.primary)
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
