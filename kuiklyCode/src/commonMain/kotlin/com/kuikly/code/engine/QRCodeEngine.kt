package com.kuikly.code.engine

import com.kuikly.code.engine.internals.QRCodeSquare
import com.kuikly.code.engine.internals.QRCodeSquareType
import com.kuikly.code.engine.raw.ErrorCorrectionLevel
import com.kuikly.code.engine.raw.MaskPattern
import com.kuikly.code.engine.raw.QRCodeProcessor
import com.kuikly.code.engine.raw.QRCodeRawData

/**
 * QR 码引擎 - 对外门面类
 * 封装 QRCodeProcessor，提供简洁易用的 API
 *
 * 用法:
 * ```kotlin
 * // 简单使用
 * val matrix = QRCodeEngine.encode("Hello World")
 *
 * // 指定纠错级别
 * val matrix = QRCodeEngine.encode("Hello World", ErrorCorrectionLevel.HIGH)
 *
 * // 获取简单布尔矩阵
 * val boolMatrix = QRCodeEngine.encodeToBooleanMatrix("Hello World")
 * ```
 */
object QRCodeEngine {

    /**
     * 将文本编码为 QR 码矩阵（富元数据版本）
     * 每个方块包含类型信息（定位图案/校正图案/定时图案/数据），可用于差异化渲染
     *
     * @param text 要编码的文本
     * @param errorCorrectionLevel 纠错级别，默认 MEDIUM
     * @return 二维 QRCodeSquare 矩阵
     */
    fun encode(
        text: String,
        errorCorrectionLevel: ErrorCorrectionLevel = ErrorCorrectionLevel.MEDIUM,
    ): QRCodeRawData =
        encode(text, errorCorrectionLevel, maskPattern = null, minVersion = 1)

    /**
     * 带选项编码（0.2.0+）
     *
     * @param maskPattern 指定掩码；null = 自动选择惩罚分最低的最优掩码（ISO 18004 规则）
     * @param minVersion 版本下限（1-40），用于固定最小矩阵尺寸（防止文本变短时二维码跳变缩小）
     */
    fun encode(
        text: String,
        errorCorrectionLevel: ErrorCorrectionLevel,
        maskPattern: MaskPattern?,
        minVersion: Int = 1,
    ): QRCodeRawData {
        val processor = QRCodeProcessor(text, errorCorrectionLevel)
        return processor.encodeWithOptions(maskPattern, minVersion)
    }

    /**
     * 将文本编码为简单的布尔矩阵
     * true = 深色模块，false = 浅色模块
     *
     * @param text 要编码的文本
     * @param errorCorrectionLevel 纠错级别，默认 MEDIUM
     * @return 二维布尔数组
     */
    fun encodeToBooleanMatrix(
        text: String,
        errorCorrectionLevel: ErrorCorrectionLevel = ErrorCorrectionLevel.MEDIUM,
    ): Array<BooleanArray> {
        val rawData = encode(text, errorCorrectionLevel)
        return Array(rawData.size) { row ->
            BooleanArray(rawData[row].size) { col ->
                rawData[row][col].dark
            }
        }
    }

    /**
     * 获取矩阵尺寸（模块数）
     */
    fun getModuleCount(
        text: String,
        errorCorrectionLevel: ErrorCorrectionLevel = ErrorCorrectionLevel.MEDIUM,
    ): Int = getModuleCount(text, errorCorrectionLevel, minVersion = 1)

    /**
     * 获取矩阵尺寸（模块数，含版本下限）
     */
    fun getModuleCount(
        text: String,
        errorCorrectionLevel: ErrorCorrectionLevel = ErrorCorrectionLevel.MEDIUM,
        minVersion: Int,
    ): Int {
        val type = QRCodeProcessor.infoDensityForDataAndECL(text, errorCorrectionLevel)
        return maxOf(type, minVersion.coerceIn(1, QRCodeProcessor.MAXIMUM_INFO_DENSITY)) * 4 + 17
    }

    /**
     * 检查方块是否为定位图案（三个角的大方块）
     */
    fun isPositionProbe(square: QRCodeSquare): Boolean =
        square.squareInfo.type == QRCodeSquareType.POSITION_PROBE

    /**
     * 检查方块是否为校正图案
     */
    fun isPositionAdjust(square: QRCodeSquare): Boolean =
        square.squareInfo.type == QRCodeSquareType.POSITION_ADJUST

    /**
     * 检查方块是否为定时图案
     */
    fun isTimingPattern(square: QRCodeSquare): Boolean =
        square.squareInfo.type == QRCodeSquareType.TIMING_PATTERN

    /**
     * 检查方块是否为普通数据方块
     */
    fun isDataModule(square: QRCodeSquare): Boolean =
        square.squareInfo.type == QRCodeSquareType.DEFAULT
}
