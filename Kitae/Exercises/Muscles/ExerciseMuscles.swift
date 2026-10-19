// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import SwiftUI

/// The muscles an exercise works: the ones it works most, and the ones that help.
nonisolated struct MuscleWork: Hashable, Sendable {
    var primary: [Muscle]
    var secondary: [Muscle]

    /// "Glutes, hamstrings, lower back".
    static func list(_ muscles: [Muscle]) -> String {
        muscles.enumerated()
            .map { index, muscle in index == 0 ? muscle.name : muscle.name.lowercased() }
            .joined(separator: ", ")
    }

    /// What VoiceOver reads for the muscles, like "Works most: Chest. Helped by: Front delts, triceps".
    var spoken: String {
        var parts = ["Works most: \(Self.list(primary))"]
        if !secondary.isEmpty {
            parts.append("Helped by: \(Self.list(secondary))")
        }
        return parts.joined(separator: ". ")
    }
}

/// The muscle map's body, from the front and from the back, and every starter exercise's
/// muscles, as `scripts/motions/muscles.js` writes them to `ExerciseMuscles.json`.
nonisolated struct MuscleMap: Decodable, Sendable {
    /// A closed shape: its first point, then for each point after it a cubic Bézier's two
    /// control points and the point, all in one list.
    typealias Shape = [Double]

    enum Side: Sendable {
        case front
        case back
    }

    struct Entry: Decodable, Sendable {
        let id: Int64
        let name: String
        let primary: [Muscle]
        let secondary: [Muscle]
    }

    /// The size of the picture of one side.
    let width: Double
    let height: Double
    /// The body's outline, in parts that overlap.
    let body: [Shape]
    /// Each muscle's shapes on that side, by `Muscle` name.
    let front: [String: [Shape]]
    let back: [String: [Shape]]
    let exercises: [Entry]

    func muscles(_ side: Side) -> [(Muscle, [Shape])] {
        let shapes = side == .front ? front : back
        return shapes.keys.sorted().compactMap { key in
            Muscle(rawValue: key).map { ($0, shapes[key] ?? []) }
        }
    }

    static func path(_ shapes: [Shape]) -> Path {
        var path = Path()
        for shape in shapes where shape.count >= 8 {
            path.move(to: CGPoint(x: shape[0], y: shape[1]))
            var i = 2
            while i + 5 < shape.count {
                path.addCurve(
                    to: CGPoint(x: shape[i + 4], y: shape[i + 5]),
                    control1: CGPoint(x: shape[i], y: shape[i + 1]),
                    control2: CGPoint(x: shape[i + 2], y: shape[i + 3])
                )
                i += 6
            }
            path.closeSubpath()
        }
        return path
    }
}

/// Every starter exercise's muscles, and what to show for people's own exercises.
nonisolated enum ExerciseMuscles {
    static let map: MuscleMap? = {
        guard let url = Bundle.main.url(forResource: "ExerciseMuscles", withExtension: "json"),
              let data = try? Data(contentsOf: url) else { return nil }
        return try? decode(data)
    }()

    static func decode(_ data: Data) throws -> MuscleMap {
        try JSONDecoder().decode(MuscleMap.self, from: data)
    }

    static let byID: [Int64: MuscleWork] = Dictionary(
        (map?.exercises ?? []).map { ($0.id, MuscleWork(primary: $0.primary, secondary: $0.secondary)) },
        uniquingKeysWith: { first, _ in first }
    )

    /// A starter exercise's own muscles, or for people's own exercises, their muscle group.
    static func work(for exercise: Exercise) -> MuscleWork? {
        work(exerciseID: exercise.id, target: exercise.target)
    }

    /// The same, from an exercise's id and its muscle group as it's stored, like "Chest".
    static func work(exerciseID: Int64, target: String) -> MuscleWork? {
        if let work = byID[exerciseID] { return work }
        guard let group = MuscleGroup(rawValue: target) else { return nil }
        return MuscleWork(primary: group.muscles, secondary: [])
    }
}

nonisolated extension MuscleGroup {
    /// The muscles the map shows for an exercise of this group.
    var muscles: [Muscle] {
        switch self {
        case .biceps: [.biceps]
        case .triceps: [.triceps]
        case .shoulders: [.frontDelts, .sideDelts, .rearDelts]
        case .quads: [.quads]
        case .hamstrings: [.hamstrings]
        case .calves: [.calves]
        case .glutes: [.glutes]
        case .core: [.abs, .obliques]
        case .chest: [.chest]
        case .traps: [.traps]
        case .lats: [.lats]
        case .upperBack: [.upperBack]
        }
    }
}
