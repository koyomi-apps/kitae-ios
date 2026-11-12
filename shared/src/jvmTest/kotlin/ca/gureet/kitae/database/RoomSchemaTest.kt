// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Room 3 makes the iPhone's database: the schema it exports for version 10 is compared with the
 * iPhone's, `ios/KitaeTests/RoomSchema10.json`. Room opens a database only if its identity
 * hash matches, so a difference here means neither app could open the other's files.
 */
class RoomSchemaTest {
    private val exported = database(File(System.getProperty("kitae.schemas"), "ca.gureet.kitae.database.KitaeDatabase/10.json"))
    private val iPhone = database(File(System.getProperty("kitae.iPhoneSchema")))

    @Test
    fun versionTenHasTheIPhonesIdentityHash() {
        assertEquals(10, iPhone.getValue("version").jsonPrimitive.int)
        assertEquals("a8dd8c0661148678f11cce771546f37e", iPhone.string("identityHash"))

        assertEquals(10, exported.getValue("version").jsonPrimitive.int)
        assertEquals(iPhone.string("identityHash"), exported.string("identityHash"))
    }

    @Test
    fun tablesAreTheIPhones() {
        val iPhoneTables = iPhone.getValue("entities").jsonArray.map { it.withoutFieldPaths() }
        val exportedTables = exported.getValue("entities").jsonArray.map { it.withoutFieldPaths() }
        assertEquals(iPhoneTables.map { it.tableName }, exportedTables.map { it.tableName })
        for ((iPhoneTable, exportedTable) in iPhoneTables.zip(exportedTables)) {
            assertEquals(iPhoneTable, exportedTable, "The table ${iPhoneTable.tableName} differs.")
        }

        assertEquals(iPhone.withoutFieldPaths(), exported.withoutFieldPaths())
    }

    /** A schema file's `database`. `shared/build.gradle.kts` says where the files are. */
    private fun database(file: File): JsonObject =
        Json.parseToJsonElement(file.readText()).jsonObject.getValue("database").jsonObject

    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content

    private val JsonElement.tableName get() = jsonObject.string("tableName")

    /**
     * The schema without `fieldPath`, which is the Kotlin property's name and never reaches the
     * database: the classes here are named as the iPhone names things, like `weightKg` for the
     * column `weight`.
     */
    private fun JsonElement.withoutFieldPaths(): JsonElement = when (this) {
        is JsonObject -> JsonObject(filterKeys { it != "fieldPath" }.mapValues { it.value.withoutFieldPaths() })
        is JsonArray -> JsonArray(map { it.withoutFieldPaths() })
        else -> this
    }
}
