package com.kuikly.code

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Code 128 自动 B/C 子集混合编码测试
 * 校验和手算依据：(start + Σ 第 i 个符号值×i) % 103，i 从 1 起（含切换符）
 */
class Code128AutoSubsetTest {

    private fun checksumOf(values: List<Int>): Int {
        var sum = values[0]
        for (i in 1 until values.size) sum += values[i] * i
        return sum % 103
    }

    /** 独立展开：值序列(含起始) + 校验 + 停止符 → 模块 */
    private fun expandValues(values: List<Int>): BooleanArray {
        val check = checksumOf(values)
        val all = values + listOf(check, Code128Encoder.CODE_STOP)
        val out = mutableListOf<Boolean>()
        for (v in all) {
            var isBar = true
            for (w in Code128Encoder.CODE_PATTERNS[v]) {
                repeat(w) { out.add(isBar) }
                isBar = !isBar
            }
        }
        return out.toBooleanArray()
    }

    @Test
    fun evenDigitsUsePureCodeC() {
        // "123456" → Start C + 12 34 56；checksum = (105+12+68+168)%103 = 44
        val expectedValues = listOf(Code128Encoder.CODE_START_C, 12, 34, 56)
        assertEquals(expectedValues, Code128Encoder.buildAutoValues("123456"))
        assertEquals(44, checksumOf(expectedValues))
        assertContentEquals(expandValues(expectedValues), Code128Encoder.encodeAuto("123456"))
    }

    @Test
    fun oddDigitsKeepFirstDigitInB() {
        // "1234567" → Start B + '1' + 切C + 23 45 67；checksum = (104+17+198+69+180+335)%103 = 79
        val expectedValues = listOf(
            Code128Encoder.CODE_START_B, 17, Code128Encoder.CODE_CODE_C, 23, 45, 67
        )
        assertEquals(expectedValues, Code128Encoder.buildAutoValues("1234567"))
        assertEquals(79, checksumOf(expectedValues))
        assertContentEquals(expandValues(expectedValues), Code128Encoder.encodeAuto("1234567"))
    }

    @Test
    fun mixedContentSwitchesToCForLongDigitRuns() {
        // "ABC123456DEF" → B(ABC) 切C(12 34 56) 切B(DEF)
        // checksum = (104 + 33×1+34×2+35×3 + 99×4+12×5+34×6+56×7 + 100×8+36×9+37×10+38×11) % 103
        //          = 3274 % 103 = 81
        val expectedValues = listOf(
            Code128Encoder.CODE_START_B, 33, 34, 35,
            Code128Encoder.CODE_CODE_C, 12, 34, 56,
            Code128Encoder.CODE_CODE_B, 36, 37, 38,
        )
        assertEquals(expectedValues, Code128Encoder.buildAutoValues("ABC123456DEF"))
        assertEquals(81, checksumOf(expectedValues))
        assertContentEquals(expandValues(expectedValues), Code128Encoder.encodeAuto("ABC123456DEF"))
    }

    @Test
    fun shortDigitRunsStayInB() {
        // "AB12"：数字游程 2 <4，全 B → [104, 33, 34, 17, 18]，checksum = 328%103 = 19
        val values = listOf(104, 33, 34, 17, 18)
        assertEquals(values, Code128Encoder.buildAutoValues("AB12"))
        assertEquals(19, checksumOf(values))
    }

    @Test
    fun threeDigitAllNumericStaysInB() {
        // 全数字但仅 3 位（<4），走混合路径且游程 3<4 → 纯 B
        val values = listOf(104, 17, 18, 19)
        assertEquals(values, Code128Encoder.buildAutoValues("123"))
    }

    @Test
    fun pureLetterContentAutoEqualsPlainB() {
        // 无数字时 auto 与纯 B 完全一致
        assertContentEquals(Code128Encoder.encode("ABC-XYZ"), Code128Encoder.encodeAuto("ABC-XYZ"))
    }

    @Test
    fun autoModeIsDenserForLongDigits() {
        // 纯 B 20 位数字：11*(20+2)+13 = 255 模块；auto（偶数 20 位全 C）：11*(1+10+1)+13 = 143
        val plain = Code128Encoder.encode("01234567890123456789")
        val auto = Code128Encoder.encodeAuto("01234567890123456789")
        assertEquals(255, plain.size)
        assertEquals(11 * 12 + 13, auto.size)
        assertTrue(auto.size < plain.size, "auto 应显著更密")
    }

    @Test
    fun autoModeModuleCountFormula() {
        // 符号数 S（含起始/切换）→ 总模块 11*(S+1)+13
        listOf(
            "123456" to 4,      // SC + 3 对
            "ABC123456DEF" to 12,
            "AB12" to 5,
        ).forEach { (text, symbolCount) ->
            val values = Code128Encoder.buildAutoValues(text)
            assertEquals(symbolCount, values.size, "symbols of '$text'")
            assertEquals(11 * (symbolCount + 1) + 13, Code128Encoder.encodeAuto(text).size)
        }
    }

    @Test
    fun autoModeIsDeterministic() {
        assertContentEquals(
            Code128Encoder.encodeAuto("MEMBER-2026-0001"),
            Code128Encoder.encodeAuto("MEMBER-2026-0001"),
        )
    }
}
