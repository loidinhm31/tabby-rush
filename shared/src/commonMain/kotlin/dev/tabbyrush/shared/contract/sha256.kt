package dev.tabbyrush.shared.contract

object Sha256 {
    private val K = intArrayOf(
        0x428a2f98.toInt(), 0x71374491.toInt(), 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(), 0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(), 0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(), 0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(), 0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt()
    )

    fun sha256Hex(bytes: ByteArray): String {
        val hash = sha256Bytes(bytes)
        val sb = StringBuilder(64)
        for (b in hash) {
            val v = b.toInt() and 0xFF
            if (v < 16) sb.append('0')
            sb.append(v.toString(16))
        }
        return sb.toString()
    }

    fun sha256Bytes(bytes: ByteArray): ByteArray {
        var h0 = 0x6a09e667.toInt(); var h1 = 0xbb67ae85.toInt(); var h2 = 0x3c6ef372.toInt(); var h3 = 0xa54ff53a.toInt()
        var h4 = 0x510e527f.toInt(); var h5 = 0x9b05688c.toInt(); var h6 = 0x1f83d9ab.toInt(); var h7 = 0x5be0cd19.toInt()
        val bitLength = bytes.size.toLong() * 8L
        val padLen = ((56 - ((bytes.size + 1) % 64)) + 64) % 64
        val totalLen = bytes.size + 1 + padLen + 8
        val padded = ByteArray(totalLen)
        bytes.copyInto(padded, 0)
        padded[bytes.size] = 0x80.toByte()
        for (i in 7 downTo 0) padded[totalLen - 8 + (7 - i)] = ((bitLength ushr (i * 8)) and 0xFFL).toByte()

        val w = IntArray(64)
        val blockCount = totalLen / 64
        for (b in 0 until blockCount) {
            val off = b * 64
            for (t in 0 until 16) {
                val o = off + t * 4
                w[t] = ((padded[o].toInt() and 0xFF) shl 24) or ((padded[o + 1].toInt() and 0xFF) shl 16) or
                        ((padded[o + 2].toInt() and 0xFF) shl 8) or (padded[o + 3].toInt() and 0xFF)
            }
            for (t in 16 until 64) {
                val s0 = (w[t - 15] ushr 7 or (w[t - 15] shl 25)) xor (w[t - 15] ushr 18 or (w[t - 15] shl 14)) xor (w[t - 15] ushr 3)
                val s1 = (w[t - 2] ushr 17 or (w[t - 2] shl 15)) xor (w[t - 2] ushr 19 or (w[t - 2] shl 13)) xor (w[t - 2] ushr 10)
                w[t] = w[t - 16] + s0 + w[t - 7] + s1
            }

            var a = h0; var c1 = h1; var c2 = h2; var d = h3; var e = h4; var f = h5; var g = h6; var h = h7
            for (t in 0 until 64) {
                val s1 = (e ushr 6 or (e shl 26)) xor (e ushr 11 or (e shl 21)) xor (e ushr 25 or (e shl 7))
                val ch = (e and f) xor (e.inv() and g)
                val temp1 = h + s1 + ch + K[t] + w[t]
                val s0 = (a ushr 2 or (a shl 30)) xor (a ushr 13 or (a shl 19)) xor (a ushr 22 or (a shl 10))
                val maj = (a and c1) xor (a and c2) xor (c1 and c2)
                val temp2 = s0 + maj
                h = g; g = f; f = e; e = d + temp1; d = c2; c2 = c1; c1 = a; a = temp1 + temp2
            }
            h0 += a; h1 += c1; h2 += c2; h3 += d; h4 += e; h5 += f; h6 += g; h7 += h
        }

        val result = ByteArray(32)
        val hs = intArrayOf(h0, h1, h2, h3, h4, h5, h6, h7)
        for (i in 0 until 8) {
            val v = hs[i]
            result[i * 4] = (v ushr 24).toByte()
            result[i * 4 + 1] = (v ushr 16).toByte()
            result[i * 4 + 2] = (v ushr 8).toByte()
            result[i * 4 + 3] = v.toByte()
        }
        return result
    }
}
