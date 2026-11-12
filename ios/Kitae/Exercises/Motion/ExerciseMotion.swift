// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation

/// Muscles an exercise works. The muscle map shows them all; the animated figure has shapes for
/// all but the last three (see `FigureMuscles`).
nonisolated enum Muscle: String, Decodable, Sendable, CaseIterable {
    case quads, hamstrings, glutes, calves
    case chest, abs, obliques, lats, traps, lowerBack
    case frontDelts, sideDelts, rearDelts, biceps, triceps, forearms
    case upperBack, adductors, abductors

    var name: String {
        switch self {
        case .quads: "Quads"
        case .hamstrings: "Hamstrings"
        case .glutes: "Glutes"
        case .calves: "Calves"
        case .chest: "Chest"
        case .abs: "Abs"
        case .obliques: "Obliques"
        case .lats: "Lats"
        case .traps: "Traps"
        case .lowerBack: "Lower back"
        case .frontDelts: "Front delts"
        case .sideDelts: "Side delts"
        case .rearDelts: "Rear delts"
        case .biceps: "Biceps"
        case .triceps: "Triceps"
        case .forearms: "Forearms"
        case .upperBack: "Upper back"
        case .adductors: "Adductors"
        case .abductors: "Abductors"
        }
    }
}

/// Something the figure holds or uses, or a piece of a bench or machine.
nonisolated struct Gear: Decodable, Sendable, Equatable {
    nonisolated enum Kind: String, Decodable, Sendable {
        /// A plate seen end-on: in the hands, across the shoulders (`on` "back"), on the front of
        /// the shoulders ("front") or across the hips ("hips"). In the front view, a bar across the
        /// body through both hands, its plates edge-on.
        case barbell
        /// A barbell with smaller plates.
        case ezbar
        /// A trap bar in the hands: its frame front to back through the grip, a plate at the grip.
        case trapbar
        /// A kettlebell in the hands, its bell beyond the grip along the forearm.
        case kettlebell
        /// In the hands, or held at the chest (`on` "chest") for a goblet squat.
        case dumbbells
        /// A bench, seat or pad: its top edge from `from` to `to`, `thick` deep, with legs down to the
        /// floor at the x of each of `legs`. `front` pads are drawn over the body, under the near arm.
        /// With `on` "back" or "front" it moves with the body, from `top` to `bottom` along the spine;
        /// with `on` "shins" it's a machine's knee pad under the near shin, on a lever from `anchor`.
        case pad
        /// A frame member from `from` to `to`, `width` wide.
        case post
        /// A cable from a pulley at `from` to the hands (or `on`), ending in a `handle`:
        /// "bar", "rope", "single" or "none". `stack` draws the weight stack under the pulley.
        /// In the front view, `side` picks the hand, or with `on` "ankle" that side's ankle.
        case cable
        /// A pull-up bar seen end-on at `at`.
        case bar
        /// A machine's foam roller, at `at` or following the body (`on`).
        case roller
        /// A machine's handle in the hands.
        case handle
        /// A plate under the near foot that moves with it (leg press). With `on` "ball" it stays
        /// square to the rail from `from` to `to`, pushed by the ball of the foot (leg press calf raises).
        case footplate
        /// A bar from `anchor` on the floor to the hands, plates at its end (T-bar rows).
        case tbar
        /// An ab wheel in the hands.
        case wheel
        /// A weight plate held in both hands: face-on from the front, upright and edge-on from the side.
        case plate
        /// Pads on the outside of the knees (front view), or the inside with `side` -1.
        case kneepads
        /// A resistance band stretched between the hands (front view).
        case band
    }

    var kind: Kind
    /// The part of the body it follows: "hands", "back", "front", "shins", "ankle", "shin", "calf",
    /// "thigh", "knee", "hips" or "chest".
    var on: String?
    var from: Vec?
    var to: Vec?
    var at: Vec?
    var anchor: Vec?
    var thick: Double
    var legs: [Double]
    var width: Double
    var length: Double
    var handle: String
    var stack: Bool
    /// Dumbbells seen side-on, for a neutral grip.
    var profile: Bool
    /// One dumbbell hanging from both hands, as in overhead extensions.
    var hang: Bool
    /// Only in the near hand.
    var one: Bool
    var radius: Double?
    var front: Bool
    var top: Double
    var bottom: Double
    var out: Double?
    var side: Double

    nonisolated private enum Keys: String, CodingKey {
        case kind, on, from, to, at, anchor, thick, legs, width, length, handle, stack, profile, hang, one, radius, front
        case top, bottom, out, side
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: Keys.self)
        kind = try c.decode(Kind.self, forKey: .kind)
        on = try c.decodeIfPresent(String.self, forKey: .on)
        from = try c.decodeIfPresent(Vec.self, forKey: .from)
        to = try c.decodeIfPresent(Vec.self, forKey: .to)
        at = try c.decodeIfPresent(Vec.self, forKey: .at)
        anchor = try c.decodeIfPresent(Vec.self, forKey: .anchor)
        thick = try c.decodeIfPresent(Double.self, forKey: .thick) ?? 8
        legs = try c.decodeIfPresent([Double].self, forKey: .legs) ?? []
        width = try c.decodeIfPresent(Double.self, forKey: .width) ?? 4
        length = try c.decodeIfPresent(Double.self, forKey: .length) ?? 40
        handle = try c.decodeIfPresent(String.self, forKey: .handle) ?? "bar"
        stack = try c.decodeIfPresent(Bool.self, forKey: .stack) ?? false
        profile = try c.decodeIfPresent(Bool.self, forKey: .profile) ?? false
        hang = try c.decodeIfPresent(Bool.self, forKey: .hang) ?? false
        one = try c.decodeIfPresent(Bool.self, forKey: .one) ?? false
        radius = try c.decodeIfPresent(Double.self, forKey: .radius)
        front = try c.decodeIfPresent(Bool.self, forKey: .front) ?? false
        top = try c.decodeIfPresent(Double.self, forKey: .top) ?? 70
        bottom = try c.decodeIfPresent(Double.self, forKey: .bottom) ?? 0
        out = try c.decodeIfPresent(Double.self, forKey: .out)
        side = try c.decodeIfPresent(Double.self, forKey: .side) ?? 1
    }
}

/// An exercise's animation: two keyframes of the figure and the gear it uses, read from
/// ExerciseMotions.json, and the muscles of the exercise's muscle map. Most are drawn from the
/// side; movements out to the sides (`view` "front") face the viewer. They're keyed by the
/// exercises' library keys (`Exercise.libraryKey`), which never change. scripts/motions makes the
/// file and draws it as pictures for checking.
nonisolated struct ExerciseMotion: Decodable, Sendable, Identifiable {
    nonisolated enum Keyframes: Sendable {
        case side(FigurePose, FigurePose)
        case front(FrontPose, FrontPose)
    }

    /// The library key of the exercise it shows.
    var key: String
    var id: String { key }
    /// The exercise's name as Kitae starts with it, to make the file easier to read.
    var name: String
    var keyframes: Keyframes
    /// In the front view, legs that come toward the viewer (sitting) are drawn over the body.
    var legsFront: Bool
    var gear: [Gear]
    /// The ends of the hold at the start, the move to the end, the hold at the end and the
    /// move back, as fractions of a loop.
    var timing: [Double]
    /// Length of one loop.
    var seconds: Double
    /// Moves the figure sideways so it sits in the middle of its card.
    var shift: Double
    /// Draws the figure smaller (below 1) for exercises that need more room, such as pull-ups,
    /// or bigger for ones close to the floor.
    var zoom: Double
    /// Middle and half width of the shadow on the floor.
    var shadow: Vec
    var shadowWidth: Double

    nonisolated private enum Keys: String, CodingKey {
        case key, name, view, legsFront, gear, timing, seconds, shift, zoom, shadow, shadowWidth, from, to
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: Keys.self)
        key = try c.decode(String.self, forKey: .key)
        name = try c.decode(String.self, forKey: .name)
        if try c.decodeIfPresent(String.self, forKey: .view) == "front" {
            keyframes = .front(try c.decode(FrontPose.self, forKey: .from), try c.decode(FrontPose.self, forKey: .to))
        } else {
            keyframes = .side(try c.decode(FigurePose.self, forKey: .from), try c.decode(FigurePose.self, forKey: .to))
        }
        legsFront = try c.decodeIfPresent(Bool.self, forKey: .legsFront) ?? false
        gear = try c.decodeIfPresent([Gear].self, forKey: .gear) ?? []
        timing = try c.decodeIfPresent([Double].self, forKey: .timing) ?? [0.08, 0.5, 0.58, 0.96]
        seconds = try c.decodeIfPresent(Double.self, forKey: .seconds) ?? 2.4
        shift = try c.decodeIfPresent(Double.self, forKey: .shift) ?? 0
        zoom = try c.decodeIfPresent(Double.self, forKey: .zoom) ?? 1
        shadow = try c.decodeIfPresent(Vec.self, forKey: .shadow) ?? Vec(4, 0)
        shadowWidth = try c.decodeIfPresent(Double.self, forKey: .shadowWidth) ?? 40
    }

    /// How far from the start pose (0) toward the end pose (1) the loop is at `t` (0…1).
    func progress(at t: Double) -> Double {
        FigureMath.phase(t, timing)
    }

    func gear(_ kind: Gear.Kind) -> [Gear] {
        gear.filter { $0.kind == kind }
    }

    /// The muscles the figure shades, from the exercise's muscle map.
    var muscles: FigureMuscles {
        FigureMuscles(ExerciseMuscles.byKey[key])
    }
}

/// The muscles the figure shades for an exercise, from its muscle map: the ones it works most in
/// olive and the ones that help lighter. The figure has no shapes of its own for the upper back
/// and the hip's abductors, so its traps and glutes show them; the adductors, inside the thighs,
/// show only from the front. scripts/motions/motion.js does the same for the pictures.
nonisolated struct FigureMuscles: Equatable, Sendable {
    /// The lighter shade comes first, so where muscles meet the ones worked most are drawn on top.
    nonisolated enum Shade: Comparable, CaseIterable, Sendable {
        case secondary, primary
    }

    private(set) var primary: Set<Muscle> = []
    private(set) var secondary: Set<Muscle> = []

    init(_ work: MuscleWork?) {
        primary = Set((work?.primary ?? []).compactMap(Self.shape))
        secondary = Set((work?.secondary ?? []).compactMap(Self.shape)).subtracting(primary)
    }

    /// The muscle whose shape shows `muscle` on the figure, or nil when it can't be seen.
    static func shape(_ muscle: Muscle) -> Muscle? {
        switch muscle {
        case .upperBack: .traps
        case .abductors: .glutes
        default: muscle
        }
    }

    /// How the figure shades `muscle`, or nil when the exercise doesn't work it.
    subscript(muscle: Muscle) -> Shade? {
        primary.contains(muscle) ? .primary : secondary.contains(muscle) ? .secondary : nil
    }
}

nonisolated enum ExerciseMotions {
    /// The animations, in the file's order.
    static let all: [ExerciseMotion] = {
        guard let url = Bundle.main.url(forResource: "ExerciseMotions", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let list = try? decode(data) else { return [] }
        return list
    }()

    /// The animations, by library key.
    static let byKey: [String: ExerciseMotion] = Dictionary(
        all.map { ($0.key, $0) }, uniquingKeysWith: { first, _ in first }
    )

    static func decode(_ data: Data) throws -> [ExerciseMotion] {
        try JSONDecoder().decode([ExerciseMotion].self, from: data)
    }

    /// The animation of an exercise, if it's one of Kitae's with one.
    static func motion(for exercise: Exercise) -> ExerciseMotion? {
        exercise.libraryKey.flatMap { byKey[$0] }
    }
}
