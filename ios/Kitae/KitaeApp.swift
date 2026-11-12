// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI
import UIKit

@main
struct KitaeApp: App {
    @State private var launch = Launch.open()

    init() {
        KitaeFonts.register()
        Self.styleLargeTitles()
    }

    /// Large titles, like History's, in Darker Grotesque as in the design.
    private static func styleLargeTitles() {
        guard let font = UIFont(name: KitaeFonts.displayBold, size: 48) else { return }
        var attributes: [NSAttributedString.Key: Any] = [
            .font: UIFontMetrics(forTextStyle: .largeTitle).scaledFont(for: font),
            .kern: -0.5,
        ]
        if let ink = UIColor(named: "Ink") {
            attributes[.foregroundColor] = ink
        }
        UINavigationBar.appearance().largeTitleTextAttributes = attributes
    }

    var body: some Scene {
        WindowGroup {
            switch launch {
            case .ready(let model):
                StartingOver(model: model)
                    .overlay {
                        #if DEBUG
                        if let page = DebugLaunch.galleryPage {
                            RestActivityGallery(page: page)
                        } else if DebugLaunch.opensMotionGallery {
                            MotionGallery()
                        }
                        #endif
                    }
            case .failed(let message):
                ContentUnavailableView {
                    Label("Kitae can't open its data", systemImage: "exclamationmark.triangle")
                } description: {
                    Text(message)
                }
            }
        }
    }
}

/// Kitae's screens, made again from the start after a reset, as on a new install.
private struct StartingOver: View {
    let model: AppModel

    var body: some View {
        RootView(model: model)
            .id(model.generation)
    }
}

/// Opens the database when Kitae starts.
enum Launch {
    case ready(AppModel)
    case failed(String)

    static func open() -> Launch {
        do {
            #if DEBUG
            // Example data for screenshots, kept away from the real database.
            if DebugLaunch.usesExampleData {
                let model = AppModel(
                    database: try .inMemory(),
                    restStore: .none,
                    settingsStore: .none,
                    backups: DebugLaunch.backups,
                    restAlerts: false
                )
                DebugLaunch.prepare(model)
                return .ready(model)
            }
            #endif
            return .ready(AppModel(database: try .app(), restStore: .standard, settingsStore: .standard, backups: .standard))
        } catch {
            return .failed(error.localizedDescription)
        }
    }
}
