// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import KitaeCore

// The front view: the figure facing the viewer, for movements out to the sides. Angles work as in
// the side view for the +x arm and leg (the figure's left, on the right of the picture); the other
// arm and leg mirror them unless `otherArm`, or `otherThigh` and `otherShin`, say otherwise. `turn`
// turns the whole figure (a side plank lies at about -80), `lean` bends the body sideways above the
// waist, and the lengths shorten parts that point toward or away from the viewer.

/// Where an arm is in a front keyframe.
nonisolated enum FrontArmPose: Equatable, Sendable, Decodable {
    /// The arm swung out from hanging by `upper` degrees, the elbow bent by `bend`.
    case swing(upper: Double, bend: Double, upperLength: Double, foreLength: Double)
    /// The hand at a point: on the body when `onTorso`, otherwise in the scene. A point given for
    /// `arm` is mirrored for the other arm; one given for `otherArm` is used as it is.
    case reach(hand: Vec, onTorso: Bool, elbow: Double, upperLength: Double, foreLength: Double)

    nonisolated private enum Keys: String, CodingKey { case upper, bend, hand, on, elbow, upperLength, foreLength }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: Keys.self)
        let upperLength = try c.decodeIfPresent(Double.self, forKey: .upperLength) ?? 1
        let foreLength = try c.decodeIfPresent(Double.self, forKey: .foreLength) ?? 1
        if c.contains(.upper) {
            self = .swing(upper: try c.decode(Double.self, forKey: .upper),
                          bend: try c.decodeIfPresent(Double.self, forKey: .bend) ?? 0,
                          upperLength: upperLength, foreLength: foreLength)
        } else {
            self = .reach(hand: try c.decode(Vec.self, forKey: .hand),
                          onTorso: try c.decodeIfPresent(String.self, forKey: .on) == "torso",
                          elbow: try c.decodeIfPresent(Double.self, forKey: .elbow) ?? 1,
                          upperLength: upperLength, foreLength: foreLength)
        }
    }

    /// Blended by the shared module, which keeps an end's arm when the two are placed differently.
    static func lerp(_ a: FrontArmPose, _ b: FrontArmPose, _ t: Double) -> FrontArmPose {
        FrontArmPose(IPhoneMotion.shared.blendFrontArms(a: a.core, b: b.core, t: t))
    }

    /// Two keyframes can only be blended when their arms are placed the same way.
    var kind: String {
        switch self {
        case .swing: "swing"
        case let .reach(_, onTorso, _, _, _): onTorso ? "torso" : "scene"
        }
    }

    var foreLength: Double {
        switch self {
        case let .swing(_, _, _, foreLength), let .reach(_, _, _, _, foreLength): foreLength
        }
    }
}

/// One front keyframe.
nonisolated struct FrontPose: Equatable, Sendable, Decodable {
    /// The middle of the pelvis, between the hip joints.
    var hip: Vec
    var turn: Double
    var lean: Double
    var torsoLength: Double
    var shrug: Double
    /// Tilts the head.
    var head: Double
    /// Below 1 lowers the head in front of the shoulders, as when bent over; below 0.5 it hangs,
    /// showing its crown without a neck.
    var headHeight: Double
    /// The legs' angles, for the +x leg; the other mirrors them unless `otherThigh` and
    /// `otherShin` say otherwise, mirrored the same way (one leg lifted out to the side).
    var thigh: Double
    var thighLength: Double
    var shin: Double
    var shinLength: Double
    var arm: FrontArmPose
    var otherArm: FrontArmPose?
    var otherThigh: Double?
    var otherShin: Double?
    /// The +x ankle, for legs that reach for their feet instead of taking angles (feet planted wide
    /// while the body rises and falls): the knees are worked out, bending outward, and the other
    /// leg mirrors.
    var ankle: Vec?

    nonisolated private enum Keys: String, CodingKey {
        case hip, turn, lean, torsoLength, shrug, head, headHeight, thigh, thighLength, shin, shinLength, arm, otherArm
        case otherThigh, otherShin, ankle
    }

    init(hip: Vec, turn: Double = 0, lean: Double = 0, torsoLength: Double = 1, shrug: Double = 0, head: Double = 0,
         headHeight: Double = 1, thigh: Double = 3, thighLength: Double = 1, shin: Double = 1.5, shinLength: Double = 1,
         arm: FrontArmPose, otherArm: FrontArmPose? = nil, otherThigh: Double? = nil, otherShin: Double? = nil,
         ankle: Vec? = nil) {
        self.hip = hip
        self.turn = turn
        self.lean = lean
        self.torsoLength = torsoLength
        self.shrug = shrug
        self.head = head
        self.headHeight = headHeight
        self.thigh = thigh
        self.thighLength = thighLength
        self.shin = shin
        self.shinLength = shinLength
        self.arm = arm
        self.otherArm = otherArm
        self.otherThigh = otherThigh
        self.otherShin = otherShin
        self.ankle = ankle
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: Keys.self)
        hip = try c.decode(Vec.self, forKey: .hip)
        turn = try c.decodeIfPresent(Double.self, forKey: .turn) ?? 0
        lean = try c.decodeIfPresent(Double.self, forKey: .lean) ?? 0
        torsoLength = try c.decodeIfPresent(Double.self, forKey: .torsoLength) ?? 1
        shrug = try c.decodeIfPresent(Double.self, forKey: .shrug) ?? 0
        head = try c.decodeIfPresent(Double.self, forKey: .head) ?? 0
        headHeight = try c.decodeIfPresent(Double.self, forKey: .headHeight) ?? 1
        thigh = try c.decodeIfPresent(Double.self, forKey: .thigh) ?? 3
        thighLength = try c.decodeIfPresent(Double.self, forKey: .thighLength) ?? 1
        shin = try c.decodeIfPresent(Double.self, forKey: .shin) ?? 1.5
        shinLength = try c.decodeIfPresent(Double.self, forKey: .shinLength) ?? 1
        arm = try c.decode(FrontArmPose.self, forKey: .arm)
        otherArm = try c.decodeIfPresent(FrontArmPose.self, forKey: .otherArm)
        otherThigh = try c.decodeIfPresent(Double.self, forKey: .otherThigh)
        otherShin = try c.decodeIfPresent(Double.self, forKey: .otherShin)
        ankle = try c.decodeIfPresent(Vec.self, forKey: .ankle)
    }

    /// Blended by the shared module.
    static func lerp(_ a: FrontPose, _ b: FrontPose, _ t: Double) -> FrontPose {
        FrontPose(KitaeCore.FrontPose.Companion.shared.lerp(a: a.core, b: b.core, t: t))
    }
}

/// A front pose worked out into joint positions, by the shared module (`FrontFigure.kt`).
nonisolated struct FrontFigure {
    nonisolated struct Leg {
        var hip, knee, ankle: Vec
        /// 1 for the +x leg, -1 for the other.
        var side: Double
    }

    nonisolated struct Arm {
        var shoulder, elbow, wrist: Vec
        var side: Double
        var foreLength: Double
    }

    let pose: FrontPose
    /// Up and toward the +x side, turned with the figure.
    let up: Vec
    let side: Vec
    let headUp: Vec
    let neckBase: Vec
    let neckTop: Vec
    let head: Vec
    let showsNeck: Bool
    /// The -x arm first, so the +x arm is drawn in front of it.
    let arms: [Arm]
    let legs: [Leg]
    /// The shared module's own.
    private let core: KitaeCore.FrontFigure

    init(_ pose: FrontPose) {
        let core = KitaeCore.FrontFigure(pose: pose.core)
        self.core = core
        self.pose = pose
        up = Vec(core.up)
        side = Vec(core.side)
        headUp = Vec(core.headUp)
        neckBase = Vec(core.neckBase)
        neckTop = Vec(core.neckTop)
        head = Vec(core.head)
        showsNeck = core.showsNeck
        arms = core.arms.map { Arm($0) }
        legs = core.legs.map { Leg($0) }
    }

    /// A point on the body: `along` the spine from the middle of the pelvis, then `out` to the +x
    /// side. The body bends sideways above the waist by `lean`, smoothly between 10 and 26 along
    /// it, and shrugging lifts the shoulders fully and the base of the neck a little.
    func torso(_ along: Double, _ out: Double) -> Vec { Vec(core.torso(along: along, out: out)) }

    /// The body's angle at a point `along` the spine, for shapes that turn with it.
    func turn(at along: Double) -> Double { core.turn(along: along) }
}
