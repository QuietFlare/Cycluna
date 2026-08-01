import SwiftUI

/// Month calendar — native LazyVGrid port of the web cycle calendar. Period days
/// tint pink, fertile days show a heart, other days show their cycle-day number.
struct CalendarCard: View {
    @Environment(CycleStore.self) private var store
    @State private var monthAnchor = Date()
    @State private var selected = Date()

    private let cal = Calendar.current

    var body: some View {
        VStack(spacing: 14) {
            Text("Your cycle calendar")
                .font(.cyclunaSerif(22))
                .foregroundStyle(Theme.ink)

            monthHeader
            weekdayHeader
            grid
            legend
            selectedPanel
        }
        .cyclunaCard(padding: 16)
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

    // MARK: sections

    private var monthHeader: some View {
        HStack {
            Button { shift(-1) } label: { Image(systemName: "chevron.left") }
            Spacer()
            Text(fmt(firstOfMonth, "MMMM yyyy")).font(.headline).foregroundStyle(Theme.ink)
            Spacer()
            Button { shift(1) } label: { Image(systemName: "chevron.right") }
        }
        .tint(Theme.primary)
    }

    private var weekdayHeader: some View {
        HStack(spacing: 4) {
            ForEach(Array(orderedWeekdays().enumerated()), id: \.offset) { _, s in
                Text(s).font(.caption2).foregroundStyle(Theme.inkSoft).frame(maxWidth: .infinity)
            }
        }
    }

    private var grid: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 4), count: 7), spacing: 4) {
            ForEach(Array(cells.enumerated()), id: \.offset) { _, date in
                if let date { dayCell(date) } else { Color.clear.frame(height: 44) }
            }
        }
    }

    private struct DayStyle { var bg: Color; var icon: String?; var iconColor: Color; var label: String }

    /// Cell tints are theme-adaptive: the light-cream opacities are too faint on the dark
    /// night-sky background, so dark mode uses stronger fills (esp. predicted period).
    private func tint(_ base: Color, light: Double, dark: Double) -> Color {
        Color(light: base.opacity(light), dark: base.opacity(dark))
    }

    private func markerStyle(_ marker: String) -> DayStyle {
        switch marker {
        case "period":
            return .init(bg: tint(Theme.phaseMenstrual, light: 0.28, dark: 0.45), icon: "drop.fill", iconColor: Theme.phaseMenstrual, label: "period day")
        case "predicted-period":
            return .init(bg: tint(Theme.phaseMenstrual, light: 0.10, dark: 0.32), icon: "drop",
                         iconColor: Color(light: Theme.phaseMenstrual.opacity(0.7), dark: Theme.phaseMenstrual), label: "predicted period")
        case "fertile-peak":
            return .init(bg: tint(Theme.phaseOvulatory, light: 0.45, dark: 0.5), icon: "heart.fill", iconColor: Theme.phaseOvulatory, label: "peak fertile day")
        case "fertile-high":
            return .init(bg: tint(Theme.phaseOvulatory, light: 0.28, dark: 0.4), icon: "heart.fill", iconColor: Theme.phaseOvulatory.opacity(0.9), label: "high fertility")
        case "fertile-medium":
            return .init(bg: tint(Theme.phaseOvulatory, light: 0.15, dark: 0.3), icon: "heart", iconColor: Theme.phaseOvulatory.opacity(0.85), label: "fertile")
        default:
            return .init(bg: .clear, icon: nil, iconColor: .clear, label: "")
        }
    }

    /// Friendly note for the selected-day panel, keyed off the marker.
    private func dayNote(_ marker: String) -> (text: String, color: Color)? {
        switch marker {
        case "period":            return ("Logged period", Theme.phaseMenstrual)
        case "predicted-period":  return ("Predicted period", Theme.inkSoft)
        case "fertile-peak":      return ("Peak fertile day", Theme.accentText)
        case "fertile-high":      return ("High fertility", Theme.accentText)
        case "fertile-medium":    return ("Fertile window", Theme.accentText)
        default:                  return nil
        }
    }

    private func dayCell(_ date: Date) -> some View {
        let iso = fmt(date, "yyyy-MM-dd")
        let marker = store.dayMarker(iso)
        let cycleDay = store.linearCycleDay(iso)
        let style = markerStyle(marker)
        let isSelected = cal.isDate(date, inSameDayAs: selected)
        let isToday = cal.isDateInToday(date)

        return Button { selected = date } label: {
            VStack(spacing: 2) {
                Text("\(cal.component(.day, from: date))")
                    .font(.callout).fontWeight(isToday ? .bold : .regular)
                    .foregroundStyle(Theme.ink)
                Group {
                    if let icon = style.icon {
                        Image(systemName: icon).foregroundStyle(style.iconColor)
                    } else if cycleDay > 0 {
                        Text("\(cycleDay)").foregroundStyle(Theme.inkSoft.opacity(0.7))
                    } else {
                        Text(" ")
                    }
                }
                .font(.system(size: 9))
            }
            .frame(maxWidth: .infinity)
            .frame(height: 44)
            .background(style.bg, in: RoundedRectangle(cornerRadius: 10))
            .overlay(alignment: .topTrailing) {
                if let dot = logDot(iso) {
                    Circle().fill(dot).frame(width: 6, height: 6).padding(4)
                }
            }
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(isSelected ? Theme.primary : .clear, lineWidth: 1.5))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(fmt(date, "MMMM d"))" +
            (style.label.isEmpty ? (cycleDay > 0 ? ", cycle day \(cycleDay)" : "") : ", \(style.label)") +
            (store.hasLog(on: iso) ? ", has a log" : ""))
    }

    /// A small dot on days with a log — coloured by mood if one exists, neutral otherwise.
    private func logDot(_ iso: String) -> Color? {
        if let m = store.mood(on: iso) {
            switch Int(m.mood) {
            case 1:  return Theme.phaseMenstrual
            case 2:  return Theme.secondary
            case 3:  return Theme.accent
            case 4:  return Color(hex: "7FB88A")
            default: return Theme.phaseFollicular
            }
        }
        if !store.headaches(on: iso).isEmpty || !store.journalEntries(on: iso).isEmpty {
            return Theme.inkSoft
        }
        return nil
    }

    private var legend: some View {
        VStack(spacing: 6) {
            HStack(spacing: 14) {
                legendDot(Theme.phaseMenstrual.opacity(0.55), "Period")
                legendDot(Theme.phaseMenstrual.opacity(0.22), "Predicted")
                HStack(spacing: 3) {
                    Circle().fill(Theme.phaseOvulatory.opacity(0.3)).frame(width: 9, height: 9)
                    Circle().fill(Theme.phaseOvulatory.opacity(0.5)).frame(width: 9, height: 9)
                    Circle().fill(Theme.phaseOvulatory.opacity(0.75)).frame(width: 9, height: 9)
                    Text("Fertile").font(.caption2).foregroundStyle(Theme.inkSoft)
                }
            }
            HStack(spacing: 14) {
                HStack(spacing: 4) {
                    Text("14").font(.system(size: 9)).foregroundStyle(Theme.inkSoft)
                    Text("Cycle day").font(.caption2).foregroundStyle(Theme.inkSoft)
                }
                legendDot(Theme.primary, "Logged")
            }
        }
        .padding(.top, 2)
    }

    private func legendDot(_ color: Color, _ label: String) -> some View {
        HStack(spacing: 5) {
            Circle().fill(color).frame(width: 10, height: 10)
            Text(label).font(.caption2).foregroundStyle(Theme.inkSoft)
        }
    }

    private var selectedPanel: some View {
        let iso = fmt(selected, "yyyy-MM-dd")
        let phase = store.phaseForDate(iso)
        let cycleDay = store.linearCycleDay(iso)
        let note = dayNote(store.dayMarker(iso))
        return VStack(spacing: 4) {
            Text(fmt(selected, "EEEE, MMMM d").uppercased())
                .font(.caption2).tracking(0.5)
                .foregroundStyle(Theme.inkSoft)
            HStack(spacing: 6) {
                if cycleDay > 0 {
                    Text("Cycle day \(cycleDay)")
                    if !phase.isEmpty { Text("·").foregroundStyle(Theme.inkSoft) }
                }
                if !phase.isEmpty { Text("\(phase) phase") }
            }
            .font(.footnote.weight(.medium))
            .foregroundStyle(Theme.ink)
            if let note {
                Text(note.text)
                    .font(.caption).fontWeight(.medium)
                    .foregroundStyle(note.color)
            }

            // What was logged that day (mood / headache / notes).
            let mood = store.mood(on: iso)
            let headacheCount = store.headaches(on: iso).count
            let noteCount = store.journalEntries(on: iso).count
            if mood != nil || headacheCount > 0 || noteCount > 0 {
                HStack(spacing: 12) {
                    if let mood {
                        loggedChip(text: "\(MoodScale.emoji(Int(mood.mood))) \(MoodScale.label(Int(mood.mood)))")
                    }
                    if headacheCount > 0 {
                        loggedChip(text: "🤕 \(headacheCount) headache\(headacheCount == 1 ? "" : "s")")
                    }
                    if noteCount > 0 {
                        loggedChip(text: "📝 \(noteCount) note\(noteCount == 1 ? "" : "s")")
                    }
                }
                .padding(.top, 2)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 4)
    }

    private func loggedChip(text: String) -> some View {
        Text(text)
            .font(.caption2)
            .foregroundStyle(Theme.ink)
            .padding(.horizontal, 8).padding(.vertical, 4)
            .background(Theme.background, in: Capsule())
    }

    // MARK: helpers

    private func shift(_ n: Int) {
        monthAnchor = cal.date(byAdding: .month, value: n, to: firstOfMonth) ?? firstOfMonth
    }
    private func fmt(_ d: Date, _ p: String) -> String {
        let f = DateFormatter(); f.dateFormat = p; return f.string(from: d)
    }
    private func orderedWeekdays() -> [String] {
        let s = cal.veryShortWeekdaySymbols
        let start = cal.firstWeekday - 1
        return Array(s[start...] + s[..<start])
    }
}
