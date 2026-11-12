// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI

/// Adds a weigh-in, or changes one: the day, the weight, and body fat if it's known. A day has
/// one weigh-in, so saving on a day that has one replaces it.
struct WeighInSheet: View {
    let draft: WeighInDraft
    let formatter: WeightFormatter

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var date: Date
    @State private var weight: String
    @State private var bodyFat: String
    @State private var problem: String?
    @FocusState private var focus: Field?

    private enum Field {
        case weight
        case bodyFat
    }

    /// What people can type, in kilograms and percent.
    static let weights = 20.0...400
    static let bodyFats = 2.0...70

    init(draft: WeighInDraft, formatter: WeightFormatter) {
        self.draft = draft
        self.formatter = formatter
        let entry = draft.entry
        _date = State(initialValue: entry.map { EpochDay.date($0.date) } ?? .now)
        _weight = State(initialValue: entry?.weightKg.map { formatter.bodyNumber(kilograms: $0) } ?? "")
        _bodyFat = State(initialValue: entry?.bodyFatPercent.map { formatter.tenthsOrWhole($0) } ?? "")
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    DatePicker("Day", selection: $date, in: ...Date.now, displayedComponents: .date)
                        .listRowBackground(Color.card)
                    field("Weight", text: $weight, unit: formatter.unit.symbol, field: .weight)
                    field("Body fat", text: $bodyFat, unit: "%", field: .bodyFat)
                } footer: {
                    VStack(alignment: .leading, spacing: 6) {
                        if let problem {
                            Text(problem)
                                .foregroundStyle(.red)
                        }
                        if let replaced {
                            Text("Replaces the weigh-in on \(HistoryDates.dayAndMonth(replaced.date)).")
                                .foregroundStyle(.inkSoft)
                        }
                        Text("Body fat is optional. Weighing in at the same time of day, like first thing in the morning, makes the line steadier.")
                            .foregroundStyle(.inkSoft)
                    }
                }

                if let entry = draft.entry {
                    Section {
                        Button("Delete weigh-in", role: .destructive) {
                            model.deleteWeighIn(entry.id)
                            dismiss()
                        }
                        .listRowBackground(Color.card)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Color.paper)
            .navigationTitle(draft.entry == nil ? "Add weigh-in" : "Weigh-in")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", systemImage: "xmark") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save", systemImage: "checkmark", action: save)
                        .disabled(isBlank)
                }
            }
        }
        .presentationDetents([.medium, .large])
        .onAppear { focus = draft.entry == nil ? .weight : nil }
        .onChange(of: weight) { problem = nil }
        .onChange(of: bodyFat) { problem = nil }
    }

    private func field(_ title: String, text: Binding<String>, unit: String, field: Field) -> some View {
        LabeledContent(title) {
            HStack(spacing: 6) {
                TextField("–", text: text)
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.trailing)
                    .font(.kitaeMono(17, relativeTo: .body, bold: true))
                    .focused($focus, equals: field)
                Text(unit)
                    .font(.kitaeMono(15, relativeTo: .body))
                    .foregroundStyle(.inkSoft)
            }
        }
        .listRowBackground(Color.card)
    }

    private var isBlank: Bool {
        weight.trimmingCharacters(in: .whitespaces).isEmpty && bodyFat.trimmingCharacters(in: .whitespaces).isEmpty
    }

    /// Another day's weigh-in that saving would replace.
    private var replaced: BodyEntry? {
        let day = EpochDay.of(date)
        return model.bodyLog.entries.first { $0.date == day && $0.id != draft.entry?.id }
    }

    private func save() {
        let typedWeight = weight.trimmingCharacters(in: .whitespaces)
        let typedFat = bodyFat.trimmingCharacters(in: .whitespaces)
        var kilograms: Double?
        var percent: Double?
        if !typedWeight.isEmpty {
            guard let value = formatter.kilograms(from: typedWeight), Self.weights.contains(value) else {
                let low = formatter.body(kilograms: Self.weights.lowerBound)
                let high = formatter.body(kilograms: Self.weights.upperBound)
                problem = "Type a weight from \(low) to \(high)."
                return
            }
            kilograms = value
        }
        if !typedFat.isEmpty {
            guard let value = WeightFormatter.number(from: typedFat), Self.bodyFats.contains(value) else {
                problem = "Type body fat from 2 to 70%."
                return
            }
            percent = value
        }
        model.saveWeighIn(date: EpochDay.of(date), weightKg: kilograms, bodyFatPercent: percent, replacing: draft.entry?.id)
        dismiss()
    }
}

/// Asks for a height: in centimetres, or in feet and inches for people who weigh in pounds.
struct HeightSheet: View {
    let heightCm: Double?
    let formatter: HeightFormatter

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var centimetres: String
    @State private var feet: String
    @State private var inches: String
    @State private var problem: String?

    static let heights = 50.0...250

    init(heightCm: Double?, formatter: HeightFormatter) {
        self.heightCm = heightCm
        self.formatter = formatter
        let parts = heightCm.map(HeightFormatter.feetAndInches)
        _centimetres = State(initialValue: heightCm.map { "\(Int($0.rounded()))" } ?? "")
        _feet = State(initialValue: parts.map { "\($0.feet)" } ?? "")
        _inches = State(initialValue: parts.map { "\($0.inches)" } ?? "")
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    if formatter.usesFeet {
                        field("Feet", text: $feet, unit: "ft")
                        field("Inches", text: $inches, unit: "in")
                    } else {
                        field("Height", text: $centimetres, unit: "cm")
                    }
                } footer: {
                    if let problem {
                        Text(problem)
                            .foregroundStyle(.red)
                    }
                }

                if heightCm != nil {
                    Section {
                        Button("Clear height", role: .destructive) {
                            save(nil)
                        }
                        .listRowBackground(Color.card)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Color.paper)
            .navigationTitle("Height")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", systemImage: "xmark") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save", systemImage: "checkmark", action: submit)
                }
            }
        }
        .presentationDetents([.medium])
    }

    private func field(_ title: String, text: Binding<String>, unit: String) -> some View {
        LabeledContent(title) {
            HStack(spacing: 6) {
                TextField("–", text: text)
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.trailing)
                    .font(.kitaeMono(17, relativeTo: .body, bold: true))
                Text(unit)
                    .font(.kitaeMono(15, relativeTo: .body))
                    .foregroundStyle(.inkSoft)
            }
        }
        .listRowBackground(Color.card)
    }

    private func submit() {
        let height: Double?
        if formatter.usesFeet {
            let wholeFeet = WeightFormatter.number(from: feet).map { Int($0) }
            let moreInches = WeightFormatter.number(from: inches) ?? 0
            height = wholeFeet.map { HeightFormatter.centimetres(feet: $0, inches: moreInches) }
        } else {
            height = WeightFormatter.number(from: centimetres)
        }
        guard let height, Self.heights.contains(height) else {
            problem = formatter.usesFeet ? "Type a height from 1 ft 8 in to 8 ft 2 in." : "Type a height from 50 to 250 cm."
            return
        }
        save(height)
    }

    private func save(_ height: Double?) {
        var profile = model.bodyLog.profile
        profile.heightCm = height
        model.saveProfile(profile)
        dismiss()
    }
}

/// Asks for a date of birth, for the age that energy estimates use.
struct BirthDateSheet: View {
    let birthDate: Int?

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var date: Date

    init(birthDate: Int?) {
        self.birthDate = birthDate
        let thirtyYearsAgo = Calendar.current.date(byAdding: .year, value: -30, to: .now) ?? .now
        _date = State(initialValue: birthDate.map { EpochDay.date($0) } ?? thirtyYearsAgo)
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                DatePicker("Date of birth", selection: $date, in: Self.range, displayedComponents: .date)
                    .datePickerStyle(.wheel)
                    .labelsHidden()
                if birthDate != nil {
                    Button("Clear date of birth", role: .destructive) {
                        save(nil)
                    }
                }
            }
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .background(Color.paper)
            .navigationTitle("Date of birth")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", systemImage: "xmark") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save", systemImage: "checkmark") {
                        save(EpochDay.of(date))
                    }
                }
            }
        }
        .presentationDetents([.medium])
    }

    /// From 1900 to five years ago.
    private static var range: ClosedRange<Date> {
        let calendar = Calendar.current
        let first = calendar.date(from: DateComponents(year: 1900, month: 1, day: 1)) ?? .distantPast
        let last = calendar.date(byAdding: .year, value: -5, to: .now) ?? .now
        return first...last
    }

    private func save(_ day: Int?) {
        var profile = model.bodyLog.profile
        profile.birthDate = day
        model.saveProfile(profile)
        dismiss()
    }
}
