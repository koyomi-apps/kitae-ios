// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI
import WidgetKit

@main
struct KitaeWidgetsBundle: WidgetBundle {
    init() {
        KitaeFonts.register()
    }

    var body: some Widget {
        RestTimerLiveActivity()
    }
}
