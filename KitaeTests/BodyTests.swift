// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import GRDB
import Testing
@testable import Kitae

/// Weigh-ins, the profile, and the energy workouts use.
struct BodyTests {
    @Test func theBodyTablesLeaveRoomsVersionAlone() throws {
        let database = try KitaeDatabase.inMemory()
        let (version, hash, tables) = try database.read { db in
            (
                try Int.fetchOne(db, sql: "PRAGMA user_version"),
                try String.fetchOne(db, sql: "SELECT identity_hash FROM room_master_table WHERE id = 42"),
                try String.fetchAll(db, sql: "SELECT name FROM sqlite_master WHERE type = 'table' AND name LIKE 'body_%' ORDER BY name")
            )
        }
        #expect(version == Schema.version)
        #expect(hash == Schema.identityHash)
        #expect(tables == ["body_measurements", "body_profile"])
    }

    @Test func aDayHasOneWeighIn() throws {
        let database = try KitaeDatabase.inMemory()
        try database.write { db in
            try BodyStore.save(db, date: 100, weightKg: 80, bodyFatPercent: nil)
            try BodyStore.save(db, date: 100, weightKg: 79.5, bodyFatPercent: 18)
            try BodyStore.save(db, date: 98, weightKg: 81, bodyFatPercent: nil)
        }
        var log = try database.read(BodyStore.log)
        #expect(log.entries.map(\.date) == [98, 100])
        #expect(log.entries.last?.weightKg == 79.5 && log.entries.last?.bodyFatPercent == 18)

        // Moving a weigh-in to another day takes it off its old day.
        let moved = try #require(log.entries.first)
        try database.write { db in
            try BodyStore.save(db, date: 99, weightKg: 81, bodyFatPercent: nil, replacing: moved.id)
        }
        log = try database.read(BodyStore.log)
        #expect(log.entries.map(\.date) == [99, 100])

        // Without a weight or body fat, the day has nothing.
        try database.write { db in
            try BodyStore.save(db, date: 100, weightKg: nil, bodyFatPercent: nil)
        }
        #expect(try database.read(BodyStore.log).entries.map(\.date) == [99])
    }

    @Test func theProfileIsSavedAsAndroidWouldReadIt() throws {
        let database = try KitaeDatabase.inMemory()
        let profile = BodyProfile(heightCm: 178, birthDate: 9630, sex: .female, activityLevel: .moderatelyActive)
        try database.write { db in try BodyStore.save(db, profile: profile) }
        #expect(try database.read(BodyStore.log).profile == profile)
        let row = try #require(try database.read { db in try Row.fetchOne(db, sql: "SELECT * FROM body_profile") })
        #expect(row["id"] as Int64 == 1)
        #expect(row["sex"] as String == "Female")
        #expect(row["activityLevel"] as String == "ModeratelyActive")
    }

    @Test func aBackupFromAndroidGetsTheBodyTables() throws {
        let database = try KitaeDatabase.inMemory()
        try database.write { db in try BodyStore.save(db, date: 100, weightKg: 80, bodyFatPercent: nil) }
        // Room's tables only, as Kitae for Android makes them.
        let android = try DatabaseQueue()
        try android.write { db in try Schema.create(db) }
        try database.replaceContents(with: android)
        let log = try database.read(BodyStore.log)
        #expect(log.entries.isEmpty)
        try database.write { db in try BodyStore.save(db, date: 101, weightKg: 79, bodyFatPercent: nil) }
        #expect(try database.read(BodyStore.log).entries.count == 1)
    }

    @Test func aDayUsesTheLastWeightBeforeIt() {
        let log = BodyLog(entries: [
            BodyEntry(id: 1, date: 10, weightKg: 80, bodyFatPercent: 20),
            BodyEntry(id: 2, date: 20, weightKg: nil, bodyFatPercent: 19),
            BodyEntry(id: 3, date: 30, weightKg: 78, bodyFatPercent: nil),
        ])
        #expect(log.weightKg(on: 5) == 80)
        #expect(log.weightKg(on: 25) == 80)
        #expect(log.weightKg(on: 30) == 78)
        #expect(log.bodyFatPercent(on: 25) == 19)
        #expect(log.latestWeight?.id == 3)
        #expect(log.entries[0].leanKg == 64)
        #expect(BodyLog().weightKg(on: 10) == nil)
    }

    @Test func ageCountsWholeYears() {
        let profile = BodyProfile(birthDate: 9630) // 14 May 1996
        #expect(profile.age(on: 9630 + 365 * 30 + 6) == 29) // 7 May 2026
        #expect(profile.age(on: 9630 + 365 * 30 + 8) == 30) // 15 May 2026
    }

    private func workout(minutes: Int?, sets: Int, date: Int = 100) -> WorkoutSummary {
        WorkoutSummary(id: 1, date: date, planID: nil, dayID: nil, dayName: "Legs", isFinished: true,
                       startedAt: minutes == nil ? nil : 0, finishedAt: minutes.map { Int64($0) * 60_000 },
                       setCount: sets, doneCount: sets)
    }

    private func done(_ key: String, reps: Int, kg: Double, _ type: SetType = .standard,
                      target: String = "Quads", isIsometric: Bool = false) -> DoneSet {
        DoneSet(exerciseID: 0, libraryKey: key, target: target, isIsometric: isIsometric, reps: reps, weightKg: kg, type: type)
    }

    @Test func aSetCostsTheWeightItMovesByTheExercise() {
        // Squats: 0.0075 kcal a rep for each kilogram moved, the bar and 85% of the body.
        let squats = ExerciseEnergy.of(key: "barbell-back-squats", target: "Quads", isIsometric: false)
        #expect(abs(squats.kilocalories(reps: 10, weightKg: 100, bodyKg: 80) - 0.0075 * (100 + 68) * 10) < 1e-9)
        // Curls with a pair of 12 kg dumbbells: both count.
        let curls = ExerciseEnergy.of(key: "dumbbell-curls", target: "Biceps", isIsometric: false)
        #expect(abs(curls.kilocalories(reps: 10, weightKg: 12, bodyKg: 80) - 0.011 * (24 + 1.6) * 10) < 1e-9)
        // A minute of plank: 2.5 METs over resting.
        let plank = ExerciseEnergy.of(key: "plank", target: "Core", isIsometric: true)
        #expect(abs(plank.kilocalories(reps: 60, weightKg: 0, bodyKg: 80) - 2.5 * 80 / 60) < 1e-9)
        // Dips on a machine that takes 30 kg off: 95% of the body less the help, and never below nothing.
        let assisted = ExerciseEnergy.of(key: "assisted-dips", target: "Triceps", isIsometric: false)
        #expect(abs(assisted.kilocalories(reps: 10, weightKg: 30, bodyKg: 80) - 0.011 * (76 - 30) * 10) < 1e-9)
        #expect(assisted.kilocalories(reps: 10, weightKg: 100, bodyKg: 80) == 0)
        // A minute of farmer's carry with two 20 kg dumbbells: 2.5 METs over resting, for the body
        // and both weights.
        let carry = ExerciseEnergy.of(key: "dumbbell-farmers-carry", target: "Traps", isIsometric: true)
        #expect(abs(carry.kilocalories(reps: 60, weightKg: 20, bodyKg: 80) - 2.5 * 120 / 60) < 1e-9)
        // A big lift costs far more than a small one.
        #expect(squats.kilocalories(reps: 10, weightKg: 40, bodyKg: 80) > 3 * curls.kilocalories(reps: 10, weightKg: 10, bodyKg: 80))
        // People's own exercises go by their muscle group.
        #expect(ExerciseEnergy.of(key: nil, target: "Calves", isIsometric: false).perKilogram == 0.0034)
        #expect(ExerciseEnergy.of(key: nil, target: "Core", isIsometric: true).holdMETs != nil)
    }

    @Test func everyStarterExerciseHasItsOwnEnergy() throws {
        #expect(Set(ExerciseEnergy.starters.keys) == Set(try Seed.bundled().exercises.map(\.key)))
        for (key, energy) in ExerciseEnergy.starters {
            #expect(energy.holdMETs != nil || energy.perKilogram > 0, "\(key)")
        }
        // The holds are Kitae's isometric exercises.
        let isometric = Set(try Seed.bundled().exercises.filter(\.isIsometric).map(\.key))
        #expect(Set(ExerciseEnergy.starters.filter { $0.value.holdMETs != nil }.keys) == isometric)
    }

    @Test func setTypesCountWhatTheirRepsDontShow() {
        let between = 0.5 * 80 * 2.5 / 60
        let plain = WorkoutEnergy.active(sets: [done("barbell-back-squats", reps: 10, kg: 100)], durationMilliseconds: nil, bodyKg: 80)
        let failure = WorkoutEnergy.active(sets: [done("barbell-back-squats", reps: 10, kg: 100, .failure)], durationMilliseconds: nil, bodyKg: 80)
        #expect(abs((failure - between) - 1.3 * (plain - between)) < 1e-9)
        #expect(SetType.drop.energyFactor == 1.3 && SetType.restPause.energyFactor == 1.3)
        // A warm-up counts for its own, lighter weight.
        let warmUp = WorkoutEnergy.active(sets: [done("barbell-back-squats", reps: 10, kg: 40, .warmup)], durationMilliseconds: nil, bodyKg: 80)
        #expect(warmUp < plain)
    }

    @Test func heightScalesHowFarEachRepMoves() {
        let squats = ExerciseEnergy.of(key: "barbell-back-squats", target: "Quads", isIsometric: false)
        let average = squats.kilocalories(reps: 10, weightKg: 100, bodyKg: 80)
        let tall = squats.kilocalories(reps: 10, weightKg: 100, bodyKg: 80, reach: WorkoutEnergy.reach(heightCm: 190))
        #expect(abs(tall - average * 190 / 175) < 1e-9)
        // A hold doesn't move, so height doesn't change it.
        let plank = ExerciseEnergy.of(key: "plank", target: "Core", isIsometric: true)
        #expect(plank.kilocalories(reps: 60, weightKg: 0, bodyKg: 80, reach: 1.1) == plank.kilocalories(reps: 60, weightKg: 0, bodyKg: 80))
        // Without a height nothing changes, and far from the usual heights the change stops.
        #expect(WorkoutEnergy.reach(heightCm: nil) == 1 && WorkoutEnergy.reach(heightCm: 0) == 1)
        #expect(WorkoutEnergy.reach(heightCm: 120) == 0.8 && WorkoutEnergy.reach(heightCm: 230) == 1.2)
    }

    @Test func aWorkoutUsesTheHeightOfTheProfile() throws {
        let squats = [done("barbell-back-squats", reps: 10, kg: 100)]
        let weighIn = [BodyEntry(id: 1, date: 90, weightKg: 80)]
        let short = try #require(BodyLog(profile: BodyProfile(heightCm: 160), entries: weighIn)
            .activeKilocalories(workout(minutes: 30, sets: 1), sets: squats))
        let unknown = try #require(BodyLog(entries: weighIn).activeKilocalories(workout(minutes: 30, sets: 1), sets: squats))
        // The sets scale, the 3 minutes between them don't.
        let between = 0.5 * 80 * 3 / 60
        #expect(abs((short - between) - (unknown - between) * 160 / 175) < 1e-9)
    }

    @Test func timeBetweenSetsCountsLittleAndHasALimit() {
        let sets = Array(repeating: done("barbell-bench-press", reps: 10, kg: 60, target: "Chest"), count: 10)
        let lifting = WorkoutEnergy.active(sets: sets, durationMilliseconds: 0, bodyKg: 80)
        let hour = WorkoutEnergy.active(sets: sets, durationMilliseconds: 3_600_000, bodyKg: 80)
        let overnight = WorkoutEnergy.active(sets: sets, durationMilliseconds: 8 * 3_600_000, bodyKg: 80)
        // At most 3 minutes a set: 30 minutes at half a MET for 80 kg is 20 kcal.
        #expect(abs((hour - lifting) - 20) < 1e-9)
        #expect(overnight == hour)
        // Without the workout's length, 2.5 minutes a set.
        let unknown = WorkoutEnergy.active(sets: sets, durationMilliseconds: nil, bodyKg: 80)
        #expect(abs((unknown - lifting) - 0.5 * 80 * 25 / 60) < 1e-9)
    }

    @Test func aLegDayUsesMoreThanAnArmDay() throws {
        let log = BodyLog(entries: [BodyEntry(id: 1, date: 90, weightKg: 80)])
        let legs = Array(repeating: done("barbell-back-squats", reps: 8, kg: 100), count: 4)
            + Array(repeating: done("barbell-romanian-deadlift", reps: 10, kg: 80, target: "Hamstrings"), count: 3)
        let arms = Array(repeating: done("dumbbell-curls", reps: 12, kg: 12, target: "Biceps"), count: 4)
            + Array(repeating: done("tricep-pushdowns", reps: 12, kg: 25, target: "Triceps"), count: 3)
        let legDay = try #require(log.activeKilocalories(workout(minutes: 45, sets: 7), sets: legs))
        let armDay = try #require(log.activeKilocalories(workout(minutes: 45, sets: 7), sets: arms))
        #expect(legDay > 2 * armDay)
        #expect((90...110).contains(legDay) && (30...45).contains(armDay))
    }

    @Test func noWeightOrNoSetsMeansNoEnergy() {
        let squats = [done("barbell-back-squats", reps: 10, kg: 100)]
        #expect(BodyLog().activeKilocalories(workout(minutes: 60, sets: 1), sets: squats) == nil)
        let log = BodyLog(entries: [BodyEntry(id: 1, date: 90, weightKg: 80)])
        #expect(log.activeKilocalories(workout(minutes: 60, sets: 0), sets: []) == nil)
        // A workout without its times, from older Android data, still has its sets.
        #expect(log.activeKilocalories(workout(minutes: nil, sets: 1), sets: squats) != nil)
    }

    @Test func theWeekAddsUpItsWorkouts() {
        let log = BodyLog(entries: [BodyEntry(id: 1, date: 90, weightKg: 80)])
        let first = workout(minutes: 60, sets: 1)
        var second = workout(minutes: 30, sets: 1)
        second.id = 2
        let sets: [Int64: [DoneSet]] = [1: [done("barbell-back-squats", reps: 10, kg: 100)], 2: [done("barbell-bench-press", reps: 10, kg: 60, target: "Chest")]]
        let totals = log.totals([first, second], sets: sets)
        #expect(totals.workouts == 2 && totals.minutes == 90)
        let expected = (log.activeKilocalories(first, sets: sets[1] ?? []) ?? 0) + (log.activeKilocalories(second, sets: sets[2] ?? []) ?? 0)
        #expect(abs((totals.kilocalories ?? 0) - expected) < 1e-9)
        #expect(BodyLog().totals([first], sets: sets).kilocalories == nil)
    }

    @Test func weeksStartOnThePhonesFirstDay() {
        // 1 January 1970 was a Thursday.
        #expect(startOfWeek(0, firstWeekday: 1) == -4) // Sunday
        #expect(startOfWeek(0, firstWeekday: 2) == -3) // Monday
        #expect(startOfWeek(3, firstWeekday: 1) == 3) // Sunday 4 January
    }

    @Test func bodyWeightsAndPercentagesAreShownToATenth() {
        let kilograms = WeightFormatter(unit: .kilograms, decimals: .point)
        #expect(kilograms.body(kilograms: 78.44) == "78.4 kg")
        #expect(kilograms.percent(18) == "18%")
        #expect(kilograms.percent(18.25) == "18.3%")
        let pounds = WeightFormatter(unit: .pounds, decimals: .comma)
        #expect(pounds.body(kilograms: 80) == "176,4 lb")
        #expect(HeightFormatter(unit: .pounds).string(centimetres: 180) == "5 ft 11 in")
        #expect(HeightFormatter(unit: .kilograms).string(centimetres: 179.6) == "180 cm")
        #expect(kilocaloriesLabel(232) == "≈ 230 kcal")
    }
}
