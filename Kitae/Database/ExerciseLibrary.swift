// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import GRDB

/// Kitae's own exercises: the ones a database starts with and the ones later versions of Kitae
/// bring. Each has a library key (`exercises.libraryKey`, like "barbell-bench-press") that never
/// changes, by which Kitae finds its animation, muscles and energy; exercises people make have
/// none.
///
/// Which keys a database has been given is kept in Kitae's own table `exercise_library`, outside
/// Room's schema like the body tables, so a new version of Kitae adds the exercises it brings
/// once and never brings back one people deleted. The statement is the one Room would write for
/// this entity, so Kitae for Android can take the table over unchanged:
///
///     @Entity(tableName = "exercise_library")
///     data class LibraryEntity(@PrimaryKey val key: String)
nonisolated enum ExerciseLibrary {
    static let statements = [
        "CREATE TABLE IF NOT EXISTS `exercise_library` (`key` TEXT NOT NULL, PRIMARY KEY(`key`))",
    ]

    /// Makes the table if it isn't there yet. Every Kitae database started with the starter
    /// exercises, so one without the table has been given those, deleted or not.
    static func create(_ db: Database, seed: Seed) throws {
        guard try !db.tableExists("exercise_library") else { return }
        for statement in statements {
            try db.execute(sql: statement)
        }
        for exercise in seed.exercises where exercise.id != nil {
            try db.execute(sql: "INSERT OR IGNORE INTO `exercise_library` (`key`) VALUES (?)", arguments: [exercise.key])
        }
    }

    /// Adds the library's exercises the database hasn't been given yet. An exercise people made
    /// with the same name becomes Kitae's (it gets the key, and so the animation), instead of
    /// being added twice. Returns how many were added.
    @discardableResult
    static func sync(_ db: Database, library: [Seed.Exercise]) throws -> Int {
        let given = Set(try String.fetchAll(db, sql: "SELECT `key` FROM `exercise_library`"))
        var added = 0
        for exercise in library where !given.contains(exercise.key) {
            let hasKey = try Bool.fetchOne(
                db, sql: "SELECT EXISTS (SELECT 1 FROM `exercises` WHERE `libraryKey` = ?)", arguments: [exercise.key]
            ) ?? false
            if !hasKey {
                let sameName = try Row.fetchOne(
                    db, sql: "SELECT `id`, `libraryKey` FROM `exercises` WHERE `name` = ? COLLATE NOCASE",
                    arguments: [exercise.name]
                )
                if let sameName {
                    // Someone's own exercise becomes Kitae's; one of Kitae's own keeps its key.
                    if (sameName["libraryKey"] as String?) == nil {
                        try db.execute(
                            sql: "UPDATE `exercises` SET `libraryKey` = ? WHERE `id` = ?",
                            arguments: [exercise.key, sameName["id"] as Int64]
                        )
                    }
                } else {
                    try exercise.insert(db)
                    added += 1
                }
            }
            try db.execute(sql: "INSERT OR IGNORE INTO `exercise_library` (`key`) VALUES (?)", arguments: [exercise.key])
        }
        return added
    }
}
