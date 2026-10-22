// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

#if DEBUG
import Foundation
import os

/// Opens a screen straight away in debug builds, for screenshots: launch with
/// `-KitaeScreen today`, `workout`, `typing`, `finish`, `options`, `minimized` or `switch`;
/// `activity` and `activitystates` for the Lock Screen and Dynamic Island rest timer;
/// `welcome` and `plans` for the first launch and `newplan` for naming a new plan; or `planedit`, `planset`,
/// `plansettyping`, `addexercise`, `newday` and `firstday` for the plan editor, and `editworkout` and
/// `workoutedit` for it while a workout is going, from Today and from the workout; `history` and
/// `workoutdetail` for History, `exercises`, `exercise` and `howto` (Deadlift's page, which has no
/// workouts) for Exercises, and `you`, `settings` and `body` for You, with five weeks of example
/// workouts and weigh-ins; `donetoday` for Today after a workout and `workoutdone` for its
/// summary; `motions` for every exercise animation.
/// These use a database in memory, so the real one isn't touched, and they don't ask for alerts.
/// Their backups go into a temporary folder.
enum DebugLaunch {
    static let screen = UserDefaults.standard.string(forKey: "KitaeScreen")
    private static let log = Logger(subsystem: "ca.gureet.kitae", category: "debug")
    private static let workoutScreens: Set = [
        "workout", "typing", "finish", "options", "minimized", "switch", "editworkout", "workoutedit", "history",
    ]
    private static let historyScreens: Set = ["history", "workoutdetail"]
    private static let exerciseScreens: Set = ["exercises", "exercise", "howto"]
    private static let youScreens: Set = ["you", "settings", "body"]
    /// Screens after a workout was finished today.
    private static let doneScreens: Set = ["donetoday", "workoutdone"]
    private static let editorScreens: Set = [
        "planedit", "planset", "plansettyping", "addexercise", "newday", "editworkout",
    ]
    /// Screens that make a new plan to show it.
    private static let newPlanScreens: Set = ["firstday"]
    /// Screens of the first launch, before a plan is chosen.
    private static let firstLaunchScreens: Set = ["welcome", "plans", "newplan"]
    private static var opened: Set<String> = []

    static func note(_ message: String) {
        log.notice("\(message, privacy: .public)")
    }

    /// Backups of the example data go into a temporary folder, away from real ones.
    static var backups: BackupController {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent("ExampleBackups", isDirectory: true)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        return BackupController(defaults: nil, ownFolder: folder)
    }

    /// Whether this launch is for a screenshot.
    static var usesExampleData: Bool {
        guard let screen else { return false }
        return screen == "today" || doneScreens.contains(screen) || workoutScreens.contains(screen) || editorScreens.contains(screen)
            || newPlanScreens.contains(screen) || firstLaunchScreens.contains(screen)
            || historyScreens.contains(screen) || exerciseScreens.contains(screen) || youScreens.contains(screen)
            || galleryPage != nil || opensMotionGallery
    }

    /// The rest timer's Lock Screen and Dynamic Island looks, drawn in the app.
    static var galleryPage: String? {
        ["activity", "activitystates"].contains(screen ?? "") ? screen : nil
    }

    /// Every exercise animation in a list, drawn over the app.
    static var opensMotionGallery: Bool {
        screen == "motions"
    }

    /// Whether Kitae opens on History.
    static var opensHistory: Bool {
        historyScreens.contains(screen ?? "")
    }

    /// Whether Kitae opens on Exercises.
    static var opensExercises: Bool {
        exerciseScreens.contains(screen ?? "")
    }

    /// Whether Kitae opens on You.
    static var opensYou: Bool {
        youScreens.contains(screen ?? "")
    }

    /// Whether Today opens the plan editor straight away.
    static var opensPlanEditor: Bool {
        editorScreens.contains(screen ?? "")
    }

    /// True the first time a screen asks, so it only opens once per launch.
    static func consume(_ screen: String) -> Bool {
        opened.insert(screen).inserted
    }

    /// Skips the welcome unless it's the screen, and follows the starter plan unless the screen
    /// is of the first launch. History, Exercises, You, the screens after a workout and adding an
    /// exercise (for its recent ones) get example workouts, and the workout and exercise screens
    /// notes on Bench Press and Incline Bench. For the workout screens, starts an example workout
    /// 23 minutes in, with two exercises done and the rest timer running.
    static func prepare(_ model: AppModel) {
        guard let screen else { return }
        if screen != "welcome" {
            model.finishWelcome()
        }
        if !firstLaunchScreens.contains(screen), let plan = model.plans.first {
            model.selectPlan(plan.id)
        }
        if historyScreens.contains(screen) || exerciseScreens.contains(screen) || youScreens.contains(screen)
            || doneScreens.contains(screen) || screen == "addexercise" {
            addExampleBody(to: model)
            addExampleHistory(to: model, finishedToday: doneScreens.contains(screen))
        }
        if workoutScreens.contains(screen) || exerciseScreens.contains(screen) {
            addExampleNotes(to: model)
        }
        guard workoutScreens.contains(screen), let day = model.selectedDay else { return }
        model.startWorkout(day, at: Date.now.addingTimeInterval(-(23 * 60 + 36)))
        guard let workout = model.workout else { return }
        for exercise in workout.exercises.prefix(2) {
            for set in exercise.sets {
                workout.toggleDone(set.id)
            }
        }
        workout.startRest(seconds: 95)
        model.isWorkoutPresented = false
    }

    private static func addExampleNotes(to model: AppModel) {
        let notes = [
            "Bench Press": "Grip a thumb's width outside the rings. Feet flat, shoulder blades back.",
            "Incline Bench": "Bench on the third notch.",
        ]
        let exercises = (try? model.database.read { db in try Exercises.all(db) }) ?? []
        for exercise in exercises {
            if let note = notes[exercise.name] {
                model.saveNote(note, for: exercise.id)
            }
        }
    }

    /// Weigh-ins every few days for five weeks, going down a little, with body fat about once a
    /// week, and a profile.
    private static func addExampleBody(to model: AppModel) {
        let today = EpochDay.of(.now)
        let weighIns: [(ago: Int, kg: Double, fat: Double?)] = [
            (37, 80.6, 19.9), (34, 80.3, nil), (31, 80.4, nil), (29, 79.9, 19.6), (26, 79.8, nil), (23, 79.6, nil),
            (21, 79.4, 19.2), (18, 79.5, nil), (15, 79.1, nil), (13, 78.9, 18.9), (10, 79.0, nil), (7, 78.7, nil),
            (5, 78.6, 18.6), (2, 78.5, nil), (0, 78.4, 18.5),
        ]
        do {
            try model.database.write { db in
                for weighIn in weighIns {
                    try BodyStore.save(db, date: today - weighIn.ago, weightKg: weighIn.kg, bodyFatPercent: weighIn.fat)
                }
                // 14 May 1996.
                try BodyStore.save(db, profile: BodyProfile(heightCm: 178, birthDate: 9630, sex: .male, activityLevel: .moderatelyActive))
            }
        } catch {
            note("Couldn't add the example weigh-ins: \(error)")
        }
    }

    /// Five weeks of finished workouts of the plan's days in turn, every other day or so, with
    /// the weights going up a little each time and one workout skipped, and with `finishedToday`
    /// one more this afternoon.
    private static func addExampleHistory(to model: AppModel, finishedToday: Bool = false) {
        guard let days = model.currentPlan?.days, !days.isEmpty else { return }
        let today = EpochDay.of(.now)
        let daysAgo = [36, 34, 33, 31, 29, 27, 26, 24, 22, 20, 19, 17, 15, 13, 12, 10, 8, 6, 5, 3, 1] + (finishedToday ? [0] : [])
        do {
            try model.database.write { db in
                for (index, ago) in daysAgo.enumerated() {
                    let day = days[index % days.count]
                    let date = today - ago
                    let minutes = 41 + (index * 7) % 24
                    // Today's ends a few minutes ago.
                    let start = ago == 0
                        ? Date.now.addingTimeInterval(-TimeInterval((minutes + 4) * 60))
                        : EpochDay.date(date).addingTimeInterval(TimeInterval(17 * 3600 + (index % 4) * 1500))
                    let sessionID = try Sessions.start(db, dayID: day.id, today: date, now: start.epochMilliseconds)
                    if ago != 8 {
                        let sets = try Sessions.exercises(db, sessionID: sessionID, dayID: day.id).flatMap(\.sets)
                        for var set in sets {
                            set.weightKg += 1.25
                            try Sessions.update(db, set: set)
                            try Sessions.setDone(db, setID: set.id, isDone: true)
                        }
                    }
                    let end = start.addingTimeInterval(TimeInterval(minutes * 60))
                    try Sessions.finish(db, sessionID: sessionID, mode: .keepSkipped, addNewSets: false, now: end.epochMilliseconds)
                }
            }
        } catch {
            note("Couldn't add the example history: \(error)")
        }
        model.reload()
    }

    /// `workoutdone`: the summary of the example workout finished today.
    static func openWorkoutDone(_ model: AppModel) {
        guard screen == "workoutdone", consume("workoutdone") else { return }
        let today = EpochDay.of(.now)
        guard let latest = try? model.database.read({ db in try Sessions.finished(db, from: today, to: today) }).first
        else { return }
        model.finishedWorkout = FinishedWorkout(id: latest.id)
    }

    /// Whether the example workout should open, rather than stay minimized.
    static var opensWorkout: Bool {
        ["workout", "typing", "finish", "options", "workoutedit"].contains(screen ?? "")
    }
}
#endif
