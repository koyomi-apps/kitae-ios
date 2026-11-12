// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation

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
/// to the database. It's a Jetpack DataStore `PreferenceMap` in protocol buffers: an entry for
/// each setting, with its name and a value of one of eight kinds.
///
/// Kitae for Android doesn't recover from a settings file it can't read, so this writes
/// exactly the format DataStore does, with keys in order like protobuf's deterministic output.
nonisolated enum PreferencesFile {
    static func encode(_ preferences: [String: PreferenceValue]) -> Data {
        var file = ProtoWriter()
        for key in preferences.keys.sorted() {
            guard let value = preferences[key] else { continue }
            var entry = ProtoWriter()
            entry.string(key, field: 1)
            entry.bytes(encode(value), field: 2)
            file.bytes(entry.data, field: 1)
        }
        return file.data
    }

    static func decode(_ data: Data) throws -> [String: PreferenceValue] {
        var file = ProtoReader(data)
        var preferences: [String: PreferenceValue] = [:]
        while let (field, wire) = try file.nextTag() {
            guard field == 1, wire == .lengthDelimited else {
                try file.skip(wire)
                continue
            }
            var entry = ProtoReader(try file.lengthDelimited())
            var key = ""
            var value: PreferenceValue?
            while let (field, wire) = try entry.nextTag() {
                switch (field, wire) {
                case (1, .lengthDelimited): key = try entry.string()
                case (2, .lengthDelimited): value = try decodeValue(try entry.lengthDelimited())
                default: try entry.skip(wire)
                }
            }
            if let value {
                preferences[key] = value
            }
        }
        return preferences
    }

    private static func encode(_ value: PreferenceValue) -> Data {
        var writer = ProtoWriter()
        switch value {
        case .bool(let isOn): writer.varint(isOn ? 1 : 0, field: 1)
        case .float(let number): writer.fixed32(number.bitPattern, field: 2)
        case .int(let number): writer.varint(UInt64(bitPattern: Int64(number)), field: 3)
        case .long(let number): writer.varint(UInt64(bitPattern: number), field: 4)
        case .string(let text): writer.string(text, field: 5)
        case .stringSet(let texts):
            var set = ProtoWriter()
            for text in texts {
                set.string(text, field: 1)
            }
            writer.bytes(set.data, field: 6)
        case .double(let number): writer.fixed64(number.bitPattern, field: 7)
        case .bytes(let bytes): writer.bytes(bytes, field: 8)
        }
        return writer.data
    }

    private static func decodeValue(_ data: Data) throws -> PreferenceValue? {
        var reader = ProtoReader(data)
        var value: PreferenceValue?
        while let (field, wire) = try reader.nextTag() {
            switch (field, wire) {
            case (1, .varint): value = .bool(try reader.varint() != 0)
            case (2, .fixed32): value = .float(Float(bitPattern: try reader.fixed32()))
            case (3, .varint): value = .int(Int32(truncatingIfNeeded: try reader.varint()))
            case (4, .varint): value = .long(Int64(bitPattern: try reader.varint()))
            case (5, .lengthDelimited): value = .string(try reader.string())
            case (6, .lengthDelimited):
                var set = ProtoReader(try reader.lengthDelimited())
                var texts: [String] = []
                while let (field, wire) = try set.nextTag() {
                    if field == 1, wire == .lengthDelimited {
                        texts.append(try set.string())
                    } else {
                        try set.skip(wire)
                    }
                }
                value = .stringSet(texts)
            case (7, .fixed64): value = .double(Double(bitPattern: try reader.fixed64()))
            case (8, .lengthDelimited): value = .bytes(try reader.lengthDelimited())
            default: try reader.skip(wire)
            }
        }
        return value
    }
}

/// How a protobuf field is laid out.
private nonisolated enum WireType: UInt64 {
    case varint = 0
    case fixed64 = 1
    case lengthDelimited = 2
    case startGroup = 3
    case endGroup = 4
    case fixed32 = 5
}

private nonisolated struct ProtoWriter {
    private(set) var data = Data()

    mutating func varint(_ value: UInt64, field: Int) {
        tag(field, .varint)
        rawVarint(value)
    }

    mutating func fixed32(_ value: UInt32, field: Int) {
        tag(field, .fixed32)
        withUnsafeBytes(of: value.littleEndian) { data.append(contentsOf: $0) }
    }

    mutating func fixed64(_ value: UInt64, field: Int) {
        tag(field, .fixed64)
        withUnsafeBytes(of: value.littleEndian) { data.append(contentsOf: $0) }
    }

    mutating func bytes(_ value: Data, field: Int) {
        tag(field, .lengthDelimited)
        rawVarint(UInt64(value.count))
        data.append(value)
    }

    mutating func string(_ value: String, field: Int) {
        bytes(Data(value.utf8), field: field)
    }

    private mutating func tag(_ field: Int, _ wire: WireType) {
        rawVarint(UInt64(field) << 3 | wire.rawValue)
    }

    private mutating func rawVarint(_ value: UInt64) {
        var rest = value
        while rest >= 0x80 {
            data.append(UInt8(rest & 0x7F) | 0x80)
            rest >>= 7
        }
        data.append(UInt8(rest))
    }
}

private nonisolated struct ProtoReader {
    private let bytes: [UInt8]
    private var index = 0

    init(_ data: Data) {
        bytes = [UInt8](data)
    }

    /// The next field's number and layout, or nil at the end.
    mutating func nextTag() throws -> (field: Int, wire: WireType)? {
        guard index < bytes.count else { return nil }
        let key = try varint()
        guard let wire = WireType(rawValue: key & 7), key >> 3 > 0, key >> 3 <= UInt64(Int32.max) else {
            throw PreferencesFileError.malformed
        }
        return (Int(key >> 3), wire)
    }

    mutating func varint() throws -> UInt64 {
        var result: UInt64 = 0
        var shift: UInt64 = 0
        while shift < 64 {
            guard index < bytes.count else { throw PreferencesFileError.malformed }
            let byte = bytes[index]
            index += 1
            result |= UInt64(byte & 0x7F) << shift
            if byte & 0x80 == 0 { return result }
            shift += 7
        }
        throw PreferencesFileError.malformed
    }

    mutating func fixed32() throws -> UInt32 {
        UInt32(truncatingIfNeeded: try littleEndian(4))
    }

    mutating func fixed64() throws -> UInt64 {
        try littleEndian(8)
    }

    mutating func lengthDelimited() throws -> Data {
        let length = try varint()
        guard length <= UInt64(bytes.count - index) else { throw PreferencesFileError.malformed }
        let end = index + Int(length)
        defer { index = end }
        return Data(bytes[index..<end])
    }

    mutating func string() throws -> String {
        guard let text = String(data: try lengthDelimited(), encoding: .utf8) else {
            throw PreferencesFileError.malformed
        }
        return text
    }

    mutating func skip(_ wire: WireType) throws {
        switch wire {
        case .varint: _ = try varint()
        case .fixed64: _ = try littleEndian(8)
        case .lengthDelimited: _ = try lengthDelimited()
        case .fixed32: _ = try littleEndian(4)
        case .startGroup, .endGroup: throw PreferencesFileError.malformed
        }
    }

    private mutating func littleEndian(_ count: Int) throws -> UInt64 {
        guard count <= bytes.count - index else { throw PreferencesFileError.malformed }
        var value: UInt64 = 0
        for offset in 0..<count {
            value |= UInt64(bytes[index + offset]) << (8 * UInt64(offset))
        }
        index += count
        return value
    }
}
