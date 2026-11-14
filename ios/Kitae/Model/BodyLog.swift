// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import KitaeCore

/// A day's weigh-in: weight, body fat, or both.
nonisolated struct BodyEntry: Identifiable, Hashable, Sendable {
    var id: Int64
    /// The day, in days since 1970 where the phone was.
    var date: Int
    var weightKg: Double?
    /// Percent, like 18.5.
    var bodyFatPercent: Double?

    /// The weight that isn't fat, when both are known.
    var leanKg: Double? {
        core.leanKg?.doubleValue
    }

    var fatKg: Double? {
        core.fatKg?.doubleValue
    }
}

nonisolated enum Sex: String, CaseIterable, Identifiable, Sendable {
    case female = "Female"
    case male = "Male"

    var id: Self { self }
    var title: String { rawValue }
}

/// How active someone's day is outside their workouts, which Kitae counts itself. Kitae keeps it
/// with the profile, for apps that read its backups, like a calorie tracker.
nonisolated enum ActivityLevel: String, CaseIterable, Identifiable, Sendable {
    case sedentary = "Sedentary"
    case lightlyActive = "LightlyActive"
    case moderatelyActive = "ModeratelyActive"
    case veryActive = "VeryActive"
    case extraActive = "ExtraActive"

    var id: Self { self }

    var title: String {
        switch self {
        case .sedentary: "Sedentary"
        case .lightlyActive: "Lightly active"
        case .moderatelyActive: "Moderately active"
        case .veryActive: "Very active"
        case .extraActive: "Extra active"
        }
    }

    var detail: String {
        switch self {
        case .sedentary: "Mostly sitting, like at a desk"
        case .lightlyActive: "Some walking or standing"
        case .moderatelyActive: "On your feet most of the day"
        case .veryActive: "Physical work most of the day"
        case .extraActive: "Hard physical work all day"
        }
    }
}

/// What people tell Kitae about themselves. Everything is optional.
nonisolated struct BodyProfile: Hashable, Sendable {
    var heightCm: Double?
    /// In days since 1970.
    var birthDate: Int?
    var sex: Sex?
    var activityLevel: ActivityLevel?

    /// Whole years old on `day`.
    func age(on day: Int) -> Int? {
        core.age(day: Int64(day))?.intValue
    }
}

/// The profile and every weigh-in.
nonisolated struct BodyLog: Hashable, Sendable {
    var profile = BodyProfile()
    /// Oldest first, one a day.
    var entries: [BodyEntry] = []

    var isEmpty: Bool { core.isEmpty }

    /// The newest weigh-in with a weight.
    var latestWeight: BodyEntry? { core.latestWeight.map { BodyEntry($0) } }

    /// The newest weigh-in with body fat.
    var latestBodyFat: BodyEntry? { core.latestBodyFat.map { BodyEntry($0) } }

    /// The weight that stands for `day`: the last one weighed on or before it, else the first
    /// one after, so workouts from before the first weigh-in still have one.
    func weightKg(on day: Int) -> Double? {
        core.weightKg(day: Int64(day))?.doubleValue
    }

    /// The body fat that stands for `day`, found the same way.
    func bodyFatPercent(on day: Int) -> Double? {
        core.bodyFatPercent(day: Int64(day))?.doubleValue
    }
}

/// Heights in centimetres, or in feet and inches for people who weigh in pounds.
nonisolated struct HeightFormatter: Sendable {
    var unit: WeightUnit = .kilograms

    var usesFeet: Bool { core.usesFeet }

    /// The shared module's, which formats it.
    private var core: KitaeCore.HeightFormatter { KitaeCore.HeightFormatter(unit: unit.core) }

    /// "180 cm", or "5 ft 11 in".
    func string(centimetres cm: Double) -> String {
        core.string(centimetres: cm)
    }

    /// The nearest whole inch, as feet and inches.
    static func feetAndInches(_ cm: Double) -> (feet: Int, inches: Int) {
        let both = KitaeCore.HeightFormatter.Companion.shared.feetAndInches(centimetres: cm)
        return (Int(both.first?.intValue ?? 0), Int(both.second?.intValue ?? 0))
    }

    static func centimetres(feet: Int, inches: Double) -> Double {
        KitaeCore.HeightFormatter.Companion.shared.centimetres(feet: Int32(clamping: feet), inches: inches)
    }
}

/// "≈ 230 kcal", to the nearest 5.
nonisolated func kilocaloriesLabel(_ kcal: Double) -> String {
    BodyLogKt.kilocaloriesLabel(kcal: kcal)
}

/// To the nearest 5, as the estimates are shown.
nonisolated func roundedKilocalories(_ kcal: Double) -> Int {
    Int(BodyLogKt.roundedKilocalories(kcal: kcal))
}
