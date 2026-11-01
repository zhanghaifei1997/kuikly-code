package com.kuikly.code.engine.internals

import com.kuikly.code.engine.raw.QRCodeDataType
import com.kuikly.code.engine.raw.QRCodeDataType.DEFAULT
import com.kuikly.code.engine.raw.QRCodeDataType.NUMBERS
import com.kuikly.code.engine.raw.QRCodeDataType.UPPER_ALPHA_NUM

/**
 * QR 码数据编码基类
 * 基于 qrcode-kotlin (MIT License) by Rafael Lins & Kazuhiko Arase
 */
internal abstract class QRData(val dataType: QRCodeDataType, val data: String) {
    abstract fun length(): Int
    abstract fun write(buffer: BitBuffer)

    fun getLengthInBits(type: Int): Int =
        when (type) {
            in 1..9 -> when (dataType) {
                NUMBERS -> 10
                UPPER_ALPHA_NUM -> 9
                DEFAULT -> 8
            }
            in 1..26 -> when (dataType) {
                NUMBERS -> 12
                UPPER_ALPHA_NUM -> 11
                DEFAULT -> 16
            }
            in 1..40 -> when (dataType) {
                NUMBERS -> 14
                UPPER_ALPHA_NUM -> 13
                DEFAULT -> 16
            }
            else -> throw IllegalArgumentException("'type' must be between 1 and 40: $type")
        }
}

/** 8-Bit Byte 编码 */
internal class QR8BitByte(data: String) : QRData(DEFAULT, data) {
    private val dataBytes = data.encodeToByteArray()

    override fun write(buffer: BitBuffer) {
        for (i in dataBytes.indices) {
            buffer.put(dataBytes[i].toInt(), 8)
        }
    }

    override fun length(): Int = dataBytes.size
}

/** 字母数字编码 */
internal class QRAlphaNum(data: String) : QRData(UPPER_ALPHA_NUM, data) {
    override fun write(buffer: BitBuffer) {
        var i = 0
        val dataLength = data.length
        while (i + 1 < dataLength) {
            buffer.put(charCode(data[i]) * 45 + charCode(data[i + 1]), 11)
            i += 2
        }
        if (i < dataLength) {
            buffer.put(charCode(data[i]), 6)
        }
    }

    override fun length(): Int = data.length

    private fun charCode(c: Char): Int =
        when (c) {
            in '0'..'9' -> c - '0'
            in 'A'..'Z' -> c - 'A' + 10
            else -> when (c) {
                ' ' -> 36; '$' -> 37; '%' -> 38; '*' -> 39
                '+' -> 40; '-' -> 41; '.' -> 42; '/' -> 43; ':' -> 44
                else -> throw IllegalArgumentException("Illegal character: $c")
            }
        }
}

/** 纯数字编码 */
internal class QRNumber(data: String) : QRData(NUMBERS, data) {
    override fun write(buffer: BitBuffer) {
        var i = 0
        val len = length()
        while (i + 2 < len) {
            buffer.put(data.substring(i, i + 3).toInt(), 10)
            i += 3
        }
        if (i < len) {
            if (len - i == 1) buffer.put(data.substring(i, i + 1).toInt(), 4)
            else if (len - i == 2) buffer.put(data.substring(i, i + 2).toInt(), 7)
        }
    }

    override fun length(): Int = data.length
}
