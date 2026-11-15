// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import KitaeCore

/// Kilograms or pounds. Weights are always stored in kilograms, and the shared module converts
/// them (`Weight.kt`).
nonisolated enum WeightUnit: String, CaseIterable, Identifiable, Sendable {
    case kilograms
    case pounds

    static let kilogramsPerPound = KitaeCore.WeightUnit.Companion.shared.KILOGRAMS_PER_POUND

    var id: Self { self }

    var symbol: String { core.symbol }

    /// What VoiceOver says for it.
    var spokenName: String {
        switch self {
        case .kilograms: "Kilograms"
        case .pounds: "Pounds"
        }
    }

    func fromKilograms(_ kg: Double) -> Double { core.fromKilograms(kg: kg) }

    func toKilograms(_ value: Double) -> Double { core.toKilograms(value: value) }

    /// The −/+ buttons above the keyboard. The same numbers in both units, like Kitae for Android.
    var steps: [Double] { doubles(core.steps) }

    /// What a new set weighs when there's nothing to copy: 20 kg, or 45 lb.
    var defaultWeightKg: Double { core.defaultWeightKg }
}

nonisolated extension WeightUnit {
    /// The shared module's own.
    var core: KitaeCore.WeightUnit {
        switch self {
        case .kilograms: KitaeCore.WeightUnit.kilograms
        case .pounds: KitaeCore.WeightUnit.pounds
        }
    }

    init(_ unit: KitaeCore.WeightUnit) {
        self = unit == KitaeCore.WeightUnit.pounds ? .pounds : .kilograms
    }

    /// Kitae for Android's name for it, as its settings keep it.
    var androidName: String { core.stored }

    init?(androidName: String) {
        guard let unit = KitaeCore.WeightUnit.Companion.shared.fromStored(stored: androidName) else { return nil }
        self.init(unit)
    }
}

/// How weights are written: 22.5 or 22,5.
nonisolated enum DecimalStyle: String, CaseIterable, Identifiable, Sendable {
    /// Follows the phone's region.
    case automatic
    case point
    case comma

    var id: Self { self }

    /// As Settings shows it: Auto, 22.5 or 22,5.
    var title: String {
        switch self {
        case .automatic: "Auto"
        case .point: "22.5"
        case .comma: "22,5"
        }
    }

    /// What VoiceOver says for it.
    var spokenName: String {
        switch self {
        case .automatic: "Automatic"
        case .point: "Point"
        case .comma: "Comma"
        }
    }

    func separator(for locale: Locale) -> String {
        String(utf16CodeUnits: [core.separator(phoneSeparator: Self.phoneSeparator(locale))], count: 1)
    }

    /// The decimal separator of the phone's region, which `automatic` follows.
    static func phoneSeparator(_ locale: Locale) -> unichar {
        (locale.decimalSeparator ?? ".").utf16.first ?? 46
    }

    /// The shared module's own, by the name both apps store.
    var core: KitaeCore.DecimalStyle {
        KitaeCore.DecimalStyle.Companion.shared.fromStored(stored: rawValue)!
    }

    init(_ style: KitaeCore.DecimalStyle) {
        self = DecimalStyle(rawValue: style.stored) ?? .automatic
    }
}

/// Writes and reads weights. The shared module does the numbers (`Weight.kt`); the words around
/// them are the app's.
nonisolated struct WeightFormatter: Sendable {
    var unit: WeightUnit = .kilograms
    var decimals: DecimalStyle = .automatic
    var locale: Locale = .current

    /// The shared module's own.
    private var core: KitaeCore.WeightFormatter {
        KitaeCore.WeightFormatter(unit: unit.core, decimals: decimals.core,
                                  phoneSeparator: DecimalStyle.phoneSeparator(locale))
    }

    /// Like Kitae for Android: at least one decimal and at most two, so 20 shows as 20.0
    /// and 18.75 stays 18.75.
    func string(kilograms kg: Double) -> String {
        core.string(kilograms: kg)
    }

    /// A number in the same style, for the keyboard's −/+ steps.
    func number(_ value: Double) -> String {
        core.number(value: value)
    }

    /// A set with its weight first, like "22.5 kg × 10", as an exercise's heaviest set is
    /// shown. Without weight it's "12 reps", or "45s" for a hold.
    func weightFirst(reps: Int, kilograms: Double, isIsometric: Bool = false) -> String {
        let amount = isIsometric ? "\(reps)s" : "\(reps)"
        if kilograms > 0 {
            return "\(string(kilograms: kilograms)) \(unit.symbol) × \(amount)"
        }
        return isIsometric ? amount : counted(reps, "rep", "reps")
    }

    /// A body weight to a tenth, like "78.4 kg".
    func body(kilograms kg: Double) -> String {
        core.body(kilograms: kg)
    }

    /// A body weight's number to a tenth, like 78.4, for typing it again.
    func bodyNumber(kilograms kg: Double) -> String {
        core.bodyNumber(kilograms: kg)
    }

    /// A percentage to a tenth, like "18.5%", or "18%" when it's whole.
    func percent(_ value: Double) -> String {
        core.percent(value: value)
    }

    /// A number to a tenth, like 18.5, or 18 when it's whole, for typing it again.
    func tenthsOrWhole(_ value: Double) -> String {
        core.tenthsOrWhole(value: value)
    }

    /// Reads a typed number, with a point or a comma.
    static func number(from text: String) -> Double? {
        KitaeCore.WeightFormatter.Companion.shared.number(text: text)?.doubleValue
    }

    /// A −/+ step above the keyboard, like −2.5 or +5, with the decimal separator from Settings.
    func step(_ value: Double) -> String {
        core.step(value: value)
    }

    /// Reads a typed weight. A point and a comma both work as the decimal separator,
    /// whatever the setting, because the iPhone keypad shows the region's key.
    func kilograms(from text: String) -> Double? {
        core.kilograms(text: text)?.doubleValue
    }

    /// A set's reps and weight like "10 × 17.5 kg", or "12 reps" without weight.
    /// Isometric exercises count seconds instead of reps.
    func summary(reps: Int, kilograms: Double, isIsometric: Bool = false) -> String {
        let amount = isIsometric ? "\(reps)s" : "\(reps)"
        if kilograms > 0 {
            return "\(amount) × \(string(kilograms: kilograms)) \(unit.symbol)"
        }
        return isIsometric ? amount : counted(reps, "rep", "reps")
    }
}
