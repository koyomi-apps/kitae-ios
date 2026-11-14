// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import KitaeCore

/// People's notes on their exercises, like a seat height or a grip, in Kitae's own table. Like
/// the body tables (`BodyTables.kt`), it's in the same file as Kitae for Android's tables, so
/// backups carry it, but it isn't part of Room's schema. It's laid out exactly as Room would make
/// it for this entity, so Kitae for Android can take it over in a later version:
///
///     @Entity(tableName = "exercise_notes")
///     data class ExerciseNote(@PrimaryKey val exerciseId: Long, val note: String)
///
/// An exercise has one note at most, and deleting the exercise deletes it (`Exercises.delete`).
/// There's no foreign key on purpose: a migration that rebuilds `exercises`, here or on Android,
/// would take every note with it. Kitae for Android leaves a deleted exercise's note behind,
/// where nothing reads it, as exercise ids aren't used again.
nonisolated enum ExerciseNotes {
    /// Every exercise's note, by exercise.
    static func all(_ db: Database) throws -> [Int64: String] {
        let notes = try db.core.notes()
        return Dictionary(uniqueKeysWithValues: notes.map { ($0.key.int64Value, $0.value) })
    }

    /// Saves an exercise's note, trimmed. An empty one deletes it.
    static func save(_ db: Database, exerciseID: Int64, note: String) throws {
        try db.core.saveNote(exerciseId: exerciseID, note: note)
    }

    static func delete(_ db: Database, exerciseID: Int64) throws {
        try db.core.deleteNote(exerciseId: exerciseID)
    }
}
