package com.kuikly.code.engine.raw

import com.kuikly.code.engine.internals.*
import com.kuikly.code.engine.internals.QRCodeSetup.applyMaskPattern
import com.kuikly.code.engine.internals.QRCodeSetup.setupBottomLeftPositionProbePattern
import com.kuikly.code.engine.internals.QRCodeSetup.setupPositionAdjustPattern
import com.kuikly.code.engine.internals.QRCodeSetup.setupTimingPattern
import com.kuikly.code.engine.internals.QRCodeSetup.setupTopLeftPositionProbePattern
import com.kuikly.code.engine.internals.QRCodeSetup.setupTopRightPositionProbePattern
import com.kuikly.code.engine.internals.QRCodeSetup.setupTypeInfo
import com.kuikly.code.engine.internals.QRCodeSetup.setupTypeNumber
import com.kuikly.code.engine.raw.QRCodeDataType.*

/**
 * QR 码处理器 (仅编码逻辑，不含渲染)
 * 基于 qrcode-kotlin (MIT License) by Rafael Lins & Kazuhiko Arase
 *
 * 用法:
 * ```kotlin
 * val processor = QRCodeProcessor("Hello World")
 * val rawData = processor.encode()
 * // rawData 是 Array<Array<QRCodeSquare>>，每个元素包含位置、类型、是否深色等元数据
 * ```
 */
class QRCodeProcessor(
    private val data: String,
    private val errorCorrectionLevel: ErrorCorrectionLevel = ErrorCorrectionLevel.MEDIUM,
    private val dataType: QRCodeDataType = QRUtil.getDataType(data),
) {
    private val qrCodeData: QRData = when (dataType) {
        NUMBERS -> QRNumber(data)
        UPPER_ALPHA_NUM -> QRAlphaNum(data)
        DEFAULT -> QR8BitByte(data)
    }

    companion object {
        const val DEFAULT_CELL_SIZE = 25
        private const val PAD0 = 0xEC
        private const val PAD1 = 0x11
        const val MAXIMUM_INFO_DENSITY = 40

        /**
         * 自动推断给定数据在指定纠错级别下所需的最小信息密度参数
         */
        fun infoDensityForDataAndECL(
            data: String,
            errorCorrectionLevel: ErrorCorrectionLevel,
            dataType: QRCodeDataType = QRUtil.getDataType(data),
        ): Int {
            val qrCodeData = when (dataType) {
                NUMBERS -> QRNumber(data)
                UPPER_ALPHA_NUM -> QRAlphaNum(data)
                DEFAULT -> QR8BitByte(data)
            }
            val dataLength = qrCodeData.length()
            for (typeNum in 1 until errorCorrectionLevel.maxTypeNum) {
                if (dataLength <= QRUtil.getMaxLength(typeNum, dataType, errorCorrectionLevel)) {
                    return typeNum
                }
            }
            return MAXIMUM_INFO_DENSITY
        }
    }

    /**
     * 计算 QR 码图像最终尺寸
     */
    fun computeImageSize(cellSize: Int = DEFAULT_CELL_SIZE, rawData: QRCodeRawData = encode()): Int =
        rawData.size * cellSize

    /**
     * 编码数据为 QR 码矩阵（核心方法）
     *
     * @param type 信息密度参数 (1-40)
     * @param maskPattern 掩码模式
     * @return 二维 QRCodeSquare 矩阵，包含每个方块的完整元数据
     */
    fun encode(
        type: Int = infoDensityForDataAndECL(data, errorCorrectionLevel),
        maskPattern: MaskPattern = MaskPattern.PATTERN000,
    ): QRCodeRawData {        val moduleCount = type * 4 + 17
        val modules: Array<Array<QRCodeSquare?>> = Array(moduleCount) { Array(moduleCount) { null } }

        setupTopLeftPositionProbePattern(modules)
        setupTopRightPositionProbePattern(modules)
        setupBottomLeftPositionProbePattern(modules)
        setupPositionAdjustPattern(type, modules)
        setupTimingPattern(moduleCount, modules)
        setupTypeInfo(errorCorrectionLevel, maskPattern, moduleCount, modules)

        if (type >= 7) {
            setupTypeNumber(type, moduleCount, modules)
        }

        val data = createData(type)
        applyMaskPattern(data, maskPattern, moduleCount, modules)

        return Array(moduleCount) { row ->
            Array(moduleCount) { column ->
                modules[row][column] ?: QRCodeSquare(false, row, column, moduleCount)
            }
        }
    }

    /**
     * 带选项编码：自动最优掩码 + 版本下限
     *
     * @param maskPattern 指定掩码；null = 在 8 种掩码中自动选择惩罚分最低者
     * @param minVersion 版本下限（1-40），防止短文本时矩阵尺寸过小/跳变
     */
    fun encodeWithOptions(
        maskPattern: MaskPattern? = null,
        minVersion: Int = 1,
    ): QRCodeRawData {
        val autoType = infoDensityForDataAndECL(data, errorCorrectionLevel)
        val type = maxOf(autoType, minVersion.coerceIn(1, MAXIMUM_INFO_DENSITY))
        return if (maskPattern != null) {
            encode(type, maskPattern)
        } else {
            encode(type, bestMaskPattern(type))
        }
    }

    /**
     * 按 ISO 惩罚分规则在 8 种掩码中选择最优者（分数最低）
     */
    fun bestMaskPattern(type: Int): MaskPattern {
        var best = MaskPattern.PATTERN000
        var bestScore = Int.MAX_VALUE
        for (mask in MaskPattern.entries) {
            val score = MaskEvaluator.penaltyScore(toBooleanMatrix(encode(type, mask)))
            if (score < bestScore) {
                bestScore = score
                best = mask
            }
        }
        return best
    }

    private fun toBooleanMatrix(data: QRCodeRawData): Array<BooleanArray> =
        Array(data.size) { r -> BooleanArray(data[r].size) { c -> data[r][c].dark } }

    private fun createData(type: Int): IntArray {
        val rsBlocks = RSBlock.getRSBlocks(type, errorCorrectionLevel)
        val buffer = BitBuffer()

        buffer.put(qrCodeData.dataType.value, 4)
        buffer.put(qrCodeData.length(), qrCodeData.getLengthInBits(type))
        qrCodeData.write(buffer)

        val totalDataCount = rsBlocks.sumOf { it.dataCount } * 8

        if (buffer.lengthInBits > totalDataCount) {
            throw IllegalArgumentException(
                "数据过长: type=$type [需要=${buffer.lengthInBits}bit, 最大=${totalDataCount}bit]"
            )
        }

        if (buffer.lengthInBits + 4 <= totalDataCount) {
            buffer.put(0, 4)
        }
        while (buffer.lengthInBits % 8 != 0) buffer.put(false)
        while (true) {
            if (buffer.lengthInBits >= totalDataCount) break
            buffer.put(PAD0, 8)
            if (buffer.lengthInBits >= totalDataCount) break
            buffer.put(PAD1, 8)
        }

        return createBytes(buffer, rsBlocks)
    }

    private fun createBytes(buffer: BitBuffer, rsBlocks: Array<RSBlock>): IntArray {
        var offset = 0
        var maxDcCount = 0
        var maxEcCount = 0
        var totalCodeCount = 0
        val dcData = Array(rsBlocks.size) { IntArray(0) }
        val ecData = Array(rsBlocks.size) { IntArray(0) }

        rsBlocks.forEachIndexed { i, it ->
            val dcCount = it.dataCount
            val ecCount = it.totalCount - dcCount
            totalCodeCount += it.totalCount
            maxDcCount = maxDcCount.coerceAtLeast(dcCount)
            maxEcCount = maxEcCount.coerceAtLeast(ecCount)

            dcData[i] = IntArray(dcCount) { idx -> 0xff and buffer.buffer[idx + offset] }
            offset += dcCount

            val rsPoly = QRUtil.getErrorCorrectPolynomial(ecCount)
            val rawPoly = Polynomial(dcData[i], rsPoly.len() - 1)
            val modPoly = rawPoly.mod(rsPoly)
            val ecDataSize = rsPoly.len() - 1
            ecData[i] = IntArray(ecDataSize) { idx ->
                val modIndex = idx + modPoly.len() - ecDataSize
                if (modIndex >= 0) modPoly[modIndex] else 0
            }
        }

        var index = 0
        val data = IntArray(totalCodeCount)
        for (i in 0 until maxDcCount) {
            for (r in rsBlocks.indices) {
                if (i < dcData[r].size) data[index++] = dcData[r][i]
            }
        }
        for (i in 0 until maxEcCount) {
            for (r in rsBlocks.indices) {
                if (i < ecData[r].size) data[index++] = ecData[r][i]
            }
        }
        return data
    }
}
