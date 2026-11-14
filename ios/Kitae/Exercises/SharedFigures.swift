// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import KitaeCore

// The figures' poses and joints, and the muscles they and the muscle map shade, as the iPhone's
// own types and the shared Kotlin module's (`motion/` and `muscles/`), which works them out.

nonisolated extension Vec {
    init(_ v: KitaeCore.Vec) {
        self.init(v.x, v.y)
    }

    /// The shared module's own.
    var core: KitaeCore.Vec { KitaeCore.Vec(x: x, y: y) }
}

nonisolated extension ArmPose {
    init(_ pose: any KitaeCore.ArmPose) {
        if let reach = pose as? ArmPoseReach {
            self = .reach(hand: Vec(reach.hand), onTorso: reach.onTorso, scale: reach.scale,
                          upperLength: reach.upperLength, elbow: reach.elbow, flex: reach.flex)
        } else {
            let swing = pose as! ArmPoseSwing
            self = .swing(upper: swing.upper, bend: swing.bend, upperLength: swing.upperLength, flex: swing.flex)
        }
    }

    /// The shared module's own.
    var core: any KitaeCore.ArmPose {
        switch self {
        case let .swing(upper, bend, upperLength, flex):
            ArmPoseSwing(upper: upper, bend: bend, upperLength: upperLength, flex: flex)
        case let .reach(hand, onTorso, scale, upperLength, elbow, flex):
            ArmPoseReach(hand: hand.core, onTorso: onTorso, scale: scale, upperLength: upperLength, elbow: elbow,
                         flex: flex)
        }
    }
}

nonisolated extension FigurePose {
    init(_ pose: KitaeCore.FigurePose) {
        self.init(
            hip: Vec(pose.hip), lean: pose.lean, curl: pose.curl, shrug: pose.shrug, head: pose.head?.doubleValue,
            ankle: Vec(pose.ankle), foot: pose.foot, thigh: pose.thigh?.doubleValue, shin: pose.shin?.doubleValue,
            farAnkle: pose.farAnkle.map { Vec($0) }, farFoot: pose.farFoot?.doubleValue,
            farThigh: pose.farThigh?.doubleValue, farShin: pose.farShin?.doubleValue, knee: pose.knee,
            kneeAt: pose.kneeAt.map { Vec($0) }, arm: ArmPose(pose.arm), farArm: pose.farArm.map { ArmPose($0) }
        )
    }

    /// The shared module's own.
    var core: KitaeCore.FigurePose {
        KitaeCore.FigurePose(
            hip: hip.core, lean: lean, curl: curl, shrug: shrug, head: head?.kotlin, ankle: ankle.core, foot: foot,
            thigh: thigh?.kotlin, shin: shin?.kotlin, farAnkle: farAnkle?.core, farFoot: farFoot?.kotlin,
            farThigh: farThigh?.kotlin, farShin: farShin?.kotlin, knee: knee, kneeAt: kneeAt?.core, arm: arm.core,
            farArm: farArm?.core
        )
    }
}

nonisolated extension Figure.Leg {
    init(_ leg: KitaeCore.Figure.Leg) {
        self.init(hip: Vec(leg.hip), knee: Vec(leg.knee), ankle: Vec(leg.ankle), foot: leg.foot)
    }
}

nonisolated extension Figure.Arm {
    init(_ arm: KitaeCore.Figure.Arm) {
        self.init(shoulder: Vec(arm.shoulder), elbow: Vec(arm.elbow), wrist: Vec(arm.wrist), scale: arm.scale,
                  flex: arm.flex)
    }
}

nonisolated extension FrontArmPose {
    init(_ pose: any KitaeCore.FrontArmPose) {
        if let reach = pose as? FrontArmPoseReach {
            self = .reach(hand: Vec(reach.hand), onTorso: reach.onTorso, elbow: reach.elbow,
                          upperLength: reach.upperLength, foreLength: reach.foreLength)
        } else {
            let swing = pose as! FrontArmPoseSwing
            self = .swing(upper: swing.upper, bend: swing.bend, upperLength: swing.upperLength,
                          foreLength: swing.foreLength)
        }
    }

    /// The shared module's own.
    var core: any KitaeCore.FrontArmPose {
        switch self {
        case let .swing(upper, bend, upperLength, foreLength):
            FrontArmPoseSwing(upper: upper, bend: bend, upperLength: upperLength, foreLength: foreLength)
        case let .reach(hand, onTorso, elbow, upperLength, foreLength):
            FrontArmPoseReach(hand: hand.core, onTorso: onTorso, elbow: elbow, upperLength: upperLength,
                              foreLength: foreLength)
        }
    }
}

nonisolated extension FrontPose {
    init(_ pose: KitaeCore.FrontPose) {
        self.init(
            hip: Vec(pose.hip), turn: pose.turn, lean: pose.lean, torsoLength: pose.torsoLength, shrug: pose.shrug,
            head: pose.head, headHeight: pose.headHeight, thigh: pose.thigh, thighLength: pose.thighLength,
            shin: pose.shin, shinLength: pose.shinLength, arm: FrontArmPose(pose.arm),
            otherArm: pose.otherArm.map { FrontArmPose($0) }, otherThigh: pose.otherThigh?.doubleValue,
            otherShin: pose.otherShin?.doubleValue, ankle: pose.ankle.map { Vec($0) }
        )
    }

    /// The shared module's own.
    var core: KitaeCore.FrontPose {
        KitaeCore.FrontPose(
            hip: hip.core, turn: turn, lean: lean, torsoLength: torsoLength, shrug: shrug, head: head,
            headHeight: headHeight, thigh: thigh, thighLength: thighLength, shin: shin, shinLength: shinLength,
            arm: arm.core, otherArm: otherArm?.core, otherThigh: otherThigh?.kotlin, otherShin: otherShin?.kotlin,
            ankle: ankle?.core
        )
    }
}

nonisolated extension FrontFigure.Leg {
    init(_ leg: KitaeCore.FrontFigure.Leg) {
        self.init(hip: Vec(leg.hip), knee: Vec(leg.knee), ankle: Vec(leg.ankle), side: leg.side)
    }
}

nonisolated extension FrontFigure.Arm {
    init(_ arm: KitaeCore.FrontFigure.Arm) {
        self.init(shoulder: Vec(arm.shoulder), elbow: Vec(arm.elbow), wrist: Vec(arm.wrist), side: arm.side,
                  foreLength: arm.foreLength)
    }
}

nonisolated extension Muscle {
    init(_ muscle: KitaeCore.Muscle) {
        self = Muscle(rawValue: muscle.stored)!
    }

    /// The shared module's own, by the name both store.
    var core: KitaeCore.Muscle {
        KitaeCore.Muscle.Companion.shared.fromStored(stored: rawValue)!
    }
}

nonisolated extension MuscleWork {
    init(_ work: KitaeCore.MuscleWork) {
        self.init(primary: work.primary.map { Muscle($0) }, secondary: work.secondary.map { Muscle($0) })
    }

    /// The shared module's own.
    var core: KitaeCore.MuscleWork {
        KitaeCore.MuscleWork(primary: primary.map(\.core), secondary: secondary.map(\.core))
    }
}

nonisolated extension ExerciseSetCount {
    /// The shared module's own.
    var core: KitaeCore.ExerciseSetCount {
        KitaeCore.ExerciseSetCount(exercise: exercise.core, sets: Int32(clamping: sets))
    }
}
