// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import SwiftUI

nonisolated extension FigurePainter {
    /// The front figure's outline: from the crotch round the hips, up the side, over the shoulders
    /// and the base of the neck, and down the other side. Along the spine, then out to the +x side.
    private static let frontTorso: [(Double, Double)] = [
        (-10, 0), (-8, 9), (-4, 15), (4, 16.5), (14, 14), (24, 15), (34, 18), (42, 21), (48, 22),
        (51.5, 17.5), (54, 10), (55.5, 5), (55.5, -5), (54, -10), (51.5, -17.5), (48, -22), (42, -21),
        (34, -18), (24, -15), (14, -14), (4, -16.5), (-4, -15), (-8, -9),
    ]

    /// Draws a front figure back to front: the shadow, frames and stacks, pads, legs, body and
    /// muscles (legs in front of the body when they come toward the viewer), head, cables, arms, and
    /// gear in the hands. Arms, and legs in front of the body, get a thin outline in the card's
    /// colour so they stand apart from the body behind them. scripts/motions/motion.js draws the same.
    func draw(_ f: FrontFigure, in context: inout GraphicsContext) {
        let c = palette
        let muscles = motion.muscles
        let turn = f.pose.turn, r = turn * .pi / 180

        func fill(_ path: Path, _ color: Color) {
            context.fill(path, with: .color(color))
        }
        func fillInside(_ clip: Path, _ path: Path, _ color: Color) {
            var clipped = context
            clipped.clip(to: clip)
            clipped.fill(path, with: .color(color))
        }
        // An outline in the card's colour, left off round a joint so limbs stay joined to the body.
        func separate(_ path: Path, from joint: Vec, radius: Double) {
            var outside = Path(CGRect(x: -2000, y: -2000, width: 4000, height: 4000))
            outside.addPath(FigureShapes.circle(joint, radius))
            var kept = context
            kept.clip(to: outside, style: FillStyle(eoFill: true))
            kept.stroke(path, with: .color(c.card), style: StrokeStyle(lineWidth: 3.2, lineJoin: .round))
        }
        func plate(_ at: Vec, _ radius: Double) {
            fill(FigureShapes.circle(at, radius), c.gear)
            fill(FigureShapes.circle(at, radius * 0.735), c.gearFace)
            fill(FigureShapes.circle(at, radius * 0.21), c.body)
        }
        func pad(_ g: Gear) {
            guard let from = g.from, let to = g.to else { return }
            let d = (to - from).unit, right = Vec(-d.y, d.x), h = g.thick / 2
            for x in g.legs {
                let k = (x - from.x) / (to.x - from.x == 0 ? 1 : to.x - from.x)
                let top = Vec.lerp(from, to, k) + right * h
                fill(FigureShapes.capsule(top, Vec(x, -2), 1.9, 1.9), c.prop)
                fill(FigureShapes.capsule(Vec(x - 8, -1.6), Vec(x + 8, -1.6), 1.6, 1.6), c.prop)
            }
            fill(FigureShapes.capsule(from + right * h, to + right * h, h, h), c.prop)
        }
        func legParts(_ l: FrontFigure.Leg) -> (thigh: Path, shin: Path, foot: Path, all: Path) {
            let mid = Vec.lerp(l.hip, l.knee, 0.42), calf = Vec.lerp(l.knee, l.ankle, 0.3)
            let thigh = FigureShapes.union(FigureShapes.capsule(l.hip, mid, 10.4, 9.2),
                                           FigureShapes.capsule(mid, l.knee, 9.2, 6.6))
            let shin = FigureShapes.union(FigureShapes.capsule(l.knee, calf, 6.6, 7.2),
                                          FigureShapes.capsule(calf, l.ankle, 7.2, 3.9))
            let foot = FigureShapes.ellipse(l.ankle + Vec(1.5 * l.side, 3.4).rotated(by: r), 5.2, 3.6, turned: turn)
            return (thigh, shin, foot, FigureShapes.union(thigh, shin, foot))
        }
        func drawLegs(inFront: Bool) {
            for l in f.legs {
                let parts = legParts(l)
                if inFront { separate(parts.all, from: l.hip, radius: 11.5) }
                fill(parts.thigh, c.body)
                fill(parts.shin, c.body)
                fill(parts.foot, c.body)
            }
        }
        func armParts(_ a: FrontFigure.Arm) -> (fd: Vec, grip: Vec, upper: Path, forearm: Path, hand: Path, all: Path) {
            let fd = (a.wrist - a.elbow).unit, fore = Vec.lerp(a.elbow, a.wrist, 0.3)
            let grip = a.wrist + fd * (6.5 * max(a.foreLength, 0.5))
            let upper = FigureShapes.capsule(a.shoulder, a.elbow, 6.8, 4.5)
            let forearm = FigureShapes.union(FigureShapes.capsule(a.elbow, fore, 4.5, 5.0),
                                             FigureShapes.capsule(fore, a.wrist, 5.0, 3.3))
            let hand = FigureShapes.capsule(a.wrist, grip, 3.6, 3.9)
            return (fd, grip, upper, forearm, hand, FigureShapes.union(upper, forearm, hand))
        }
        let arms = f.arms.map { (arm: $0, parts: armParts($0)) }
        func grip(side: Double) -> Vec {
            arms.first { $0.arm.side == side }?.parts.grip ?? .zero
        }

        fill(FigureShapes.ellipse(motion.shadow, motion.shadowWidth, 3.4, turned: 0), c.shadow)
        for g in motion.gear(.cable) where g.stack {
            if let pulley = g.from {
                fill(FigureShapes.capsule(Vec(pulley.x, -3), pulley, 6.5, 6.5), c.prop)
            }
        }
        for g in motion.gear(.post) {
            if let from = g.from, let to = g.to {
                fill(FigureShapes.capsule(from, to, g.width / 2, g.width / 2), c.prop)
            }
        }
        for g in motion.gear(.pad) where !g.front {
            pad(g)
        }
        if !motion.legsFront {
            drawLegs(inFront: false)
        }

        // The body and its muscles.
        let torso = FigureShapes.outline(Self.frontTorso.map { f.torso($0.0, $0.1) })
        fill(torso, c.body)
        let sides = [1.0, -1.0]
        func tint(_ shade: FigureMuscles.Shade) -> Color {
            shade == .primary ? c.muscle : c.muscleSecondary
        }
        // The glutes show at the outer hips; with legs in front, on the outside of the thighs.
        func glutes(_ s: Double) -> Path {
            motion.legsFront ? FigureShapes.ellipse(f.torso(2, 17 * s), 3.2, 7, turned: f.turn(at: 2))
                : FigureShapes.ellipse(f.torso(1, 13.5 * s), 3.6, 7.5, turned: f.turn(at: 1))
        }
        for shade in FigureMuscles.Shade.allCases {
            let color = tint(shade)
            if muscles[.chest] == shade {
                for s in sides {
                    fillInside(torso, FigureShapes.ellipse(f.torso(37.5, 8.5 * s), 8.2, 6.4, turned: f.turn(at: 37.5)), color)
                }
            }
            if muscles[.abs] == shade {
                fillInside(torso, FigureShapes.capsule(f.torso(5, 0), f.torso(31, 0), 5, 5.2), color)
            }
            if muscles[.obliques] == shade {
                for s in sides {
                    fillInside(torso, FigureShapes.ellipse(f.torso(16, 12 * s), 3.6, 8.5, turned: f.turn(at: 16)), color)
                }
            }
            if muscles[.lats] == shade {
                for s in sides {
                    fillInside(torso, FigureShapes.ellipse(f.torso(31, 16 * s), 3.4, 8, turned: f.turn(at: 31)), color)
                }
            }
            if muscles[.traps] == shade {
                for s in sides {
                    fillInside(torso, FigureShapes.capsule(f.torso(53.5, 6 * s), f.torso(50.5, 15.5 * s), 3.2, 3.6), color)
                }
            }
            if muscles[.glutes] == shade, !motion.legsFront {
                for s in sides { fillInside(torso, glutes(s), color) }
            }
        }
        if motion.legsFront {
            drawLegs(inFront: true)
            for shade in FigureMuscles.Shade.allCases where muscles[.glutes] == shade {
                for s in sides {
                    fillInside(FigureShapes.union(torso, legParts(f.legs[s > 0 ? 1 : 0]).thigh), glutes(s), tint(shade))
                }
            }
        }

        // Neck and head; a head hanging forward shows its crown in front of the shoulders.
        let headTurn = turn + f.pose.lean + f.pose.head
        if f.showsNeck {
            fill(FigureShapes.capsule(f.neckBase, f.neckTop, 5.6, 5.0), c.body)
            fill(FigureShapes.ellipse(f.head + f.headUp * 1.5, 9.8, 10.4, turned: headTurn), c.body)
            fill(FigureShapes.ellipse(f.head - f.headUp * 5, 7.2, 6.8, turned: headTurn), c.body)
        } else {
            context.stroke(FigureShapes.circle(f.head, 10.6), with: .color(c.card), lineWidth: 3.2)
            fill(FigureShapes.circle(f.head, 10), c.body)
        }
        for g in motion.gear(.pad) where g.front {
            pad(g)
        }

        // Cables run in front of the body to the hand on their side.
        for g in motion.gear(.cable) {
            guard let pulley = g.from else { continue }
            fill(FigureShapes.capsule(pulley, grip(side: g.side), 0.8, 0.8), c.gear)
            fill(FigureShapes.circle(pulley, 4.2), c.gear)
            fill(FigureShapes.circle(pulley, 1.7), c.prop)
        }
        // A band stretched between the hands, in front of the body.
        if !motion.gear(.band).isEmpty {
            fill(FigureShapes.capsule(grip(side: 1), grip(side: -1), 1.2, 1.2), c.gear)
        }

        // The arms, the -x one first. From the front the three delts are one shoulder cap, shaded
        // as the most worked of them.
        let delts = [Muscle.frontDelts, .sideDelts, .rearDelts].compactMap { muscles[$0] }.max()
        for (a, p) in arms {
            separate(p.all, from: a.shoulder, radius: 9)
            fill(p.upper, c.body)
            fill(p.forearm, c.body)
            fill(p.hand, c.body)
            for shade in FigureMuscles.Shade.allCases {
                let color = tint(shade)
                if delts == shade {
                    let cap = FigureShapes.union(p.upper, FigureShapes.circle(a.shoulder, 7.2))
                    fillInside(cap, muscleShape(a.shoulder, a.elbow, 6.8, 4.5, at: (0, 0.16, 0.38), widths: (1, 1, 0.7), side: 0),
                               color)
                }
                if muscles[.triceps] == shade {
                    fillInside(p.upper, muscleShape(a.shoulder, a.elbow, 6.8, 4.5, at: (0.25, 0.55, 0.88),
                                                    widths: (0.5, 0.6, 0.5), side: a.side), color)
                }
                if muscles[.biceps] == shade {
                    fillInside(p.upper, muscleShape(a.shoulder, a.elbow, 6.8, 4.5, at: (0.25, 0.55, 0.88),
                                                    widths: (0.5, 0.62, 0.5), side: -a.side), color)
                }
            }
        }

        // What the hands hold, and pads beside the knees.
        let dumbbells = motion.gear(.dumbbells)
        if !dumbbells.isEmpty {
            let onlyOne = dumbbells.contains(where: \.one), profile = dumbbells.contains(where: \.profile)
            for (a, p) in arms where !onlyOne || a.side == 1 {
                let g = p.grip
                if profile {
                    let axis = p.fd.front
                    fill(FigureShapes.capsule(g - axis * 7, g + axis * 7, 1.7, 1.7), c.gear)
                    for k in [-8.0, 8] {
                        let end = g + axis * k
                        fill(FigureShapes.capsule(end - p.fd * 6.5, end + p.fd * 6.5, 2.8, 2.8), c.gear)
                    }
                } else {
                    fill(FigureShapes.hexagon(g, 8.6, turned: 30), c.gear)
                    fill(FigureShapes.hexagon(g, 6.0, turned: 30), c.gearFace)
                    fill(FigureShapes.circle(g, 2.4), c.body)
                }
            }
        }
        for g in motion.gear(.plate) {
            plate(grip(side: 1), g.radius ?? 12)
        }
        if !motion.gear(.handle).isEmpty {
            for (_, p) in arms {
                fill(FigureShapes.circle(p.grip, 3.4), c.gear)
            }
        }
        if !motion.gear(.kneepads).isEmpty {
            for l in f.legs {
                let at = l.knee + f.side * (10.5 * l.side)
                fill(FigureShapes.capsule(at + f.up * 8, at - f.up * 8, 4, 4), c.prop)
            }
        }
    }
}
