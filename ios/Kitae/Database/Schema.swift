// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import GRDB

/// Kitae for Android's database, version 10, table for table.
///
/// The statements are the ones Room writes for that version (its schema file is
/// `app/schemas/com.looker.kenko.data.local.KenkoDatabase/10.json`), so a database made here
/// opens in Kitae for Android and the other way round. Change nothing here without making
/// the same change in Kitae for Android. Version 9 gave exercises their library key
/// (`ExerciseLibrary`), and version 10 whether they use weight; databases and backups from
/// versions 8 and 9 are brought up to it.
nonisolated enum Schema {
    /// What Room keeps in `PRAGMA user_version`.
    static let version = 10

    /// The oldest version Kitae can bring up to date.
    static let oldestVersion = 8

    /// What Room keeps in `room_master_table` for version 10. Room refuses a database whose
    /// hash doesn't match. It's worked out from the entities the way Room's compiler does
    /// (`SchemaIdentityKey`), which gives version 8's hash from its schema file too.
    static let identityHash = "a8dd8c0661148678f11cce771546f37e"

    /// Room's hash for each version Kitae can open.
    static let identityHashes = [
        8: "ed136697240555e41abaa05a57ab6667",
        9: "5d1b3eb2bd3f9bcf00e2d5627becb694",
        10: identityHash,
    ]

    /// Room's statements in Room's order: each table, then its indices, then Room's own table.
    static let statements = [
        "CREATE TABLE IF NOT EXISTS `sessions` (`date` INTEGER NOT NULL, `planId` INTEGER, `routineId` INTEGER, `isFinished` INTEGER NOT NULL DEFAULT 1, `startedAt` INTEGER, `finishedAt` INTEGER, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, FOREIGN KEY(`planId`) REFERENCES `plans`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`routineId`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        "CREATE INDEX IF NOT EXISTS `index_sessions_planId` ON `sessions` (`planId`)",
        "CREATE INDEX IF NOT EXISTS `index_sessions_routineId` ON `sessions` (`routineId`)",
        "CREATE TABLE IF NOT EXISTS `exercises` (`name` TEXT NOT NULL, `target` TEXT NOT NULL, `reference` TEXT, `isIsometric` INTEGER NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `restSeconds` INTEGER, `instructions` TEXT, `libraryKey` TEXT, `usesWeight` INTEGER NOT NULL DEFAULT 1)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_exercises_libraryKey` ON `exercises` (`libraryKey`)",
        "CREATE TABLE IF NOT EXISTS `plans` (`name` TEXT NOT NULL, `description` TEXT DEFAULT NULL, `difficulty` TEXT DEFAULT NULL, `focus` TEXT DEFAULT NULL, `equipment` TEXT DEFAULT NULL, `time` TEXT DEFAULT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS `plan_history` (`planId` INTEGER, `start` INTEGER NOT NULL, `end` INTEGER DEFAULT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, FOREIGN KEY(`planId`) REFERENCES `plans`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        "CREATE INDEX IF NOT EXISTS `index_plan_history_planId_start_end` ON `plan_history` (`planId`, `start`, `end`)",
        "CREATE TABLE IF NOT EXISTS `routines` (`planId` INTEGER NOT NULL, `name` TEXT NOT NULL, `position` INTEGER NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, FOREIGN KEY(`planId`) REFERENCES `plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_routines_planId` ON `routines` (`planId`)",
        "CREATE TABLE IF NOT EXISTS `routine_exercises` (`routineId` INTEGER NOT NULL, `exerciseId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, FOREIGN KEY(`routineId`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_routine_exercises_routineId` ON `routine_exercises` (`routineId`)",
        "CREATE INDEX IF NOT EXISTS `index_routine_exercises_exerciseId` ON `routine_exercises` (`exerciseId`)",
        "CREATE TABLE IF NOT EXISTS `routine_sets` (`routineExerciseId` INTEGER NOT NULL, `reps` INTEGER NOT NULL, `weight` REAL NOT NULL, `type` TEXT NOT NULL, `position` INTEGER NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `restSeconds` INTEGER, FOREIGN KEY(`routineExerciseId`) REFERENCES `routine_exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_routine_sets_routineExerciseId` ON `routine_sets` (`routineExerciseId`)",
        "CREATE TABLE IF NOT EXISTS `sets` (`reps` INTEGER NOT NULL, `weight` REAL NOT NULL, `type` TEXT NOT NULL, `order` INTEGER NOT NULL, `sessionId` INTEGER NOT NULL, `exerciseId` INTEGER NOT NULL, `rir` INTEGER NOT NULL, `isCompleted` INTEGER NOT NULL DEFAULT 1, `routineSetId` INTEGER, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `restSeconds` INTEGER, FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`routineSetId`) REFERENCES `routine_sets`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        "CREATE INDEX IF NOT EXISTS `index_sets_sessionId_exerciseId` ON `sets` (`sessionId`, `exerciseId`)",
        "CREATE INDEX IF NOT EXISTS `index_sets_routineSetId` ON `sets` (`routineSetId`)",
        "CREATE TABLE IF NOT EXISTS `set_type` (`type` TEXT NOT NULL, `modifier` REAL NOT NULL, PRIMARY KEY(`type`))",
        "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
        "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '\(identityHash)')",
    ]

    /// How much each set type counts towards an exercise's rating. Kitae for Android joins on
    /// this table, so every type needs a row. Its numbers are 32-bit floats, written here the
    /// way they land in the database.
    static let setTypeModifiers: [(type: SetType, modifier: Double)] = [
        (.standard, Double(Float(1.0))),
        (.drop, Double(Float(1.35))),
        (.restPause, Double(Float(1.2))),
        (.warmup, Double(Float(0))),
        (.failure, Double(Float(1.15))),
    ]

    /// Makes every table in an empty database and marks it as version 10.
    static func create(_ db: Database) throws {
        for statement in statements {
            try db.execute(sql: statement)
        }
        for (type, modifier) in setTypeModifiers {
            try db.execute(
                sql: "INSERT OR REPLACE INTO `set_type` (`type`, `modifier`) VALUES (?, ?)",
                arguments: [type.rawValue, modifier]
            )
        }
        try db.execute(sql: "PRAGMA user_version = \(version)")
    }

    /// Brings a version 8 database up to version 9, as Room's `Migration(8, 9)` in Kitae for
    /// Android does: exercises get a library key, and the starter exercises theirs (`keys`, by
    /// id), renamed or not.
    static func migrate8(_ db: Database, keys: [Int64: String]) throws {
        try db.execute(sql: "ALTER TABLE `exercises` ADD COLUMN `libraryKey` TEXT")
        try db.execute(sql: "CREATE UNIQUE INDEX IF NOT EXISTS `index_exercises_libraryKey` ON `exercises` (`libraryKey`)")
        for (id, key) in keys.sorted(by: { $0.key < $1.key }) {
            try db.execute(sql: "UPDATE `exercises` SET `libraryKey` = ? WHERE `id` = ?", arguments: [key, id])
        }
        try mark(db, version: 9)
    }

    /// Brings a version 9 database up to version 10, as Room's `Migration(9, 10)` in Kitae for
    /// Android does: exercises say whether they use weight, and Kitae's exercises done with
    /// the body alone (`weightless`, by library key) don't. Weights their sets already have
    /// stay as they were; Kitae leaves them out while the exercise doesn't use weight.
    static func migrate9(_ db: Database, weightless: Set<String>) throws {
        try db.execute(sql: "ALTER TABLE `exercises` ADD COLUMN `usesWeight` INTEGER NOT NULL DEFAULT 1")
        for key in weightless.sorted() {
            try db.execute(sql: "UPDATE `exercises` SET `usesWeight` = 0 WHERE `libraryKey` = ?", arguments: [key])
        }
        try mark(db, version: 10)
    }

    /// Records that the database is now at `version`, with Room's hash for it.
    private static func mark(_ db: Database, version: Int) throws {
        guard let hash = identityHashes[version] else { throw KitaeDatabaseError.unsupportedVersion(version) }
        try db.execute(sql: "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, ?)", arguments: [hash])
        try db.execute(sql: "PRAGMA user_version = \(version)")
    }
}
