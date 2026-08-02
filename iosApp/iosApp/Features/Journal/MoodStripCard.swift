import SwiftUI
import Shared

/// "How you've been" — a quick seven-day glance: one colored mood bubble per day
/// (dashed empty circle for days you didn't log), today ringed. The at-a-glance companion
/// to the mood-vs-cycle plot.
struct MoodStripCard: View {
    @Environment(CycleStore.self) private var store
    private let cal = Calendar.current

    private var days: [Date] {
        (0..<7).reversed().compactMap {
            cal.date(byAdding: .day, value: -$0, to: cal.startOfDay(for: .now))
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("How you've been").font(.cyclunaSerif(22)).foregroundStyle(Theme.ink)
            Text("Your mood over the last seven days")
                .font(.subheadline).italic().foregroundStyle(Theme.inkSoft)

            // Seven cells share the card's width rather than scrolling. Fixed-width cells in
            // a horizontal ScrollView (from when this showed fourteen days) overflowed by just
            // enough to push today — the one cell that always matters — off the right edge.
            HStack(spacing: 4) {
                ForEach(days, id: \.self) { dayCell($0) }
            }
            .padding(.top, 4)
        }
        .cyclunaCard(padding: 18)
    }

    private func dayCell(_ date: Date) -> some View {
        let mood = store.mood(on: store.isoDay(date))
        let isToday = cal.isDateInToday(date)
        return VStack(spacing: 6) {
            ZStack {
                if let mood {
                    Circle().fill(MoodScale.color(Int(mood.mood)).opacity(0.22))
                    Text(MoodScale.emoji(Int(mood.mood))).font(.system(size: 19))
                } else {
                    Circle().strokeBorder(style: StrokeStyle(lineWidth: 1.5, dash: [3, 3]))
                        .foregroundStyle(Theme.inkSoft.opacity(0.3))
                }
            }
            .frame(width: 36, height: 36)
            .overlay(Circle().stroke(isToday ? Theme.primary : .clear, lineWidth: 2))

            Text(isToday ? "Today" : weekday(date))
                .font(.system(size: 9))
                .foregroundStyle(isToday ? Theme.primary : Theme.inkSoft)
                .lineLimit(1).minimumScaleFactor(0.7)
            Text("\(cal.component(.day, from: date))")
                .font(.system(size: 12))
                .fontWeight(isToday ? .semibold : .regular)
                .foregroundStyle(isToday ? Theme.primary : Theme.ink)
        }
        // Equal shares of the available width, so all seven always fit.
        .frame(maxWidth: .infinity)
        .padding(.vertical, 4)
        .background(isToday ? Theme.primary.opacity(0.07) : .clear,
                    in: RoundedRectangle(cornerRadius: 12))
    }

    private func weekday(_ d: Date) -> String {
        let f = DateFormatter(); f.dateFormat = "EEE"; return f.string(from: d)
    }
}
