// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import KitaeCore

/// One setting in Kitae for Android's settings file.
nonisolated enum PreferenceValue: Equatable, Sendable {
    case bool(Bool)
    case float(Float)
    case int(Int32)
    case long(Int64)
    case string(String)
    case stringSet([String])
    case double(Double)
    case bytes(Data)
}

nonisolated enum PreferencesFileError: Error, LocalizedError {
    case malformed

    var errorDescription: String? {
        "The backup's settings can't be read."
    }
}

/// Kitae for Android's settings file, `settings.preferences_pb`, which its backups carry next
/// to the database: a Jetpack DataStore `PreferenceMap` in protocol buffers, which the shared
/// module reads and writes (`PreferencesFile.kt`) exactly as DataStore does.
nonisolated enum PreferencesFile {
    static func encode(_ preferences: [String: PreferenceValue]) -> Data {
        Data(KitaeCore.PreferencesFile.shared.encode(preferences: preferences.mapValues(\.core)))
    }

    static func decode(_ data: Data) throws -> [String: PreferenceValue] {
        let preferences: [String: any KitaeCore.PreferenceValue]
        do {
            preferences = try KitaeCore.PreferencesFile.shared.decode(data: KotlinByteArray.of(data))
        } catch {
            throw PreferencesFileError.malformed
        }
        return preferences.compactMapValues { PreferenceValue($0) }
    }
}

nonisolated extension PreferenceValue {
    /// The shared module's setting, as the iPhone's.
    init?(_ value: any KitaeCore.PreferenceValue) {
        switch value {
        case let value as PreferenceValueBoolValue: self = .bool(value.value)
        case let value as PreferenceValueFloatValue: self = .float(value.value)
        case let value as PreferenceValueIntValue: self = .int(value.value)
        case let value as PreferenceValueLongValue: self = .long(value.value)
        case let value as PreferenceValueStringValue: self = .string(value.value)
        case let value as PreferenceValueStringSetValue: self = .stringSet(value.value)
        case let value as PreferenceValueDoubleValue: self = .double(value.value)
        case let value as PreferenceValueBytesValue: self = .bytes(Data(value.value))
        default: return nil
        }
    }

    /// The shared module's own.
    var core: any KitaeCore.PreferenceValue {
        switch self {
        case .bool(let value): PreferenceValueBoolValue(value: value)
        case .float(let value): PreferenceValueFloatValue(value: value)
        case .int(let value): PreferenceValueIntValue(value: value)
        case .long(let value): PreferenceValueLongValue(value: value)
        case .string(let value): PreferenceValueStringValue(value: value)
        case .stringSet(let value): PreferenceValueStringSetValue(value: value)
        case .double(let value): PreferenceValueDoubleValue(value: value)
        case .bytes(let value): PreferenceValueBytesValue(value: KotlinByteArray.of(value))
        }
    }
}

nonisolated extension Data {
    /// The bytes of a Kotlin byte array.
    init(_ bytes: KotlinByteArray) {
        self.init((0..<bytes.size).map { UInt8(bitPattern: bytes.get(index: $0)) })
    }
}

nonisolated extension KotlinByteArray {
    /// A Kotlin byte array of `data`'s bytes.
    static func of(_ data: Data) -> KotlinByteArray {
        let bytes = KotlinByteArray(size: Int32(data.count))
        for (index, byte) in data.enumerated() {
            bytes.set(index: Int32(index), value: Int8(bitPattern: byte))
        }
        return bytes
    }
}
