// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI

/// A weigh-in sheet's start: a weigh-in to change, or nil for a new one.
struct WeighInDraft: Identifiable {
    let id = UUID()
    var entry: BodyEntry?
}

/// Which line the Body page's chart draws.
enum BodyChartKind: Hashable, CaseIterable {
    case weight
    case bodyFat
    case lean

    var title: String {
        switch self {
        case .weight: "Weight"
        case .bodyFat: "Body fat"
        case .lean: "Lean mass"
        }
    }
}

/// The Body page, from You: the latest weigh-in and how weight and body fat have gone, every
/// weigh-in, and what people tell Kitae about themselves. All of it is optional, and stays on
/// the iPhone and in Kitae's backups.
struct BodyView: View {
    @Environment(AppModel.self) private var model
    @State private var weighIn: WeighInDraft?
    @State private var chart = BodyChartKind.weight
    @State private var editsHeight = false
    @State private var editsBirthDate = false

    var body: some View {
        let log = model.bodyLog
        List {
            Section {
                summary(log)
                    .listRowInsets(EdgeInsets(top: 16, leading: 18, bottom: 14, trailing: 18))
                    .listRowBackground(Color.card)
            }

            Section {
                if log.entries.isEmpty {
                    Button {
                        weighIn = WeighInDraft()
                    } label: {
                        Label("Add a weigh-in", systemImage: "plus")
                            .foregroundStyle(.olive)
                    }
                    .listRowBackground(Color.card)
                }
                ForEach(log.entries.reversed()) { entry in
                    entryRow(entry)
                }
            } header: {
                SectionCaption(".weigh-ins")
            }

            Section {
                profileRows(log.profile)
            } header: {
                SectionCaption(".about you")
            } footer: {
                Text("All optional, and kept on this iPhone and in Kitae's backups. Kitae estimates the energy your workouts use from the sets you do and your weight, and your height makes it closer. Sex, age and body fat mainly change the energy you use at rest, which isn't part of it, so they're left out. Either way, it's an estimate.")
                    .foregroundStyle(.inkSoft)
            }
        }
        .listSectionSpacing(20)
        .environment(\.defaultMinListRowHeight, 52)
        .scrollContentBackground(.hidden)
        .background(Color.paper)
        .navigationTitle("Body")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Add weigh-in", systemImage: "plus") {
                    weighIn = WeighInDraft()
                }
            }
        }
        .sheet(item: $weighIn) { draft in
            WeighInSheet(draft: draft, formatter: model.weightFormatter)
        }
        .sheet(isPresented: $editsHeight) {
            HeightSheet(heightCm: log.profile.heightCm, formatter: HeightFormatter(unit: model.settings.unit))
        }
        .sheet(isPresented: $editsBirthDate) {
            BirthDateSheet(birthDate: log.profile.birthDate)
        }
    }

    // MARK: Latest

    @ViewBuilder
    private func summary(_ log: BodyLog) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            if let latest = log.latestWeight, let kg = latest.weightKg {
                VStack(alignment: .leading, spacing: 4) {
                    Caption(".latest · \(HistoryDates.dayAndMonth(latest.date))", size: 12)
                    Text(model.weightFormatter.body(kilograms: kg))
                        .font(.kitaeDisplay(46, relativeTo: .largeTitle))
                        .foregroundStyle(.olive)
                        .padding(.vertical, -8)
                    if let composition = composition(log) {
                        Text(composition)
                            .font(.footnote)
                            .foregroundStyle(.inkSoft)
                    }
                }
                .accessibilityElement(children: .combine)
            } else {
                VStack(alignment: .leading, spacing: 6) {
                    Caption(".no weigh-ins yet", size: 12)
                    Text("Track your weight")
                        .font(.kitaeDisplay(30, relativeTo: .title))
                        .foregroundStyle(.ink)
                        .padding(.vertical, -4)
                    Text("Add your weight, and body fat if you know it, to see how they go and the energy your workouts use.")
                        .font(.footnote)
                        .foregroundStyle(.inkSoft)
                }
            }
            chartSection(log)
        }
    }

    /// "18.5% body fat · 64.1 kg lean · 14.3 kg fat", from the latest weigh-in with body fat.
    private func composition(_ log: BodyLog) -> String? {
        guard let entry = log.latestBodyFat, let percent = entry.bodyFatPercent else { return nil }
        let formatter = model.weightFormatter
        var parts = ["\(formatter.percent(percent)) body fat"]
        if let lean = entry.leanKg, let fat = entry.fatKg {
            parts.append("\(formatter.body(kilograms: lean)) lean")
            parts.append("\(formatter.body(kilograms: fat)) fat")
        }
        return parts.joined(separator: " · ")
    }

    // MARK: Chart

    private struct Point {
        var date: Int
        var value: Double
    }

    /// Values in the unit people see.
    private func points(_ kind: BodyChartKind, _ log: BodyLog) -> [Point] {
        let unit = model.weightFormatter.unit
        return log.entries.compactMap { entry in
            switch kind {
            case .weight: entry.weightKg.map { Point(date: entry.date, value: unit.fromKilograms($0)) }
            case .bodyFat: entry.bodyFatPercent.map { Point(date: entry.date, value: $0) }
            case .lean: entry.leanKg.map { Point(date: entry.date, value: unit.fromKilograms($0)) }
            }
        }
    }

    @ViewBuilder
    private func chartSection(_ log: BodyLog) -> some View {
        let kinds = BodyChartKind.allCases.filter { points($0, log).count >= 2 }
        if let kind = kinds.contains(chart) ? chart : kinds.first {
            let points = points(kind, log)
            VStack(alignment: .leading, spacing: 10) {
                if kinds.count > 1 {
                    ChoiceBar(
                        title: "Chart",
                        selection: Binding(get: { kind }, set: { chart = $0 }),
                        choices: kinds,
                        name: \.title
                    )
                }
                ProgressChart(
                    values: points.map(\.value),
                    minimumStep: 0.5,
                    positions: points.map { Double($0.date) }
                ) { value in
                    model.weightFormatter.tenthsOrWhole(value)
                }
                .frame(height: 132)
                .accessibilityElement()
                .accessibilityLabel(chartDescription(kind, points))
                if let first = points.first, let last = points.last {
                    HStack {
                        Text(HistoryDates.dayAndMonth(first.date))
                        Spacer()
                        Text(HistoryDates.dayAndMonth(last.date))
                    }
                    .font(.kitaeMono(10, relativeTo: .caption2))
                    .foregroundStyle(.inkSoft)
                    .accessibilityHidden(true)
                }
            }
        }
    }

    /// "Your weight went from 80.2 kg on Sep 10 to 78.4 kg on Oct 16".
    private func chartDescription(_ kind: BodyChartKind, _ points: [Point]) -> String {
        guard let first = points.first, let last = points.last else { return "" }
        let formatter = model.weightFormatter
        let value: (Double) -> String = { value in
            kind == .bodyFat ? formatter.percent(value) : "\(formatter.tenthsOrWhole(value)) \(formatter.unit.symbol)"
        }
        return "Your \(kind.title.lowercased()) went from \(value(first.value)) on \(HistoryDates.dayAndMonth(first.date)) to \(value(last.value)) on \(HistoryDates.dayAndMonth(last.date))"
    }

    // MARK: Weigh-ins

    private func entryRow(_ entry: BodyEntry) -> some View {
        Button {
            weighIn = WeighInDraft(entry: entry)
        } label: {
            HStack(spacing: 12) {
                Text(HistoryDates.short(entry.date, today: EpochDay.of(.now)))
                    .foregroundStyle(.ink)
                Spacer(minLength: 8)
                Text(values(entry))
                    .font(.kitaeMono(15, relativeTo: .body))
                    .foregroundStyle(.inkSoft)
            }
            .contentShape(.rect)
        }
        .listRowBackground(Color.card)
        .accessibilityHint("Changes this weigh-in")
        .swipeActions(edge: .trailing) {
            Button("Delete", systemImage: "trash", role: .destructive) {
                model.deleteWeighIn(entry.id)
            }
        }
    }

    /// "78.4 kg · 18.5%".
    private func values(_ entry: BodyEntry) -> String {
        let formatter = model.weightFormatter
        return [entry.weightKg.map { formatter.body(kilograms: $0) }, entry.bodyFatPercent.map { formatter.percent($0) }]
            .compactMap { $0 }
            .joined(separator: " · ")
    }

    // MARK: About you

    @ViewBuilder
    private func profileRows(_ profile: BodyProfile) -> some View {
        let height = HeightFormatter(unit: model.settings.unit)
        valueRow("Height", profile.heightCm.map { height.string(centimetres: $0) }) {
            editsHeight = true
        }
        valueRow("Date of birth", profile.birthDate.map { birthDateLabel($0, profile) }) {
            editsBirthDate = true
        }
        MenuRow(
            title: "Sex",
            selection: Binding(get: { profile.sex }, set: { sex in update { $0.sex = sex } }),
            choices: [nil] + Sex.allCases.map { Optional($0) },
            name: { $0?.title ?? "Not set" }
        )
        .listRowBackground(Color.card)
        MenuRow(
            title: "Activity level",
            selection: Binding(get: { profile.activityLevel }, set: { level in update { $0.activityLevel = level } }),
            choices: [nil] + ActivityLevel.allCases.map { Optional($0) },
            name: { $0?.title ?? "Not set" },
            detail: { $0?.detail }
        )
        .listRowBackground(Color.card)
    }

    private func update(_ change: (inout BodyProfile) -> Void) {
        var profile = model.bodyLog.profile
        change(&profile)
        model.saveProfile(profile)
    }

    /// "May 14, 1996 · 30".
    private func birthDateLabel(_ day: Int, _ profile: BodyProfile) -> String {
        let date = EpochDay.date(day).formatted(.dateTime.year().month(.abbreviated).day())
        guard let age = profile.age(on: EpochDay.of(.now)) else { return date }
        return "\(date) · \(age)"
    }

    private func valueRow(_ title: String, _ value: String?, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            LabeledContent(title) {
                HStack(spacing: 6) {
                    Text(value ?? "Not set")
                        .foregroundStyle(.inkSoft)
                    Image(systemName: "chevron.forward")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.inkFaint)
                }
            }
            .foregroundStyle(.ink)
            .contentShape(.rect)
        }
        .listRowBackground(Color.card)
    }
}
