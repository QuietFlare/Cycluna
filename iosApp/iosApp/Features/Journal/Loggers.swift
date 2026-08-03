import SwiftUI
import Shared

// Shared vocabulary for the mood scale.
enum MoodScale {
    static let emojis = ["😫", "😕", "😐", "🙂", "😄"]
    static let labels = ["Rough", "Meh", "Mid", "Good", "Lit"]
    static func emoji(_ v: Int) -> String { emojis[min(max(v - 1, 0), 4)] }
    static func label(_ v: Int) -> String { labels[min(max(v - 1, 0), 4)] }
    /// Diverging valence ramp: deep red fades to a NEUTRAL midpoint, then greens deepen.
    /// The old scale reused brand colours (rose for "meh" read as positive, and "good"/"lit"
    /// were two near-identical greens). These five are validated for adjacent-step
    /// distinguishability, incl. colour-blind simulation, against the cream card surface.
    /// Colour is never the only carrier: the legend labels each dot, charts encode mood by
    /// position, and the sheets pair every value with its emoji.
    static func color(_ v: Int) -> Color {
        switch v {
        case 1:  return Color(hex: "B03A3A")   // rough — deep red
        case 2:  return Color(hex: "EC9A82")   // meh — washed salmon
        case 3:  return Color(hex: "847A6B")   // mid — neutral warm grey (the midpoint)
        case 4:  return Color(hex: "6DBA7F")   // good — light green
        default: return Color(hex: "2E7B50")   // lit — deep green
        }
    }
}

enum HeadacheScale {
    static let labels = ["", "Mild", "Moderate", "Severe"]
    static func label(_ v: Int) -> String { labels[min(max(v, 0), 3)] }
}

/// Log or edit the mood for a given day.
struct MoodSheet: View {
    @Environment(CycleStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    let dateISO: String

    @State private var vibe: Int?
    @State private var note = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("How's the vibe?") {
                    HStack(spacing: 6) {
                        ForEach(1...5, id: \.self) { v in
                            Button {
                                withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) { vibe = v }
                            } label: {
                                VStack(spacing: 4) {
                                    Text(MoodScale.emoji(v))
                                        .font(.system(size: 28))
                                        .opacity(vibe == nil || vibe == v ? 1 : 0.4)
                                    Text(MoodScale.label(v))
                                        .font(.caption2)
                                        .foregroundStyle(vibe == v ? Theme.primary : Theme.inkSoft)
                                }
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 6)
                                .background(vibe == v ? Theme.primary.opacity(0.1) : .clear,
                                            in: RoundedRectangle(cornerRadius: 12))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                Section("Note (optional)") {
                    TextField("What's on your mind?", text: $note, axis: .vertical).lineLimit(2...5)
                }
                if store.mood(on: dateISO) != nil {
                    Button("Remove mood", role: .destructive) { store.clearMood(on: dateISO); dismiss() }
                }
            }
            .navigationTitle("Mood").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        if let v = vibe {
                            store.logMood(v, note: note.trimmingCharacters(in: .whitespacesAndNewlines), on: dateISO)
                        }
                        dismiss()
                    }
                    .disabled(vibe == nil)
                }
            }
            .onAppear {
                if let m = store.mood(on: dateISO) { vibe = Int(m.mood); note = m.note }
            }
        }
    }
}

/// Log or edit one headache / migraine episode. Multiple per day are supported, so this
/// always adds a new episode unless editing an [existing] one.
struct HeadacheSheet: View {
    @Environment(CycleStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    let dateISO: String
    var existing: HeadacheLog?

    @State private var intensity = 2
    @State private var time = Date()
    @State private var symptoms: Set<String> = []
    @State private var triggers: Set<String> = []
    @State private var note = ""

    private let symptomOptions = ["Throbbing", "One-sided", "Nausea", "Light sensitivity",
                                  "Sound sensitivity", "Visual aura", "Tingling"]
    private let triggerOptions = ["Poor sleep", "Skipped meal", "Stress", "Dehydration",
                                  "Caffeine", "Alcohol", "Weather", "Strong smells", "Hormones"]

    var body: some View {
        NavigationStack {
            Form {
                Section("Intensity") {
                    Picker("Intensity", selection: $intensity) {
                        Text("Mild").tag(1); Text("Moderate").tag(2); Text("Severe").tag(3)
                    }
                    .pickerStyle(.segmented)
                }
                Section("Time") {
                    DatePicker("Onset", selection: $time, displayedComponents: .hourAndMinute)
                }
                Section("Symptoms") { ChipWrap(options: symptomOptions, selection: $symptoms) }
                Section("Possible triggers") { ChipWrap(options: triggerOptions, selection: $triggers) }
                Section("Note (optional)") {
                    TextField("Anything to remember", text: $note, axis: .vertical).lineLimit(2...4)
                }
                if existing != nil {
                    Button("Delete this episode", role: .destructive) {
                        store.deleteHeadache(id: existing!.id); dismiss()
                    }
                }
            }
            .navigationTitle(existing == nil ? "Headache" : "Edit headache")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Save") { save() } }
            }
            .onAppear(perform: prefill)
        }
    }

    private func save() {
        let at = combinedDate()
        let n = note.trimmingCharacters(in: .whitespacesAndNewlines)
        if let e = existing {
            store.updateHeadache(id: e.id, intensity: intensity, symptoms: Array(symptoms),
                                 triggers: Array(triggers), note: n, at: at)
        } else {
            store.addHeadache(intensity: intensity, symptoms: Array(symptoms),
                              triggers: Array(triggers), note: n, at: at)
        }
        dismiss()
    }

    /// Combine the browsed day with the chosen time.
    private func combinedDate() -> Date {
        let cal = Calendar.current
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; f.timeZone = .current
        let day = f.date(from: dateISO) ?? Date()
        let tc = cal.dateComponents([.hour, .minute], from: time)
        return cal.date(bySettingHour: tc.hour ?? 9, minute: tc.minute ?? 0, second: 0, of: day) ?? day
    }

    private func prefill() {
        guard let e = existing else { return }
        intensity = Int(e.intensity)
        symptoms = Set(e.symptoms)
        triggers = Set(e.triggers)
        note = e.note
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd'T'HH:mm"; f.timeZone = .current
        if let d = f.date(from: e.at) { time = d }
    }
}

/// A wrapping set of multi-select chips.
struct ChipWrap: View {
    let options: [String]
    @Binding var selection: Set<String>

    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 104), spacing: 8)], alignment: .leading, spacing: 8) {
            ForEach(options, id: \.self) { opt in
                let on = selection.contains(opt)
                Button {
                    if on { selection.remove(opt) } else { selection.insert(opt) }
                } label: {
                    Text(opt)
                        .font(.footnote)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 7).padding(.horizontal, 8)
                        .foregroundStyle(on ? .white : Theme.ink)
                        .background(on ? AnyShapeStyle(Theme.primary) : AnyShapeStyle(Theme.background),
                                    in: Capsule())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.vertical, 4)
    }
}

/// "Headaches & your cycle" — the hormonal-cluster insight, only shown when confident.
struct HeadacheInsightCard: View {
    let insight: HeadacheInsight

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "bolt.heart.fill").foregroundStyle(Theme.secondary)
            VStack(alignment: .leading, spacing: 4) {
                Text("Headaches & your cycle").font(.cyclunaSerif(19)).foregroundStyle(Theme.ink)
                Text("Your headaches tend to cluster in your \(phaseWord(insight.phase)) — a common hormonal pattern (\(Int(insight.count)) of \(Int(insight.total)) so far).")
                    .font(.footnote).foregroundStyle(Theme.inkSoft)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .cyclunaCard()
    }

    private func phaseWord(_ phase: Phase) -> String {
        switch phase.label {
        case "Ovulatory":  return "ovulation window"
        case "Follicular": return "follicular phase"
        case "Luteal":     return "luteal phase"
        default:           return "period"
        }
    }
}

/// Write a new journal note, or edit an existing one, for a given day.
struct NoteSheet: View {
    @Environment(CycleStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    let dateISO: String
    var existing: JournalEntry?

    @State private var text = ""

    /// UI-level cap only. The core stays permissive so an imported entry longer than this
    /// is never silently truncated on the way in.
    private static let maxLength = 2_000

    private var remaining: Int { Self.maxLength - text.count }

    var body: some View {
        NavigationStack {
            VStack(alignment: .trailing, spacing: 4) {
                TextEditor(text: $text)
                    .font(.system(.body, design: .serif))
                    .onChange(of: text) { _, new in
                        if new.count > Self.maxLength { text = String(new.prefix(Self.maxLength)) }
                    }
                // Only surface the limit as it comes into view — a counter on an empty note
                // is just clutter.
                if remaining <= 200 {
                    Text("\(remaining) characters left")
                        .font(.caption2)
                        .foregroundStyle(remaining <= 0 ? Theme.phaseMenstrual : Theme.inkSoft)
                }
            }
            .padding()
                .navigationTitle(existing == nil ? "New note" : "Edit note")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Save") {
                            let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
                            if !t.isEmpty {
                                if let e = existing {
                                    store.updateJournalEntry(id: e.id, text: t, on: dateISO)
                                } else {
                                    store.addJournalEntry(text: t, on: dateISO)
                                }
                            }
                            dismiss()
                        }
                    }
                    if let e = existing {
                        ToolbarItem(placement: .bottomBar) {
                            Button("Delete", role: .destructive) { store.deleteJournalEntry(id: e.id); dismiss() }
                        }
                    }
                }
                .onAppear { if let e = existing { text = e.text } }
        }
    }
}
