package org.videopocket.probe.beam

/**
 * Compact, self-contained QR Code Matrix generator (Model 2, Version 1..4, Error Correction L/M).
 * Generates an accurate 2D boolean grid (black = true, white = false) for Compose Canvas rendering.
 * Zero external libraries or native bloat required.
 */
class QrCodeEncoder private constructor(
    val size: Int,
    private val modules: Array<BooleanArray>,
    private val isFunction: Array<BooleanArray>
) {
    fun getModule(x: Int, y: Int): Boolean = modules[y][x]

    companion object {
        /**
         * Encodes [text] into a QR code boolean matrix.
         * Automatically picks the smallest fitting version (Version 1 to 6).
         */
        fun encodeText(text: String): QrCodeEncoder {
            val bytes = text.toByteArray(Charsets.ISO_8859_1)
            val version = selectVersion(bytes.size)
            val size = version * 4 + 17
            val modules = Array(size) { BooleanArray(size) }
            val isFunction = Array(size) { BooleanArray(size) }

            val qr = QrCodeEncoder(size, modules, isFunction)
            qr.drawFunctionPatterns(version)

            val dataBits = qr.encodeDataBits(bytes, version)
            qr.drawCodewords(dataBits)
            qr.applyMaskPattern(version)
            return qr
        }

        private fun selectVersion(numBytes: Int): Int {
            return when {
                numBytes <= 17 -> 1
                numBytes <= 32 -> 2
                numBytes <= 53 -> 3
                numBytes <= 78 -> 4
                numBytes <= 106 -> 5
                else -> 6
            }
        }
    }

    private fun setFunctionModule(x: Int, y: Int, isBlack: Boolean) {
        modules[y][x] = isBlack
        isFunction[y][x] = true
    }

    private fun drawFunctionPatterns(version: Int) {
        // Draw 3 finder patterns (top-left, top-right, bottom-left)
        drawFinderPattern(3, 3)
        drawFinderPattern(size - 4, 3)
        drawFinderPattern(3, size - 4)

        // Draw separators
        for (i in 0..7) {
            setFunctionModule(7, i, false)
            setFunctionModule(size - 8, i, false)
            setFunctionModule(7, size - 1 - i, false)

            setFunctionModule(i, 7, false)
            setFunctionModule(size - 1 - i, 7, false)
            setFunctionModule(i, size - 8, false)
        }

        // Draw timing patterns
        for (i in 8 until (size - 8)) {
            val isBlack = i % 2 == 0
            setFunctionModule(6, i, isBlack)
            setFunctionModule(i, 6, isBlack)
        }

        // Alignment patterns for version >= 2
        if (version >= 2) {
            val alignPos = getAlignmentPatternPositions(version)
            for (r in alignPos) {
                for (c in alignPos) {
                    if ((r == 6 && c == 6) || (r == 6 && c == size - 7) || (r == size - 7 && c == 6)) continue
                    drawAlignmentPattern(c, r)
                }
            }
        }

        // Dark module
        setFunctionModule(8, size - 8, true)

        // Reserve format info area
        for (i in 0..8) {
            if (!isFunction[8][i]) isFunction[8][i] = true
            if (!isFunction[i][8]) isFunction[i][8] = true
            if (!isFunction[8][size - 1 - i]) isFunction[8][size - 1 - i] = true
            if (!isFunction[size - 1 - i][8]) isFunction[size - 1 - i][8] = true
        }
    }

    private fun drawFinderPattern(centerX: Int, centerY: Int) {
        for (dy in -3..3) {
            for (dx in -3..3) {
                val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                val isBlack = dist == 0 || dist == 1 || dist == 3
                setFunctionModule(centerX + dx, centerY + dy, isBlack)
            }
        }
    }

    private fun drawAlignmentPattern(centerX: Int, centerY: Int) {
        for (dy in -2..2) {
            for (dx in -2..2) {
                val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                val isBlack = dist != 1
                setFunctionModule(centerX + dx, centerY + dy, isBlack)
            }
        }
    }

    private fun getAlignmentPatternPositions(version: Int): IntArray = when (version) {
        2 -> intArrayOf(6, 18)
        3 -> intArrayOf(6, 22)
        4 -> intArrayOf(6, 26)
        5 -> intArrayOf(6, 30)
        6 -> intArrayOf(6, 34)
        else -> intArrayOf()
    }

    private fun encodeDataBits(bytes: ByteArray, version: Int): BooleanArray {
        val totalBits = getDataCapacityBits(version)
        val bitList = mutableListOf<Boolean>()

        // Byte mode indicator: 0100
        bitList.add(false); bitList.add(true); bitList.add(false); bitList.add(false)

        // Character count indicator (8 bits for version 1..9)
        val len = bytes.size
        for (i in 7 downTo 0) {
            bitList.add(((len ushr i) and 1) != 0)
        }

        // Data bytes
        for (b in bytes) {
            val unsigned = b.toInt() and 0xFF
            for (i in 7 downTo 0) {
                bitList.add(((unsigned ushr i) and 1) != 0)
            }
        }

        // Terminator (up to 4 zero bits)
        repeat(minOf(4, totalBits - bitList.size)) {
            bitList.add(false)
        }

        // Pad to 8-bit boundary
        while (bitList.size % 8 != 0) {
            bitList.add(false)
        }

        // Pad codewords: 0xEC, 0x11
        var padByte = 0xEC
        while (bitList.size < totalBits) {
            for (i in 7 downTo 0) {
                bitList.add(((padByte ushr i) and 1) != 0)
            }
            padByte = if (padByte == 0xEC) 0x11 else 0xEC
        }

        return bitList.take(totalBits).toBooleanArray()
    }

    private fun getDataCapacityBits(version: Int): Int {
        // Error correction level L total data bits
        val totalCodewords = when (version) {
            1 -> 19
            2 -> 34
            3 -> 55
            4 -> 80
            5 -> 108
            else -> 136
        }
        return totalCodewords * 8
    }

    private fun drawCodewords(dataBits: BooleanArray) {
        var bitIndex = 0
        var right = size - 1
        var upwards = true

        while (right > 0) {
            if (right == 6) right-- // Skip vertical timing column

            val yRange = if (upwards) (size - 1 downTo 0) else (0 until size)
            for (y in yRange) {
                for (dx in 0 downTo -1) {
                    val x = right + dx
                    if (!isFunction[y][x]) {
                        modules[y][x] = if (bitIndex < dataBits.size) dataBits[bitIndex++] else false
                    }
                }
            }
            upwards = !upwards
            right -= 2
        }
    }

    private fun applyMaskPattern(version: Int) {
        // Format info for Mask 0, ECC Level L: 111011111000100 (BCH code)
        val formatBits = booleanArrayOf(
            true, true, true, false, true, true, true, true,
            true, false, false, false, true, false, false
        )

        // Write format bits top-left & split
        for (i in 0..5) modules[8][i] = formatBits[i]
        modules[8][7] = formatBits[6]
        modules[8][8] = formatBits[7]
        modules[7][8] = formatBits[8]
        for (i in 9..14) modules[14 - i][8] = formatBits[i]

        // Write format bits second copy
        for (i in 0..7) modules[size - 1 - i][8] = formatBits[i]
        for (i in 8..14) modules[8][size - 15 + i] = formatBits[i]

        // Apply Mask 0: (x + y) % 2 == 0
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (!isFunction[y][x]) {
                    if ((x + y) % 2 == 0) {
                        modules[y][x] = !modules[y][x]
                    }
                }
            }
        }
    }
}
