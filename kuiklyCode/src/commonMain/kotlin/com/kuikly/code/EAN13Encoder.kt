package com.kuikly.code

/**
 * EAN-13 编码器（纯逻辑，无 UI 依赖），ISO/IEC 15420。
 *
 * 编码表对照 zxing `UPCEANReader.L_PATTERNS`（L = 起始空格的游程宽度展开；
 * G = L 游程反转；R = L 取反）与 `EAN13Reader.FIRST_DIGIT_ENCODINGS`（首位奇偶表）逐条校验。
 *
 * 布局（95 模块）：左守护 101 + 左 6 位×7 + 中守护 01010 + 右 6 位×7 + 右守护 101。
 * 首位数字不直接编码，由左侧 6 位的 L/G 奇偶组合表示。
 */
object EAN13Encoder {

    /** EAN-13 固定模块数（不含静区） */
    const val MODULE_COUNT = 95

    /** 左侧奇偶（L）编码表，索引=数字 */
    internal val L_PATTERNS = arrayOf(
        "0001101", // 0
        "0011001", // 1
        "0010011", // 2
        "0111101", // 3
        "0100011", // 4
        "0110001", // 5
        "0101111", // 6
        "0111011", // 7
        "0110111", // 8
        "0001011", // 9
    )

    /** 左侧偶编码（G）表：L 游程反转（zxing G_PATTERNS 同源） */
    internal val G_PATTERNS = arrayOf(
        "0100111", // 0
        "0110011", // 1
        "0011011", // 2
        "0100001", // 3
        "0011101", // 4
        "0111001", // 5
        "0000101", // 6
        "0010001", // 7
        "0001001", // 8
        "0010111", // 9
    )

    /** 右侧编码（R）表：L 取反 */
    internal val R_PATTERNS = arrayOf(
        "1110010", // 0
        "1100110", // 1
        "1101100", // 2
        "1000010", // 3
        "1011100", // 4
        "1001110", // 5
        "1010000", // 6
        "1000100", // 7
        "1001000", // 8
        "1110100", // 9
    )

    /** 首位数字 → 左侧 6 位（第 2~7 位）的 L/G 奇偶结构 */
    internal val FIRST_DIGIT_PARITY = arrayOf(
        "LLLLLL", // 0
        "LLGLGG", // 1
        "LLGGLG", // 2
        "LLGGGL", // 3
        "LGLLGG", // 4
        "LGGLLG", // 5
        "LGGGLL", // 6
        "LGLGLG", // 7
        "LGLGGL", // 8
        "LGGLGL", // 9
    )

    private const val LEFT_GUARD = "101"
    private const val MIDDLE_GUARD = "01010"
    private const val RIGHT_GUARD = "101"

    /**
     * 计算 EAN-13 校验位（第 13 位）
     * 规则：奇数位（第 1,3,5…位）×1，偶数位 ×3，校验位 = (10 - 和 % 10) % 10
     *
     * @param first12 前 12 位数字
     */
    fun calcCheckDigit(first12: String): Int {
        require(first12.length == 12 && first12.all { it in '0'..'9' }) {
            "EAN-13 需要 12 位数字计算校验位，收到: '$first12'"
        }
        var sum = 0
        first12.forEachIndexed { i, c ->
            val d = c - '0'
            sum += if (i % 2 == 0) d else d * 3   // i 从 0 起：第 1 位(索引0)×1
        }
        return (10 - sum % 10) % 10
    }

    /**
     * 校验 13 位 EAN 码校验位是否合法
     */
    fun isValid(text13: String): Boolean {
        if (text13.length != 13 || !text13.all { it in '0'..'9' }) return false
        return calcCheckDigit(text13.substring(0, 12)) == text13[12] - '0'
    }

    /**
     * 将 EAN-13 编码为 95 模块位数组（不含静区）
     *
     * @param text 12 位（自动补算校验位）或 13 位数字（校验位错误将抛异常）
     * @return true=黑条，false=白空
     * @throws IllegalArgumentException 非数字、长度不符或校验位错误
     */
    fun encode(text: String): BooleanArray {
        val digits = when {
            text.length == 12 && text.all { it in '0'..'9' } ->
                text + calcCheckDigit(text)
            text.length == 13 && text.all { it in '0'..'9' } -> {
                if (!isValid(text)) {
                    throw IllegalArgumentException("EAN-13 校验位错误: '$text'（期望 ${calcCheckDigit(text.substring(0, 12))}）")
                }
                text
            }
            else -> throw IllegalArgumentException("EAN-13 需要 12 或 13 位数字，收到: '$text'")
        }

        val sb = StringBuilder(MODULE_COUNT)
        sb.append(LEFT_GUARD)
        val parity = FIRST_DIGIT_PARITY[digits[0] - '0']
        for (i in 0 until 6) {
            val d = digits[i + 1] - '0'
            sb.append(if (parity[i] == 'L') L_PATTERNS[d] else G_PATTERNS[d])
        }
        sb.append(MIDDLE_GUARD)
        for (i in 7 until 13) {
            sb.append(R_PATTERNS[digits[i] - '0'])
        }
        sb.append(RIGHT_GUARD)

        require(sb.length == MODULE_COUNT)
        return BooleanArray(MODULE_COUNT) { sb[it] == '1' }
    }
}
