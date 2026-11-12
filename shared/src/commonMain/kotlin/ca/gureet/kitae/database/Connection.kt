// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL

// Plain SQL on a connection, for the work Room's callbacks and migrations do, with arguments
// bound in order like the iPhone's `db.execute(sql:arguments:)`.

/** Runs [sql] with [arguments]. */
internal fun SQLiteConnection.execute(sql: String, vararg arguments: Any?) {
    prepare(sql).use { statement ->
        statement.bind(arguments)
        statement.step()
    }
}

/** Runs an INSERT and returns the new row's id. */
internal fun SQLiteConnection.insert(sql: String, vararg arguments: Any?): Long {
    execute(sql, *arguments)
    return prepare("SELECT last_insert_rowid()").use { statement ->
        statement.step()
        statement.getLong(0)
    }
}

/** The first column of the rows [sql] returns, as text. */
internal fun SQLiteConnection.texts(sql: String, vararg arguments: Any?): List<String> =
    prepare(sql).use { statement ->
        statement.bind(arguments)
        buildList { while (statement.step()) add(statement.getText(0)) }
    }

/** Whether [sql] returns a row. */
internal fun SQLiteConnection.hasRow(sql: String, vararg arguments: Any?): Boolean =
    prepare(sql).use { statement ->
        statement.bind(arguments)
        statement.step()
    }

/** Runs [block] in a transaction, or in the one the connection is already in. */
internal inline fun SQLiteConnection.transaction(block: () -> Unit) {
    if (inTransaction()) {
        block()
        return
    }
    execSQL("BEGIN IMMEDIATE TRANSACTION")
    try {
        block()
    } catch (error: Throwable) {
        execSQL("ROLLBACK TRANSACTION")
        throw error
    }
    execSQL("END TRANSACTION")
}

private fun SQLiteStatement.bind(arguments: Array<out Any?>) {
    arguments.forEachIndexed { index, argument ->
        val position = index + 1
        when (argument) {
            null -> bindNull(position)
            is String -> bindText(position, argument)
            is Long -> bindLong(position, argument)
            is Int -> bindLong(position, argument.toLong())
            is Double -> bindDouble(position, argument)
            is Boolean -> bindBoolean(position, argument)
            else -> throw IllegalArgumentException("Can't bind a ${argument::class.simpleName}.")
        }
    }
}
