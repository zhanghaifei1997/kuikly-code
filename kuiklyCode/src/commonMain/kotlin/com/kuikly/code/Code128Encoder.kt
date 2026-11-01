package com.kuikly.code

/**
 * Code 128-B 编码器（纯逻辑，无 UI 依赖）
 *
 * 条/空宽度编码表（values 0-106）已对照 zxing 的
 * `Code128Reader.CODE_PATTERNS`（ISO/IEC 15417 标准）逐条校验。
 *
 * 用法:
 * ```kotlin
 * val bars = Code128Encoder.encode("ABC123") // true=黑条, false=白空
 * ```
 */
object Code128Encoder {

    /** Code B 起始符 value（104，pattern 211214） */
    const val CODE_START_B = 104

    /** Code C 起始符 value（105，纯数字双密度模式） */
    const val CODE_START_C = 105

    /** 子集切换符：Code B → Code C（value 99） */
    const val CODE_CODE_C = 99

    /** 子集切换符：Code C → Code B（value 100） */
    const val CODE_CODE_B = 100

    /** 停止符 value（106，pattern 2331112，7 个元素） */
    const val CODE_STOP = 106

    /** Code B 可编码字符范围：ASCII 32(空格) ~ 126(~) */
    const val MIN_CHAR = 32
    const val MAX_CHAR = 126

    /**
     * 标准编码表（value 0..106），字符串 6 位（停止符 7 位），
     * 每位数字依次表示 条,空,条,空,条,空 的宽度单位
     */
    internal val CODE_PATTERN_STRINGS = arrayOf(
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

    /** 解析后的宽度表（107 条） */
    internal val CODE_PATTERNS: Array<IntArray> =
        CODE_PATTERN_STRINGS.map { s -> IntArray(s.length) { s[it] - '0' } }.toTypedArray()

    /**
     * 将文本编码为 Code 128-B 条/空模块序列
     *
     * 结构：Start B + 数据 + 校验符 + Stop
     *
     * @param text 仅支持 ASCII 32~126 的字符
     * @return 模块位数组，true=黑条，false=白空；总模块数恒为 11*(n+2)+13
     * @throws IllegalArgumentException 文本含 Code B 字符集之外的字符或为空
     */
    fun encode(text: String): BooleanArray {
        validateText(text)

        val bars = ArrayList<Boolean>(11 * (text.length + 2) + 13)

        // Start B
        addPattern(bars, CODE_PATTERNS[CODE_START_B])

        // 数据 + 校验和（Start 值 + Σ valueᵢ×位置ᵢ, 位置从 1 起, mod 103）
        var checksum = CODE_START_B
        text.forEachIndexed { i, c ->
            val codeValue = c.code - 32
            checksum += codeValue * (i + 1)
            addPattern(bars, CODE_PATTERNS[codeValue])
        }
        addPattern(bars, CODE_PATTERNS[checksum % 103])

        // Stop
        addPattern(bars, CODE_PATTERNS[CODE_STOP])

        return bars.toBooleanArray()
    }

    /**
     * 自动子集模式编码（0.2.0+）：数字段自动切换 Code C（两位一组，密度翻倍），
     * 其余字符走 Code B。规则与 zxing Code128Writer 的数字段策略一致：
     * - 全数字且偶数位 → Start C + 数字对
     * - 全数字且奇数位（≥3 位）→ 首位走 B，其余走 C
     * - 混合内容 → 默认 B，遇 ≥4 位连续数字切 C（奇数个时首数字留在 B），段间用 99/100 切换
     *
     * @param text 仅支持 ASCII 32~126
     * @return 模块位数组；总模块数 = 11×(符号数+2)+13，符号数含切换符
     */
    fun encodeAuto(text: String): BooleanArray {
        val values = buildAutoValues(text)
        return valuesToBars(values)
    }

    /**
     * 自动模式的符号值序列（含起始符与切换符，不含校验与停止符），供测试与调试
     */
    internal fun buildAutoValues(text: String): List<Int> {
        validateText(text)
        val out = mutableListOf<Int>()

        // 全数字特例
        if (text.length >= 4 && text.all { it in '0'..'9' }) {
            if (text.length % 2 == 0) {
                out += CODE_START_C
                var i = 0
                while (i < text.length) {
                    out += text.substring(i, i + 2).toInt()
                    i += 2
                }
            } else {
                // 首位留在 B，其余走 C
                out += CODE_START_B
                out += text[0].code - 32
                out += CODE_CODE_C
                var i = 1
                while (i < text.length) {
                    out += text.substring(i, i + 2).toInt()
                    i += 2
                }
            }
            return out
        }

        // 混合内容：默认 B，数字游程 ≥4 切 C
        out += CODE_START_B
        var i = 0
        while (i < text.length) {
            if (text[i] in '0'..'9') {
                var j = i
                while (j < text.length && text[j] in '0'..'9') j++
                val runLen = j - i
                if (runLen >= 4) {
                    var pairStart = i
                    if (runLen % 2 == 1) {
                        // 奇数个数字：首数字留在 B
                        out += text[i].code - 32
                        pairStart = i + 1
                    }
                    out += CODE_CODE_C
                    var k = pairStart
                    while (k < j) {
                        out += text.substring(k, k + 2).toInt()
                        k += 2
                    }
                    if (j < text.length) out += CODE_CODE_B
                    i = j
                } else {
                    out += text[i].code - 32
                    i++
                }
            } else {
                out += text[i].code - 32
                i++
            }
        }
        return out
    }

    /** 符号值序列 → 条/空模块（含校验与停止符） */
    private fun valuesToBars(values: List<Int>): BooleanArray {
        val bars = ArrayList<Boolean>(11 * (values.size + 1) + 13)

        // 校验和：起始值 + Σ 第 i 个数据符号值×i（i 从 1 起，含切换符）
        var checksum = values[0]
        for (i in 1 until values.size) {
            checksum += values[i] * i
        }

        addPattern(bars, CODE_PATTERNS[values[0]])
        for (i in 1 until values.size) {
            addPattern(bars, CODE_PATTERNS[values[i]])
        }
        addPattern(bars, CODE_PATTERNS[checksum % 103])
        addPattern(bars, CODE_PATTERNS[CODE_STOP])
        return bars.toBooleanArray()
    }

    /**
     * 编码后的总模块数（不含静区）：Start(11) + 数据×11 + 校验(11) + Stop(13)
     */
    fun moduleCount(text: String): Int = 11 * (text.length + 2) + 13

    private fun validateText(text: String) {
        if (text.isEmpty()) {
            throw IllegalArgumentException("Code 128: 文本不能为空")
        }
        for (c in text) {
            if (c.code < MIN_CHAR || c.code > MAX_CHAR) {
                throw IllegalArgumentException(
                    "Code 128-B 不支持字符 '$c' (ASCII ${c.code})，仅支持 ASCII $MIN_CHAR~$MAX_CHAR"
                )
            }
        }
    }

    private fun addPattern(bars: MutableList<Boolean>, pattern: IntArray) {
        var isBar = true
        for (width in pattern) {
            repeat(width) { bars.add(isBar) }
            isBar = !isBar
        }
    }
}
