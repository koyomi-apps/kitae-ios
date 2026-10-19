// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import GRDB

nonisolated extension Exercise {
    /// An exercise from a row with the `exercises` table's columns.
    init(row: Row) {
        self.init(
            id: row["id"],
            name: row["name"],
            target: row["target"],
            isIsometric: row["isIsometric"],
            restSeconds: row["restSeconds"],
            reference: row["reference"],
            instructions: row["instructions"],
            libraryKey: row["libraryKey"]
        )
    }
}
