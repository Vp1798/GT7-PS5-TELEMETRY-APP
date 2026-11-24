package com.gt7.telemetry

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One decoded GT7 telemetry sample (own car only; GT7 sends nothing about other cars). */
class Telemetry(
    val rpm: Float = 0f,
    val speedMs: Float = 0f,
    val gear: Int = 0,
    val suggestedGear: Int = 15,
    val throttle: Float = 0f,   // 0..1
    val brake: Float = 0f,      // 0..1
    val fuel: Float = 0f,
    val fuelCap: Float = 100f,
    val boost: Float = 0f,
    val oilTemp: Float = 0f,
    val waterTemp: Float = 0f,
    val tyres: FloatArray = FloatArray(4), // FL, FR, RL, RR in degrees C
    val lap: Int = 0,
    val laps: Int = 0,
    val bestMs: Int = -1,
    val lastMs: Int = -1,
    val flags: Int = 0,
    val minAlertRpm: Int = 0,
    val maxAlertRpm: Int = 0,
    val packetId: Int = 0,
) {
    val speedKmh: Float get() = speedMs * 3.6f
    val onTrack: Boolean get() = flags and 1 != 0
    val paused: Boolean get() = flags and 2 != 0
    val loading: Boolean get() = flags and 4 != 0
    val revLimiter: Boolean get() = flags and 32 != 0
}

object Gt7Packet {
    private const val MAGIC = 0x47375330 // "0S7G" little-endian
    private val KEY: ByteArray = "Simulator Interface Packet GT7 ver 0.0".toByteArray(Charsets.US_ASCII).copyOf(32)
    const val SIZE = 296

    private fun le32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8) or
            ((b[o + 2].toInt() and 0xff) shl 16) or ((b[o + 3].toInt() and 0xff) shl 24)

    private fun put32(b: ByteArray, o: Int, v: Int) {
        b[o] = v.toByte(); b[o + 1] = (v shr 8).toByte(); b[o + 2] = (v shr 16).toByte(); b[o + 3] = (v shr 24).toByte()
    }

    /** Decrypts and parses a raw heartbeat-"A" packet. Returns null if it is not a valid GT7 packet. */
    fun decode(raw: ByteArray): Telemetry? {
        if (raw.size < SIZE) return null
        val iv1 = le32(raw, 0x40)
        val iv2 = iv1 xor 0xDEADBEAF.toInt()
        val nonce = ByteArray(8)
        put32(nonce, 0, iv2)
        put32(nonce, 4, iv1)
        val d = Salsa20.crypt(KEY, nonce, raw.copyOf(SIZE))
        val bb = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)
        if (bb.getInt(0) != MAGIC) return null
        val g = bb.get(0x90).toInt() and 0xff
        return Telemetry(
            rpm = bb.getFloat(0x3C),
            speedMs = bb.getFloat(0x4C),
            gear = g and 0x0f,
            suggestedGear = g shr 4,
            throttle = (bb.get(0x91).toInt() and 0xff) / 255f,
            brake = (bb.get(0x92).toInt() and 0xff) / 255f,
            fuel = bb.getFloat(0x44),
            fuelCap = bb.getFloat(0x48),
            boost = bb.getFloat(0x50) - 1f,
            oilTemp = bb.getFloat(0x5C),
            waterTemp = bb.getFloat(0x58),
            tyres = floatArrayOf(bb.getFloat(0x60), bb.getFloat(0x64), bb.getFloat(0x68), bb.getFloat(0x6C)),
            lap = bb.getShort(0x74).toInt(),
            laps = bb.getShort(0x76).toInt(),
            bestMs = bb.getInt(0x78),
            lastMs = bb.getInt(0x7C),
            flags = bb.getShort(0x8E).toInt() and 0xffff,
            minAlertRpm = bb.getShort(0x88).toInt() and 0xffff,
            maxAlertRpm = bb.getShort(0x8A).toInt() and 0xffff,
            packetId = bb.getInt(0x70),
        )
    }
}
