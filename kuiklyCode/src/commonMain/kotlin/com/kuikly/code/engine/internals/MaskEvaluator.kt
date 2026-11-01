package com.kuikly.code.engine.internals

/**
 * QR 掩码惩罚分评估（ISO/IEC 18004 评价规则）
 *
 * 四类惩罚：
 * - N1：行/列连续同色 ≥5 模块，3 + (run-5) 分
 * - N2：2×2 同色块，每块 3 分
 * - N3：行/列出现类定位图案 1011101 + 一侧 0000，每处 40 分
 * - N4：深色占比偏离 50%，每 5% 记 10 分
 *
 * 分数越低越好。用于在 8 种掩码中自动选择最优（qrcode-kotlin / zxing 同款规则）。
 */
internal object MaskEvaluator {

    private const val N1 = 3
    private const val N2 = 3
    private const val N3 = 40
    private const val N4 = 10

    /** 对最终矩阵（含功能图形与格式信息）计算惩罚分 */
    fun penaltyScore(modules: Array<BooleanArray>): Int {
        val n = modules.size
        var score = 0

        // N1：行方向游程
        for (r in 0 until n) {
            var run = 1
            for (c in 1 until n) {
                if (modules[r][c] == modules[r][c - 1]) {
                    run++
                } else {
                    if (run >= 5) score += N1 + run - 5
                    run = 1
                }
            }
            if (run >= 5) score += N1 + run - 5
        }
        // N1：列方向游程
        for (c in 0 until n) {
            var run = 1
            for (r in 1 until n) {
                if (modules[r][c] == modules[r - 1][c]) {
                    run++
                } else {
                    if (run >= 5) score += N1 + run - 5
                    run = 1
                }
            }
            if (run >= 5) score += N1 + run - 5
        }

        // N2：2×2 同色块
        for (r in 0 until n - 1) {
            for (c in 0 until n - 1) {
                val v = modules[r][c]
                if (v == modules[r][c + 1] && v == modules[r + 1][c] && v == modules[r + 1][c + 1]) {
                    score += N2
                }
            }
        }

        // N3：类定位图案（行与列）
        for (r in 0 until n) {
            val row = toLineString(modules, r, vertical = false)
            score += countPattern(row, "10111010000") * N3
            score += countPattern(row, "00001011101") * N3
        }
        for (c in 0 until n) {
            val col = toLineString(modules, c, vertical = true)
            score += countPattern(col, "10111010000") * N3
            score += countPattern(col, "00001011101") * N3
        }

        // N4：深色占比
        val dark = modules.sumOf { row -> row.count { it } }
        val percent = dark * 100 / (n * n)
        score += (kotlin.math.abs(percent - 50) / 5) * N4

        return score
    }

    private fun toLineString(modules: Array<BooleanArray>, index: Int, vertical: Boolean): String {
        val sb = StringBuilder(modules.size)
        if (vertical) {
            for (r in modules.indices) sb.append(if (modules[r][index]) '1' else '0')
        } else {
            for (c in modules.indices) sb.append(if (modules[index][c]) '1' else '0')
        }
        return sb.toString()
    }

    /** 统计重叠出现次数 */
    private fun countPattern(line: String, pattern: String): Int {
        var count = 0
        var idx = line.indexOf(pattern)
        while (idx >= 0) {
            count++
            idx = line.indexOf(pattern, idx + 1)
        }
        return count
    }
}
