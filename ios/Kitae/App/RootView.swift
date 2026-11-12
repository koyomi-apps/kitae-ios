// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI
import UIKit

enum AppTab: Hashable {
    case today
    case history
    case exercises
    case you
}

/// The four tabs, the workout bar above them, and the workout itself.
struct RootView: View {
    @Bindable var model: AppModel
    @State private var tab = Self.firstTab
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        TabView(selection: $tab) {
            Tab("Today", systemImage: "dumbbell", value: .today) {
                TodayView()
            }
            Tab("History", systemImage: "clock.arrow.circlepath", value: .history) {
                HistoryView()
            }
            Tab("Exercises", systemImage: "list.bullet", value: .exercises) {
                ExercisesView()
            }
            Tab("You", systemImage: "person", value: .you) {
                YouView()
            }
        }
        .tabBarMinimizeBehavior(.onScrollDown)
        .workoutAccessory(model.workout != nil) {
            WorkoutAccessory()
        }
        .fullScreenCover(isPresented: $model.isWorkoutPresented, onDismiss: { model.workoutClosed() }) {
            if let workout = model.workout {
                WorkoutView(workout: workout)
                    .environment(model)
            }
        }
        .sheet(item: $model.finishedWorkout) { finished in
            WorkoutDoneView(workoutID: finished.id)
                .environment(model)
        }
        .sheet(isPresented: $model.isChoosingPlan) {
            PlansView()
                .environment(model)
        }
        .fullScreenCover(isPresented: $model.isWelcoming, onDismiss: { model.askForPlanIfNeeded() }) {
            WelcomeView()
                .environment(model)
        }
        .environment(model)
        .tint(.olive)
        .alert(
            "Something went wrong",
            isPresented: Binding(get: { model.problem != nil }, set: { if !$0 { model.problem = nil } })
        ) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(model.problem ?? "")
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { model.sceneBecameActive() }
        }
        .onChange(of: model.settings.theme, initial: true) { _, theme in
            Self.apply(theme)
        }
        .onChange(of: model.settings.textSize, initial: true) { _, textSize in
            Self.apply(textSize)
        }
        .task {
            // A sheet or cover asked for before the first frame doesn't open, so wait a moment.
            try? await Task.sleep(for: .milliseconds(400))
            #if DEBUG
            if DebugLaunch.opensWorkout {
                model.isWorkoutPresented = true
            }
            DebugLaunch.openWorkoutDone(model)
            #endif
            model.welcomeIfNeeded()
        }
    }

    /// Today, or the tab of a debug screen.
    private static var firstTab: AppTab {
        #if DEBUG
        if DebugLaunch.opensHistory { return .history }
        if DebugLaunch.opensExercises { return .exercises }
        if DebugLaunch.opensYou { return .you }
        #endif
        return .today
    }

    /// Light or dark from Settings, or the phone's. Set on the windows rather than with
    /// `preferredColorScheme`, so sheets, alerts and the workout follow straight away, also
    /// when going back to the phone's.
    private static func apply(_ theme: AppTheme) {
        let style: UIUserInterfaceStyle = switch theme {
        case .system: .unspecified
        case .light: .light
        case .dark: .dark
        }
        for window in windows {
            window.overrideUserInterfaceStyle = style
        }
    }

    /// Text as big as Settings say, or as the phone's. Set on the windows too, so sheets,
    /// alerts and menus follow it as well.
    private static func apply(_ textSize: TextSize) {
        for window in windows {
            if let category = textSize.contentSizeCategory {
                window.traitOverrides.preferredContentSizeCategory = category
            } else if window.traitOverrides.contains(UITraitPreferredContentSizeCategory.self) {
                window.traitOverrides.remove(UITraitPreferredContentSizeCategory.self)
            }
        }
    }

    private static var windows: [UIWindow] {
        UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows)
    }
}
