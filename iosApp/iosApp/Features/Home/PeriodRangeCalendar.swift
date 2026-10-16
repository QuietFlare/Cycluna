import SwiftUI

/// A month grid for picking a period's first and last day: tap the first day, then the last,
/// and the days between shade. SwiftUI has no range picker, so this is the Android range
/// dialog drawn by hand. Tapping a day before the start, or any day once a range is set,
/// starts a new range. Days after today cannot be picked. `end` nil means still going.
struct PeriodRangeCalendar: View {
    @Binding var start: Date
    @Binding var end: Date?

    @State private var monthAnchor: Date
    private let cal = Calendar.current
    private let today = Calendar.current.startOfDay(for: .now)

    init(start: Binding<Date>, end: Binding<Date?>) {
        _start = start
        _end = end
        _monthAnchor = State(initialValue: start.wrappedValue)
    }

    var body: some View {
        VStack(spacing: 8) {
            monthHeader
            weekdayHeader
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 0), count: 7), spacing: 4) {
                ForEach(Array(cells.enumerated()), id: \.offset) { _, date in
                    if let date { dayCell(date) } else { Color.clear.frame(height: 40) }
                }
            }
        }
    }

    // MARK: layout data

    private var firstOfMonth: Date {
        cal.date(from: cal.dateComponents([.year, .month], from: monthAnchor)) ?? monthAnchor
    }

    private var cells: [Date?] {
        let range = cal.range(of: .day, in: .month, for: firstOfMonth) ?? (1..<2)
        let firstWeekday = cal.component(.weekday, from: firstOfMonth)
        let leading = (firstWeekday - cal.firstWeekday + 7) % 7
        var out: [Date?] = Array(repeating: nil, count: leading)
        for d in range { out.append(cal.date(byAdding: .day, value: d - 1, to: firstOfMonth)) }
        while out.count % 7 != 0 { out.append(nil) }
        return out
    }

    private var weekdaySymbols: [String] {
        let symbols = cal.veryShortStandaloneWeekdaySymbols
        let first = cal.firstWeekday - 1
        return Array(symbols[first...] + symbols[..<first])
    }

    // MARK: sections

    private var monthHeader: some View {
        HStack {
            Button { shift(-1) } label: { Image(systemName: "chevron.left") }
            Spacer()
            Text(monthAnchor.formatted(.dateTime.month(.wide).year()))
                .font(.headline).foregroundStyle(Theme.ink)
            Spacer()
            Button { shift(1) } label: { Image(systemName: "chevron.right") }
                .disabled(cal.isDate(firstOfMonth, equalTo: today, toGranularity: .month))
        }
        .tint(Theme.primary)
        .padding(.horizontal, 8)
    }

    private var weekdayHeader: some View {
        HStack(spacing: 0) {
            ForEach(Array(weekdaySymbols.enumerated()), id: \.offset) { _, s in
                Text(s).font(.caption2).foregroundStyle(Theme.inkSoft).frame(maxWidth: .infinity)
            }
        }
    }

    private enum Role { case start, end, single, between, none }

    private func role(of date: Date) -> Role {
        let day = cal.startOfDay(for: date)
        let s = cal.startOfDay(for: start)
        guard let end else { return day == s ? .single : .none }
        let e = cal.startOfDay(for: end)
        if day == s && day == e { return .single }
        if day == s { return .start }
        if day == e { return .end }
        return (day > s && day < e) ? .between : .none
    }

    private func dayCell(_ date: Date) -> some View {
        let day = cal.startOfDay(for: date)
        let role = role(of: day)
        let future = day > today
        let isToday = day == today
        let filled = role == .start || role == .end || role == .single

        return Button { pick(day) } label: {
            ZStack {
                // The band between the ends runs edge to edge so the range reads as one bar.
                if role == .between || role == .start || role == .end {
                    HStack(spacing: 0) {
                        Rectangle().fill(role == .start ? .clear : Theme.primary.opacity(0.15))
                        Rectangle().fill(role == .end ? .clear : Theme.primary.opacity(0.15))
                    }
                }
                Circle()
                    .fill(filled ? Theme.primary : .clear)
                    .overlay(Circle().stroke(isToday && !filled ? Theme.primary : .clear, lineWidth: 1.5))
                    .padding(2)
                Text("\(cal.component(.day, from: day))")
                    .font(.subheadline.weight(filled ? .semibold : .regular))
                    .foregroundStyle(filled ? .white : (future ? Theme.inkSoft.opacity(0.4) : Theme.ink))
            }
            .frame(height: 40)
        }
        .buttonStyle(.plain)
        .disabled(future)
        .accessibilityLabel(day.formatted(.dateTime.day().month(.wide)))
        .accessibilityValue(accessibilityValue(for: role))
    }

    private func accessibilityValue(for role: Role) -> String {
        switch role {
        case .start: return "first day"
        case .end: return "last day"
        case .single: return "first day, still going"
        case .between: return "period day"
        case .none: return ""
        }
    }

    // MARK: behaviour

    private func pick(_ day: Date) {
        let s = cal.startOfDay(for: start)
        if end != nil || day < s {
            start = day
            end = nil
        } else if day > s {
            end = day
        }
    }

    private func shift(_ months: Int) {
        if let d = cal.date(byAdding: .month, value: months, to: monthAnchor) { monthAnchor = d }
    }
}
