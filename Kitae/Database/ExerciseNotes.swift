// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import GRDB

/// People's notes on their exercises, like a seat height or a grip, in Kitae's own table. Like
/// the body tables (see `BodyTables`), it's in the same file as Kitae for Android's tables, so
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
    static let statements = [
        "CREATE TABLE IF NOT EXISTS `exercise_notes` (`exerciseId` INTEGER NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`exerciseId`))",
    ]

    /// Makes the table if it isn't there yet: in a new database, in one from before notes, and
    /// in one restored from Kitae for Android.
    static func create(_ db: Database) throws {
        for statement in statements {
            try db.execute(sql: statement)
        }
    }

    /// Every exercise's note, by exercise.
    static func all(_ db: Database) throws -> [Int64: String] {
        var notes: [Int64: String] = [:]
        for row in try Row.fetchAll(db, sql: """
            SELECT `exercise_notes`.`exerciseId`, `exercise_notes`.`note`
            FROM `exercise_notes`
            INNER JOIN `exercises` ON `exercises`.`id` = `exercise_notes`.`exerciseId`
            """) {
            notes[row["exerciseId"]] = row["note"]
        }
        return notes
    }

    /// Saves an exercise's note, trimmed. An empty one deletes it.
    static func save(_ db: Database, exerciseID: Int64, note: String) throws {
        let trimmed = note.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return try delete(db, exerciseID: exerciseID) }
        try db.execute(sql: """
            INSERT INTO `exercise_notes` (`exerciseId`, `note`) VALUES (?, ?)
            ON CONFLICT(`exerciseId`) DO UPDATE SET `note` = excluded.`note`
            """, arguments: [exerciseID, trimmed])
    }

    static func delete(_ db: Database, exerciseID: Int64) throws {
        try db.execute(sql: "DELETE FROM `exercise_notes` WHERE `exerciseId` = ?", arguments: [exerciseID])
    }
}
