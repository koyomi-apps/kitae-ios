// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import ca.gureet.kitae.app.SettingsStore
import ca.gureet.kitae.app.ZipArchive
import ca.gureet.kitae.backup.ArchiveFile
import ca.gureet.kitae.backup.PreferenceValue
import ca.gureet.kitae.model.SettingKey
import ca.gureet.kitae.model.WeightUnit
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Android's side of backups: its zips, and taking a restored backup's settings. */
class BackupFilesTest {
    private val folder: File = Files.createTempDirectory("backups").toFile()

    @Test
    fun zipsKeepTheirFiles() {
        val database = File(folder, "kenko_database").apply { writeBytes(ByteArray(5000) { (it % 7).toByte() }) }
        val settings = File(folder, "settings.preferences_pb").apply { writeText("settings") }
        val zip = File(folder, "backup.zip").path
        ZipArchive.write(zip, listOf(ArchiveFile("kenko_database", database.path), ArchiveFile("settings.preferences_pb", settings.path)))

        assertEquals(
            listOf("kenko_database" to 5000L, "settings.preferences_pb" to 8L),
            ZipArchive.files(zip).map { it.path to it.size },
        )
        val out = File(folder, "out")
        ZipArchive.extract(zip, "kenko_database", out.path)
        assertEquals(database.readBytes().toList(), out.readBytes().toList())
    }

    @Test
    fun aRestoreKeepsThisPhonesBackupSettings() = runTest {
        val store = SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder, "settings.preferences_pb") })
        store.setBackupFolder("content://folder")
        store.update { it.copy(backUpAfterWorkouts = false) }

        store.restore(
            mapOf(
                SettingKey.UNIT to PreferenceValue.StringValue("Pounds"),
                SettingKey.BACKUP_URI to PreferenceValue.StringValue("content://another-phone"),
                SettingKey.BACK_UP_AFTER_WORKOUTS to PreferenceValue.BoolValue(true),
            ),
        )

        assertEquals(WeightUnit.Pounds, store.settings.first().unit)
        assertEquals(false, store.settings.first().backUpAfterWorkouts)
        assertEquals("content://folder", store.backups.first().folderUri)
    }

    @Test
    fun aResetKeepsTheBackupFolder() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder, "reset.preferences_pb") }
        val store = SettingsStore(dataStore)
        store.setBackupFolder("content://folder")
        dataStore.edit {
            it[stringPreferencesKey(SettingKey.UNIT)] = "Pounds"
            it[booleanPreferencesKey(SettingKey.ONBOARDING_DONE)] = true
        }

        store.reset()

        assertEquals(WeightUnit.Kilograms, store.settings.first().unit)
        assertEquals(false, store.onboardingDone.first())
        assertEquals("content://folder", store.backups.first().folderUri)
    }
}
