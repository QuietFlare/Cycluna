import SwiftUI
import Shared

/// The logging hub — mood, headache, and notes in one place, with a date-browsable
/// timeline of what's been logged. Everything is on-device; tiles log the selected day
/// (default today) so you can also fill in a missed day. Journal text is plaintext for
/// now (an `enc:v1` E2EE port comes later per the migration plan).
struct JournalView: View {
    @Environment(CycleStore.self) private var store
    @State private var selectedDate = Date()
    @State private var moodOpen = false
    @State private var headacheOpen = false
    @State private var noteOpen = false
    @State private var editingNote: EditingNote?
    @State private var editingHeadache: EditingHeadache?
    @State private var showDatePicker = false

    /// Identifiable wrappers so KMP values can drive `.sheet(item:)`.
    private struct EditingNote: Identifiable { let id = UUID(); let entry: JournalEntry }
    private struct EditingHeadache: Identifiable { let id = UUID(); let log: HeadacheLog }

    private let cal = Calendar.current
    private var iso: String { store.isoDay(selectedDate) }
    private var isToday: Bool { cal.isDateInToday(selectedDate) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    tiles
                    timelineCard
                    MoodPatternsCard()
                    if let hi = store.headacheInsight { HeadacheInsightCard(insight: hi) }
                }
                .padding()
            }
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle("Your journal")
            .navigationBarTitleDisplayMode(.large)
            .sheet(isPresented: $moodOpen) { MoodSheet(dateISO: iso) }
            .sheet(isPresented: $headacheOpen) { HeadacheSheet(dateISO: iso) }
            .sheet(isPresented: $noteOpen) { NoteSheet(dateISO: iso) }
            .sheet(item: $editingNote) { NoteSheet(dateISO: iso, existing: $0.entry) }
            .sheet(item: $editingHeadache) { HeadacheSheet(dateISO: iso, existing: $0.log) }
            .sheet(isPresented: $showDatePicker) { datePickerSheet }
        }
    }

    private var tiles: some View {
        HStack(spacing: 12) {
            tile("Mood", "face.smiling", Theme.secondary) { moodOpen = true }
            tile("Headache", "bolt.heart", Theme.accentText) { headacheOpen = true }
            tile("Note", "square.and.pencil", Theme.primary) { noteOpen = true }
        }
    }

    private func tile(_ label: String, _ icon: String, _ color: Color, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 10) {
                Image(systemName: icon).font(.system(size: 26)).foregroundStyle(color)
                Text(label).font(.cyclunaSerif(17)).foregroundStyle(Theme.ink)
            }
            .frame(maxWidth: .infinity).frame(height: 96)
            .background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: 22))
            .overlay(RoundedRectangle(cornerRadius: 22).stroke(color.opacity(0.18)))
        }
        .buttonStyle(.plain)
    }

    private var timelineCard: some View {
        let mood = store.mood(on: iso)
        let headaches = store.headaches(on: iso)
        let notes = store.journalEntries(on: iso)
        let empty = mood == nil && headaches.isEmpty && notes.isEmpty

        return VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(isToday ? "Today" : selectedDate.formatted(.dateTime.month().day().year()))
                    .font(.cyclunaSerif(22)).foregroundStyle(Theme.ink)
                Spacer()
                if !isToday {
                    Button { selectedDate = Date() } label: {
                        Image(systemName: "xmark.circle.fill").foregroundStyle(Theme.inkSoft)
                    }
                }
                Button { showDatePicker = true } label: {
                    Image(systemName: "calendar").foregroundStyle(Theme.primary)
                }
            }

            if empty {
                Text(isToday ? "Nothing logged today — tap a tile above to start."
                             : "Nothing logged on this day.")
                    .font(.callout).italic().foregroundStyle(Theme.inkSoft)
                    .frame(maxWidth: .infinity).padding(.vertical, 10)
            } else {
                if let mood { entryRow(icon: MoodScale.emoji(Int(mood.mood)), isEmoji: true,
                                       title: "Mood · \(MoodScale.label(Int(mood.mood)))",
                                       note: mood.note, tint: Theme.secondary) { moodOpen = true } }
                ForEach(headaches, id: \.id) { h in
                    entryRow(icon: "bolt.heart", isEmoji: false,
                             title: headacheTitle(h), note: headacheDetail(h),
                             tint: Theme.accentText) { editingHeadache = EditingHeadache(log: h) }
                }
                ForEach(notes, id: \.id) { n in
                    entryRow(icon: "text.alignleft", isEmoji: false, title: "Note",
                             note: n.text, tint: Theme.primary) { editingNote = EditingNote(entry: n) }
                }
            }
        }
        .cyclunaCard()
    }

    private func entryRow(icon: String, isEmoji: Bool, title: String, note: String,
                          tint: Color, tap: @escaping () -> Void) -> some View {
        Button(action: tap) {
            HStack(alignment: .top, spacing: 12) {
                Group {
                    if isEmoji { Text(icon).font(.system(size: 22)) }
                    else { Image(systemName: icon).foregroundStyle(tint).font(.system(size: 18)).frame(width: 24) }
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.subheadline.weight(.medium)).foregroundStyle(Theme.ink)
                    if !note.isEmpty {
                        Text(note).font(.footnote).foregroundStyle(Theme.inkSoft)
                            .lineLimit(3).multilineTextAlignment(.leading)
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(.vertical, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func headacheTitle(_ h: HeadacheLog) -> String {
        var parts = ["Headache"]
        let tf = DateFormatter(); tf.dateFormat = "yyyy-MM-dd'T'HH:mm"; tf.timeZone = .current
        if let d = tf.date(from: h.at) { parts.append(d.formatted(date: .omitted, time: .shortened)) }
        parts.append(HeadacheScale.label(Int(h.intensity)))
        return parts.joined(separator: " · ")
    }

    private func headacheDetail(_ h: HeadacheLog) -> String {
        var bits: [String] = []
        if !h.symptoms.isEmpty { bits.append(h.symptoms.joined(separator: ", ")) }
        if !h.triggers.isEmpty { bits.append("Triggers: " + h.triggers.joined(separator: ", ")) }
        if !h.note.isEmpty { bits.append(h.note) }
        return bits.joined(separator: " · ")
    }

    private var datePickerSheet: some View {
        NavigationStack {
            DatePicker("Pick a day", selection: $selectedDate, in: ...Date.now, displayedComponents: .date)
                .datePickerStyle(.graphical)
                .tint(Theme.primary)
                .padding()
                .navigationTitle("Browse by day")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) { Button("Done") { showDatePicker = false } }
                }
        }
        .presentationDetents([.medium, .large])
    }
}
