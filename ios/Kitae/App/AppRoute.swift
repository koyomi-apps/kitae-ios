// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI

/// Pages that can be pushed onto a tab's stack, or the workout's: a past workout or an exercise.
enum AppRoute: Hashable {
    case workout(Int64)
    case exercise(Int64)
}

extension View {
    /// Shows the page for each `AppRoute` pushed onto this stack.
    func appRoutes() -> some View {
        navigationDestination(for: AppRoute.self) { route in
            switch route {
            case .workout(let id):
                WorkoutDetailView(id: id)
            case .exercise(let id):
                ExerciseDetailView(id: id)
            }
        }
    }
}
