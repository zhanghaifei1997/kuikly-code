package com.kuikly.code

import com.kuikly.code.engine.QRCodeEngine
import com.kuikly.code.engine.raw.ErrorCorrectionLevel
import com.kuikly.code.engine.raw.QRCodeRawData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * QR 引擎结构性验证（编码正确性由 qrcode-kotlin 血统保证，此处验证矩阵结构不变量）
 *
 * 注意：测试方法名不可用反引号中文（JS 目标非法标识符）
 */
class QRCodeEngineTest {

    @Test
    fun shortTextYieldsVersion1Matrix21x21() {
        val data = QRCodeEngine.encode("Hello")
        assertEquals(21, data.size)
        data.forEach { assertEquals(21, it.size) }
    }

    @Test
    fun getModuleCountMatchesEncodeSize() {
        listOf(
            "Hello" to ErrorCorrectionLevel.MEDIUM,
            "https://kuikly.tds.qq.com/QuickStart/hello-world.html" to ErrorCorrectionLevel.HIGH,
            "1234567890" to ErrorCorrectionLevel.LOW,
        ).forEach { (text, ecl) ->
            assertEquals(QRCodeEngine.getModuleCount(text, ecl), QRCodeEngine.encode(text, ecl).size)
        }
    }

    /** 校验定位图案（7×7：外圈全黑、中间白环、中心 3×3 黑） */
    private fun checkFinder(data: QRCodeRawData, rowOff: Int, colOff: Int) {
        // 上下边全黑
        for (c in 0..6) {
            assertTrue(data[rowOff][colOff + c].dark, "finder top row (${rowOff},${colOff + c})")
            assertTrue(data[rowOff + 6][colOff + c].dark, "finder bottom row (${rowOff + 6},${colOff + c})")
        }
        // 左右边全黑
        for (r in 0..6) {
            assertTrue(data[rowOff + r][colOff].dark, "finder left col (${rowOff + r},${colOff})")
            assertTrue(data[rowOff + r][colOff + 6].dark, "finder right col (${rowOff + r},${colOff + 6})")
        }
        // 白环（第 1/5 圈的非角位置）
        for (i in 1..5) {
            assertTrue(!data[rowOff + 1][colOff + i].dark, "finder white ring top")
            assertTrue(!data[rowOff + 5][colOff + i].dark, "finder white ring bottom")
            assertTrue(!data[rowOff + i][colOff + 1].dark, "finder white ring left")
            assertTrue(!data[rowOff + i][colOff + 5].dark, "finder white ring right")
        }
        // 中心 3×3 黑
        for (r in 2..4) {
            for (c in 2..4) {
                assertTrue(data[rowOff + r][colOff + c].dark, "finder center (${rowOff + r},${colOff + c})")
            }
        }
    }

    @Test
    fun finderPatternsAtThreeCorners() {
        val data = QRCodeEngine.encode("Hello")
        val n = data.size
        checkFinder(data, 0, 0)                    // 左上
        checkFinder(data, 0, n - 7)                // 右上
        checkFinder(data, n - 7, 0)                // 左下
    }

    @Test
    fun timingPatternAlternates() {
        val data = QRCodeEngine.encode("Hello")
        val n = data.size
        // 第 6 行/列在两侧定位图案之间交替，偶数位为深色
        for (c in 8 until n - 8) {
            assertEquals(c % 2 == 0, data[6][c].dark, "timing row6 col$c")
        }
        for (r in 8 until n - 8) {
            assertEquals(r % 2 == 0, data[r][6].dark, "timing col6 row$r")
        }
    }

    @Test
    fun encodingIsDeterministic() {
        val a = QRCodeEngine.encode("determinism", ErrorCorrectionLevel.HIGH)
        val b = QRCodeEngine.encode("determinism", ErrorCorrectionLevel.HIGH)
        assertEquals(a.size, b.size)
        for (r in a.indices) {
            for (c in a[r].indices) {
                assertEquals(a[r][c].dark, b[r][c].dark, "($r,$c)")
            }
        }
    }

    @Test
    fun differentECLYieldsDifferentMatrix() {
        val text = "https://example.com/kuikly"
        val m = QRCodeEngine.encode(text, ErrorCorrectionLevel.MEDIUM)
        val h = QRCodeEngine.encode(text, ErrorCorrectionLevel.HIGH)
        var diff = 0
        for (r in m.indices) {
            for (c in m[r].indices) {
                if (m[r][c].dark != h[r][c].dark) diff++
            }
        }
        assertTrue(diff > 0, "不同纠错级别应产生不同矩阵")
    }

    @Test
    fun booleanMatrixConsistentWithRichMatrix() {
        val text = "consistency"
        val rich = QRCodeEngine.encode(text)
        val bool = QRCodeEngine.encodeToBooleanMatrix(text)
        assertEquals(rich.size, bool.size)
        for (r in rich.indices) {
            for (c in rich[r].indices) {
                assertEquals(rich[r][c].dark, bool[r][c], "($r,$c)")
            }
        }
    }

    @Test
    fun utf8ChineseContentEncodes() {
        val data = QRCodeEngine.encode("美好超市会员码", ErrorCorrectionLevel.MEDIUM)
        val n = data.size
        assertTrue(n >= 21 && n <= 177, "尺寸在 21~177 之间")
        assertEquals(n, data[0].size, "矩阵为正方形")
    }

    @Test
    fun numericModeNotLargerThanByteMode() {
        val numeric = QRCodeEngine.encode("12345678901234567890")   // 20 位纯数字
        val bytes = QRCodeEngine.encode("abcdefghijklmnopqrst")     // 20 字节混合文本
        assertTrue(numeric.size <= bytes.size, "数字模式容量更大，尺寸应不大于字节模式")
    }
}
