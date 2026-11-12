// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.app

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import ca.gureet.kitae.backup.ArchiveEntry
import ca.gureet.kitae.backup.ArchiveFile
import ca.gureet.kitae.backup.BackupArchive
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Backups' zips, with `java.util.zip`, behind the shared module's interface. */
object ZipArchive : BackupArchive {
    override fun write(zip: String, files: List<ArchiveFile>) {
        ZipOutputStream(File(zip).outputStream().buffered()).use { out ->
            for (file in files) {
                out.putNextEntry(ZipEntry(file.name))
                File(file.path).inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
    }

    override fun files(zip: String): List<ArchiveEntry> = ZipFile(zip).use { file ->
        file.entries().asSequence().filter { !it.isDirectory }.map { ArchiveEntry(it.name, it.size) }.toList()
    }

    override fun extract(zip: String, entry: String, destination: String) {
        ZipFile(zip).use { file ->
            val found = file.getEntry(entry) ?: error("No $entry in the zip")
            file.getInputStream(found).use { input -> File(destination).outputStream().use { input.copyTo(it) } }
        }
    }
}

/**
 * The folder backups go in, picked with Android's folder picker, as Android has no folder of
 * Kamae's that people can see. Kamae keeps its permission across restarts.
 */
class BackupFolder(private val resolver: ContentResolver, val uri: Uri) {
    private val folderDocument: Uri
        get() = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))

    /** The folder's name, as the picker showed it. */
    val name: String?
        get() = runCatching {
            resolver.query(folderDocument, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()

    /** Copies [file] into the folder as [name], over a file of that name if there's one. */
    fun save(file: File, name: String) {
        val document = find(name)
            ?: DocumentsContract.createDocument(resolver, folderDocument, "application/zip", name)
            ?: error("The folder didn't take a new file")
        (resolver.openOutputStream(document, "wt") ?: error("The file can't be written")).use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
    }

    private fun find(name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        resolver.query(children, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == name) return DocumentsContract.buildDocumentUriUsingTree(uri, cursor.getString(0))
            }
        }
        return null
    }

    companion object {
        /** Keeps the permission to [uri] once the picker gives it. */
        fun keep(resolver: ContentResolver, uri: Uri) {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

        /** Whether Kamae still may write in the folder at [uri]. */
        fun isReachable(resolver: ContentResolver, uri: Uri): Boolean =
            resolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
    }
}

/** A file's name as the picker shows it, like "kitae_backup_21_10.zip". */
fun displayName(resolver: ContentResolver, uri: Uri): String = runCatching {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull() ?: uri.lastPathSegment.orEmpty()
