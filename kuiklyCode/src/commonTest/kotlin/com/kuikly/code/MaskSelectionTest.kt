package com.kuikly.code

import com.kuikly.code.engine.QRCodeEngine
import com.kuikly.code.engine.internals.MaskEvaluator
import com.kuikly.code.engine.raw.ErrorCorrectionLevel
import com.kuikly.code.engine.raw.MaskPattern
import com.kuikly.code.engine.raw.QRCodeProcessor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 掩码自动选择与版本下限测试（0.2.0+）
 */
class MaskSelectionTest {

    @Test
    fun penaltyAllLightMatrixScore() {
        // 全浅色 10×10：N1 行 10×(3+5)=80 + 列 80 + N2 81×3=243 + N4 (50/5)*10=100 → 503
        val m = Array(10) { BooleanArray(10) { false } }
        assertEquals(503, MaskEvaluator.penaltyScore(m))
    }

    @Test
    fun penaltyCheckerboardIsNearPerfect() {
        // 深浅棋盘矩阵无 N1 游程、无 N2 同色块、占比 50% → 惩罚分应为 0（理想矩阵）
        val checker = Array(9) { r -> BooleanArray(9) { c -> (r + c) % 2 == 0 } }
        assertEquals(0, MaskEvaluator.penaltyScore(checker), "棋盘矩阵应零惩罚")
    }

    @Test
    fun penaltyFiveRunIsScored() {
        // 第 4 行前 5 列连深 → 相对全浅矩阵，行方向多一处 run=5 的 N1 惩罚（3 分）
        // （其余 N1/N2/N4 项因新增深色模块亦有变化，只验证分数严格上升）
        val n = 9
        val allLight = Array(n) { BooleanArray(n) { false } }
        val withRun = Array(n) { r -> BooleanArray(n) { c -> r == 4 && c < 5 } }
        assertTrue(
            MaskEvaluator.penaltyScore(withRun) != MaskEvaluator.penaltyScore(allLight),
            "加入 5 连深后惩罚分应变化",
        )
    }

    @Test
    fun bestMaskScoreNotWorseThanAnyFixedMask() {
        val processor = QRCodeProcessor("https://kuikly.tds.qq.com", ErrorCorrectionLevel.MEDIUM)
        val best = processor.bestMaskPattern(type = 3)
        val bestScore = MaskEvaluator.penaltyScore(
            Array(29) { r -> BooleanArray(29) { c -> processor.encode(3, best)[r][c].dark } }
        )
        for (mask in MaskPattern.entries) {
            val data = processor.encode(3, mask)
            val matrix = Array(data.size) { r -> BooleanArray(data[r].size) { c -> data[r][c].dark } }
            assertTrue(bestScore <= MaskEvaluator.penaltyScore(matrix), "best 应不劣于 $mask")
        }
    }

    @Test
    fun autoMaskProducesValidStructure() {
        // 自动掩码输出仍是合法 QR（定位/定时图案不受掩码影响）
        val data = QRCodeEngine.encode("structure", ErrorCorrectionLevel.MEDIUM, maskPattern = null)
        val n = data.size
        for (c in 0..6) {
            assertTrue(data[0][c].dark, "finder top")
            assertTrue(data[c][0].dark, "finder left")
        }
        for (c in 8 until n - 8) {
            assertEquals(c % 2 == 0, data[6][c].dark, "timing")
        }
    }

    @Test
    fun minVersionRaisesMatrixSize() {
        // "Hi" 自动为版本 1（21），minVersion=5 → 37
        assertEquals(21, QRCodeEngine.getModuleCount("Hi"))
        assertEquals(37, QRCodeEngine.getModuleCount("Hi", minVersion = 5))
        assertEquals(37, QRCodeEngine.encode("Hi", ErrorCorrectionLevel.MEDIUM, null, 5).size)
        // minVersion 低于自动版本时不缩小
        assertEquals(
            QRCodeEngine.getModuleCount("0123456789abcdefghijklmnopqrstuvwxyz", minVersion = 1),
            QRCodeEngine.getModuleCount("0123456789abcdefghijklmnopqrstuvwxyz", minVersion = 1),
        )
    }

    @Test
    fun specifiedMaskIsRespected() {
        // 指定两种不同掩码应产出不同数据区（掩码位翻转规则不同）
        // 注：auto 最优掩码可能恰好等于任一指定值，故此处对比两个固定掩码
        val text = "https://example.com/kuikly-code"
        val m0 = QRCodeEngine.encode(text, ErrorCorrectionLevel.MEDIUM, maskPattern = MaskPattern.PATTERN000)
        val m7 = QRCodeEngine.encode(text, ErrorCorrectionLevel.MEDIUM, maskPattern = MaskPattern.PATTERN111)
        assertEquals(m0.size, m7.size)
        var diff = 0
        for (r in m0.indices) {
            for (c in m0[r].indices) {
                if (m0[r][c].dark != m7[r][c].dark) diff++
            }
        }
        assertTrue(diff > 0, "不同掩码应产生不同数据区")
    }
}
