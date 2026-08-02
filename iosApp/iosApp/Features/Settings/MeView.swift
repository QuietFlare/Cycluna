import SwiftUI

/// Identifiable wrapper so a file URL can drive `.sheet(item:)`.
private struct ExportItem: Identifiable {
    let id = UUID()
    let url: URL
}

/// Bridges UIKit's share sheet into SwiftUI for exporting the data file.
private struct ActivityView: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

struct MeView: View {
    @Environment(CycleStore.self) private var store
    @AppStorage("appTheme") private var themeRaw = AppTheme.system.rawValue
    @AppStorage("appLockEnabled") private var lockEnabled = false
    @AppStorage(ReminderSettings.Key.periodOn) private var periodReminders = false
    @AppStorage(ReminderSettings.Key.ovulationOn) private var ovulationReminders = false
    @AppStorage(ReminderSettings.Key.periodLead) private var periodLead = ReminderSettings.defaultPeriodLead
    @AppStorage(ReminderSettings.Key.ovulationLead) private var ovulationLead = ReminderSettings.defaultOvulationLead
    @AppStorage(ReminderSettings.Key.cycleMinute) private var cycleMinute = ReminderSettings.defaultCycleMinute
    @AppStorage(ReminderSettings.Key.moodCheckIn) private var moodCheckIn = false
    @AppStorage(ReminderSettings.Key.headacheCheckIn) private var headacheCheckIn = false
    @AppStorage(ReminderSettings.Key.checkInMinute) private var checkInMinute = ReminderSettings.defaultCheckInMinute
    @State private var confirmDelete = false
    @State private var exportItem: ExportItem?
    @State private var pendingExportURL: URL?

    var body: some View {
        @Bindable var store = store
        NavigationStack {
            Form {
                Section {
                    TextField("Your name", text: $store.displayName)
                        .textInputAutocapitalization(.words)
                } header: {
                    Text("You")
                } footer: {
                    Text("We'll greet you by this on Home. Leave it blank for \u{201C}beautiful\u{201D}. Stays on this device.")
                }

                Section {
                    // A period can only have started in the past. A future anchor makes every
                    // derived value nonsense — cycle day clamps to 1, so the app would claim
                    // "Day 1 · Menstrual" for a period that hasn't happened. Matches the same
                    // bound already used in onboarding and the Journal day picker.
                    DatePicker("Last period start", selection: $store.lastPeriodStart,
                               in: ...Date.now, displayedComponents: .date)
                    Stepper("Cycle length: \(store.cycleLengthSetting) days", value: $store.cycleLengthSetting, in: 21...45)
                    Stepper("Period length: \(store.periodLength) days", value: $store.periodLength, in: 2...10)
                } header: {
                    Text("Cycle")
                } footer: {
                    Text("Cycle length auto-adjusts from your logged periods once there's enough history; otherwise this setting is used.")
                }

                Section("Appearance") {
                    Picker("Theme", selection: $themeRaw) {
                        Text("System").tag(AppTheme.system.rawValue)
                        Text("Light").tag(AppTheme.light.rawValue)
                        Text("Dark").tag(AppTheme.dark.rawValue)
                    }
                }

                Section {
                    Toggle("Period reminders", isOn: $periodReminders)
                        .onChange(of: periodReminders) { _, _ in remindersChanged() }
                    if periodReminders {
                        Picker("Remind me", selection: $periodLead) {
                            ForEach(leadOptions, id: \.self) { Text(leadLabel($0)).tag($0) }
                        }
                        .onChange(of: periodLead) { _, _ in remindersChanged() }
                    }

                    Toggle("Ovulation reminders", isOn: $ovulationReminders)
                        .onChange(of: ovulationReminders) { _, _ in remindersChanged() }
                    if ovulationReminders {
                        Picker("Remind me", selection: $ovulationLead) {
                            ForEach(leadOptions, id: \.self) { Text(leadLabel($0)).tag($0) }
                        }
                        .onChange(of: ovulationLead) { _, _ in remindersChanged() }
                    }

                    if periodReminders || ovulationReminders {
                        DatePicker("Time", selection: timeBinding($cycleMinute),
                                   displayedComponents: .hourAndMinute)
                            .onChange(of: cycleMinute) { _, _ in remindersChanged() }
                    }
                } header: {
                    Text("Cycle reminders")
                } footer: {
                    Text("A gentle heads-up before your period starts and when your fertile window opens. Local only — no push server.")
                }

                Section {
                    Toggle("Mood check-in", isOn: $moodCheckIn)
                        .onChange(of: moodCheckIn) { _, _ in remindersChanged() }
                    Toggle("Headache check-in", isOn: $headacheCheckIn)
                        .onChange(of: headacheCheckIn) { _, _ in remindersChanged() }
                    if moodCheckIn || headacheCheckIn {
                        DatePicker("Time", selection: timeBinding($checkInMinute),
                                   displayedComponents: .hourAndMinute)
                            .onChange(of: checkInMinute) { _, _ in remindersChanged() }
                    }
                } header: {
                    Text("Daily check-in")
                } footer: {
                    Text("A daily nudge to log how you're feeling. With both on you get one reminder, not two.")
                }

                Section {
                    Toggle("Require Face ID to open", isOn: $lockEnabled)
                    NavigationLink("Privacy Policy") { PrivacyPolicyView() }
                } header: {
                    Text("Privacy")
                } footer: {
                    Text("Locks the app behind Face ID, Touch ID, or your device passcode — so your data stays private even on an unlocked phone.")
                }

                // Required by App Store Guideline 5.1.1(v) + GDPR/CCPA. All on-device.
                Section {
                    Button("Export my data") {
                        let item = store.exportFileURL().map(ExportItem.init)
                        pendingExportURL = item?.url
                        exportItem = item
                    }
                    Button("Delete all my data", role: .destructive) { confirmDelete = true }
                } header: {
                    Text("Your data")
                } footer: {
                    Text("Everything is stored only on this device. Export saves a JSON file you can share or keep; deleting erases it permanently.")
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle("Me")
            .confirmationDialog(
                "Delete all your data? This erases everything on this device and cannot be undone.",
                isPresented: $confirmDelete, titleVisibility: .visible
            ) {
                Button("Delete everything", role: .destructive) { deleteEverything() }
                Button("Cancel", role: .cancel) {}
            }
            .sheet(item: $exportItem, onDismiss: {
                if let url = pendingExportURL { try? FileManager.default.removeItem(at: url) }
                pendingExportURL = nil
            }) { item in
                ActivityView(items: [item.url])
            }
        }
    }

    // MARK: - Reminders

    /// How far ahead a cycle reminder can fire. Kept short — beyond a couple of days the
    /// prediction isn't precise enough for the reminder to mean much.
    private let leadOptions = [2, 1, 0]

    private func leadLabel(_ days: Int) -> String {
        switch days {
        case 0: return "On the day"
        case 1: return "Day before"
        default: return "\(days) days before"
        }
    }

    /// Bridges a "minutes from midnight" setting to the `Date` a `DatePicker` wants.
    private func timeBinding(_ minutes: Binding<Int>) -> Binding<Date> {
        Binding(
            get: {
                Calendar.current.date(
                    bySettingHour: minutes.wrappedValue / 60,
                    minute: minutes.wrappedValue % 60, second: 0, of: .now
                ) ?? .now
            },
            set: { newDate in
                let c = Calendar.current.dateComponents([.hour, .minute], from: newDate)
                minutes.wrappedValue = (c.hour ?? 9) * 60 + (c.minute ?? 0)
            }
        )
    }

    /// Erase the stored data *and* everything derived from it. Reminders are cancelled here
    /// rather than waiting for the next foreground refresh — otherwise a notification predicted
    /// from now-deleted data could still fire if the user never reopens the app.
    private func deleteEverything() {
        store.deleteAllData()
        periodReminders = false
        ovulationReminders = false
        moodCheckIn = false
        headacheCheckIn = false
        ReminderManager.cancelAll()
    }

    /// Ask permission the first time any reminder is switched on, then (re)schedule.
    private func remindersChanged() {
        Task {
            let settings = ReminderSettings.current
            if settings.anyCycleReminder || settings.anyCheckIn {
                await ReminderManager.requestAuthorization()
            }
            let logged = store.hasLoggedPeriod
            ReminderManager.reschedule(
                nextPeriod: logged ? store.nextPeriodDate : nil,
                fertileStart: logged ? store.fertileStartDate : nil,
                settings: settings)
        }
    }
}
