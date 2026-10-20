// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import CoreGraphics
import Foundation
import SwiftUI
import Testing
@testable import Kitae

/// The exercise animations: that they load, match the starter exercises and hold together.
struct ExerciseMotionTests {
    private func motions() throws -> [ExerciseMotion] {
        let url = try #require(Bundle.main.url(forResource: "ExerciseMotions", withExtension: "json"))
        return try ExerciseMotions.decode(Data(contentsOf: url))
    }

    /// The starter exercises' names by library key, from Seed.json.
    private func starterNames() throws -> [String: String] {
        Dictionary(uniqueKeysWithValues: try Seed.bundled().exercises.map { ($0.key, $0.name) })
    }

    @Test func motionsLoadFromTheApp() {
        #expect(!ExerciseMotions.all.isEmpty && ExerciseMotions.byKey.count == ExerciseMotions.all.count)
    }

    @Test func everyStarterExerciseHasOneByItsName() throws {
        let names = try starterNames()
        let list = try motions()
        #expect(Set(list.map(\.key)).count == list.count)
        #expect(Set(list.map(\.key)) == Set(names.keys))
        for motion in list {
            #expect(names[motion.key] == motion.name, "\(motion.key)")
        }
    }

    @Test func keyframesCanBeBlended() throws {
        for motion in try motions() {
            switch motion.keyframes {
            case let .side(from, to):
                #expect(from.arm.kind == to.arm.kind, "\(motion.name): arm")
                #expect(from.farArm?.kind == to.farArm?.kind, "\(motion.name): far arm")
                #expect((from.thigh == nil) == (to.thigh == nil), "\(motion.name): legs")
                #expect((from.shin == nil) == (to.shin == nil), "\(motion.name): shins")
                #expect((from.kneeAt == nil) == (to.kneeAt == nil), "\(motion.name): knee")
            case let .front(from, to):
                #expect(from.arm.kind == to.arm.kind, "\(motion.name): arm")
                #expect(from.otherArm?.kind == to.otherArm?.kind, "\(motion.name): other arm")
            }
            #expect(motion.timing.count == 4, "\(motion.name): timing")
            #expect(motion.timing == motion.timing.sorted() && motion.timing.allSatisfy { (0...1).contains($0) },
                    "\(motion.name): timing")
            #expect(motion.seconds > 0 && (0.5...1.5).contains(motion.zoom), "\(motion.name): loop")
        }
    }

    @Test func limbsKeepTheirLengthsAndStayAboveTheFloor() throws {
        for motion in try motions() {
            for step in 0...20 {
                let k = motion.progress(at: Double(step) / 20)
                switch motion.keyframes {
                case let .side(from, to):
                    let figure = Figure(.lerp(from, to, k))
                    for leg in [figure.nearLeg, figure.farLeg] {
                        #expect(abs((leg.knee - leg.hip).length - FigureBody.thigh) < 0.01, "\(motion.name): thigh")
                        #expect(abs((leg.ankle - leg.knee).length - FigureBody.shin) < 0.01, "\(motion.name): shin")
                        #expect(leg.knee.y <= 0 && leg.ankle.y <= 0, "\(motion.name): leg below the floor")
                    }
                    for arm in [figure.nearArm, figure.farArm] {
                        let forearm = (arm.wrist - arm.elbow).length
                        #expect(abs(forearm - FigureBody.forearm * arm.scale) < 0.01, "\(motion.name): forearm")
                        #expect(arm.wrist.y <= 0 && arm.elbow.y <= 0, "\(motion.name): arm below the floor")
                    }
                    #expect(figure.head.y < 0 && figure.pose.hip.y < 0, "\(motion.name): body below the floor")
                case let .front(from, to):
                    let pose = FrontPose.lerp(from, to, k)
                    let figure = FrontFigure(pose)
                    for leg in figure.legs {
                        let thigh = (leg.knee - leg.hip).length
                        #expect(abs(thigh - FigureBody.thigh * pose.thighLength) < 0.01, "\(motion.name): thigh")
                        #expect(leg.knee.y <= 0 && leg.ankle.y <= 0, "\(motion.name): leg below the floor")
                    }
                    for arm in figure.arms {
                        let forearm = (arm.wrist - arm.elbow).length
                        #expect(abs(forearm - FigureBody.forearm * arm.foreLength) < 0.01, "\(motion.name): forearm")
                        #expect(arm.wrist.y <= 0 && arm.elbow.y <= 0, "\(motion.name): arm below the floor")
                    }
                    #expect(figure.head.y < 0, "\(motion.name): head below the floor")
                }
            }
        }
    }

    /// Draws every animation off screen at its start, the middle of its move and its end, and as
    /// the Reduce Motion picture, and checks the figure lands on the card.
    @MainActor @Test func everyMotionDraws() throws {
        for motion in try motions() {
            let painter = FigurePainter(motion: motion, palette: .kitae)
            let k = motion.timing
            for t in [0, (k[0] + k[1]) / 2, (k[1] + k[2]) / 2] {
                let card = Canvas { context, size in painter.draw(at: t, in: &context, size: size) }
                let image = try #require(ImageRenderer(content: card.frame(width: 360, height: 230)).cgImage)
                #expect(Self.drawnPixels(image) > 2000, "\(motion.name) at \(t)")
            }
            let still = Canvas { context, size in painter.drawStill(in: &context, size: size) }
            let image = try #require(ImageRenderer(content: still.frame(width: 360, height: 230)).cgImage)
            #expect(Self.drawnPixels(image) > 2000, "\(motion.name), still")
        }
    }

    private static func drawnPixels(_ image: CGImage) -> Int {
        let width = image.width, height = image.height
        var data = [UInt8](repeating: 0, count: width * height * 4)
        data.withUnsafeMutableBytes { bytes in
            let context = CGContext(data: bytes.baseAddress, width: width, height: height, bitsPerComponent: 8,
                                    bytesPerRow: width * 4, space: CGColorSpaceCreateDeviceRGB(),
                                    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
            context?.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
        }
        return stride(from: 3, to: data.count, by: 4).reduce(0) { $0 + (data[$1] > 0 ? 1 : 0) }
    }

    @Test func blendingHalfwayMixesBothKeyframes() {
        let a = FigurePose(hip: Vec(0, -100), lean: 0, arm: .swing(upper: 0, bend: 0, upperLength: 1))
        let b = FigurePose(hip: Vec(-30, -60), lean: 40, curl: 10, arm: .swing(upper: 20, bend: 120, upperLength: 0.5))
        let mid = FigurePose.lerp(a, b, 0.5)
        #expect(mid.hip == Vec(-15, -80))
        #expect(mid.lean == 20 && mid.curl == 5)
        #expect(mid.arm == .swing(upper: 10, bend: 60, upperLength: 0.75))
    }

    @Test func aWristBendsTheHandAndWhatItHolds() throws {
        let json = Data(#"{"hand": [40, -70], "elbow": 1, "flex": 40}"#.utf8)
        let bent = try JSONDecoder().decode(ArmPose.self, from: json)
        #expect(bent == .reach(hand: Vec(40, -70), onTorso: false, scale: 1, upperLength: 1, elbow: 1, flex: 40))
        let straight = ArmPose.reach(hand: Vec(40, -70), onTorso: false, scale: 1, upperLength: 1, elbow: 1)
        #expect(ArmPose.lerp(straight, bent, 0.5) == .reach(hand: Vec(40, -70), onTorso: false, scale: 1, upperLength: 1, elbow: 1, flex: 20))
        // The wrist stays put; only the hand turns.
        let pose = FigurePose(hip: Vec(-8, -58), lean: 30, arm: bent)
        #expect(Figure(pose).nearArm.wrist == Figure(FigurePose(hip: Vec(-8, -58), lean: 30, arm: straight)).nearArm.wrist)
        #expect(Figure(pose).nearArm.flex == 40)
    }

    @Test func aKneelingPoseStaysOnItsKnee() {
        let knee = Vec(-6, -6.6)
        for thigh in [160.0, 200, 245] {
            let figure = Figure(FigurePose(hip: .zero, lean: 60, thigh: thigh, shin: 270, kneeAt: knee,
                                           arm: .swing(upper: 0, bend: 0, upperLength: 1)))
            #expect((figure.nearLeg.knee - knee).length < 0.01)
        }
    }

    @Test func theFrontFigureMirrorsItsArms() {
        let figure = FrontFigure(FrontPose(hip: Vec(0, -100), arm: .swing(upper: 80, bend: 10, upperLength: 1, foreLength: 1)))
        let other = figure.arms[0], arm = figure.arms[1]
        #expect(abs(other.wrist.x + arm.wrist.x) < 0.01 && abs(other.wrist.y - arm.wrist.y) < 0.01)
        #expect(arm.wrist.x > 60)
    }

    @Test func aLoopHoldsThenMovesThenHolds() {
        let k = [0.1, 0.5, 0.6, 0.9]
        #expect(FigureMath.phase(0.05, k) == 0)
        #expect(abs(FigureMath.phase(0.3, k) - 0.5) < 1e-9)
        #expect(FigureMath.phase(0.55, k) == 1)
        #expect(FigureMath.phase(0.95, k) == 0)
    }
}
