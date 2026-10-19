// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import SwiftUI
import Testing
@testable import Kitae

/// The muscle map: that every starter exercise has its muscles, and that the map can show them.
struct ExerciseMusclesTests {
    private func map() throws -> MuscleMap {
        let url = try #require(Bundle.main.url(forResource: "ExerciseMuscles", withExtension: "json"))
        return try ExerciseMuscles.decode(Data(contentsOf: url))
    }

    /// The starter exercises' names by library key, from Seed.json.
    private func starterNames() throws -> [String: String] {
        Dictionary(uniqueKeysWithValues: try Seed.bundled().exercises.map { ($0.key, $0.name) })
    }

    @Test func everyStarterExerciseHasItsMusclesByItsName() throws {
        let names = try starterNames()
        let map = try map()
        #expect(Set(map.exercises.map(\.key)) == Set(names.keys))
        for entry in map.exercises {
            #expect(names[entry.key] == entry.name, "\(entry.key)")
            #expect(!entry.primary.isEmpty, "\(entry.name)")
            #expect(Set(entry.primary).isDisjoint(with: entry.secondary), "\(entry.name)")
        }
        #expect(ExerciseMuscles.byKey.count == names.count)
    }

    @Test func everyMuscleIsOnTheMap() throws {
        let map = try map()
        let shown = Set((map.muscles(.front) + map.muscles(.back)).map(\.0))
        #expect(shown == Set(Muscle.allCases))
        #expect(map.body.allSatisfy { $0.count >= 8 && ($0.count - 2) % 6 == 0 })
    }

    @Test func theAnimationsShadeTheMapsMuscles() throws {
        // Bench press: the chest most, with the front delts and triceps helping.
        let bench = try #require(ExerciseMotions.byKey["barbell-bench-press"]).muscles
        #expect(bench.primary == [.chest] && bench.secondary == [.frontDelts, .triceps])
        #expect(bench[.chest] == .primary && bench[.triceps] == .secondary && bench[.quads] == nil)
        // The traps show the upper back, and the glutes the abductors.
        let rows = try #require(ExerciseMotions.byKey["barbell-bent-over-rows"]).muscles
        #expect(rows[.traps] == .primary && rows[.rearDelts] == .secondary)
        let abductions = try #require(ExerciseMotions.byKey["machine-hip-abductions"]).muscles
        #expect(abductions.primary == [.glutes] && abductions.secondary.isEmpty)
        // Every animation has a muscle in olive, and shades only muscles it has shapes for.
        for motion in ExerciseMotions.all {
            let muscles = motion.muscles
            #expect(!muscles.primary.isEmpty, "\(motion.name)")
            #expect(muscles.primary.isDisjoint(with: muscles.secondary), "\(motion.name)")
            #expect(muscles.primary.union(muscles.secondary).isDisjoint(with: [.upperBack, .adductors, .abductors]),
                    "\(motion.name)")
        }
    }

    @Test func theMusclesAreSpokenByWhatTheyDo() {
        let bench = MuscleWork(primary: [.chest], secondary: [.frontDelts, .triceps])
        #expect(bench.spoken == "Works most: Chest. Helped by: Front delts, triceps")
        #expect(MuscleWork(primary: [.abs, .obliques], secondary: []).spoken == "Works most: Abs, obliques")
    }

    @Test func peoplesOwnExercisesShowTheirGroup() {
        for group in MuscleGroup.allCases {
            #expect(!group.muscles.isEmpty, "\(group)")
        }
        let own = Exercise(id: 9_999, name: "Cable Press", target: "Chest")
        #expect(ExerciseMuscles.work(for: own) == MuscleWork(primary: [.chest], secondary: []))
        #expect(ExerciseMuscles.work(for: Exercise(id: 9_998, name: "Odd", target: "Elsewhere")) == nil)
    }

    @Test func namesAreListedLikeASentence() {
        #expect(MuscleWork.list([.glutes, .hamstrings, .lowerBack]) == "Glutes, hamstrings, lower back")
    }

    @Test func aSetCountsOnceForPrimaryMusclesAndHalfForSecondary() {
        let bench = Exercise(id: 21, name: "Bench Press", target: "Chest", libraryKey: "barbell-bench-press")
        let curls = Exercise(id: 1, name: "Curls", target: "Biceps", libraryKey: "dumbbell-curls")
        let volume = MuscleVolume([ExerciseSetCount(exercise: bench, sets: 6), ExerciseSetCount(exercise: curls, sets: 2)])
        #expect(volume.totalSets == 8)
        #expect(volume.sets[.chest] == 6 && volume.sets[.frontDelts] == 3 && volume.sets[.triceps] == 3)
        #expect(volume.sets[.biceps] == 2 && volume.sets[.forearms] == 1)
        // Half the top muscle's sets or more is "most".
        #expect(volume.shading == MuscleWork(primary: [.chest, .frontDelts, .triceps], secondary: [.biceps, .forearms]))
        #expect(MuscleVolume().shading == MuscleWork(primary: [], secondary: []))
    }

    @Test func aWeeksSetsLeaveOutWarmUpsUndoneSetsAndOtherDays() throws {
        let today = 20_736
        let database = try KitaeDatabase.followingStarterPlan(today: today)
        let day = try #require(try database.read { db in try Plans.currentID(db).flatMap { try Plans.plan(db, id: $0) } }?.days.first)
        let done = try database.write { db -> Int in
            // Long ago.
            let old = try Sessions.start(db, dayID: day.id, today: today - 10, now: Int64(today - 10) * 86_400_000)
            for set in try Sessions.exercises(db, sessionID: old, dayID: day.id).flatMap(\.sets) {
                try Sessions.setDone(db, setID: set.id, isDone: true)
            }
            try Sessions.finish(db, sessionID: old, mode: .keepSkipped, addNewSets: false, now: Int64(today - 10) * 86_400_000 + 3_600_000)
            // Two days ago: the first exercise's sets, one of them a warm-up.
            let recent = try Sessions.start(db, dayID: day.id, today: today - 2, now: Int64(today - 2) * 86_400_000)
            let first = try #require(try Sessions.exercises(db, sessionID: recent, dayID: day.id).first)
            for set in first.sets {
                try Sessions.setDone(db, setID: set.id, isDone: true)
            }
            var warmUp = try #require(first.sets.first)
            warmUp.type = .warmup
            try Sessions.update(db, set: warmUp)
            return first.sets.count - 1
        }
        let counts = try database.read { db in try Stats.exerciseSets(db, from: today - 6, to: today) }
        #expect(counts.count == 1)
        #expect(counts.first?.sets == done)
    }

    @MainActor @Test func theMapDrawsItsMuscles() throws {
        let view = MuscleMapView(work: MuscleWork(primary: [.chest], secondary: [.triceps]))
            .frame(width: 370)
            .environment(\.colorScheme, .light)
        let image = try #require(ImageRenderer(content: view).cgImage)
        // Pixels near olive, #63611E, are the chest and the swatch beside its name.
        #expect(Self.pixels(image, near: (0x63, 0x61, 0x1E)) > 150)
    }

    private static func pixels(_ image: CGImage, near color: (Int, Int, Int)) -> Int {
        let width = image.width, height = image.height
        var data = [UInt8](repeating: 0, count: width * height * 4)
        data.withUnsafeMutableBytes { bytes in
            let context = CGContext(data: bytes.baseAddress, width: width, height: height, bitsPerComponent: 8,
                                    bytesPerRow: width * 4, space: CGColorSpaceCreateDeviceRGB(),
                                    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
            context?.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
        }
        return stride(from: 0, to: data.count, by: 4).reduce(0) { count, i in
            let close = abs(Int(data[i]) - color.0) < 12 && abs(Int(data[i + 1]) - color.1) < 12
                && abs(Int(data[i + 2]) - color.2) < 12 && data[i + 3] > 200
            return count + (close ? 1 : 0)
        }
    }
}
