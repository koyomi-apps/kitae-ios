// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.backup

/** One setting in a backup's settings file: a value of one of DataStore's eight kinds. */
sealed interface PreferenceValue {
    data class BoolValue(val value: Boolean) : PreferenceValue

    data class FloatValue(val value: Float) : PreferenceValue

    data class IntValue(val value: Int) : PreferenceValue

    data class LongValue(val value: Long) : PreferenceValue

    data class StringValue(val value: String) : PreferenceValue

    data class StringSetValue(val value: List<String>) : PreferenceValue

    data class DoubleValue(val value: Double) : PreferenceValue

    class BytesValue(val value: ByteArray) : PreferenceValue {
        override fun equals(other: Any?): Boolean = other is BytesValue && value.contentEquals(other.value)

        override fun hashCode(): Int = value.contentHashCode()

        override fun toString(): String = "BytesValue(${value.size} bytes)"
    }
}

/** Thrown when a settings file can't be read. */
class PreferencesFileException : IllegalArgumentException("The backup's settings can't be read.")

/**
 * The settings file backups carry next to the database, `settings.preferences_pb`, as the
 * iPhone reads and writes it (`PreferencesFile.swift`). It's a Jetpack DataStore
 * `PreferenceMap` in protocol buffers: an entry for each setting, with its name and its value.
 *
 * Kitae for Android 2.0.0 doesn't recover from a settings file it can't read, so this writes
 * exactly the format DataStore does, with keys in order like protobuf's deterministic output.
 */
object PreferencesFile {
    fun encode(preferences: Map<String, PreferenceValue>): ByteArray {
        val file = ProtoWriter()
        for (key in preferences.keys.sorted()) {
            val entry = ProtoWriter()
            entry.string(key, field = 1)
            entry.bytes(encode(preferences.getValue(key)), field = 2)
            file.bytes(entry.toByteArray(), field = 1)
        }
        return file.toByteArray()
    }

    /** Reads a settings file, throwing [PreferencesFileException] when it's broken. */
    @Throws(Exception::class)
    fun decode(data: ByteArray): Map<String, PreferenceValue> {
        val file = ProtoReader(data)
        val preferences = LinkedHashMap<String, PreferenceValue>()
        while (true) {
            val (field, wire) = file.nextTag() ?: break
            if (field != 1 || wire != LENGTH_DELIMITED) {
                file.skip(wire)
                continue
            }
            val entry = ProtoReader(file.lengthDelimited())
            var key = ""
            var value: PreferenceValue? = null
            while (true) {
                val (entryField, entryWire) = entry.nextTag() ?: break
                when {
                    entryField == 1 && entryWire == LENGTH_DELIMITED -> key = entry.string()
                    entryField == 2 && entryWire == LENGTH_DELIMITED -> value = decodeValue(entry.lengthDelimited())
                    else -> entry.skip(entryWire)
                }
            }
            if (value != null) preferences[key] = value
        }
        return preferences
    }

    private fun encode(value: PreferenceValue): ByteArray {
        val writer = ProtoWriter()
        when (value) {
            is PreferenceValue.BoolValue -> writer.varint(if (value.value) 1 else 0, field = 1)
            is PreferenceValue.FloatValue -> writer.fixed32(value.value.toRawBits(), field = 2)
            is PreferenceValue.IntValue -> writer.varint(value.value.toLong(), field = 3)
            is PreferenceValue.LongValue -> writer.varint(value.value, field = 4)
            is PreferenceValue.StringValue -> writer.string(value.value, field = 5)
            is PreferenceValue.StringSetValue -> {
                val set = ProtoWriter()
                for (text in value.value) set.string(text, field = 1)
                writer.bytes(set.toByteArray(), field = 6)
            }
            is PreferenceValue.DoubleValue -> writer.fixed64(value.value.toRawBits(), field = 7)
            is PreferenceValue.BytesValue -> writer.bytes(value.value, field = 8)
        }
        return writer.toByteArray()
    }

    private fun decodeValue(data: ByteArray): PreferenceValue? {
        val reader = ProtoReader(data)
        var value: PreferenceValue? = null
        while (true) {
            val (field, wire) = reader.nextTag() ?: break
            value = when {
                field == 1 && wire == VARINT -> PreferenceValue.BoolValue(reader.varint() != 0L)
                field == 2 && wire == FIXED32 -> PreferenceValue.FloatValue(Float.fromBits(reader.fixed(4).toInt()))
                field == 3 && wire == VARINT -> PreferenceValue.IntValue(reader.varint().toInt())
                field == 4 && wire == VARINT -> PreferenceValue.LongValue(reader.varint())
                field == 5 && wire == LENGTH_DELIMITED -> PreferenceValue.StringValue(reader.string())
                field == 6 && wire == LENGTH_DELIMITED -> {
                    val set = ProtoReader(reader.lengthDelimited())
                    val texts = mutableListOf<String>()
                    while (true) {
                        val (setField, setWire) = set.nextTag() ?: break
                        if (setField == 1 && setWire == LENGTH_DELIMITED) texts += set.string() else set.skip(setWire)
                    }
                    PreferenceValue.StringSetValue(texts)
                }
                field == 7 && wire == FIXED64 -> PreferenceValue.DoubleValue(Double.fromBits(reader.fixed(8)))
                field == 8 && wire == LENGTH_DELIMITED -> PreferenceValue.BytesValue(reader.lengthDelimited())
                else -> {
                    reader.skip(wire)
                    value
                }
            }
        }
        return value
    }

    // How a protobuf field is laid out.
    private const val VARINT = 0
    private const val FIXED64 = 1
    private const val LENGTH_DELIMITED = 2
    private const val START_GROUP = 3
    private const val END_GROUP = 4
    private const val FIXED32 = 5

    private class ProtoWriter {
        private val bytes = mutableListOf<Byte>()

        fun toByteArray(): ByteArray = bytes.toByteArray()

        fun varint(value: Long, field: Int) {
            tag(field, VARINT)
            rawVarint(value)
        }

        fun fixed32(value: Int, field: Int) {
            tag(field, FIXED32)
            for (index in 0 until 4) bytes += (value ushr (8 * index)).toByte()
        }

        fun fixed64(value: Long, field: Int) {
            tag(field, FIXED64)
            for (index in 0 until 8) bytes += (value ushr (8 * index)).toByte()
        }

        fun bytes(value: ByteArray, field: Int) {
            tag(field, LENGTH_DELIMITED)
            rawVarint(value.size.toLong())
            bytes += value.toList()
        }

        fun string(value: String, field: Int) = bytes(value.encodeToByteArray(), field)

        private fun tag(field: Int, wire: Int) = rawVarint((field.toLong() shl 3) or wire.toLong())

        private fun rawVarint(value: Long) {
            var rest = value
            while (rest and 0x7FL.inv() != 0L) {
                bytes += ((rest and 0x7F) or 0x80).toByte()
                rest = rest ushr 7
            }
            bytes += rest.toByte()
        }
    }

    private class ProtoReader(private val bytes: ByteArray) {
        private var index = 0

        /** The next field's number and layout, or null at the end. */
        fun nextTag(): Pair<Int, Int>? {
            if (index >= bytes.size) return null
            val key = varint()
            val wire = (key and 7).toInt()
            val field = key ushr 3
            if (wire > FIXED32 || field <= 0 || field > Int.MAX_VALUE) throw PreferencesFileException()
            return field.toInt() to wire
        }

        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (shift < 64) {
                if (index >= bytes.size) throw PreferencesFileException()
                val byte = bytes[index++].toInt() and 0xFF
                result = result or ((byte and 0x7F).toLong() shl shift)
                if (byte and 0x80 == 0) return result
                shift += 7
            }
            throw PreferencesFileException()
        }

        /** [count] bytes, little-endian. */
        fun fixed(count: Int): Long {
            if (count > bytes.size - index) throw PreferencesFileException()
            var value = 0L
            for (offset in 0 until count) value = value or ((bytes[index + offset].toLong() and 0xFF) shl (8 * offset))
            index += count
            return value
        }

        fun lengthDelimited(): ByteArray {
            val length = varint()
            if (length < 0 || length > bytes.size - index) throw PreferencesFileException()
            val end = index + length.toInt()
            return bytes.copyOfRange(index, end).also { index = end }
        }

        fun string(): String = try {
            lengthDelimited().decodeToString(throwOnInvalidSequence = true)
        } catch (error: CharacterCodingException) {
            throw PreferencesFileException()
        }

        fun skip(wire: Int) {
            when (wire) {
                VARINT -> varint()
                FIXED64 -> fixed(8)
                LENGTH_DELIMITED -> lengthDelimited()
                FIXED32 -> fixed(4)
                START_GROUP, END_GROUP -> throw PreferencesFileException()
                else -> throw PreferencesFileException()
            }
        }
    }
}
