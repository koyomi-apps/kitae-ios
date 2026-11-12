// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.backup

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ca.gureet.kitae.database.DatabaseFileTest
import ca.gureet.kitae.database.DatabaseFiles
import ca.gureet.kitae.database.KitaeDatabase
import ca.gureet.kitae.database.KitaeDatabaseConstructor
import ca.gureet.kitae.database.buildKitaeDatabase
import ca.gureet.kitae.database.bundledSeed
import ca.gureet.kitae.database.keys
import ca.gureet.kitae.database.workout
import ca.gureet.kitae.model.AppSettings
import ca.gureet.kitae.model.WeightUnit
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Zips with `java.util.zip`, as Kitae for Android does. */
object JavaZip : BackupArchive {
    override fun write(zip: String, files: List<ArchiveFile>) {
        ZipOutputStream(File(zip).outputStream()).use { out ->
            for (file in files) {
                out.putNextEntry(ZipEntry(file.name))
                File(file.path).inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
    }

    override fun files(zip: String): List<ArchiveEntry> = ZipFile(zip).use { archive ->
        archive.entries().asSequence().filter { !it.isDirectory }.map { ArchiveEntry(it.name, it.size) }.toList()
    }

    override fun extract(zip: String, entry: String, destination: String) {
        ZipFile(zip).use { archive ->
            archive.getInputStream(archive.getEntry(entry)).use { input -> File(destination).outputStream().use { input.copyTo(it) } }
        }
    }
}

/** Backups either app restores, and restoring them, as the iPhone's `BackupTests` check them. */
class BackupsTest : DatabaseFileTest() {
    private val folder = file.parent

    private fun subfolder(name: String): Path = folder.resolve(name).createDirectories()

    private fun openAt(path: Path): KitaeDatabase =
        Room.databaseBuilder(path.toString()) { KitaeDatabaseConstructor.initialize() }.buildKitaeDatabase(bundledSeed)

    /** Two finished workouts with sets done, on the plan's first two days. */
    private suspend fun addWorkouts(database: KitaeDatabase) {
        val plan = assertNotNull(database.plans().plan(assertNotNull(database.plans().currentId())))
        database.workout(plan.days[0], TODAY - 3, done = 2)
        database.workout(plan.days[1], TODAY - 1, done = 2)
    }

    private suspend fun history(database: KitaeDatabase) = database.history().workouts(activeId = null)

    /** A zip like Kitae for Android's, holding [files] as they are. */
    private fun zip(vararg files: Pair<String, ByteArray>): String {
        val work = subfolder("zip-${files.joinToString("-") { it.first }}-${System.nanoTime()}")
        val entries = files.map { (name, contents) ->
            work.resolve(name).writeBytes(contents)
            ArchiveFile(name, work.resolve(name).toString())
        }
        return work.resolve("backup.zip").toString().also { JavaZip.write(it, entries) }
    }

    /** A backup whose database is Kitae's, but marked as [version]. */
    private suspend fun backup(atVersion: Int): String {
        val database = subfolder("version-$atVersion").resolve(Backups.DATABASE_ENTRY)
        openAt(database).apply { plans().all() }.close()
        BundledSQLiteDriver().open(database.toString()).use { it.execSQL("PRAGMA user_version = $atVersion") }
        return zip(Backups.DATABASE_ENTRY to database.readBytes())
    }

    private fun refused(problem: BackupProblem, zip: String) {
        val error = assertFailsWith<BackupException> { Backups.open(zip, subfolder("open-${System.nanoTime()}").toString(), JavaZip) }
        assertEquals(problem, error.problem)
    }

    @Test
    fun backupsAreNamedByDayAndMonthLikeAndroids() {
        assertEquals("kitae_backup_10_10.zip", Backups.fileName(TODAY))
        assertEquals("kitae_backup_05_03.zip", Backups.fileName(LocalDate(2027, 3, 5).toEpochDays()))
    }

    @Test
    fun aBackupHoldsWhatKitaeForAndroidRestores() = runBlocking {
        val source = openFollowingStarterPlan()
        addWorkouts(source)
        val preferences = AppSettings(unit = WeightUnit.Pounds).backupPreferences(planChosen = true)
        val zip = Backups.makeArchive(source, preferences, subfolder("out").toString(), JavaZip)

        assertEquals(listOf("kenko_database", "settings.preferences_pb"), JavaZip.files(zip).map { it.path })
        val contents = Backups.open(zip, subfolder("open").toString(), JavaZip)
        assertEquals(preferences, contents.preferences)
        // The database is one file, without a log beside it, as Android replaces its own files.
        val neighbours = Path.of(contents.database).parent.listDirectoryEntries().map { it.name }.sorted()
        assertEquals(listOf("kenko_database", "settings.preferences_pb"), neighbours)
        source.close()
    }

    @Test
    fun aBackupRestoresOverTheAppsDatabase() = runBlocking {
        val source = openAt(subfolder("source").resolve("kenko_database"))
        source.plans().setCurrent(source.plans().all().first().id, TODAY)
        addWorkouts(source)
        val zip = Backups.makeArchive(source, mapOf("theme" to PreferenceValue.StringValue("Dark")), subfolder("out").toString(), JavaZip)
        val expected = history(source)
        val plan = source.plans().currentId()
        source.close()

        // The app's database, new, with no plan chosen and nothing done, closed before restoring.
        open().apply { assertNull(plans().currentId()) }.close()
        val contents = Backups.open(zip, subfolder("open").toString(), JavaZip)
        assertEquals(mapOf("theme" to PreferenceValue.StringValue("Dark")), contents.preferences)
        Backups.install(contents, file.toString())
        // The old database's log and journal went with it.
        assertEquals(emptyList(), folder.listDirectoryEntries("kenko_database-*").map { it.name })

        val restored = open()
        assertEquals(expected, history(restored))
        assertEquals(2, history(restored).size)
        assertEquals(plan, restored.plans().currentId())
        restored.close()
    }

    @Test
    fun androidBackupsWithALogComeBackWhole() = runBlocking {
        // Kitae for Android's database while it's open, with its latest changes still in the log.
        val android = subfolder("android")
        val database = openAt(android.resolve("kenko_database"))
        database.plans().setCurrent(database.plans().all().first().id, TODAY - 5)
        addWorkouts(database)
        val expected = history(database)
        val files = listOf("kenko_database", "kenko_database-wal", "kenko_database-shm").map { it to android.resolve(it).readBytes() }
        assertTrue(files[1].second.isNotEmpty())
        val zip = zip(*files.toTypedArray(), "settings.preferences_pb" to PreferencesFileTest.androidFile)
        database.close()

        val contents = Backups.open(zip, subfolder("open").toString(), JavaZip)
        assertEquals(PreferencesFileTest.androidSettings, contents.preferences)
        Backups.install(contents, file.toString())
        val restored = open()
        assertEquals(expected, history(restored))
        restored.close()
    }

    @Test
    fun filesThatArentKitaeBackupsAreRefused() = runBlocking {
        val text = folder.resolve("notes.zip").apply { writeBytes("Push day: bench, press, dips".encodeToByteArray()) }
        refused(BackupProblem.NotKitae, text.toString())
        refused(BackupProblem.NotKitae, zip("settings.preferences_pb" to PreferencesFileTest.androidFile))
        refused(BackupProblem.NotKitae, zip("kenko_database" to "not a database ".repeat(40).encodeToByteArray()))

        val other = subfolder("other").resolve("kenko_database")
        BundledSQLiteDriver().open(other.toString()).use { it.execSQL("CREATE TABLE notes (text TEXT)") }
        refused(BackupProblem.NotKitae, zip("kenko_database" to other.readBytes()))
    }

    @Test
    fun backupsFromOtherVersionsAreRefused() = runBlocking {
        refused(BackupProblem.OlderVersion, backup(atVersion = 7))
        refused(BackupProblem.NewerVersion, backup(atVersion = 11))
        // The version this app has goes in, and so do Kitae for Android's and the iPhone's earlier ones.
        assertNull(Backups.open(backup(atVersion = 10), subfolder("open-10").toString(), JavaZip).preferences)
        for (version in listOf(9, 8)) {
            DatabaseFiles.delete(file.toString())
            makeDatabase(version)
            val contents = Backups.open(zip("kenko_database" to file.readBytes()), subfolder("open-$version").toString(), JavaZip)
            // Restored, it's brought up to date, with all of Kitae's exercises.
            Backups.install(contents, file.toString())
            open().apply { assertEquals(169, keys().size) }.close()
        }
    }

    @Test
    fun aDamagedBackupIsRefused() = runBlocking {
        val source = openFollowingStarterPlan()
        addWorkouts(source)
        val zip = Backups.makeArchive(source, emptyMap(), subfolder("out").toString(), JavaZip)
        source.close()
        val database = File(Backups.open(zip, subfolder("open").toString(), JavaZip).database).readBytes()
        // Pages 3 to 6 written over.
        for (i in 2 * 4096 until 6 * 4096) database[i] = 0x5A
        refused(BackupProblem.Damaged, zip("kenko_database" to database))
    }

    @Test
    fun whatWasThereIsSavedBeforeRestoring() = runBlocking {
        val current = openFollowingStarterPlan()
        addWorkouts(current)
        val before = history(current)

        val contents = Backups.open(backup(atVersion = 10), subfolder("open").toString(), JavaZip)
        val safety = Backups.makeArchive(current, mapOf("theme" to PreferenceValue.StringValue("Dark")), subfolder("safety").toString(), JavaZip)
        current.close()
        Backups.install(contents, file.toString())
        open().apply { assertTrue(history(this).isEmpty()) }.close()

        val saved = Backups.open(safety, subfolder("open-safety").toString(), JavaZip)
        assertEquals(mapOf("theme" to PreferenceValue.StringValue("Dark")), saved.preferences)
        Backups.install(saved, file.toString())
        open().apply { assertEquals(before, history(this)) }.close()
    }

    @Test
    fun resettingStartsOverLikeANewInstall() = runBlocking {
        val database = openFollowingStarterPlan()
        addWorkouts(database)
        database.close()
        DatabaseFiles.delete(file.toString())
        assertEquals(emptyList(), folder.listDirectoryEntries("kenko_database*").map { it.name })

        val fresh = open()
        assertNull(fresh.plans().currentId())
        assertTrue(history(fresh).isEmpty())
        assertEquals(169, fresh.keys().size)
        fresh.close()
    }

    @Test
    fun theVersionsBackupsTakeAreRooms() {
        val schemas = File(System.getProperty("kitae.schemas"), "ca.gureet.kitae.database.KitaeDatabase")
        val hashes = schemas.listFiles().orEmpty().associate { schema ->
            val database = Json.parseToJsonElement(schema.readText()).jsonObject.getValue("database").jsonObject
            schema.nameWithoutExtension.toInt() to database.getValue("identityHash").jsonPrimitive.content
        }
        assertEquals(hashes, Backups.identityHashes)
    }
}
