// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation

// The front view: the figure facing the viewer, for movements out to the sides. Angles work as in
// the side view for the +x arm and leg (the figure's left, on the right of the picture); the other
// arm and leg mirror them unless `otherArm`, or `otherThigh` and `otherShin`, say otherwise. `turn`
// turns the whole figure (a side plank lies at about -80), `lean` bends the body sideways above the
// waist, and the lengths shorten parts that point toward or away from the viewer.

nonisolated enum FrontBody {
    /// Half the distance between the shoulder joints.
    static let shoulder = 19.5
    /// Half the distance between the hip joints.
    static let hipJoint = 9.0
}

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

    static func lerp(_ a: FrontArmPose, _ b: FrontArmPose, _ t: Double) -> FrontArmPose {
        let mix = { (x: Double, y: Double) in FigureMath.lerp(x, y, t) }
        switch (a, b) {
        case let (.swing(u1, b1, ul1, fl1), .swing(u2, b2, ul2, fl2)):
            return .swing(upper: mix(u1, u2), bend: mix(b1, b2), upperLength: mix(ul1, ul2), foreLength: mix(fl1, fl2))
        case let (.reach(h1, o1, e1, ul1, fl1), .reach(h2, _, _, ul2, fl2)):
            return .reach(hand: .lerp(h1, h2, t), onTorso: o1, elbow: e1, upperLength: mix(ul1, ul2),
                          foreLength: mix(fl1, fl2))
        default:
            return t < 0.5 ? a : b
        }
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

    static func lerp(_ a: FrontPose, _ b: FrontPose, _ t: Double) -> FrontPose {
        let mix = { (x: Double, y: Double) in FigureMath.lerp(x, y, t) }
        let mixOptional = { (x: Double?, y: Double?) -> Double? in
            if let x, let y { return mix(x, y) }
            return x ?? y
        }
        var other: FrontArmPose? = a.otherArm ?? b.otherArm
        if let x = a.otherArm, let y = b.otherArm { other = .lerp(x, y, t) }
        var ankle = a.ankle ?? b.ankle
        if let x = a.ankle, let y = b.ankle { ankle = .lerp(x, y, t) }
        return FrontPose(
            hip: .lerp(a.hip, b.hip, t), turn: mix(a.turn, b.turn), lean: mix(a.lean, b.lean),
            torsoLength: mix(a.torsoLength, b.torsoLength), shrug: mix(a.shrug, b.shrug), head: mix(a.head, b.head),
            headHeight: mix(a.headHeight, b.headHeight), thigh: mix(a.thigh, b.thigh),
            thighLength: mix(a.thighLength, b.thighLength), shin: mix(a.shin, b.shin),
            shinLength: mix(a.shinLength, b.shinLength), arm: .lerp(a.arm, b.arm, t), otherArm: other,
            otherThigh: mixOptional(a.otherThigh, b.otherThigh), otherShin: mixOptional(a.otherShin, b.otherShin),
            ankle: ankle)
    }
}

/// A front pose worked out into joint positions.
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

    init(_ p: FrontPose) {
        let r = p.turn * .pi / 180
        let up = Vec.direction(p.turn), side = Vec(cos(r), sin(r))
        let torso = { (along: Double, out: Double) in FrontFigure.point(p, up, side, along, out) }
        let upperUp = Vec.direction(p.turn + p.lean)
        pose = p
        self.up = up
        self.side = side
        headUp = Vec.direction(p.turn + p.lean + p.head)
        neckBase = torso(55, 0)
        neckTop = neckBase + upperUp * (9 * p.torsoLength)
        head = neckTop + headUp * (10.5 * p.headHeight)
        showsNeck = p.headHeight > 0.5

        func arm(_ pose: FrontArmPose, _ s: Double, explicit: Bool) -> Arm {
            let shoulder = torso(48, FrontBody.shoulder * s)
            func mirrored(_ d: Vec) -> Vec { Vec(d.x * s, d.y) }
            switch pose {
            case let .swing(upper, bend, upperLength, foreLength):
                let elbow = shoulder + mirrored(.direction(180 - upper)) * (FigureBody.upperArm * upperLength)
                let wrist = elbow + mirrored(.direction(180 - upper - bend)) * (FigureBody.forearm * foreLength)
                return Arm(shoulder: shoulder, elbow: elbow, wrist: wrist, side: s, foreLength: foreLength)
            case let .reach(hand, onTorso, elbowSide, upperLength, foreLength):
                let target = onTorso ? torso(hand.x, hand.y * s)
                    : explicit ? hand : Vec(p.hip.x + (hand.x - p.hip.x) * s, hand.y)
                let l1 = FigureBody.upperArm * upperLength, l2 = FigureBody.forearm * foreLength
                let elbow = FigureMath.joint(from: shoulder, to: target, l1, l2, bend: elbowSide * s)
                let wrist = elbow + (target - elbow).unit * l2
                return Arm(shoulder: shoulder, elbow: elbow, wrist: wrist, side: s, foreLength: foreLength)
            }
        }
        func leg(_ s: Double) -> Leg {
            let hip = p.hip + side * (FrontBody.hipJoint * s)
            func mirrored(_ d: Vec) -> Vec { Vec(d.x * s, d.y) }
            let l1 = FigureBody.thigh * p.thighLength, l2 = FigureBody.shin * p.shinLength
            if let foot = p.ankle {
                let target = Vec(p.hip.x + (foot.x - p.hip.x) * s, foot.y)
                let knee = FigureMath.joint(from: hip, to: target, l1, l2, bend: -s)
                return Leg(hip: hip, knee: knee, ankle: knee + (target - knee).unit * l2, side: s)
            }
            let thigh = s < 0 ? p.otherThigh ?? p.thigh : p.thigh, shin = s < 0 ? p.otherShin ?? p.shin : p.shin
            let knee = hip + mirrored(.direction(180 - thigh)).rotated(by: r) * l1
            let ankle = knee + mirrored(.direction(180 - shin)).rotated(by: r) * l2
            return Leg(hip: hip, knee: knee, ankle: ankle, side: s)
        }
        arms = [arm(p.otherArm ?? p.arm, -1, explicit: p.otherArm != nil), arm(p.arm, 1, explicit: false)]
        legs = [leg(-1), leg(1)]
    }

    /// A point on the body: `along` the spine from the middle of the pelvis, then `out` to the +x side.
    func torso(_ along: Double, _ out: Double) -> Vec { FrontFigure.point(pose, up, side, along, out) }

    /// The body's angle at a point `along` the spine, for shapes that turn with it.
    func turn(at along: Double) -> Double {
        pose.turn + pose.lean * FigureMath.smoothstep((along - 10) / 16)
    }

    /// The body bends sideways above the waist by `lean`, smoothly between 10 and 26 along it.
    /// Shrugging lifts the shoulders fully and the base of the neck a little.
    private static func point(_ p: FrontPose, _ up: Vec, _ side: Vec, _ along: Double, _ out: Double) -> Vec {
        let lift = p.shrug * FigureMath.smoothstep((along - 42) / 4) * (along >= 55 ? 0.35 : 1)
        let q = p.hip + up * ((along + lift) * p.torsoLength) + side * out
        guard p.lean != 0 else { return q }
        let pivot = p.hip + up * (18 * p.torsoLength)
        return pivot + (q - pivot).rotated(by: p.lean * .pi / 180 * FigureMath.smoothstep((along - 10) / 16))
    }
}
