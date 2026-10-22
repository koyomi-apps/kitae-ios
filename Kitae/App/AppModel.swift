// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import Observation
import os

/// What the whole app is showing: the plans, the day that's up next, and the workout in
/// progress, Settings and backups. Everything comes from the database and goes straight back
/// into it. While a rest is going, it's also on the Lock Screen, and an alert says when it's
/// over unless Settings turn alerts off. Finishing a workout backs up, unless Settings say not to.
@Observable
final class AppModel {
    let database: KitaeDatabase
    private(set) var plans: [PlanSummary] = []
    /// The plan being followed, with its days. nil until a plan is chosen.
    private(set) var currentPlan: Plan?
    /// The day after the last one done, which Today offers unless another day is picked.
    private(set) var upNextDayID: TrainingDay.ID?
    /// Whether any set was ever done, in the workout in progress too. Until then Today says
    /// it's the first session.
    var hasDoneSets: Bool { hadDoneSets || (workout?.doneCount ?? 0) > 0 }
    /// Whether any set was done as of the last time the database was read.
    private var hadDoneSets = false
    /// The profile and weigh-ins from the Body page.
    private(set) var bodyLog = BodyLog()
    /// People's notes on their exercises, by exercise.
    private(set) var exerciseNotes: [Exercise.ID: String] = [:]
    /// A day picked on Today instead of the one up next.
    var pickedDayID: TrainingDay.ID?
    private(set) var workout: Workout? {
        didSet {
            guard oldValue !== workout else { return }
            workout?.onChange = { [weak self] in self?.workoutChanged() }
            workoutChanged()
        }
    }
    var isWorkoutPresented = false
    /// Whether the list of plans is showing.
    var isChoosingPlan = false
    /// Whether the welcome is showing.
    var isWelcoming = false
    /// The workout just finished, while its summary shows.
    var finishedWorkout: FinishedWorkout?
    /// A summary waiting for the workout's screen to close first.
    @ObservationIgnored private var pendingSummary: FinishedWorkout?
    /// Goes up whenever workouts or plans may have changed, so History reads them again.
    private(set) var historyRevision = 0
    /// Goes up when Kitae is reset, so every screen starts over.
    private(set) var generation = 0
    /// What Settings changes. Each change is saved straight away.
    var settings: AppSettings {
        didSet { settingsChanged(from: oldValue) }
    }
    /// Where backups go and how the last one went.
    let backups: BackupController
    /// The backup made after the last workout, while it's being made.
    @ObservationIgnored private(set) var backupAfterWorkout: Task<Void, Never>?
    /// Something that went wrong, to tell people about.
    var problem: String?

    /// The time, which tests can set.
    @ObservationIgnored var clock: () -> Date = { .now }

    private let restStore: RestTimerStore
    private let settingsStore: SettingsStore
    private let liveActivity: LiveRestActivity?
    private let restAlerts: RestAlerts?

    private static let log = Logger(subsystem: "ca.gureet.kitae", category: "app")

    /// - Parameters:
    ///   - restStore: Where the rest timer is kept while Kitae is closed.
    ///   - settingsStore: Where Settings are kept.
    ///   - backups: Where backups go.
    ///   - liveActivities: Whether rests show on the Lock Screen and in the Dynamic Island.
    ///   - restAlerts: Whether a rest that runs out can set off an alert. Settings can turn it off.
    init(
        database: KitaeDatabase,
        restStore: RestTimerStore = .standard,
        settingsStore: SettingsStore = .standard,
        backups: BackupController = .standard,
        liveActivities: Bool = true,
        restAlerts: Bool = true
    ) {
        self.database = database
        self.restStore = restStore
        self.settingsStore = settingsStore
        settings = settingsStore.load()
        self.backups = backups
        liveActivity = liveActivities ? LiveRestActivity() : nil
        self.restAlerts = restAlerts ? RestAlerts() : nil
        reload()
        self.restAlerts?.onAllowed = { [weak self] in self?.workoutChanged() }
        if liveActivities {
            RestTimerRemote.perform = { [weak self] action in await self?.perform(action) }
        }
    }

    var days: [TrainingDay] { currentPlan?.days ?? [] }

    /// How weights are written, from Settings.
    var weightFormatter: WeightFormatter {
        WeightFormatter(unit: settings.unit, decimals: settings.decimals)
    }

    /// The rest after a set without one of its own, from Settings. 0 is none.
    var defaultRestSeconds: Int { settings.defaultRestSeconds }

    /// The day Today shows: the one picked, else the one in progress, else the one up next.
    var selectedDay: TrainingDay? {
        if let id = pickedDayID, let day = days.first(where: { $0.id == id }) { return day }
        if let id = workout?.dayID, let day = days.first(where: { $0.id == id }) { return day }
        return days.first { $0.id == upNextDayID } ?? days.first
    }

    /// Reads the plans and the workout in progress again.
    func reload() {
        let today = EpochDay.of(clock())
        do {
            let snapshot = try database.read { db in
                let currentID = try Plans.currentID(db)
                return (
                    plans: try Plans.all(db),
                    plan: try currentID.flatMap { try Plans.plan(db, id: $0) },
                    lastDone: try Sessions.lastDoneDayID(db),
                    hasDoneSets: try Sessions.hasDoneSets(db),
                    active: try Sessions.active(db, today: today),
                    body: try BodyStore.log(db),
                    notes: try ExerciseNotes.all(db)
                )
            }
            historyRevision += 1
            plans = snapshot.plans
            currentPlan = snapshot.plan
            upNextDayID = Plans.dayAfter(snapshot.lastDone, in: snapshot.plan?.days ?? [])?.id
            hadDoneSets = snapshot.hasDoneSets
            bodyLog = snapshot.body
            exerciseNotes = snapshot.notes
            if let active = snapshot.active {
                if workout?.id != active.id {
                    workout = try makeWorkout(id: active.id)
                }
            } else {
                isWorkoutPresented = false
                workout = nil
            }
        } catch {
            report(error, "Couldn't read your plans")
        }
    }

    /// Follows another plan from today.
    func selectPlan(_ id: Plan.ID) {
        isChoosingPlan = false
        guard id != currentPlan?.id else { return }
        let today = EpochDay.of(clock())
        do {
            try database.write { db in try Plans.setCurrent(db, planID: id, today: today) }
        } catch {
            report(error, "Couldn't switch plans")
        }
        pickedDayID = nil
        reload()
    }

    /// Opens the list of plans while no plan is followed, as Kitae for Android does on first
    /// launch.
    func askForPlanIfNeeded() {
        if currentPlan == nil { isChoosingPlan = true }
    }

    /// On first launch, and after a reset: the welcome, and once it's gone the list of plans.
    /// Someone who already follows a plan, from before there was a welcome, goes straight on.
    func welcomeIfNeeded() {
        if !settingsStore.isWelcomeDone, currentPlan == nil {
            isWelcoming = true
        } else {
            settingsStore.isWelcomeDone = true
            askForPlanIfNeeded()
        }
    }

    /// Closes the welcome for good. The list of plans opens once it's gone.
    func finishWelcome() {
        settingsStore.isWelcomeDone = true
        isWelcoming = false
    }

    // MARK: Editing plans

    /// A new, empty plan. Throws a `NameProblem` if the name can't be used.
    func createPlan(named name: String) throws -> Plan.ID {
        let id = try database.write { db in try Plans.create(db, name: name) }
        reload()
        return id
    }

    /// Whether a plan can be deleted: not while one of its days is being done.
    func canDelete(_ planID: Plan.ID) -> Bool {
        workout?.planID != planID
    }

    func deletePlan(_ planID: Plan.ID) {
        guard canDelete(planID) else { return }
        do {
            try database.write { db in try Plans.delete(db, planID: planID) }
        } catch {
            report(error, "Couldn't delete the plan")
        }
        pickedDayID = nil
        reload()
    }

    func planEditor(for route: PlanEditRoute) -> PlanEditor {
        let editor = PlanEditor(
            route: route,
            database: database,
            defaultSet: SetDraft(reps: 12, weightKg: weightFormatter.unit.defaultWeightKg),
            workout: workout
        )
        editor.onChange = { [weak self] in
            self?.reload()
            self?.workout?.reload()
        }
        return editor
    }

    /// Starts a workout of `day`, or opens it if it's the one in progress.
    func startWorkout(_ day: TrainingDay, at time: Date? = nil) {
        if let workout {
            if workout.dayID == day.id { isWorkoutPresented = true }
            return
        }
        let now = time ?? clock()
        do {
            let id = try database.write { db in
                try Sessions.start(db, dayID: day.id, today: EpochDay.of(now), now: now.epochMilliseconds)
            }
            workout = try makeWorkout(id: id)
            historyRevision += 1
            pickedDayID = nil
            isWorkoutPresented = workout != nil
        } catch {
            report(error, "Couldn't start \(day.name)")
        }
    }

    /// Ends the workout. Today moves on to the plan's next day. With a set done, its summary
    /// opens once the workout has closed, unless Settings say not to or `showsSummary` is false,
    /// as when another day starts straight after.
    func finishWorkout(_ mode: FinishMode, addNewSets: Bool, showsSummary: Bool = true) {
        guard let workout else { return }
        workout.skipRest()
        let sessionID = workout.id
        let summary = showsSummary && settings.showsWorkoutSummary && workout.doneCount > 0
            ? FinishedWorkout(id: sessionID) : nil
        let wasPresented = isWorkoutPresented
        let now = clock().epochMilliseconds
        do {
            try database.write { db in
                try Sessions.finish(db, sessionID: sessionID, mode: mode, addNewSets: addNewSets, now: now)
            }
        } catch {
            report(error, "Couldn't finish the workout")
            return
        }
        close()
        if wasPresented {
            pendingSummary = summary
        } else {
            finishedWorkout = summary
        }
        if settings.backUpAfterWorkouts {
            backupAfterWorkout = Task { _ = await backUp() }
        }
    }

    /// The workout's screen has closed: a summary that was waiting for it opens.
    func workoutClosed() {
        guard let summary = pendingSummary else { return }
        pendingSummary = nil
        finishedWorkout = summary
    }

    /// Removes the workout and its sets. The plan stays the same.
    func discardWorkout() {
        guard let workout else { return }
        workout.skipRest()
        let sessionID = workout.id
        do {
            try database.write { db in try Sessions.discard(db, sessionID: sessionID) }
        } catch {
            report(error, "Couldn't discard the workout")
            return
        }
        close()
    }

    // MARK: Body

    /// Saves a day's weigh-in. It takes the place of `replacing`, the one being changed, and
    /// of anything else on that day.
    func saveWeighIn(date: Int, weightKg: Double?, bodyFatPercent: Double?, replacing id: Int64? = nil) {
        do {
            try database.write { db in
                try BodyStore.save(db, date: date, weightKg: weightKg, bodyFatPercent: bodyFatPercent, replacing: id)
            }
        } catch {
            report(error, "Couldn't save the weigh-in")
        }
        reloadBody()
    }

    func deleteWeighIn(_ id: Int64) {
        do {
            try database.write { db in try BodyStore.delete(db, id: id) }
        } catch {
            report(error, "Couldn't delete the weigh-in")
        }
        reloadBody()
    }

    func saveProfile(_ profile: BodyProfile) {
        do {
            try database.write { db in try BodyStore.save(db, profile: profile) }
        } catch {
            report(error, "Couldn't save your details")
        }
        reloadBody()
    }

    /// A finished workout's active energy from its done sets, when Kitae knows a weight.
    func activeKilocalories(_ workout: WorkoutSummary, sets: [DoneSet]) -> Double? {
        bodyLog.activeKilocalories(workout, sets: sets)
    }

    private func reloadBody() {
        do {
            bodyLog = try database.read { db in try BodyStore.log(db) }
        } catch {
            report(error, "Couldn't read your weigh-ins")
        }
    }

    // MARK: Exercises

    /// Saves a new exercise, or an existing one's details, and returns its id. Throws a
    /// `NameProblem` if another exercise has the name, or it's empty.
    @discardableResult
    func saveExercise(_ draft: ExerciseDraft, id: Exercise.ID?) throws -> Exercise.ID {
        let saved = try database.write { db -> Exercise.ID in
            guard let id else { return try Exercises.create(db, draft) }
            try Exercises.update(db, id: id, draft)
            return id
        }
        reload()
        workout?.reload()
        return saved
    }

    /// Saves an exercise's note, like a seat height. An empty one deletes it.
    func saveNote(_ note: String, for id: Exercise.ID) {
        do {
            try database.write { db in try ExerciseNotes.save(db, exerciseID: id, note: note) }
            exerciseNotes = try database.read { db in try ExerciseNotes.all(db) }
        } catch {
            report(error, "Couldn't save the note")
        }
    }

    /// Whether an exercise can be deleted: not while it's part of the workout in progress.
    func canDeleteExercise(_ id: Exercise.ID) -> Bool {
        !(workout?.exercises.contains { $0.id == id } ?? false)
    }

    /// Deletes an exercise. It's taken out of every plan, its sets out of past workouts, and its
    /// note goes too.
    func deleteExercise(_ id: Exercise.ID) {
        guard canDeleteExercise(id) else { return }
        do {
            try database.write { db in try Exercises.delete(db, id: id) }
        } catch {
            report(error, "Couldn't delete the exercise")
        }
        reload()
        workout?.reload()
    }

    // MARK: Backups

    /// The settings a backup carries.
    var backupPreferences: [String: PreferenceValue] {
        settings.backupPreferences(planChosen: currentPlan != nil)
    }

    /// Backs up into the backup folder now. Returns whether it worked; if not,
    /// `backups.problem` says why.
    @discardableResult
    func backUp() async -> Bool {
        await backups.backUp(database, preferences: backupPreferences, at: clock())
    }

    /// Replaces plans, workouts and exercises with a backup's, from either app, and takes its
    /// settings. Not while a workout is going.
    func restoreBackup(from url: URL) async throws {
        guard workout == nil else { throw BackupError.workoutInProgress }
        let restored = try await backups.restore(url, into: database, currentPreferences: backupPreferences)
        if let restored {
            settings.apply(backup: restored)
        }
        pickedDayID = nil
        reload()
    }

    // MARK: Starting over

    /// Erases every plan, workout, exercise, note, weigh-in and setting, so Kitae is as it was
    /// when it was installed, like Kitae for Android's "Reset app", welcome and all. A workout in
    /// progress goes too. Backups stay in their folder.
    func reset() throws {
        workout?.skipRest()
        isWorkoutPresented = false
        isChoosingPlan = false
        isWelcoming = false
        try database.reset()
        settings = AppSettings()
        settingsStore.isWelcomeDone = false
        backups.forget()
        pickedDayID = nil
        reload()
        generation += 1
    }

    // MARK: History

    /// Deletes a past workout and its sets. The plan stays the same. The workout in progress
    /// is discarded from the workout instead.
    func deleteWorkout(_ id: Int64) {
        guard id != workout?.id else { return }
        do {
            try database.write { db in try Sessions.discard(db, sessionID: id) }
        } catch {
            report(error, "Couldn't delete the workout")
        }
        reload()
    }

    private func close() {
        isWorkoutPresented = false
        workout = nil
        pickedDayID = nil
        reload()
    }

    private func makeWorkout(id: Int64) throws -> Workout? {
        try Workout(
            id: id,
            database: database,
            restStore: restStore,
            defaultRestSeconds: defaultRestSeconds,
            defaultWeightKg: weightFormatter.unit.defaultWeightKg
        )
    }

    // MARK: The rest timer outside Kitae

    /// A button on the Lock Screen or in the Dynamic Island. Returns once the Lock Screen
    /// has the change, as iOS may suspend Kitae again straight after.
    func perform(_ action: RestTimerAction) async {
        guard let workout else { return }
        switch action {
        case .pause: workout.pauseRest()
        case .resume: workout.resumeRest()
        case .add(let seconds): workout.addRest(TimeInterval(seconds))
        case .skip: workout.skipRest()
        }
        await liveActivity?.lastChange?.value
    }

    /// Catches the Lock Screen up when Kitae comes back, as Live Activities can only start
    /// while Kitae is open.
    func sceneBecameActive() {
        workoutChanged()
    }

    private func workoutChanged() {
        liveActivity?.update(for: workout, formatter: weightFormatter)
        restAlerts?.update(for: settings.restAlerts ? workout?.rest : nil)
    }

    // MARK: Settings

    /// Saves Settings, and lets the workout in progress, the Lock Screen and the alert follow.
    private func settingsChanged(from old: AppSettings) {
        guard settings != old else { return }
        settingsStore.save(settings)
        workout?.defaultRestSeconds = settings.defaultRestSeconds
        workout?.defaultWeightKg = settings.unit.defaultWeightKg
        workoutChanged()
    }

    private func report(_ error: any Error, _ message: String) {
        Self.log.error("\(message, privacy: .public): \(error.localizedDescription, privacy: .public)")
        problem = "\(message). \(error.localizedDescription)"
    }
}
