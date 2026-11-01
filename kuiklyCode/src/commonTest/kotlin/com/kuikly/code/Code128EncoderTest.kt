package com.kuikly.code

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Code 128-B 编码器测试
 * 参考表来自 zxing Code128Reader.CODE_PATTERNS（ISO/IEC 15417）
 *
 * 注意：测试方法名不可用反引号中文（JS 目标非法标识符）
 */
class Code128EncoderTest {

    /** zxing 参考编码表（value 0-106），独立于生产代码硬编码，防误改 */
    private val zxingReference = arrayOf(
        "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312", "132212", "221213",
        "221312", "231212", "112232", "122132", "122231", "113222", "123122", "123221", "223211", "221132",
        "221231", "213212", "223112", "312131", "311222", "321122", "321221", "312212", "322112", "322211",
        "212123", "212321", "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313",
        "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121", "313121", "211331",
        "231131", "213113", "213311", "213131", "311123", "311321", "331121", "312113", "312311", "332111",
        "314111", "221411", "431111", "111224", "111422", "121124", "121421", "141122", "141221", "112214",
        "112412", "122114", "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111",
        "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112", "421211", "212141",
        "214121", "412121", "111143", "111341", "131141", "114113", "114311", "411113", "411311", "113141",
        "114131", "311141", "411131", "211412", "211214", "211232", "2331112"
    )

    /** 独立于生产代码的展开逻辑：宽度表 → 条/空位数组 */
    private fun expand(vararg patterns: IntArray): BooleanArray {
        val out = mutableListOf<Boolean>()
        var isBar = true
        for (pattern in patterns) {
            isBar = true
            for (w in pattern) {
                repeat(w) { out.add(isBar) }
                isBar = !isBar
            }
        }
        return out.toBooleanArray()
    }

    private fun patternOf(vararg valueIndices: Int): Array<IntArray> =
        valueIndices.map { Code128Encoder.CODE_PATTERNS[it] }.toTypedArray()

    @Test
    fun patternTableMatchesZxingReference() {
        assertEquals(107, Code128Encoder.CODE_PATTERN_STRINGS.size)
        assertEquals(107, zxingReference.size)
        zxingReference.forEachIndexed { i, expected ->
            assertEquals(expected, Code128Encoder.CODE_PATTERN_STRINGS[i], "pattern[$i] 与 zxing 不一致")
        }
    }

    @Test
    fun patternTableStructuralInvariants() {
        for ((i, pattern) in Code128Encoder.CODE_PATTERNS.withIndex()) {
            val expectLen = if (i == Code128Encoder.CODE_STOP) 7 else 6
            assertEquals(expectLen, pattern.size, "pattern[$i] 元素数")
            val expectSum = if (i == Code128Encoder.CODE_STOP) 13 else 11
            assertEquals(expectSum, pattern.sum(), "pattern[$i] 宽度和")
        }
    }

    @Test
    fun encodeSingleCharAFullVector() {
        // Start B(104) + 'A'=value33 + 校验 (104+33*1)%103=34 + Stop(106)
        val expected = expand(*patternOf(104, 33, 34, 106))
        val actual = Code128Encoder.encode("A")
        assertContentEquals(expected, actual)
    }

    @Test
    fun checksumFormulaABC123() {
        // values = [33,34,35,13,17,18,19]
        // checksum = 104 + 33*1 + 34*2 + 35*3 + 13*4 + 17*5 + 18*6 + 19*7 = 688; 688 % 103 = 70
        val text = "ABC-123"
        var checksum = 104
        text.forEachIndexed { i, c -> checksum += (c.code - 32) * (i + 1) }
        assertEquals(70, checksum % 103)

        val expected = expand(*patternOf(104, 33, 34, 35, 13, 17, 18, 19, 70, 106))
        assertContentEquals(expected, Code128Encoder.encode(text))
    }

    @Test
    fun moduleCountFormula() {
        assertEquals(46, Code128Encoder.moduleCount("X"))                      // 1 字符: 11*(1+2)+13
        assertEquals(11 * 13 + 13, Code128Encoder.moduleCount("0123456789A"))  // 11 字符: 11*(11+2)+13
        assertEquals(11 * 12 + 13, Code128Encoder.moduleCount("0123456789"))   // 10 字符
        val encoded = Code128Encoder.encode("0123456789A")
        assertEquals(Code128Encoder.moduleCount("0123456789A"), encoded.size)
    }

    @Test
    fun headModulesAreStartBPattern() {
        val encoded = Code128Encoder.encode("X")
        // Start B = 2 1 1 2 1 4 → 黑黑|白|黑|白白|黑|白白白白（共 11 模块）
        val expectedHead = booleanArrayOf(
            true, true, false, true, false, false, true, false, false, false, false
        )
        assertContentEquals(expectedHead, encoded.copyOf(11))
    }

    @Test
    fun illegalCharsThrow() {
        assertFailsWith<IllegalArgumentException> { Code128Encoder.encode("") }
        assertFailsWith<IllegalArgumentException> { Code128Encoder.encode("héllo") }  // é = U+00E9
        assertFailsWith<IllegalArgumentException> { Code128Encoder.encode("你好") }
        assertFailsWith<IllegalArgumentException> { Code128Encoder.encode("a\tb") }   // tab = ASCII 9
    }

    @Test
    fun mixedContentIsDeterministic() {
        val a = Code128Encoder.encode("MEMBER-2026-001")
        val b = Code128Encoder.encode("MEMBER-2026-001")
        assertContentEquals(a, b)
        assertTrue(a.any { it } && a.any { !it })
    }
}
