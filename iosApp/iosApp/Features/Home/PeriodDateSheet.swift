import SwiftUI

/// The date sheet behind the wheel on Home: corrects the latest logged period start in
/// place. The preview line shows what the chosen date would mean before it's committed —
/// the wheel then animates to match.
struct PeriodDateSheet: View {
    @Environment(CycleStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var date: Date

    init(initialDate: Date) {
        _date = State(initialValue: initialDate)
    }

    var body: some View {
        VStack(spacing: 10) {
            Text("When did your last period start?")
                .font(.cyclunaSerif(22))
                .foregroundStyle(Theme.ink)
                .multilineTextAlignment(.center)
                .padding(.top, 28)
                .padding(.horizontal, 24)

            // Past-only, like every period date in the app: a future anchor makes all the
            // derived values nonsense.
            DatePicker("Period start date", selection: $date, in: ...Date.now,
                       displayedComponents: .date)
                .datePickerStyle(.graphical)
                .tint(Theme.primary)
                .labelsHidden()
                .padding(.horizontal, 12)

            Text(store.previewLine(anchor: date))
                .font(.footnote)
                .foregroundStyle(Theme.inkSoft)
                .padding(.horizontal, 24)

            Button {
                store.lastPeriodStart = date
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
