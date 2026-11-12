// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import SwiftUI

// The geometry behind the exercise animations: a flat side view of a person, posed from a few
// numbers per keyframe. Units are points for a figure about 190 tall. The floor is y = 0, y grows
// downward, x grows the way the figure faces, and the near ankle usually stands at x = 0.

/// A point or a direction in the figure's space.
nonisolated struct Vec: Equatable, Sendable, Decodable {
    var x: Double
    var y: Double

    init(_ x: Double, _ y: Double) {
        self.x = x
        self.y = y
    }

    /// Written as `[x, y]` in ExerciseMotions.json.
    init(from decoder: Decoder) throws {
        var container = try decoder.unkeyedContainer()
        x = try container.decode(Double.self)
        y = try container.decode(Double.self)
    }

    static let zero = Vec(0, 0)

    static func + (a: Vec, b: Vec) -> Vec { Vec(a.x + b.x, a.y + b.y) }
    static func - (a: Vec, b: Vec) -> Vec { Vec(a.x - b.x, a.y - b.y) }
    static func * (a: Vec, k: Double) -> Vec { Vec(a.x * k, a.y * k) }

    var length: Double { (x * x + y * y).squareRoot() }
    var unit: Vec { length > 0 ? self * (1 / length) : .zero }
    /// For a limb that points down the body, the side it bends toward when it hangs straight down.
    var front: Vec { Vec(y, -x) }
    var point: CGPoint { CGPoint(x: x, y: y) }

    func rotated(by radians: Double) -> Vec {
        Vec(x * cos(radians) - y * sin(radians), x * sin(radians) + y * cos(radians))
    }

    /// A unit direction: 0° up, 90° forward, 180° down.
    static func direction(_ degrees: Double) -> Vec {
        let r = degrees * .pi / 180
        return Vec(sin(r), -cos(r))
    }

    static func lerp(_ a: Vec, _ b: Vec, _ t: Double) -> Vec { a + (b - a) * t }
}

/// Lengths of a figure 190 tall, in standard body proportions.
nonisolated enum FigureBody {
    static let thigh = 46.5
    static let shin = 46.7
    static let upperArm = 35.3
    static let forearm = 27.7
    static let ankleHeight = 7.4
    /// The far arm and leg sit a little behind the near ones.
    static let farOffset = Vec(-3.6, -1.6)
}

nonisolated enum FigureMath {
    static func lerp(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

    /// The middle joint (knee or elbow) of a two-part limb reaching from `root` to `target`.
    /// `bend` picks the side the joint goes to.
    static func joint(from root: Vec, to target: Vec, _ l1: Double, _ l2: Double, bend: Double) -> Vec {
        let d = target - root
        let dist = min(max(d.length, abs(l1 - l2) + 0.01), l1 + l2 - 0.01)
        let cosA = (l1 * l1 + dist * dist - l2 * l2) / (2 * l1 * dist)
        return root + d.unit.rotated(by: bend * acos(min(1, max(-1, cosA)))) * l1
    }

    /// Eases in and out over 0…1.
    static func ease(_ x: Double) -> Double { 0.5 - 0.5 * cos(.pi * min(1, max(0, x))) }

    /// 0 below 0, 1 above 1, and a smooth S between.
    static func smoothstep(_ x: Double) -> Double {
        let k = min(1, max(0, x))
        return k * k * (3 - 2 * k)
    }

    /// How far from the start pose (0) toward the end pose (1) a loop is at `t` (0…1).
    /// `k` holds the ends of: the hold at the start, the move to the end, the hold at the end,
    /// and the move back.
    static func phase(_ t: Double, _ k: [Double]) -> Double {
        guard k.count == 4 else { return 0 }
        if t < k[0] { return 0 }
        if t < k[1] { return ease((t - k[0]) / (k[1] - k[0])) }
        if t < k[2] { return 1 }
        if t < k[3] { return 1 - ease((t - k[2]) / (k[3] - k[2])) }
        return 0
    }
}

/// The shapes the figure is drawn with.
nonisolated enum FigureShapes {
    /// A limb: a tapered capsule between two circles. Every capsule winds the same way,
    /// so several of them filled as one path make their union.
    static func capsule(_ p1: Vec, _ p2: Vec, _ r1: Double, _ r2: Double) -> Path {
        let d = p2 - p1
        let dist = d.length
        guard dist > 0.01 else { return circle(p1, max(r1, r2)) }
        let th = atan2(d.y, d.x)
        let phi = acos(min(1, max(-1, (r1 - r2) / dist)))
        func on(_ c: Vec, _ r: Double, _ a: Double) -> Vec { c + Vec(cos(a), sin(a)) * r }
        var path = Path()
        path.move(to: on(p1, r1, th + phi).point)
        path.addLine(to: on(p2, r2, th + phi).point)
        arc(&path, around: p2, radius: r2, from: th + phi, to: th - phi)
        path.addLine(to: on(p1, r1, th - phi).point)
        arc(&path, around: p1, radius: r1, from: th - phi, to: th + phi - 2 * .pi)
        path.closeSubpath()
        return path
    }

    /// A circle, wound the same way as capsules so a union of the two fills solid.
    static func circle(_ c: Vec, _ r: Double) -> Path {
        var path = Path()
        path.move(to: (c + Vec(r, 0)).point)
        arc(&path, around: c, radius: r, from: 0, to: -2 * .pi)
        path.closeSubpath()
        return path
    }

    static func ellipse(_ c: Vec, _ rx: Double, _ ry: Double, turned degrees: Double) -> Path {
        Path(ellipseIn: CGRect(x: -rx, y: -ry, width: 2 * rx, height: 2 * ry))
            .applying(CGAffineTransform(rotationAngle: degrees * .pi / 180)
                .concatenating(CGAffineTransform(translationX: c.x, y: c.y)))
    }

    /// A smooth closed outline through the points.
    static func outline(_ points: [Vec]) -> Path {
        var path = Path()
        let n = points.count
        guard n > 2 else { return path }
        path.move(to: points[0].point)
        for i in 0..<n {
            let p0 = points[(i + n - 1) % n], p1 = points[i]
            let p2 = points[(i + 1) % n], p3 = points[(i + 2) % n]
            path.addCurve(to: p2.point,
                          control1: (p1 + (p2 - p0) * (1.0 / 6)).point,
                          control2: (p2 - (p3 - p1) * (1.0 / 6)).point)
        }
        path.closeSubpath()
        return path
    }

    static func polygon(_ points: [Vec]) -> Path {
        var path = Path()
        path.addLines(points.map(\.point))
        path.closeSubpath()
        return path
    }

    static func hexagon(_ c: Vec, _ r: Double, turned degrees: Double) -> Path {
        polygon((0..<6).map { c + Vec.direction(degrees + Double($0) * 60) * r })
    }

    /// Several shapes filled as one.
    static func union(_ paths: Path...) -> Path {
        var path = Path()
        for p in paths { path.addPath(p) }
        return path
    }

    /// Adds an arc from angle `a0` to `a1` (radians, either way round) as cubic curves,
    /// starting from the current point.
    private static func arc(_ path: inout Path, around c: Vec, radius r: Double, from a0: Double, to a1: Double) {
        let steps = max(1, Int((abs(a1 - a0) / (.pi / 2)).rounded(.up)))
        let step = (a1 - a0) / Double(steps)
        let k = 4.0 / 3.0 * tan(step / 4) * r
        var a = a0
        for _ in 0..<steps {
            let b = a + step
            let p0 = c + Vec(cos(a), sin(a)) * r
            let p3 = c + Vec(cos(b), sin(b)) * r
            path.addCurve(to: p3.point,
                          control1: (p0 + Vec(-sin(a), cos(a)) * k).point,
                          control2: (p3 - Vec(-sin(b), cos(b)) * k).point)
            a = b
        }
    }
}

/// Where an arm is in a keyframe.
nonisolated enum ArmPose: Equatable, Sendable, Decodable {
    /// The upper arm swung forward from hanging by `upper` degrees, the elbow bent by `bend`.
    /// `upperLength` shortens the upper arm when it points toward the viewer, as in a press.
    /// `flex` bends the hand at the wrist, in degrees toward the front of the forearm.
    case swing(upper: Double, bend: Double, upperLength: Double, flex: Double = 0)
    /// The hand at a point: on the body (along the spine, then out the front) when `onTorso`,
    /// otherwise in the scene. `scale` shortens an arm that reaches out sideways, `upperLength`
    /// shortens just its upper half, and `elbow` picks the side the elbow goes to.
    case reach(hand: Vec, onTorso: Bool, scale: Double, upperLength: Double, elbow: Double, flex: Double = 0)

    nonisolated private enum Keys: String, CodingKey { case upper, bend, upperLength, hand, on, scale, elbow, flex }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: Keys.self)
        let upperLength = try c.decodeIfPresent(Double.self, forKey: .upperLength) ?? 1
        let flex = try c.decodeIfPresent(Double.self, forKey: .flex) ?? 0
        if c.contains(.upper) {
            self = .swing(upper: try c.decode(Double.self, forKey: .upper),
                          bend: try c.decodeIfPresent(Double.self, forKey: .bend) ?? 0,
                          upperLength: upperLength, flex: flex)
        } else {
            self = .reach(hand: try c.decode(Vec.self, forKey: .hand),
                          onTorso: try c.decodeIfPresent(String.self, forKey: .on) == "torso",
                          scale: try c.decodeIfPresent(Double.self, forKey: .scale) ?? 1,
                          upperLength: upperLength,
                          elbow: try c.decodeIfPresent(Double.self, forKey: .elbow) ?? -1, flex: flex)
        }
    }

    static func lerp(_ a: ArmPose, _ b: ArmPose, _ t: Double) -> ArmPose {
        switch (a, b) {
        case let (.swing(u1, b1, l1, f1), .swing(u2, b2, l2, f2)):
            return .swing(upper: FigureMath.lerp(u1, u2, t), bend: FigureMath.lerp(b1, b2, t),
                          upperLength: FigureMath.lerp(l1, l2, t), flex: FigureMath.lerp(f1, f2, t))
        case let (.reach(h1, o1, s1, l1, e1, f1), .reach(h2, _, s2, l2, _, f2)):
            return .reach(hand: .lerp(h1, h2, t), onTorso: o1, scale: FigureMath.lerp(s1, s2, t),
                          upperLength: FigureMath.lerp(l1, l2, t), elbow: e1, flex: FigureMath.lerp(f1, f2, t))
        default:
            return t < 0.5 ? a : b
        }
    }

    /// Two keyframes can only be blended when their arms are placed the same way.
    var kind: String {
        switch self {
        case .swing: "swing"
        case let .reach(_, onTorso, _, _, _, _): onTorso ? "torso" : "scene"
        }
    }
}

/// One keyframe of a figure.
nonisolated struct FigurePose: Equatable, Sendable, Decodable {
    /// The near hip joint.
    var hip: Vec
    /// The spine's direction: 0 upright, 90 lying face down with the head forward,
    /// -90 lying face up with the head back.
    var lean: Double
    /// How far the spine above the waist bends forward, as in a crunch.
    var curl: Double
    /// How far the shoulders rise, as in a shrug.
    var shrug: Double
    /// The head's direction, which follows the spine when not given.
    var head: Double?
    /// The near ankle, which the leg reaches for unless `thigh` and `shin` give its angles.
    var ankle: Vec
    /// Turns the foot about the ankle: positive points the toes down.
    var foot: Double
    /// The leg's angles, for legs that swing (leg raises, leg curls) rather than stand.
    var thigh: Double?
    var shin: Double?
    /// The far leg, when it isn't beside the near one.
    var farAnkle: Vec?
    var farFoot: Double?
    var farThigh: Double?
    var farShin: Double?
    /// The side the knees bend to when reaching for the ankle: -1 forward, 1 back.
    var knee: Double
    /// A kneeling pose pinned at the knee: the hip follows from the thigh's angle.
    var kneeAt: Vec?
    var arm: ArmPose
    /// The far arm, when it isn't doing the same as the near one.
    var farArm: ArmPose?

    nonisolated private enum Keys: String, CodingKey {
        case hip, lean, curl, shrug, head, ankle, foot, thigh, shin, farAnkle, farFoot, farThigh, farShin, knee
        case kneeAt, arm, farArm
    }

    init(hip: Vec, lean: Double, curl: Double = 0, shrug: Double = 0, head: Double? = nil,
         ankle: Vec = Vec(0, -FigureBody.ankleHeight), foot: Double = 0, thigh: Double? = nil, shin: Double? = nil,
         farAnkle: Vec? = nil, farFoot: Double? = nil, farThigh: Double? = nil, farShin: Double? = nil,
         knee: Double = -1, kneeAt: Vec? = nil, arm: ArmPose, farArm: ArmPose? = nil) {
        self.hip = hip
        self.lean = lean
        self.curl = curl
        self.shrug = shrug
        self.head = head
        self.ankle = ankle
        self.foot = foot
        self.thigh = thigh
        self.shin = shin
        self.farAnkle = farAnkle
        self.farFoot = farFoot
        self.farThigh = farThigh
        self.farShin = farShin
        self.knee = knee
        self.kneeAt = kneeAt
        self.arm = arm
        self.farArm = farArm
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: Keys.self)
        hip = try c.decode(Vec.self, forKey: .hip)
        lean = try c.decode(Double.self, forKey: .lean)
        curl = try c.decodeIfPresent(Double.self, forKey: .curl) ?? 0
        shrug = try c.decodeIfPresent(Double.self, forKey: .shrug) ?? 0
        head = try c.decodeIfPresent(Double.self, forKey: .head)
        ankle = try c.decodeIfPresent(Vec.self, forKey: .ankle) ?? Vec(0, -FigureBody.ankleHeight)
        foot = try c.decodeIfPresent(Double.self, forKey: .foot) ?? 0
        thigh = try c.decodeIfPresent(Double.self, forKey: .thigh)
        shin = try c.decodeIfPresent(Double.self, forKey: .shin)
        farAnkle = try c.decodeIfPresent(Vec.self, forKey: .farAnkle)
        farFoot = try c.decodeIfPresent(Double.self, forKey: .farFoot)
        farThigh = try c.decodeIfPresent(Double.self, forKey: .farThigh)
        farShin = try c.decodeIfPresent(Double.self, forKey: .farShin)
        knee = try c.decodeIfPresent(Double.self, forKey: .knee) ?? -1
        kneeAt = try c.decodeIfPresent(Vec.self, forKey: .kneeAt)
        arm = try c.decode(ArmPose.self, forKey: .arm)
        farArm = try c.decodeIfPresent(ArmPose.self, forKey: .farArm)
    }

    static func lerp(_ a: FigurePose, _ b: FigurePose, _ t: Double) -> FigurePose {
        func mix(_ x: Double?, _ y: Double?) -> Double? {
            guard let x, let y else { return x ?? y }
            return FigureMath.lerp(x, y, t)
        }
        func mix(_ x: Vec?, _ y: Vec?) -> Vec? {
            guard let x, let y else { return x ?? y }
            return .lerp(x, y, t)
        }
        func mix(_ x: ArmPose?, _ y: ArmPose?) -> ArmPose? {
            guard let x, let y else { return x ?? y }
            return .lerp(x, y, t)
        }
        return FigurePose(
            hip: .lerp(a.hip, b.hip, t), lean: FigureMath.lerp(a.lean, b.lean, t),
            curl: FigureMath.lerp(a.curl, b.curl, t), shrug: FigureMath.lerp(a.shrug, b.shrug, t),
            head: mix(a.head, b.head), ankle: .lerp(a.ankle, b.ankle, t), foot: FigureMath.lerp(a.foot, b.foot, t),
            thigh: mix(a.thigh, b.thigh), shin: mix(a.shin, b.shin),
            farAnkle: mix(a.farAnkle, b.farAnkle), farFoot: mix(a.farFoot, b.farFoot),
            farThigh: mix(a.farThigh, b.farThigh), farShin: mix(a.farShin, b.farShin), knee: a.knee,
            kneeAt: mix(a.kneeAt, b.kneeAt), arm: .lerp(a.arm, b.arm, t), farArm: mix(a.farArm, b.farArm))
    }
}

/// A pose worked out into joint positions.
nonisolated struct Figure {
    nonisolated struct Leg {
        var hip, knee, ankle: Vec
        var foot: Double
    }

    nonisolated struct Arm {
        var shoulder, elbow, wrist: Vec
        var scale: Double
        /// How far the hand bends at the wrist, in degrees toward the front of the forearm.
        var flex = 0.0
    }

    let pose: FigurePose
    let spine: Vec
    let front: Vec
    let neckBase: Vec
    let neckTop: Vec
    let head: Vec
    let headDirection: Vec
    let nearLeg: Leg
    let farLeg: Leg
    let nearArm: Arm
    let farArm: Arm

    init(_ pose: FigurePose) {
        var p = pose
        if let kneeAt = p.kneeAt, let thigh = p.thigh {
            p.hip = kneeAt - Vec.direction(thigh) * FigureBody.thigh
        }
        let spine = Vec.direction(p.lean)
        let front = Vec(-spine.y, spine.x)
        let torso = { (along: Double, out: Double) in Figure.point(p, spine, front, along, out) }
        let headDirection = Vec.direction(p.head ?? (p.lean + p.curl))
        let headFront = Vec(-headDirection.y, headDirection.x)

        self.pose = p
        self.spine = spine
        self.front = front
        self.headDirection = headDirection
        neckBase = torso(52, 1.5)
        neckTop = neckBase + headDirection * 9
        head = neckTop + headDirection * 9.5 + headFront * 1.5

        func leg(_ ankle: Vec, _ foot: Double, _ thigh: Double?, _ shin: Double?, _ o: Vec) -> Leg {
            let hip = p.hip + o
            if let thigh, let shin {
                let knee = hip + Vec.direction(thigh) * FigureBody.thigh
                return Leg(hip: hip, knee: knee, ankle: knee + Vec.direction(shin) * FigureBody.shin, foot: foot)
            }
            let a = ankle + o
            let knee = FigureMath.joint(from: hip, to: a, FigureBody.thigh, FigureBody.shin, bend: p.knee)
            return Leg(hip: hip, knee: knee, ankle: a, foot: foot)
        }
        let shoulderPoint = torso(48 + p.shrug, -0.5)
        func arm(_ pose: ArmPose, _ o: Vec) -> Arm {
            let shoulder = shoulderPoint + o
            switch pose {
            case let .swing(upper, bend, upperLength, flex):
                let elbow = shoulder + Vec.direction(180 - upper) * (FigureBody.upperArm * upperLength)
                let wrist = elbow + Vec.direction(180 - upper - bend) * FigureBody.forearm
                return Arm(shoulder: shoulder, elbow: elbow, wrist: wrist, scale: 1, flex: flex)
            case let .reach(hand, onTorso, scale, upperLength, side, flex):
                let target = (onTorso ? torso(hand.x, hand.y) : hand) + o
                let elbow = FigureMath.joint(from: shoulder, to: target, FigureBody.upperArm * scale * upperLength,
                                             FigureBody.forearm * scale, bend: side)
                let wrist = elbow + (target - elbow).unit * (FigureBody.forearm * scale)
                return Arm(shoulder: shoulder, elbow: elbow, wrist: wrist, scale: scale, flex: flex)
            }
        }
        let far = FigureBody.farOffset
        nearLeg = leg(p.ankle, p.foot, p.thigh, p.shin, .zero)
        farLeg = leg(p.farAnkle ?? p.ankle, p.farFoot ?? p.foot, p.farThigh ?? p.thigh, p.farShin ?? p.shin, far)
        nearArm = arm(p.arm, .zero)
        farArm = arm(p.farArm ?? p.arm, far)
    }

    /// A point on the body: `along` the spine from the hip, then `out` the front.
    func torso(_ along: Double, _ out: Double) -> Vec { Figure.point(pose, spine, front, along, out) }

    /// The spine's direction at a point `along` it, following the curl.
    func spine(at along: Double) -> Vec {
        spine.rotated(by: pose.curl * .pi / 180 * FigureMath.smoothstep((along - 10) / 16))
    }

    /// The direction the body faces at a point `along` the spine, as an angle like `lean`.
    func turn(at along: Double) -> Double {
        pose.lean + pose.curl * FigureMath.smoothstep((along - 10) / 16)
    }

    /// The spine bends above the waist by `curl`, smoothly between 10 and 26 along it.
    private static func point(_ p: FigurePose, _ spine: Vec, _ front: Vec, _ along: Double, _ out: Double) -> Vec {
        let q = p.hip + spine * along + front * out
        guard p.curl != 0 else { return q }
        let pivot = p.hip + spine * 18
        return pivot + (q - pivot).rotated(by: p.curl * .pi / 180 * FigureMath.smoothstep((along - 10) / 16))
    }
}
