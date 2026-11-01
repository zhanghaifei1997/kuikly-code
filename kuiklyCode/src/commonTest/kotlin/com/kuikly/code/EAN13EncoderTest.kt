package com.kuikly.code

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * EAN-13 编码器测试
 * L/G/R 表与首位奇偶表对照 zxing（UPCEANReader.L_PATTERNS / EAN13Reader.FIRST_DIGIT_ENCODINGS）
 */
class EAN13EncoderTest {

    /** zxing FIRST_DIGIT_ENCODINGS 位掩码（d3..d7 五位，G=1），独立校验首位奇偶表 */
    private val zxingFirstDigitMasks = intArrayOf(0x00, 0x0B, 0x0D, 0x0E, 0x13, 0x19, 0x1C, 0x15, 0x16, 0x1A)

    /** 由 L 的游程宽度反转得到位串（zxing G_PATTERNS 生成规则） */
    private fun reverseRuns(bits: String): String {
        val runs = mutableListOf<Int>()
        var count = 1
        for (i in 1 until bits.length) {
            if (bits[i] == bits[i - 1]) count++ else { runs.add(count); count = 1 }
        }
        runs.add(count)
        val sb = StringBuilder()
        var ch = '0'
        for (r in runs.reversed()) {
            repeat(r) { sb.append(ch) }
            ch = if (ch == '0') '1' else '0'
        }
        return sb.toString()
    }

    @Test
    fun tableShapes() {
        // 每个数字的 L/G/R 均为 7 模块
        for (d in 0..9) {
            assertEquals(7, EAN13Encoder.L_PATTERNS[d].length, "L[$d]")
            assertEquals(7, EAN13Encoder.G_PATTERNS[d].length, "G[$d]")
            assertEquals(7, EAN13Encoder.R_PATTERNS[d].length, "R[$d]")
        }
        // 首位奇偶表为 6 字符 L/G 串
        EAN13Encoder.FIRST_DIGIT_PARITY.forEach {
            assertEquals(6, it.length)
            assertTrue(it.all { c -> c == 'L' || c == 'G' })
        }
    }

    @Test
    fun gPatternIsReversedRunsOfL() {
        // zxing 规则：G[d] = L[d] 游程反转展开（起始位为 0）
        for (d in 0..9) {
            assertEquals(reverseRuns(EAN13Encoder.L_PATTERNS[d]), EAN13Encoder.G_PATTERNS[d], "G[$d]")
        }
    }

    @Test
    fun rPatternIsComplementOfL() {
        for (d in 0..9) {
            val complement = EAN13Encoder.L_PATTERNS[d].map { if (it == '1') '0' else '1' }.joinToString("")
            assertEquals(complement, EAN13Encoder.R_PATTERNS[d], "R[$d]")
        }
    }

    @Test
    fun firstDigitParityMatchesZxingMasks() {
        // zxing 掩码覆盖 d3..d7（串下标 1..5），d2 恒为 L（0-3）或 G（4-9）
        for (d in 0..9) {
            val parity = EAN13Encoder.FIRST_DIGIT_PARITY[d]
            var mask = 0
            for (i in 1 until 6) {
                mask = mask or (if (parity[i] == 'G') 1 shl (5 - i) else 0)
            }
            assertEquals(zxingFirstDigitMasks[d], mask, "parity[$d] vs zxing mask")
        }
    }

    @Test
    fun knownCheckDigitVectors() {
        // 经典向量：zxing 测试用码 5901234123457、联合利华 4006381333931、ISBN 前缀 978
        assertEquals(7, EAN13Encoder.calcCheckDigit("590123412345"))
        assertEquals(1, EAN13Encoder.calcCheckDigit("400638133393"))
        assertEquals(4, EAN13Encoder.calcCheckDigit("978020137962"))
    }

    @Test
    fun isValidChecks() {
        assertTrue(EAN13Encoder.isValid("5901234123457"))
        assertFalse(EAN13Encoder.isValid("5901234123458"))
        assertFalse(EAN13Encoder.isValid("59012341234"))   // 11 位
        assertFalse(EAN13Encoder.isValid("590123412345a"))  // 非数字
    }

    @Test
    fun encodeTwelveDigitsAppendsCheckDigit() {
        val from12 = EAN13Encoder.encode("590123412345")
        val from13 = EAN13Encoder.encode("5901234123457")
        assertContentEquals(from13, from12)
    }

    @Test
    fun encodeStructuralLayout() {
        val m = EAN13Encoder.encode("5901234123457")
        assertEquals(EAN13Encoder.MODULE_COUNT, m.size)
        assertEquals(95, m.size)
        // 左守护 101（模块 0-2）
        assertTrue(m[0] && !m[1] && m[2])
        // 中守护 01010（模块 45-49）
        assertFalse(m[45]); assertTrue(m[46]); assertFalse(m[47]); assertTrue(m[48]); assertFalse(m[49])
        // 右守护 101（模块 92-94）
        assertTrue(m[92] && !m[93] && m[94])
    }

    @Test
    fun firstLeftDigitUsesParityPattern() {
        // 首位 5 → LGGLLG；d2=9 走 L → 模块 3..9 应为 L_PATTERNS[9]
        val m = EAN13Encoder.encode("5901234123457")
        val expected = EAN13Encoder.L_PATTERNS[9]
        for (i in 0 until 7) {
            assertEquals(expected[i] == '1', m[3 + i], "module ${3 + i}")
        }
        // d3=0 走 G → 模块 10..16 应为 G_PATTERNS[0]
        val g0 = EAN13Encoder.G_PATTERNS[0]
        for (i in 0 until 7) {
            assertEquals(g0[i] == '1', m[10 + i], "module ${10 + i}")
        }
    }

    @Test
    fun invalidInputThrows() {
        assertFailsWith<IllegalArgumentException> { EAN13Encoder.encode("12345") }        // 长度错
        assertFailsWith<IllegalArgumentException> { EAN13Encoder.encode("590123412345a") } // 非数字
        assertFailsWith<IllegalArgumentException> { EAN13Encoder.encode("5901234123458") } // 校验位错
    }
}
