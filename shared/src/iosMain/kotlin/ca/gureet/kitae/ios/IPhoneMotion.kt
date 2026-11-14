// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.ios

import ca.gureet.kitae.motion.ArmPose
import ca.gureet.kitae.motion.FrontArmPose

/**
 * The arm poses' blends, for the iPhone's figures: Swift sees the arm poses as protocols, whose
 * companions it can't reach as it does a class's.
 */
object IPhoneMotion {
    fun blendArms(a: ArmPose, b: ArmPose, t: Double): ArmPose = ArmPose.lerp(a, b, t)

    fun blendFrontArms(a: FrontArmPose, b: FrontArmPose, t: Double): FrontArmPose = FrontArmPose.lerp(a, b, t)
}
