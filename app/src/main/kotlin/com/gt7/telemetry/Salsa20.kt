package com.gt7.telemetry

/** Salsa20/20 stream cipher (256-bit key, 64-bit nonce). Verified against ECRYPT test vectors via tools/salsa20.py. */
object Salsa20 {
    private fun rotl(v: Int, n: Int): Int = (v shl n) or (v ushr (32 - n))

    private fun le(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xff) or
            ((b[o + 1].toInt() and 0xff) shl 8) or
            ((b[o + 2].toInt() and 0xff) shl 16) or
            ((b[o + 3].toInt() and 0xff) shl 24)

    private fun qr(x: IntArray, a: Int, b: Int, c: Int, d: Int) {
        x[b] = x[b] xor rotl(x[a] + x[d], 7)
        x[c] = x[c] xor rotl(x[b] + x[a], 9)
        x[d] = x[d] xor rotl(x[c] + x[b], 13)
        x[a] = x[a] xor rotl(x[d] + x[c], 18)
    }

    fun crypt(key: ByteArray, nonce: ByteArray, data: ByteArray): ByteArray {
        require(key.size == 32 && nonce.size == 8)
        val out = ByteArray(data.size)
        val st = IntArray(16)
        st[0] = 0x61707865
        st[5] = 0x3320646e
        st[10] = 0x79622d32
        st[15] = 0x6b206574
        for (i in 0..3) {
            st[1 + i] = le(key, i * 4)
            st[11 + i] = le(key, 16 + i * 4)
        }
        st[6] = le(nonce, 0)
        st[7] = le(nonce, 4)

        val x = IntArray(16)
        var pos = 0
        var counter = 0L
        while (pos < data.size) {
            st[8] = counter.toInt()
            st[9] = (counter ushr 32).toInt()
            System.arraycopy(st, 0, x, 0, 16)
            for (r in 0 until 10) {
                qr(x, 0, 4, 8, 12); qr(x, 5, 9, 13, 1); qr(x, 10, 14, 2, 6); qr(x, 15, 3, 7, 11)
                qr(x, 0, 1, 2, 3); qr(x, 5, 6, 7, 4); qr(x, 10, 11, 8, 9); qr(x, 15, 12, 13, 14)
            }
            for (i in 0..15) x[i] += st[i]
            var i = 0
            while (i < 64 && pos < data.size) {
                val ks = (x[i shr 2] ushr ((i and 3) * 8)) and 0xff
                out[pos] = (data[pos].toInt() xor ks).toByte()
                pos++
                i++
            }
            counter++
        }
        return out
    }
}
