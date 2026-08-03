import SwiftUI

/// "The moon this week" — a horizontal strip of real rendered moon discs around today.
/// Its own space, so the period calendar stays clean. Blood moons (bundled eclipses) are
/// tinted red. Educational, on-device, offline.
struct MoonWeekCard: View {
    @Environment(CycleStore.self) private var store
    private let cal = Calendar.current

    private var days: [Date] {
        (-2...4).compactMap { cal.date(byAdding: .day, value: $0, to: cal.startOfDay(for: .now)) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("The moon this week")
                .font(.cyclunaSerif(22))
                .foregroundStyle(Theme.ink)
            Text("The lunar rhythm around today")
                .font(.subheadline).italic()
                .foregroundStyle(Theme.inkSoft)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(days, id: \.self) { day in dayCell(day) }
                }
                .padding(.top, 8)
            }
        }
        .cyclunaCard(padding: 18)
    }

    private func dayCell(_ date: Date) -> some View {
        let iso = fmt(date, "yyyy-MM-dd")
        let isToday = cal.isDateInToday(date)
        let event = MoonEventCatalog.event(on: iso)
        let isBlood = event?.type == "eclipse-total"
        let phaseName = event?.title ?? store.moonLabel(on: iso)

        return VStack(spacing: 8) {
            MoonDisc(
                illumination: store.moonIllumination(on: iso),
                waxing: store.moonWaxing(on: iso),
                lit: isBlood ? Color(hex: "C0473F") : Color(hex: "EAD59B")
            )
            .frame(width: 46, height: 46)

            VStack(spacing: 2) {
                Text(isToday ? "TODAY" : fmt(date, "EEE").uppercased())
                    .font(.caption2).tracking(0.4)
                    .foregroundStyle(isToday ? Theme.primary : Theme.inkSoft)
                Text("\(cal.component(.day, from: date))")
                    .font(.cyclunaSerif(17))
                    .foregroundStyle(Theme.ink)
                Text(phaseName)
                    .font(.system(size: 10.5))
                    .foregroundStyle(isBlood ? Theme.phaseMenstrual : Theme.inkSoft)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
            }
        }
        .frame(width: 76)
        .padding(.vertical, 12)
        .background(isToday ? Theme.primary.opacity(0.07) : .clear,
                    in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16)
            .stroke(isToday ? Theme.primary.opacity(0.25) : .clear, lineWidth: 1))
    }

    private func fmt(_ d: Date, _ p: String) -> String {
        let f = DateFormatter(); f.dateFormat = p; return f.string(from: d)
    }
}

/// "Your cycle & the moon" — the signature educational insight linking the ~29.5-day cycle
/// to the ~29.53-day lunar month. All values are computed on-device from the `Moon` core.
struct MoonAlignmentCard: View {
    @Environment(CycleStore.self) private var store

    private var startedOn: String {
        store.lastPeriodMoonLabel.lowercased().replacingOccurrences(of: " moon", with: "")
    }
    private var atFullMoon: String { store.phaseAtNextFullMoon.lowercased() }

    /// "is today" / "is tomorrow" / "is in N days" — never "in 0 days" or "in 1 days".
    private var fullMoonWhen: String {
        switch store.daysUntilNextFullMoon {
        case 0:  return "is today"
        case 1:  return "is tomorrow"
        case let d: return "is in \(d) days"
        }
    }

    var body: some View {
        HStack(spacing: 16) {
            MoonDisc(
                illumination: Double(store.moonIllumination) / 100,
                waxing: store.moonWaxing(on: fmt(.now))
            )
            .frame(width: 66, height: 66)

            VStack(alignment: .leading, spacing: 5) {
                Text("Your cycle & the moon")
                    .font(.cyclunaSerif(19))
                    .foregroundStyle(Theme.ink)
                Text("Your last period began on a \(startedOn) moon. The next full moon \(fullMoonWhen), during your \(atFullMoon) phase.")
                    .font(.system(size: 13.5))
                    .foregroundStyle(Theme.inkSoft)
                    .fixedSize(horizontal: false, vertical: true)
                Label("Next full moon · \(fmt(store.nextFullMoonDate, "MMM d"))",
                      systemImage: "moon.circle.fill")
                    .font(.caption2)
                    .foregroundStyle(Theme.accentText)
                    .padding(.top, 2)
            }
            Spacer(minLength: 0)
        }
        .cyclunaCard()
    }

    private func fmt(_ d: Date, _ p: String = "yyyy-MM-dd") -> String {
        let f = DateFormatter(); f.dateFormat = p; return f.string(from: d)
    }
}
