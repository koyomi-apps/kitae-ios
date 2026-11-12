// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import CoreText
import SwiftUI

/// Kitae's two typefaces: Darker Grotesque for day names and headings, and Space Mono
/// for numbers, set labels and the small ".label" captions. Running text uses the system font.
///
/// Both fonts ship in Fonts/ under the SIL Open Font License.
enum KitaeFonts {
    static let displayBold = "DarkerGrotesque-Bold"
    static let displaySemiBold = "DarkerGrotesque-SemiBold"
    static let monoRegular = "SpaceMono-Regular"
    static let monoBold = "SpaceMono-Bold"

    /// Makes the bundled fonts available to this process. Call once at launch.
    static func register() {
        for name in [displayBold, displaySemiBold, monoRegular, monoBold] {
            guard let url = Bundle.main.url(forResource: name, withExtension: "ttf") else {
                assertionFailure("Missing font \(name).ttf")
                continue
            }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }
}

extension Font {
    /// Darker Grotesque, scaled with Dynamic Type relative to `style`.
    static func kitaeDisplay(_ size: CGFloat, relativeTo style: Font.TextStyle = .largeTitle, semibold: Bool = false) -> Font {
        .custom(semibold ? KitaeFonts.displaySemiBold : KitaeFonts.displayBold, size: size, relativeTo: style)
    }

    /// Space Mono, scaled with Dynamic Type relative to `style`.
    static func kitaeMono(_ size: CGFloat, relativeTo style: Font.TextStyle = .body, bold: Bool = false) -> Font {
        .custom(bold ? KitaeFonts.monoBold : KitaeFonts.monoRegular, size: size, relativeTo: style)
    }
}
