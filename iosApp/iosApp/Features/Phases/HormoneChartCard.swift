import SwiftUI
import Charts
import Shared

/// Educational hormone chart — typical reference curves (Speroff/Stricker) for a
/// 28-day cycle, scaled to the user's length. Tap any day to see relative levels.
/// Values are relative, not absolute IU/L — not diagnostic.
struct HormoneChartCard: View {
    @Environment(CycleStore.self) private var store
    @State private var selectedDay: Int?

    private let core = CyclunaCore.shared

    private struct DayHormones: Identifiable {
        let id: Int
        let estrogen, progesterone, lh, fsh: Double
    }
    private struct Phase: Identifiable {
        let id = UUID()
        let label: String, range: String
        let startX, endX: Double
        let color: Color
    }

    private var cycleLength: Int { store.cycleLength }
    private var currentDay: Int { min(max(store.cycleDay, 1), cycleLength) }

    private var data: [DayHormones] {
        (1...cycleLength).map { d in
            let h = core.hormoneLevels(day: Int32(d), cycleLength: Int32(cycleLength))
            return DayHormones(id: d, estrogen: h.estrogen, progesterone: h.progesterone, lh: h.lh, fsh: h.fsh)
        }
    }

    private var phases: [Phase] {
        let pl = store.periodLength
        let folEnd = cycleLength / 2 - 2
        let ovEnd = cycleLength / 2 + 2
        func band(_ label: String, _ s: Int, _ e: Int, _ c: Color) -> Phase {
            Phase(label: label, range: e > s ? "D\(s)–\(e)" : "D\(s)",
                  startX: Double(s) - 0.5, endX: Double(min(e, cycleLength)) + 0.5, color: c)
        }
        return [
            band("Menstrual", 1, pl, Theme.phaseMenstrual),
            band("Follicular", pl + 1, folEnd, Theme.phaseFollicular),
            band("Ovulatory", folEnd + 1, ovEnd, Theme.phaseOvulatory),
            band("Luteal", ovEnd + 1, cycleLength, Theme.phaseLuteal),
        ]
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Hormone chart")
                .font(.cyclunaSerif(22)).foregroundStyle(Theme.ink)

            // Room for the "Today" pill, which the chart draws above its plot area.
            chart.frame(height: 200).padding(.top, 8)
            selection
            legend
            disclaimer
        }
        .cyclunaCard(padding: 16)
    }

    private var chart: some View {
        Chart {
            ForEach(phases) { p in
                RectangleMark(xStart: .value("s", p.startX), xEnd: .value("e", p.endX),
                              yStart: .value("y0", 0.0), yEnd: .value("y1", 1.0))
                    .foregroundStyle(p.color.opacity(0.13))
            }
            line("Oestrogen", \.estrogen, Theme.primary)
            line("Progesterone", \.progesterone, Theme.secondary)
            line("LH", \.lh, Theme.accent, dash: [5, 4])
            line("FSH", \.fsh, Theme.inkSoft, dash: [2, 3])

            RuleMark(x: .value("Today", Double(currentDay)))
                .foregroundStyle(Theme.phaseMenstrual)
                .lineStyle(StrokeStyle(lineWidth: 2))
                // `spacing: 0` sat the pill directly on the plot's top edge, where it
                // crowded the card title once the subtitle was removed.
                .annotation(position: .top, spacing: 4) {
                    Text("Today").font(.system(size: 9, weight: .semibold))
                        .padding(.horizontal, 5).padding(.vertical, 2)
                        .background(Theme.phaseMenstrual, in: Capsule())
                        .foregroundStyle(.white)
                }

            if let d = selectedDay, d >= 1, d <= data.count {
                let row = data[d - 1]
                RuleMark(x: .value("Sel", Double(d)))
                    .foregroundStyle(Theme.inkSoft.opacity(0.5))
                    .lineStyle(StrokeStyle(lineWidth: 1, dash: [2, 3]))
                dot(d, row.estrogen, Theme.primary)
                dot(d, row.progesterone, Theme.secondary)
                dot(d, row.lh, Theme.accent)
                dot(d, row.fsh, Theme.inkSoft)
            }
        }
        .chartXScale(domain: 1.0...Double(cycleLength))
        .chartYScale(domain: 0.0...1.0)
        .chartYAxis(.hidden)
        .chartXAxis {
            AxisMarks(values: axisTicks) { v in
                AxisValueLabel {
                    if let d = v.as(Double.self) { Text(axisDate(Int(d))).font(.caption2) }
                }
            }
        }
        .chartOverlay { proxy in
            GeometryReader { geo in
                Rectangle().fill(.clear).contentShape(Rectangle())
                    .gesture(DragGesture(minimumDistance: 0).onChanged { value in
                        guard let plotFrame = proxy.plotFrame else { return }
                        let x = value.location.x - geo[plotFrame].origin.x
                        if let day: Double = proxy.value(atX: x) {
                            selectedDay = min(cycleLength, max(1, Int(day.rounded())))
                        }
                    })
            }
        }
    }

    private func line(_ name: String, _ kp: KeyPath<DayHormones, Double>, _ color: Color, dash: [CGFloat] = []) -> some ChartContent {
        ForEach(data) { row in
            LineMark(x: .value("Day", Double(row.id)),
                     y: .value("Level", row[keyPath: kp]),
                     series: .value("Hormone", name))
        }
        .foregroundStyle(color)
        .interpolationMethod(.catmullRom)
        .lineStyle(StrokeStyle(lineWidth: 2, lineCap: .round, dash: dash))
    }

    /// ~5 evenly spaced ticks across the cycle.
    private var axisTicks: [Double] {
        Set([1, cycleLength / 4, cycleLength / 2, (cycleLength * 3) / 4, cycleLength]
            .map { max(1, min($0, cycleLength)) }).sorted().map(Double.init)
    }

    /// The real calendar date for a cycle day, compact ("8/13").
    private func axisDate(_ day: Int) -> String {
        let date = Calendar.current.date(byAdding: .day, value: day - 1, to: store.currentCycleStart)
            ?? store.currentCycleStart
        let f = DateFormatter(); f.dateFormat = "M/d"
        return f.string(from: date)
    }

    private func dot(_ day: Int, _ v: Double, _ color: Color) -> some ChartContent {
        PointMark(x: .value("d", Double(day)), y: .value("v", v))
            .foregroundStyle(color).symbolSize(50)
    }

    @ViewBuilder private var selection: some View {
        if let d = selectedDay, d >= 1, d <= data.count {
            let row = data[d - 1]
            VStack(alignment: .leading, spacing: 4) {
                Text("Day \(d)").font(.subheadline.weight(.semibold)).foregroundStyle(Theme.ink)
                levelRow("Oestrogen", Theme.primary, row.estrogen)
                levelRow("Progesterone", Theme.secondary, row.progesterone)
                levelRow("LH", Theme.accent, row.lh)
                levelRow("FSH", Theme.inkSoft, row.fsh)
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.background, in: RoundedRectangle(cornerRadius: 12))
        } else {
            Text("Tap any day to see hormone levels")
                .font(.caption).italic().foregroundStyle(Theme.inkSoft)
                .frame(maxWidth: .infinity)
        }
    }

    private func levelRow(_ label: String, _ color: Color, _ v: Double) -> some View {
        HStack {
            Circle().fill(color).frame(width: 8, height: 8)
            Text(label).font(.footnote).foregroundStyle(Theme.ink)
            Spacer()
            Text("\(Int((v * 100).rounded()))%").font(.footnote).monospacedDigit().foregroundStyle(Theme.inkSoft)
        }
    }

    private var legend: some View {
        HStack(spacing: 14) {
            legendLine("Oestrogen", Theme.primary, dashed: false)
            legendLine("Progesterone", Theme.secondary, dashed: false)
            legendLine("LH", Theme.accent, dashed: true)
            legendLine("FSH", Theme.inkSoft, dashed: true)
        }
        .font(.caption2).foregroundStyle(Theme.inkSoft)
        .frame(maxWidth: .infinity)
    }

    private func legendLine(_ label: String, _ color: Color, dashed: Bool) -> some View {
        HStack(spacing: 4) {
            Rectangle().fill(color).frame(width: 14, height: 2).opacity(dashed ? 0.7 : 1)
            Text(label)
        }
    }

    private var disclaimer: some View {
        Text("Typical 28-day reference curves (Speroff, Stricker et al.), relative not absolute. Educational only.")
            .font(.system(size: 10)).foregroundStyle(Theme.inkSoft.opacity(0.8))
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
    }
}
