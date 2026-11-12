// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation

/// Light or dark whatever the phone uses, or the same as the phone.
nonisolated enum AppTheme: String, CaseIterable, Identifiable, Sendable {
    case system
    case light
    case dark

    var id: Self { self }

    var name: String {
        switch self {
        case .system: "System"
        case .light: "Light"
        case .dark: "Dark"
        }
    }

    /// Kitae for Android's name for it, as its settings keep it.
    var androidName: String {
        switch self {
        case .system: "System"
        case .light: "Light"
        case .dark: "Dark"
        }
    }

    init?(androidName: String) {
        guard let theme = Self.allCases.first(where: { $0.androidName == androidName }) else { return nil }
        self = theme
    }
}

/// How big Kitae's text is: the same as the rest of the iPhone's, or one of the seven sizes
/// iOS has before its larger accessibility sizes.
nonisolated enum TextSize: String, CaseIterable, Identifiable, Sendable {
    case iPhone
    case extraSmall
    case small
    case medium
    /// iOS's own size, before it's changed.
    case standard
    case large
    case extraLarge
    case largest

    var id: Self { self }

    /// The sizes to pick from, smallest first.
    static let sizes: [TextSize] = allCases.filter { $0 != .iPhone }

    var name: String {
        switch self {
        case .iPhone: "Same as iPhone"
        case .extraSmall: "Extra small"
        case .small: "Small"
        case .medium: "Medium"
        case .standard: "Default"
        case .large: "Large"
        case .extraLarge: "Extra large"
        case .largest: "Largest"
        }
    }
}

nonisolated extension WeightUnit {
    /// Kitae for Android's name for it, as its settings keep it.
    var androidName: String {
        switch self {
        case .kilograms: "Kilograms"
        case .pounds: "Pounds"
        }
    }

    init?(androidName: String) {
        guard let unit = Self.allCases.first(where: { $0.androidName == androidName }) else { return nil }
        self = unit
    }
}

/// What Settings changes.
nonisolated struct AppSettings: Equatable, Sendable {
    var unit: WeightUnit = .kilograms
    var decimals: DecimalStyle = .automatic
    /// The rest after a set when neither the set nor its exercise has one of its own, like
    /// Kitae for Android's rest timer setting. 0 starts no rest.
    var defaultRestSeconds = 90
    /// Whether "Rest over" goes off when a rest runs out.
    var restAlerts = true
    var theme: AppTheme = .system
    /// Only on this phone, like the iPhone's own text size.
    var textSize: TextSize = .iPhone
    var backUpAfterWorkouts = true
    /// Whether a summary opens after finishing a workout: its time, sets, energy and muscles.
    var showsWorkoutSummary = true
    /// Whether that summary lists the records the workout's sets beat.
    var showsNewBests = true
    /// Kitae for Android's colour palette, kept from a restored backup so that backups made
    /// here bring it back.
    var androidPalette: String?

    /// The palettes Kitae for Android has. It can't open settings with another one.
    static let androidPalettes: Set = ["Dynamic", "Default", "Zestful", "Serene", "Twilight"]
}

/// Where each setting is kept, in UserDefaults and in a backup's settings file. The settings
/// Kitae for Android also has use its names, and its values (`weight_unit` is `Kilograms` or
/// `Pounds`, and so on).
nonisolated enum SettingKey {
    static let unit = "weight_unit"
    static let decimals = "decimals"
    static let defaultRest = "rest_timer_seconds"
    static let restAlerts = "rest_alerts"
    static let theme = "theme"
    static let textSize = "text_size"
    static let backUpAfterWorkouts = "back_up_after_workouts"
    static let workoutSummary = "workout_summary"
    static let newBests = "new_bests"
    static let palette = "color_palette"
    /// Kitae for Android's first launch is over once a plan is chosen.
    static let onboardingDone = "onboarding_done"
    /// Whether the welcome is over. Not a setting, and not backed up.
    static let welcomeDone = "welcome_done"
}

/// Keeps Settings on the phone, in UserDefaults.
final class SettingsStore {
    private let defaults: UserDefaults?

    init(defaults: UserDefaults?) {
        self.defaults = defaults
    }

    static var standard: SettingsStore { SettingsStore(defaults: .standard) }

    /// Keeps nothing, for tests and example data.
    static var none: SettingsStore { SettingsStore(defaults: nil) }

    /// Whether the welcome has been and gone, so it shows on first launch and after a reset
    /// only. Without UserDefaults it's kept in memory.
    var isWelcomeDone: Bool {
        get { defaults?.bool(forKey: SettingKey.welcomeDone) ?? welcomeDoneInMemory }
        set {
            if let defaults {
                defaults.set(newValue, forKey: SettingKey.welcomeDone)
            } else {
                welcomeDoneInMemory = newValue
            }
        }
    }

    private var welcomeDoneInMemory = false

    /// The saved settings. Anything missing or unreadable is left as it comes.
    func load() -> AppSettings {
        var settings = AppSettings()
        guard let defaults else { return settings }
        if let name = defaults.string(forKey: SettingKey.unit), let unit = WeightUnit(androidName: name) {
            settings.unit = unit
        }
        if let name = defaults.string(forKey: SettingKey.decimals), let decimals = DecimalStyle(rawValue: name) {
            settings.decimals = decimals
        }
        if let seconds = defaults.object(forKey: SettingKey.defaultRest) as? Int, seconds >= 0 {
            settings.defaultRestSeconds = seconds
        }
        if let isOn = defaults.object(forKey: SettingKey.restAlerts) as? Bool {
            settings.restAlerts = isOn
        }
        if let name = defaults.string(forKey: SettingKey.theme), let theme = AppTheme(androidName: name) {
            settings.theme = theme
        }
        if let name = defaults.string(forKey: SettingKey.textSize), let size = TextSize(rawValue: name) {
            settings.textSize = size
        }
        if let isOn = defaults.object(forKey: SettingKey.backUpAfterWorkouts) as? Bool {
            settings.backUpAfterWorkouts = isOn
        }
        if let isOn = defaults.object(forKey: SettingKey.workoutSummary) as? Bool {
            settings.showsWorkoutSummary = isOn
        }
        if let isOn = defaults.object(forKey: SettingKey.newBests) as? Bool {
            settings.showsNewBests = isOn
        }
        if let name = defaults.string(forKey: SettingKey.palette), AppSettings.androidPalettes.contains(name) {
            settings.androidPalette = name
        }
        return settings
    }

    func save(_ settings: AppSettings) {
        guard let defaults else { return }
        defaults.set(settings.unit.androidName, forKey: SettingKey.unit)
        defaults.set(settings.decimals.rawValue, forKey: SettingKey.decimals)
        defaults.set(settings.defaultRestSeconds, forKey: SettingKey.defaultRest)
        defaults.set(settings.restAlerts, forKey: SettingKey.restAlerts)
        defaults.set(settings.theme.androidName, forKey: SettingKey.theme)
        defaults.set(settings.textSize.rawValue, forKey: SettingKey.textSize)
        defaults.set(settings.backUpAfterWorkouts, forKey: SettingKey.backUpAfterWorkouts)
        defaults.set(settings.showsWorkoutSummary, forKey: SettingKey.workoutSummary)
        defaults.set(settings.showsNewBests, forKey: SettingKey.newBests)
        if let palette = settings.androidPalette {
            defaults.set(palette, forKey: SettingKey.palette)
        } else {
            defaults.removeObject(forKey: SettingKey.palette)
        }
    }
}

nonisolated extension AppSettings {
    /// What a backup's settings file holds: the settings Kitae for Android has, under its names
    /// and with the kinds of value it reads, and the ones only Kitae for iPhone has, which
    /// Android keeps without using. The text size, and where Kitae and its backups go, are left
    /// out, as they're this phone's. `planChosen` tells Kitae for Android its first launch is over.
    func backupPreferences(planChosen: Bool) -> [String: PreferenceValue] {
        var preferences: [String: PreferenceValue] = [
            SettingKey.unit: .string(unit.androidName),
            SettingKey.defaultRest: .int(Int32(clamping: defaultRestSeconds)),
            SettingKey.theme: .string(theme.androidName),
            SettingKey.decimals: .string(decimals.rawValue),
            SettingKey.restAlerts: .bool(restAlerts),
            SettingKey.workoutSummary: .bool(showsWorkoutSummary),
            SettingKey.newBests: .bool(showsNewBests),
        ]
        if planChosen {
            preferences[SettingKey.onboardingDone] = .bool(true)
        }
        if let androidPalette {
            preferences[SettingKey.palette] = .string(androidPalette)
        }
        return preferences
    }

    /// Takes the settings from a backup's settings file, from either app. Anything it doesn't
    /// have, or that can't be read, stays as it is.
    mutating func apply(backup preferences: [String: PreferenceValue]) {
        if case .string(let name)? = preferences[SettingKey.unit], let unit = WeightUnit(androidName: name) {
            self.unit = unit
        }
        if case .int(let seconds)? = preferences[SettingKey.defaultRest], seconds >= 0 {
            defaultRestSeconds = Int(seconds)
        }
        if case .string(let name)? = preferences[SettingKey.theme], let theme = AppTheme(androidName: name) {
            self.theme = theme
        }
        if case .string(let name)? = preferences[SettingKey.decimals], let decimals = DecimalStyle(rawValue: name) {
            self.decimals = decimals
        }
        if case .bool(let isOn)? = preferences[SettingKey.restAlerts] {
            restAlerts = isOn
        }
        if case .bool(let isOn)? = preferences[SettingKey.workoutSummary] {
            showsWorkoutSummary = isOn
        }
        if case .bool(let isOn)? = preferences[SettingKey.newBests] {
            showsNewBests = isOn
        }
        if case .string(let name)? = preferences[SettingKey.palette], Self.androidPalettes.contains(name) {
            androidPalette = name
        }
    }
}

/// A rest as the rest pickers name it: "1:30", or "off" when there's none.
nonisolated func restSetting(seconds: Int) -> String {
    seconds > 0 ? restLabel(seconds: seconds) : "off"
}
