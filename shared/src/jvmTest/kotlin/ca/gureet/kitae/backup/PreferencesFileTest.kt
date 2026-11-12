// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.backup

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The settings file in backups, as the iPhone's `PreferencesFileTests` check it, byte for byte. */
class PreferencesFileTest {
    @Test
    fun writesWhatDataStoreWrites() {
        assertContentEquals(
            hex("0a0f0a057468656d6512062a044461726b"),
            PreferencesFile.encode(mapOf("theme" to PreferenceValue.StringValue("Dark"))),
        )
        assertContentEquals(androidFile, PreferencesFile.encode(androidSettings))
    }

    @Test
    fun readsAndroidsSettings() {
        assertEquals(androidSettings, PreferencesFile.decode(androidFile))
    }

    @Test
    fun readsAndWritesEveryKindOfValue() {
        val file = hex(
            "0a0e0a016412093900000000000002c00a0a0a01661205150000c03f0a120a036e6567120b18f9ffffffff" +
                "ffffffff010a0b0a037261771204420200ff0a0f0a03736574120832060a01610a0162",
        )
        val settings = mapOf(
            "d" to PreferenceValue.DoubleValue(-2.25),
            "f" to PreferenceValue.FloatValue(1.5f),
            "neg" to PreferenceValue.IntValue(-7),
            "raw" to PreferenceValue.BytesValue(byteArrayOf(0x00, 0xFF.toByte())),
            "set" to PreferenceValue.StringSetValue(listOf("a", "b")),
        )
        assertEquals(settings, PreferencesFile.decode(file))
        assertContentEquals(file, PreferencesFile.encode(settings))
    }

    @Test
    fun emptyFilesHaveNoSettingsAndBrokenOnesAreRefused() {
        assertTrue(PreferencesFile.decode(ByteArray(0)).isEmpty())
        assertFailsWith<PreferencesFileException> { PreferencesFile.decode(byteArrayOf(0x0A, 0x05, 0x0A)) }
        assertFailsWith<PreferencesFileException> { PreferencesFile.decode(byteArrayOf(0x0A, 0x02, 0x0A, 0xFF.toByte())) }
    }

    companion object {
        /** Settings like Kitae for Android's, with every setting it keeps. */
        val androidFile = hex(
            "0a1b0a0f6261636b75705f696e74657276616c12082a065765656b6c790a580a0a6261636b75705f75726912" +
                "4a2a48636f6e74656e743a2f2f636f6d2e616e64726f69642e65787465726e616c73746f726167652e646f63" +
                "756d656e74732f747265652f7072696d617279253341446f63756d656e74730a190a0d636f6c6f725f70616c" +
                "6574746512082a06536572656e650a220a186c6173745f6261636b75705f74696d655f7365636f6e64731206" +
                "2080e9b5d6060a1f0a156c6173745f7365745f74696d655f7365636f6e647312062080c6b0d6060a150a0f6f" +
                "6e626f617264696e675f646f6e65120208010a180a12726573745f74696d65725f7365636f6e647312021878" +
                "0a100a057468656d6512072a054c696768740a170a0b7765696768745f756e697412082a06506f756e6473",
        )

        val androidSettings = mapOf(
            "backup_interval" to PreferenceValue.StringValue("Weekly"),
            "backup_uri" to PreferenceValue.StringValue("content://com.android.externalstorage.documents/tree/primary%3ADocuments"),
            "color_palette" to PreferenceValue.StringValue("Serene"),
            "last_backup_time_seconds" to PreferenceValue.LongValue(1_791_849_600),
            "last_set_time_seconds" to PreferenceValue.LongValue(1_791_763_200),
            "onboarding_done" to PreferenceValue.BoolValue(true),
            "rest_timer_seconds" to PreferenceValue.IntValue(120),
            "theme" to PreferenceValue.StringValue("Light"),
            "weight_unit" to PreferenceValue.StringValue("Pounds"),
        )

        fun hex(text: String): ByteArray = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
