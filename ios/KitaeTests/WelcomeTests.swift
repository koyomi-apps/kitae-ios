// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import Testing
import UIKit
@testable import Kitae

/// The welcome on first launch: once, unless Kitae is reset, and not for someone who already
/// follows a plan.
@MainActor
struct WelcomeTests {
    /// UserDefaults of their own for each test, removed afterwards.
    private func withDefaults(_ body: (UserDefaults) throws -> Void) throws {
        let name = "KitaeTests.welcome.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        try body(defaults)
    }

    private func makeModel(_ database: KitaeDatabase, _ defaults: UserDefaults) -> AppModel {
        AppModel(
            database: database,
            restStore: .none,
            settingsStore: SettingsStore(defaults: defaults),
            backups: .none,
            liveActivities: false,
            restAlerts: false
        )
    }

    @Test func theFirstLaunchWelcomesThenAsksForAPlan() throws {
        try withDefaults { defaults in
            let model = makeModel(try KitaeDatabase.inMemory(), defaults)
            model.welcomeIfNeeded()
            #expect(model.isWelcoming)
            #expect(model.isChoosingPlan == false)

            model.finishWelcome()
            #expect(model.isWelcoming == false)
            #expect(defaults.bool(forKey: "welcome_done"))
            // As RootView does once the welcome has gone.
            model.askForPlanIfNeeded()
            #expect(model.isChoosingPlan)
        }
    }

    @Test func theWelcomeShowsOnce() throws {
        try withDefaults { defaults in
            let database = try KitaeDatabase.inMemory()
            makeModel(database, defaults).finishWelcome()

            let reopened = makeModel(database, defaults)
            reopened.welcomeIfNeeded()
            #expect(reopened.isWelcoming == false)
            // Still no plan, so the list of plans opens instead.
            #expect(reopened.isChoosingPlan)
        }
    }

    @Test func someoneWhoFollowsAPlanIsntWelcomed() throws {
        try withDefaults { defaults in
            let model = makeModel(try KitaeDatabase.followingStarterPlan(), defaults)
            model.welcomeIfNeeded()
            #expect(model.isWelcoming == false)
            #expect(model.isChoosingPlan == false)
            #expect(SettingsStore(defaults: defaults).isWelcomeDone)
        }
    }

    @Test func resettingWelcomesAgain() throws {
        try withDefaults { defaults in
            let model = makeModel(try KitaeDatabase.followingStarterPlan(), defaults)
            model.welcomeIfNeeded()
            model.settings.textSize = .largest

            try model.reset()
            #expect(SettingsStore(defaults: defaults).isWelcomeDone == false)
            #expect(model.settings.textSize == .iPhone)
            model.welcomeIfNeeded()
            #expect(model.isWelcoming)
        }
    }

    @Test func withoutUserDefaultsTheWelcomeIsRememberedInMemory() {
        let store = SettingsStore.none
        #expect(store.isWelcomeDone == false)
        store.isWelcomeDone = true
        #expect(store.isWelcomeDone)
    }
}

/// Kitae's own text size, from the welcome or Settings.
@MainActor
struct TextSizeTests {
    @Test func theTextSizeIsSavedButNotBackedUp() throws {
        let name = "KitaeTests.textSize.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }

        #expect(SettingsStore(defaults: defaults).load().textSize == .iPhone)
        SettingsStore(defaults: defaults).save(AppSettings(textSize: .extraLarge))
        #expect(defaults.string(forKey: "text_size") == "extraLarge")
        #expect(SettingsStore(defaults: defaults).load().textSize == .extraLarge)

        defaults.set("enormous", forKey: "text_size")
        #expect(SettingsStore(defaults: defaults).load().textSize == .iPhone)

        // It goes with the phone, like the iPhone's own text size.
        let settings = AppSettings(textSize: .largest)
        #expect(settings.backupPreferences(planChosen: true)["text_size"] == nil)
        var restored = settings
        restored.apply(backup: AppSettings().backupPreferences(planChosen: true))
        #expect(restored.textSize == .largest)
    }

    @Test func theSizesAreIOSs() throws {
        let categories: [UIContentSizeCategory] = [
            .extraSmall, .small, .medium, .large, .extraLarge, .extraExtraLarge, .extraExtraExtraLarge,
        ]
        #expect(TextSize.sizes.compactMap { $0.contentSizeCategory } == categories)
        #expect(TextSize.sizes.count == categories.count)
        #expect(TextSize.iPhone.contentSizeCategory == nil)
        for size in TextSize.sizes {
            let category = try #require(size.contentSizeCategory)
            #expect(TextSize(nearest: category) == size)
        }
        #expect(TextSize(nearest: .accessibilityExtraExtraExtraLarge) == .largest)
        #expect(TextSize(nearest: .unspecified) == .standard)
    }
}
