// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI

/// A past workout as it was saved.
struct WorkoutRecord {
    var summary: WorkoutSummary
    var exercises: [WorkoutExercise]
    /// The workout before it of the same day.
    var previousID: Int64?
}

/// A past workout: what was done, set by set, and the workout of the same day before it.
struct WorkoutDetailView: View {
    let id: Int64

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var record: WorkoutRecord?
    @State private var hasLoaded = false
    @State private var editing: PlanEditor?
    @State private var confirmsDelete = false

    var body: some View {
        List {
            if let record {
                Section {
                    if record.exercises.isEmpty {
                        Text("No sets were saved for this workout.")
                            .font(.subheadline)
                            .foregroundStyle(.inkSoft)
                            .listRowBackground(Color.clear)
                    }
                } header: {
                    header(record.summary)
                        .textCase(nil)
                        .listRowInsets(EdgeInsets(top: 0, leading: 8, bottom: 4, trailing: 8))
                }
                ForEach(record.exercises) { exercise in
                    exerciseSection(exercise)
                }
            }
        }
        .listStyle(.insetGrouped)
        .listSectionSpacing(14)
        .scrollContentBackground(.hidden)
        .background(Color.paper)
        .overlay {
            if hasLoaded, record == nil {
                ContentUnavailableView("This workout was deleted", systemImage: "clock.arrow.circlepath")
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { toolbar }
        .navigationDestination(item: $editing) { editor in
            PlanEditView(editor: editor)
        }
        .confirmationDialog("Delete this workout?", isPresented: $confirmsDelete, titleVisibility: .visible) {
            Button("Delete workout", role: .destructive) {
                model.deleteWorkout(id)
                dismiss()
            }
        } message: {
            Text("It will be removed with its sets. Your plan stays the same.")
        }
        .task(id: model.historyRevision) { load() }
    }

    private var today: Int { EpochDay.of(.now) }

    /// The energy the workout used, from its done sets.
    private var kilocalories: Double? {
        guard let record else { return nil }
        return model.activeKilocalories(record.summary, sets: record.exercises.flatMap(\.doneSets))
    }

    private func header(_ summary: WorkoutSummary) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(summary.title)
                .font(.kitaeDisplay(46))
                .foregroundStyle(summary.isSkipped ? Color.inkSoft : Color.ink)
                .lineLimit(2)
                .minimumScaleFactor(0.5)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            Caption(HistoryDates.long(summary.date, today: today))
            Caption(summary.isSkipped ? ".skipped" : workoutDetails(summary, kilocalories: kilocalories))
        }
    }

    private func exerciseSection(_ exercise: WorkoutExercise) -> some View {
        let labels = setLabels(for: exercise.sets.map(\.type))
        return Section {
            NavigationLink(value: AppRoute.exercise(exercise.id)) {
                HStack(spacing: 10) {
                    Text(exercise.exercise.name)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(.ink)
                    Spacer(minLength: 8)
                    Text(exercise.exercise.muscle?.name ?? exercise.exercise.target)
                        .font(.footnote)
                        .foregroundStyle(.inkSoft)
                }
                .frame(minHeight: 54)
            }
            .alignmentGuide(.listRowSeparatorLeading) { _ in 28 }
            .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
            .listRowBackground(Color.card)
            .accessibilityHint("Shows the exercise's page")

            ForEach(Array(exercise.sets.enumerated()), id: \.element.id) { index, set in
                setRow(set, label: labels[index], of: exercise.exercise)
            }
        }
    }

    private func setRow(_ set: WorkoutSet, label: String, of exercise: Exercise) -> some View {
        let summary = model.weightFormatter.summary(reps: set.reps, kilograms: exercise.weight(set.weightKg), isIsometric: exercise.isIsometric)
        return HStack(spacing: 12) {
            Text(label)
                .font(.kitaeMono(15, relativeTo: .body, bold: true))
                .foregroundStyle(.inkSoft)
                .frame(width: 28, alignment: .leading)
            Text(summary)
                .font(.kitaeMono(15, relativeTo: .body))
                .foregroundStyle(set.isDone ? Color.ink : Color.inkSoft)
            Spacer(minLength: 8)
            if !set.isDone {
                Text(".skipped")
                    .font(.kitaeMono(12, relativeTo: .caption))
                    .foregroundStyle(.inkSoft)
            }
        }
        .frame(minHeight: 44)
        .alignmentGuide(.listRowSeparatorLeading) { _ in 0 }
        .listRowInsets(EdgeInsets(top: 0, leading: 44, bottom: 0, trailing: 16))
        .listRowBackground(Color.card)
        .accessibilityElement(children: .combine)
    }

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        if let record, let previousID = record.previousID {
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink(value: AppRoute.workout(previousID)) {
                    Label("Previous \(record.summary.title)", systemImage: "clock.arrow.circlepath")
                }
            }
        }
        if let record {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    if let planID = record.summary.planID, let dayID = record.summary.dayID {
                        Button("Edit \(record.summary.title)", systemImage: "pencil") {
                            editing = model.planEditor(for: PlanEditRoute(planID: planID, dayID: dayID))
                        }
                    }
                    Button("Delete workout", systemImage: "trash", role: .destructive) {
                        confirmsDelete = true
                    }
                } label: {
                    Label("More", systemImage: "ellipsis")
                }
            }
        }
    }

    private func load() {
        let id = id
        do {
            record = try model.database.read { db -> WorkoutRecord? in
                guard let summary = try Sessions.summary(db, id: id) else { return nil }
                return WorkoutRecord(
                    summary: summary,
                    exercises: try Sessions.exercises(db, sessionID: id, dayID: nil),
                    previousID: try Sessions.previousID(db, before: id, dayID: summary.dayID, date: summary.date)
                )
            }
        } catch {
            model.problem = "Couldn't read the workout. \(error.localizedDescription)"
        }
        hasLoaded = true
    }
}
