import SwiftUI

/// The period date sheet, in two modes: correcting the latest logged start (tapping the
/// wheel on Home) and logging a PAST period to build history (from the Cycle overview
/// card). The past mode only allows dates before the current last period start, so the
/// logged anchor — and with it today's cycle day and every prediction — can never move.
struct PeriodDateSheet: View {
    enum Mode {
        /// Edits the latest logged period start; previews what the change means.
        case adjustLast
        /// Appends an older period start. Existing entries are never touched.
        case logPast
    }

    let mode: Mode
    @Environment(CycleStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var date: Date

    init(mode: Mode, initialDate: Date) {
        self.mode = mode
        _date = State(initialValue: initialDate)
    }

    private var question: String {
        mode == .adjustLast ? "When did your last period start?" : "When did that period start?"
    }

    /// Past-only, like every period date in the app — and in `logPast` mode, strictly
    /// before the current anchor, so the new entry can only ever be history.
    private var latestAllowed: Date {
        switch mode {
        case .adjustLast: return .now
        case .logPast:
            return Calendar.current.date(byAdding: .day, value: -1, to: store.lastPeriodStart) ?? .now
        }
    }

    private var footnote: String {
        switch mode {
        case .adjustLast: return store.previewLine(anchor: date)
        case .logPast: return "Adds to your history. Today's cycle stays as it is."
        }
    }

    var body: some View {
        VStack(spacing: 10) {
            Text(question)
                .font(.cyclunaSerif(22))
                .foregroundStyle(Theme.ink)
                .multilineTextAlignment(.center)
                .padding(.top, 28)
                .padding(.horizontal, 24)

            DatePicker("Period start date", selection: $date, in: ...latestAllowed,
                       displayedComponents: .date)
                .datePickerStyle(.graphical)
                .tint(Theme.primary)
                .labelsHidden()
                .padding(.horizontal, 12)

            Text(footnote)
                .font(.footnote)
                .foregroundStyle(Theme.inkSoft)
                .padding(.horizontal, 24)

            Button {
                switch mode {
                case .adjustLast: store.lastPeriodStart = date
                case .logPast: store.startPeriod(on: date)
                }
                dismiss()
            } label: {
                Text("Save")
                    .fontWeight(.semibold)
                    .foregroundStyle(.white)
                    .padding(.vertical, 13)
                    .padding(.horizontal, 44)
                    .background(Theme.primary, in: Capsule())
            }
            .padding(.top, 6)

            Spacer(minLength: 0)
        }
        .presentationDragIndicator(.visible)
        .background(Theme.background.ignoresSafeArea())
    }
}
