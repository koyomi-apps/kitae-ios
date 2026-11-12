// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import SwiftUI

/// An exercise's animation, shown on its page under "How to do it": a figure doing the movement
/// with the worked muscles in olive, drawn live. With Reduce Motion on, it shows the end pose with
/// the start pose faintly behind it.
struct ExerciseMotionView: View {
    let motion: ExerciseMotion
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let painter = FigurePainter(motion: motion, palette: .kitae)
        Group {
            if reduceMotion {
                Canvas { context, size in
                    painter.drawStill(in: &context, size: size)
                }
            } else {
                TimelineView(.animation) { timeline in
                    Canvas { context, size in
                        let seconds = timeline.date.timeIntervalSinceReferenceDate
                        let t = seconds.truncatingRemainder(dividingBy: motion.seconds) / motion.seconds
                        painter.draw(at: t, in: &context, size: size)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: 230)
        .background(.card, in: .rect(cornerRadius: 24))
        .accessibilityHidden(true)
    }
}

extension FigurePalette {
    static var kitae: FigurePalette {
        FigurePalette(body: .figureBody, far: .figureFar, muscle: .olive, muscleFar: .figureMuscleFar,
                      muscleSecondary: .figureMuscleSecondary, muscleSecondaryFar: .figureMuscleSecondaryFar,
                      gear: .figureGear, gearFace: .figureGearFace, prop: .figureProp, shadow: .figureShadow,
                      card: .card)
    }
}
