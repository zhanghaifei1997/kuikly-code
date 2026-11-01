package com.kuikly.code.engine.internals

import com.kuikly.code.engine.internals.QRCodeSquareType.DEFAULT

/**
 * QR 码单个方块数据
 * 基于 qrcode-kotlin (MIT License) by Rafael Lins
 */
data class QRCodeSquare(
    /** 是否为深色方块 */
    var dark: Boolean,
    /** 行号 (从上到下) */
    val row: Int,
    /** 列号 (从左到右) */
    val col: Int,
    /** 整个 QR 码矩阵尺寸 */
    val moduleSize: Int,
    /** 方块类型信息 */
    val squareInfo: QRCodeSquareInfo = QRCodeSquareInfo(DEFAULT, QRCodeRegion.UNKNOWN),
    /** 此方块占几行 (>1 表示控制图案) */
    val rowSize: Int = 1,
    /** 此方块占几列 */
    val colSize: Int = 1,
    /** 如果是控制图案的子方块，指向父方块 */
    val parent: QRCodeSquare? = null,
) {
    var rendered: Boolean = false

    /** 计算此方块在图像中的 X 像素坐标 */
    fun absoluteX(cellSize: Int = 25): Int = col * cellSize

    /** 计算此方块在图像中的 Y 像素坐标 */
    fun absoluteY(cellSize: Int = 25): Int = row * cellSize

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as QRCodeSquare
        return row == other.row && col == other.col && rowSize == other.rowSize && colSize == other.colSize
    }

    override fun hashCode(): Int {
        var result = row
        result = 31 * result + col
        result = 31 * result + rowSize
        result = 31 * result + colSize
        return result
    }
}

/** 方块类型+区域信息 */
data class QRCodeSquareInfo(
    val type: QRCodeSquareType,
    val region: QRCodeRegion,
)

/** 方块类型枚举 */
enum class QRCodeSquareType {
    /** 定位图案（三个角的大方块） */
    POSITION_PROBE,
    /** 校正图案 */
    POSITION_ADJUST,
    /** 定时图案 */
    TIMING_PATTERN,
    /** 普通数据方块 */
    DEFAULT,
}

/** 方块在控制图案中的区域位置 */
enum class QRCodeRegion {
    TOP_LEFT_CORNER, TOP_RIGHT_CORNER, TOP_MID,
    LEFT_MID, RIGHT_MID, CENTER,
    BOTTOM_LEFT_CORNER, BOTTOM_RIGHT_CORNER, BOTTOM_MID,
    MARGIN, UNKNOWN
}
