// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation

nonisolated enum WeightUnit: String, CaseIterable, Identifiable, Sendable {
    case kilograms
    case pounds

    static let kilogramsPerPound = 0.45359237

    var id: Self { self }

    var symbol: String {
        switch self {
        case .kilograms: "kg"
        case .pounds: "lb"
        }
    }

    /// What VoiceOver says for it.
    var spokenName: String {
        switch self {
        case .kilograms: "Kilograms"
        case .pounds: "Pounds"
        }
    }

    func fromKilograms(_ kg: Double) -> Double {
        switch self {
        case .kilograms: kg
        case .pounds: kg / Self.kilogramsPerPound
        }
    }

    func toKilograms(_ value: Double) -> Double {
        switch self {
        case .kilograms: value
        case .pounds: value * Self.kilogramsPerPound
        }
    }

    /// The −/+ buttons above the keyboard. The same numbers in both units, like Kitae for Android.
    var steps: [Double] { [-5, -2.5, 2.5, 5] }

    /// What a new set weighs when there's nothing to copy: 20 kg, or 45 lb.
    var defaultWeightKg: Double {
        switch self {
        case .kilograms: 20
        case .pounds: toKilograms(45)
        }
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
        switch self {
        case .automatic: locale.decimalSeparator ?? "."
        case .point: "."
        case .comma: ","
        }
    }
}

nonisolated struct WeightFormatter: Sendable {
    var unit: WeightUnit = .kilograms
    var decimals: DecimalStyle = .automatic
    var locale: Locale = .current

    /// Like Kitae for Android: at least one decimal and at most two, so 20 shows as 20.0
    /// and 18.75 stays 18.75.
    func string(kilograms kg: Double) -> String {
        number(unit.fromKilograms(kg))
    }

    /// A number in the same style, for the keyboard's −/+ steps.
    func number(_ value: Double) -> String {
        let hundredths = Int((max(value, 0) * 100).rounded())
        let whole = hundredths / 100
        let fraction = hundredths % 100
        let separator = decimals.separator(for: locale)
        if fraction % 10 == 0 {
            return "\(whole)\(separator)\(fraction / 10)"
        }
        return "\(whole)\(separator)\(fraction < 10 ? "0" : "")\(fraction)"
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
        "\(bodyNumber(kilograms: kg)) \(unit.symbol)"
    }

    /// A body weight's number to a tenth, like 78.4, for typing it again.
    func bodyNumber(kilograms kg: Double) -> String {
        tenths(unit.fromKilograms(kg))
    }

    /// A percentage to a tenth, like "18.5%", or "18%" when it's whole.
    func percent(_ value: Double) -> String {
        let tenths = Int((max(value, 0) * 10).rounded())
        if tenths % 10 == 0 { return "\(tenths / 10)%" }
        return "\(tenths / 10)\(decimals.separator(for: locale))\(tenths % 10)%"
    }

    /// A number to a tenth, like 18.5, or 18 when it's whole, for typing it again.
    func tenthsOrWhole(_ value: Double) -> String {
        let tenths = Int((max(value, 0) * 10).rounded())
        if tenths % 10 == 0 { return "\(tenths / 10)" }
        return "\(tenths / 10)\(decimals.separator(for: locale))\(tenths % 10)"
    }

    private func tenths(_ value: Double) -> String {
        let tenths = Int((max(value, 0) * 10).rounded())
        return "\(tenths / 10)\(decimals.separator(for: locale))\(tenths % 10)"
    }

    /// Reads a typed number, with a point or a comma.
    static func number(from text: String) -> Double? {
        let cleaned = text.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: ",", with: ".")
        guard !cleaned.isEmpty, let value = Double(cleaned), value.isFinite, value >= 0 else { return nil }
        return value
    }

    /// A −/+ step above the keyboard, like −2.5 or +5, with the decimal separator from Settings.
    func step(_ value: Double) -> String {
        let sign = value < 0 ? "−" : "+"
        let tenths = Int((abs(value) * 10).rounded())
        if tenths % 10 == 0 {
            return "\(sign)\(tenths / 10)"
        }
        return "\(sign)\(tenths / 10)\(decimals.separator(for: locale))\(tenths % 10)"
    }

    /// Reads a typed weight. A point and a comma both work as the decimal separator,
    /// whatever the setting, because the iPhone keypad shows the region's key.
    func kilograms(from text: String) -> Double? {
        let cleaned = text
            .trimmingCharacters(in: .whitespaces)
            .replacingOccurrences(of: ",", with: ".")
        guard !cleaned.isEmpty, let value = Double(cleaned), value.isFinite, value >= 0 else {
            return nil
        }
        return unit.toKilograms(value)
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
