package com.kuikly.code.engine.raw

import com.kuikly.code.engine.internals.QRCodeSquare

/**
 * QR 码枚举定义
 * 基于 qrcode-kotlin (MIT License) by Rafael Lins & Kazuhiko Arase
 */

/** 纠错级别 */
enum class ErrorCorrectionLevel(val value: Int, val maxTypeNum: Int) {
    LOW(1, 21),
    MEDIUM(0, 25),
    HIGH(3, 30),
    VERY_HIGH(2, 34),
}

/** 掩码模式 */
enum class MaskPattern {
    PATTERN000, PATTERN001, PATTERN010, PATTERN011,
    PATTERN100, PATTERN101, PATTERN110, PATTERN111
}

/** 数据类型 */
enum class QRCodeDataType(val value: Int) {
    /** 纯数字 */
    NUMBERS(1 shl 0),
    /** 大写字母+数字 */
    UPPER_ALPHA_NUM(1 shl 1),
    /** 通用 8-bit 字节 */
    DEFAULT(1 shl 2)
}

/** 矩阵原始数据类型别名 */
typealias QRCodeRawData = Array<Array<QRCodeSquare>>
