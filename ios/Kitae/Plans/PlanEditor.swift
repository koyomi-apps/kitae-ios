// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import GRDB
import Observation
import os

/// Where the plan editor opens: a plan, on one of its days.
struct PlanEditRoute: Hashable {
    var planID: Plan.ID
    var dayID: TrainingDay.ID?
}

/// One plan being edited. Like the workout, every change is saved straight away and the plan
/// is read back, as in Kitae for Android's plan editor.
@Observable
final class PlanEditor {
    let planID: Plan.ID
    /// The plan as it's saved. nil once it's deleted.
    private(set) var plan: Plan?
    var selectedDayID: TrainingDay.ID?
    /// Why the last change couldn't be saved.
    var problem: String?
    /// The workout in progress, whose day and plan can't be deleted while it's going.
    let busyDayID: TrainingDay.ID?
    let busyPlanID: Plan.ID?
    /// What a new exercise's sets start as when it was never done.
    let defaultSet: SetDraft
    /// Called after every change, so the rest of the app can catch up.
    @ObservationIgnored var onChange: (() -> Void)?

    private let database: KitaeDatabase
    private static let log = Logger(subsystem: "ca.gureet.kitae", category: "plans")

    /// Names offered for a new day.
    static let dayNameSuggestions = [
        "Push", "Pull", "Legs", "Upper", "Lower", "Full Body",
        "Chest", "Back", "Shoulders", "Arms", "Core", "Cardio",
    ]

    init(route: PlanEditRoute, database: KitaeDatabase, defaultSet: SetDraft, workout: Workout?) {
        planID = route.planID
        self.database = database
        self.defaultSet = defaultSet
        busyDayID = workout?.dayID
        busyPlanID = workout?.planID
        selectedDayID = route.dayID
        read()
        if selectedDay == nil { selectedDayID = plan?.days.first?.id }
    }

    var days: [TrainingDay] { plan?.days ?? [] }

    var selectedDay: TrainingDay? {
        days.first { $0.id == selectedDayID }
    }

    /// A plan needs an exercise to be worth keeping, as in Kitae for Android.
    var hasExercises: Bool { (plan?.exerciseCount ?? 0) > 0 }

    var canDeleteSelectedDay: Bool {
        selectedDay != nil && selectedDayID != busyDayID
    }

    var canDeletePlan: Bool { planID != busyPlanID }

    /// The suggested names that no day of the plan has yet.
    var unusedDayNames: [String] {
        let used = Set(days.map { $0.name.lowercased() })
        return Self.dayNameSuggestions.filter { !used.contains($0.lowercased()) }
    }

    // MARK: The plan

    /// Throws a `NameProblem` if another plan has the name, or it's empty.
    func rename(to name: String) throws {
        let planID = planID
        try save { db in try Plans.rename(db, planID: planID, to: name) }
    }

    func delete() {
        guard canDeletePlan else { return }
        let planID = planID
        change { db in try Plans.delete(db, planID: planID) }
    }

    // MARK: Days

    /// Adds a day at the end of the plan and shows it. Throws a `NameProblem` if the name is empty.
    func addDay(named name: String) throws {
        let planID = planID
        if let newID = try save({ db in try Plans.addDay(db, planID: planID, name: name) }) {
            selectedDayID = newID
        }
    }

    func renameSelectedDay(to name: String) throws {
        guard let dayID = selectedDayID else { return }
        try save { db in try Plans.renameDay(db, dayID: dayID, to: name) }
    }

    /// Deletes the day that's showing, then shows the one that took its place.
    func deleteSelectedDay() {
        guard canDeleteSelectedDay, let dayID = selectedDayID else { return }
        let index = days.firstIndex { $0.id == dayID } ?? 0
        change { db in try Plans.deleteDay(db, dayID: dayID) }
        selectedDayID = days.isEmpty ? nil : days[min(index, days.count - 1)].id
    }

    func canMoveSelectedDay(by offset: Int) -> Bool {
        guard let index = days.firstIndex(where: { $0.id == selectedDayID }) else { return false }
        return days.indices.contains(index + offset)
    }

    /// Moves the selected day earlier (−1) or later (+1) in the plan.
    func moveSelectedDay(by offset: Int) {
        guard canMoveSelectedDay(by: offset), let index = days.firstIndex(where: { $0.id == selectedDayID }) else { return }
        var order = days.map(\.id)
        order.swapAt(index, index + offset)
        change { db in try Plans.orderDays(db, order) }
    }

    // MARK: Exercises

    /// Adds an exercise to the selected day with the sets done last time, or three new ones.
    /// Returns its place in the day.
    @discardableResult
    func addExercise(_ exercise: Exercise) -> DayExercise.ID? {
        guard let dayID = selectedDayID else { return nil }
        let fallback = defaultSet
        return change { db -> DayExercise.ID in
            let sets = try Plans.suggestedSets(db, exerciseID: exercise.id, fallback: fallback)
            return try Plans.addExercise(db, dayID: dayID, exerciseID: exercise.id, sets: sets)
        }
    }

    func removeExercise(_ slotID: DayExercise.ID) {
        change { db in try Plans.removeExercise(db, slotID: slotID) }
    }

    /// Moves exercises of the selected day the way a list moves rows: `source` go in front of
    /// the exercise at `destination`.
    func moveExercises(from source: IndexSet, to destination: Int) {
        guard let day = selectedDay else { return }
        let order = moved(day.exercises.map(\.id), from: source, to: destination)
        guard order != day.exercises.map(\.id) else { return }
        change { db in try Plans.orderExercises(db, order) }
    }

    // MARK: Sets

    /// Adds a set like the exercise's last one. A warm-up is followed by a standard set.
    func addSet(to slotID: DayExercise.ID) {
        var set = exercise(slotID)?.sets.last?.draft ?? defaultSet
        if set.type == .warmup { set.type = .standard }
        change { db in try Plans.addSet(db, slotID: slotID, set: set) }
    }

    func updateSet(_ setID: PlannedSet.ID, _ edit: (inout PlannedSet) -> Void) {
        guard let old = plannedSet(setID) else { return }
        var new = old
        edit(&new)
        guard new != old else { return }
        let draft = new.draft
        change { db in try Plans.updateSet(db, setID: setID, set: draft) }
    }

    func deleteSet(_ setID: PlannedSet.ID) {
        change { db in try Plans.deleteSet(db, setID: setID) }
    }

    func exercise(_ slotID: DayExercise.ID) -> DayExercise? {
        days.lazy.flatMap(\.exercises).first { $0.id == slotID }
    }

    /// The exercise a planned set belongs to.
    func exercise(containing setID: PlannedSet.ID) -> DayExercise? {
        days.lazy.flatMap(\.exercises).first { $0.sets.contains { $0.id == setID } }
    }

    func plannedSet(_ setID: PlannedSet.ID) -> PlannedSet? {
        days.lazy.flatMap(\.exercises).flatMap(\.sets).first { $0.id == setID }
    }

    // MARK: Saving

    /// Saves a change and reads the plan back. Returns what `body` returns, or nil if the change
    /// couldn't be saved. A name that can't be used is thrown, so it can be shown next to the
    /// name; anything else goes in `problem`.
    @discardableResult
    private func save<T>(_ body: (Database) throws -> T) throws -> T? {
        let result: T
        do {
            result = try database.write(body)
        } catch let nameProblem as NameProblem {
            throw nameProblem
        } catch {
            Self.log.error("Couldn't change the plan: \(error.localizedDescription, privacy: .public)")
            problem = "Couldn't save that. \(error.localizedDescription)"
            return nil
        }
        read()
        onChange?()
        return result
    }

    /// Saves a change that doesn't involve a name.
    @discardableResult
    private func change<T>(_ body: (Database) throws -> T) -> T? {
        do {
            return try save(body)
        } catch {
            problem = error.localizedDescription
            return nil
        }
    }

    private func read() {
        let planID = planID
        do {
            plan = try database.read { db in try Plans.plan(db, id: planID) }
        } catch {
            Self.log.error("Couldn't read the plan: \(error.localizedDescription, privacy: .public)")
        }
    }
}

/// An editor is pushed as itself, so it's only equal to itself.
extension PlanEditor: Hashable {
    nonisolated static func == (lhs: PlanEditor, rhs: PlanEditor) -> Bool {
        lhs === rhs
    }

    nonisolated func hash(into hasher: inout Hasher) {
        hasher.combine(ObjectIdentifier(self))
    }
}
