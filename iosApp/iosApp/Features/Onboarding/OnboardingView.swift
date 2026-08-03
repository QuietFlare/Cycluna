import SwiftUI

/// First-run onboarding — four unhurried steps on the night-sky theme: an atmospheric
/// hero, a trust story, then two warm personalization steps (last period, cycle length).
/// The app never fabricates a cycle; finishing here logs the user's real first period,
/// which flips `hasLoggedPeriod` and reveals the tabs. Follows the system/app theme.
struct OnboardingView: View {
    @State private var heroTextIn = false
    @Environment(CycleStore.self) private var store

    @State private var step = 0
    @State private var lastPeriod = Calendar.current.startOfDay(for: .now)
    @State private var cycleLength = 28
    @State private var periodLength = 5
    @State private var showEstimate = false

    /// Screen-width-proportional scale (1.0 at the 393pt reference width), so headings and
    /// the moon hold the same presence on an iPhone SE and a Pro Max. Clamped to stay sane.
    @State private var uiScale: CGFloat = 1

    private func updateScale(_ width: CGFloat) {
        uiScale = min(max(width / 393, 0.82), 1.18)
    }

    /// Display serif at a base size, scaled proportionally to the device width.
    private func serif(_ base: CGFloat) -> Font { .cyclunaSerif(base * uiScale) }

    var body: some View {
        ZStack {
            Theme.backgroundGradient.ignoresSafeArea()
            StarField().ignoresSafeArea()

            Group {
                switch step {
                case 0:  hero
                case 1:  trust
                case 2:  lastPeriodStep
                default: cycleLengthStep
                }
            }
            .transition(.asymmetric(
                insertion: .move(edge: .trailing).combined(with: .opacity),
                removal: .move(edge: .leading).combined(with: .opacity)
            ))
            .padding(.horizontal, 26)
            .padding(.bottom, 24)

            if step > 0 { backButton }
        }
        .background(
            GeometryReader { geo in
                Color.clear
                    .onAppear { updateScale(geo.size.width) }
                    .onChange(of: geo.size.width) { _, w in updateScale(w) }
            }
        )
    }


    private func advance() {
        withAnimation(.spring(response: 0.5, dampingFraction: 0.86)) { step += 1 }
    }

    private func finish() {
        store.completeOnboarding(lastPeriod: lastPeriod, cycleLength: cycleLength, periodLength: periodLength)
    }

    /// Maps a coarse "about how long ago" answer to a representative date, then advances.
    /// It's explicitly an estimate — rolled into the current cycle at finish and editable
    /// later in Me. Honest fallback for someone who doesn't recall the exact day.
    private func estimate(daysAgo: Int) {
        let d = Calendar.current.date(byAdding: .day, value: -daysAgo,
                                      to: Calendar.current.startOfDay(for: .now)) ?? .now
        withAnimation(.spring(response: 0.5, dampingFraction: 0.86)) {
            lastPeriod = d
            step = 3
        }
    }

    private var backButton: some View {
        VStack {
            HStack {
                Button {
                    withAnimation(.spring(response: 0.5, dampingFraction: 0.86)) { step -= 1 }
                } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(Theme.inkSoft)
                        .padding(10)
                }
                Spacer()
            }
            Spacer()
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
    }

    // MARK: - Step 1 · Hero

    private var hero: some View {
        VStack(spacing: 0) {
            Spacer()
            GlowMoon(size: 156 * uiScale)
            // The words settle in just after the moon lands, so the screen assembles rather
            // than appearing all at once.
            Text("Welcome to\nCycluna")
                .font(serif(38))
                .multilineTextAlignment(.center)
                .foregroundStyle(Theme.ink)
                .padding(.top, 26)
                .opacity(heroTextIn ? 1 : 0)
                .offset(y: heroTextIn ? 0 : 10)
                .animation(.easeOut(duration: 0.45).delay(0.25), value: heroTextIn)
            Text("Your rhythm, in tune with\nthe moon and your body.")
                .font(.subheadline)
                .multilineTextAlignment(.center)
                .foregroundStyle(Theme.inkSoft)
                .padding(.top, 10)
                .opacity(heroTextIn ? 1 : 0)
                .offset(y: heroTextIn ? 0 : 10)
                .animation(.easeOut(duration: 0.45).delay(0.38), value: heroTextIn)
            Spacer()
            ProgressDots(current: 0)
            OnboardingButton("Begin") { advance() }
                .padding(.top, 18)
        }
        .onAppear { heroTextIn = true }
    }

    // MARK: - Step 2 · Trust

    private var trust: some View {
        VStack(spacing: 0) {
            Spacer(minLength: 24)
            Text("YOURS, AND ONLY YOURS")
                .font(.caption2).tracking(1.4)
                .foregroundStyle(Theme.accentText)
            Text("Private by design")
                .font(serif(34))
                .foregroundStyle(Theme.ink)
                .padding(.top, 6)

            VStack(spacing: 0) {
                featureRow("lock.fill", "On your phone, full stop",
                           "No account, no cloud, no tracking. Your data never leaves this device.")
                Divider().overlay(Theme.inkSoft.opacity(0.18))
                featureRow("moon.stars.fill", "Moon-synced phases",
                           "See your cycle and the lunar phase side by side, day by day.")
                Divider().overlay(Theme.inkSoft.opacity(0.18))
                featureRow("calendar.badge.clock", "Gentle predictions",
                           "Fertile window and next period, refined as you log — never alarmist.")
            }
            .padding(.top, 22)

            Spacer(minLength: 24)
            ProgressDots(current: 1)
            OnboardingButton("Continue") { advance() }
                .padding(.top, 18)
                .padding(.bottom, 14)
        }
    }

    private func featureRow(_ icon: String, _ title: String, _ sub: String) -> some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 19))
                .foregroundStyle(Theme.accentText)
                .frame(width: 42, height: 42)
                .background(.black.opacity(0.05),
                            in: RoundedRectangle(cornerRadius: 13))
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.system(size: 17, weight: .semibold)).foregroundStyle(Theme.ink)
                Text(sub).font(.system(size: 13)).foregroundStyle(Theme.inkSoft)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 15)
    }

    // MARK: - Step 3 · Last period

    private var lastPeriodStep: some View {
        VStack(spacing: 0) {
            Spacer().frame(height: 36)
            Text("STEP 1 OF 2")
                .font(.caption2).tracking(1.4)
                .foregroundStyle(Theme.accentText)
            Text("Your last period")
                .font(serif(31))
                .foregroundStyle(Theme.ink)
                .padding(.top, 6)
            Text("Pick the first day it started.")
                .font(.subheadline)
                .foregroundStyle(Theme.inkSoft)
                .padding(.top, 8)

            DatePicker("", selection: $lastPeriod, in: ...Date.now, displayedComponents: .date)
                .datePickerStyle(.graphical)
                .labelsHidden()
                .tint(Theme.secondary)
                .padding(8)
                .background(Theme.surface.opacity(0.55), in: RoundedRectangle(cornerRadius: 20))
                .overlay(RoundedRectangle(cornerRadius: 20).stroke(Theme.inkSoft.opacity(0.12)))
                .padding(.top, 18)

            Spacer()
            ProgressDots(current: 2)
            OnboardingButton("Next") { advance() }
                .padding(.top, 18)
            Button { showEstimate = true } label: {
                Text("Not sure of the exact day?")
                    .font(.footnote).foregroundStyle(Theme.inkSoft)
            }
            .padding(.top, 14)
            .confirmationDialog("About how long ago did it start?",
                                isPresented: $showEstimate, titleVisibility: .visible) {
                Button("Within the last week") { estimate(daysAgo: 3) }
                Button("1–2 weeks ago") { estimate(daysAgo: 10) }
                Button("3–4 weeks ago") { estimate(daysAgo: 25) }
                Button("Over a month ago") { estimate(daysAgo: 42) }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("We'll set an approximate date — you can fine-tune it anytime in Me.")
            }
        }
    }

    // MARK: - Step 4 · Cycle length

    private var cycleLengthStep: some View {
        VStack(spacing: 0) {
            Spacer(minLength: 24)
            Text("STEP 2 OF 2")
                .font(.caption2).tracking(1.4)
                .foregroundStyle(Theme.accentText)
            Text("Your cycle rhythm")
                .font(serif(34))
                .multilineTextAlignment(.center)
                .foregroundStyle(Theme.ink)
                .padding(.top, 6)
            Text("Two quick details — change them\nanytime in Me.")
                .font(.subheadline)
                .multilineTextAlignment(.center)
                .foregroundStyle(Theme.inkSoft)
                .padding(.top, 10)

            fieldLabel("CYCLE LENGTH", "days from one period to the next")
            chipPicker(21...45, selection: $cycleLength)
            fieldLabel("PERIOD LENGTH", "days your period usually lasts")
            chipPicker(2...10, selection: $periodLength)

            Spacer(minLength: 20)
            ProgressDots(current: 3)
            OnboardingButton("Start my journey") { finish() }
                .padding(.top, 16)
            Text("Wellness & education — not medical advice")
                .font(.footnote)
                .foregroundStyle(Theme.inkSoft)
                .padding(.top, 12)
        }
    }

    private func fieldLabel(_ title: String, _ hint: String) -> some View {
        VStack(spacing: 2) {
            Text(title).font(.caption2).tracking(1.0).foregroundStyle(Theme.accentText)
            Text(hint).font(.caption2).foregroundStyle(Theme.inkSoft)
        }
        .padding(.top, 20)
        .padding(.bottom, 10)
    }

    private func chipPicker(_ range: ClosedRange<Int>, selection: Binding<Int>) -> some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 9) {
                    ForEach(Array(range), id: \.self) { n in
                        let selected = selection.wrappedValue == n
                        Button {
                            withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) { selection.wrappedValue = n }
                            withAnimation { proxy.scrollTo(n, anchor: .center) }
                        } label: {
                            Text("\(n)")
                                .font(.system(size: 15, weight: selected ? .bold : .regular))
                                .frame(width: 52)
                                .padding(.vertical, 12)
                                .foregroundStyle(selected ? Color(hex: "3A2A08") : Theme.ink)
                                .background(
                                    selected
                                        ? AnyShapeStyle(LinearGradient(colors: [Theme.accent, Color(hex: "D9B45F")],
                                                                       startPoint: .topLeading, endPoint: .bottomTrailing))
                                        : AnyShapeStyle(Theme.surface.opacity(0.7)),
                                    in: RoundedRectangle(cornerRadius: 16)
                                )
                                .overlay(RoundedRectangle(cornerRadius: 16)
                                    .stroke(Theme.inkSoft.opacity(selected ? 0 : 0.14)))
                        }
                        .id(n)
                    }
                }
                .padding(.horizontal, 30)
            }
            .onAppear { proxy.scrollTo(selection.wrappedValue, anchor: .center) }
        }
        .frame(height: 56)
    }
}

// MARK: - Reusable pieces

/// The gold-to-white breathing moon used on the hero.
/// The welcome crescent — the same mark as the app icon and the launch screen, so the three
/// read as one identity rather than three different moons.
///
/// It enters at the launch mark's size and grows into place, which makes the handoff from the
/// launch screen feel continuous: the mark appears to stay put while the app takes over.
/// Mauve rather than the icon's gold, because gold was chosen to sit on the icon's night sky
/// and is far too pale on cream.
private struct GlowMoon: View {
    var size: CGFloat = 150

    /// The launch screen draws its mark at 96pt; starting there makes the growth read as
    /// continuous rather than as a separate animation.
    private var launchScale: CGFloat { 96 / max(size, 1) }

    @State private var arrived = false
    @State private var breathe = false

    var body: some View {
        Circle()
            .fill(
                LinearGradient(
                    colors: [Color(hex: "8C6BC4"), Color(hex: "6B3FA0"), Color(hex: "D4849A")],
                    startPoint: .topLeading, endPoint: .bottomTrailing
                )
            )
            .frame(width: size, height: size)
            // Crescent geometry copied from the icon: a disc with an offset disc removed.
            .mask {
                Circle()
                    .overlay {
                        Circle()
                            .frame(width: size * 0.806, height: size * 0.806)
                            .offset(x: size * 0.355, y: -size * 0.274)
                            .blendMode(.destinationOut)
                    }
                    .compositingGroup()
            }
            .shadow(color: Theme.primary.opacity(0.30), radius: 34)
            .shadow(color: Theme.secondary.opacity(0.22), radius: 70)
            // Two scale effects compose: a slow breath on top of the entrance growth.
            .scaleEffect(breathe ? 1.03 : 0.985)
            .animation(.easeInOut(duration: 4.5).repeatForever(autoreverses: true), value: breathe)
            .scaleEffect(arrived ? 1 : launchScale)
            .opacity(arrived ? 1 : 0)
            .animation(.spring(response: 0.75, dampingFraction: 0.82), value: arrived)
            .onAppear {
                arrived = true
                breathe = true
            }
    }
}

/// Four-dot progress indicator; the active dot is a gold pill.
private struct ProgressDots: View {
    let current: Int
    var body: some View {
        HStack(spacing: 7) {
            ForEach(0..<4, id: \.self) { i in
                Capsule()
                    .fill(i == current ? Theme.accentText
                          : .black.opacity(0.15))
                    .frame(width: i == current ? 22 : 7, height: 7)
                    .animation(.spring(response: 0.4, dampingFraction: 0.8), value: current)
            }
        }
    }
}

/// Primary onboarding CTA — mauve gradient pill.
private struct OnboardingButton: View {
    let title: String
    let action: () -> Void
    init(_ title: String, action: @escaping () -> Void) { self.title = title; self.action = action }

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.headline)
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background(
                    LinearGradient(colors: [Theme.primary, Color(hex: "8F6FD6")],
                                   startPoint: .topLeading, endPoint: .bottomTrailing),
                    in: Capsule()
                )
                .shadow(color: Theme.primary.opacity(0.5), radius: 16, y: 8)
        }
    }
}

/// Sparse, deterministic starfield behind the gradient.
private struct StarField: View {
    private static let stars: [(x: CGFloat, y: CGFloat, r: CGFloat, o: Double)] = [
        (0.10, 0.12, 1.6, 0.5), (0.78, 0.09, 1.2, 0.4), (0.88, 0.20, 1.6, 0.35),
        (0.20, 0.26, 1.1, 0.3), (0.55, 0.06, 1.0, 0.28), (0.34, 0.16, 1.3, 0.32),
        (0.66, 0.30, 1.2, 0.30), (0.14, 0.40, 1.0, 0.22), (0.92, 0.44, 1.4, 0.26),
        (0.48, 0.36, 1.0, 0.24), (0.72, 0.52, 1.2, 0.20), (0.28, 0.55, 1.1, 0.22),
    ]

    var body: some View {
        GeometryReader { geo in
            ForEach(Array(Self.stars.enumerated()), id: \.offset) { _, s in
                Circle()
                    .fill(Color.white.opacity(s.o))
                    .frame(width: s.r * 2, height: s.r * 2)
                    .position(x: s.x * geo.size.width, y: s.y * geo.size.height)
            }
        }
    }
}
