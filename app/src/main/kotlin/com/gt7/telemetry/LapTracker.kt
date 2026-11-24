package com.gt7.telemetry

/**
 * Derives live delta to best lap, current lap time and a laps-of-fuel estimate from the telemetry stream.
 * Delta compares elapsed time at the same distance travelled (integrated from speed) with the best lap's trace.
 */
class LapTracker {
    @Volatile var deltaSec: Double? = null     // + = slower than best lap, - = faster
    @Volatile var curLapSec: Double = 0.0
    @Volatile var lapsLeft: Float = -1f        // laps of fuel remaining, -1 = unknown

    private var lap = Int.MIN_VALUE
    private var lapStartNs = 0L
    private var lastNs = 0L
    private var dist = 0.0
    private var lastStoredDist = -100.0
    private var curD = ArrayList<Float>()
    private var curT = ArrayList<Float>()
    private var refD: FloatArray? = null
    private var refT: FloatArray? = null
    private var fuelAtLapStart = -1f
    private val burns = ArrayList<Float>()

    fun reset() {
        deltaSec = null; curLapSec = 0.0; lapsLeft = -1f
        lap = Int.MIN_VALUE; lapStartNs = 0L; lastNs = 0L; dist = 0.0; lastStoredDist = -100.0
        curD = ArrayList(); curT = ArrayList(); refD = null; refT = null
        fuelAtLapStart = -1f; burns.clear()
    }

    fun update(p: Telemetry, nowNs: Long) {
        if (p.paused || p.loading || !p.onTrack) {
            lastNs = nowNs
            return
        }
        if (p.lap != lap) onLapChange(p, nowNs)
        val dt = if (lastNs == 0L) 0.0 else (nowNs - lastNs) / 1e9
        lastNs = nowNs
        if (dt > 1.0) { // gap in data: trace is unreliable for this lap
            curD.clear(); curT.clear(); lastStoredDist = -100.0
        }
        dist += p.speedMs * dt
        val t = (nowNs - lapStartNs) / 1e9
        curLapSec = t
        if (dist - lastStoredDist >= 5.0) {
            curD.add(dist.toFloat()); curT.add(t.toFloat()); lastStoredDist = dist
        }
        deltaSec = computeDelta(dist, t)
        updateFuel(p)
    }

    private fun onLapChange(p: Telemetry, nowNs: Long) {
        val completed = lap != Int.MIN_VALUE && p.lap == lap + 1 && lap >= 1
        if (p.bestMs <= 0) { refD = null; refT = null }
        if (completed) {
            if (p.lastMs > 0 && p.lastMs == p.bestMs && curD.size > 10) {
                refD = curD.toFloatArray(); refT = curT.toFloatArray()
            }
            if (fuelAtLapStart >= 0f) {
                val burn = fuelAtLapStart - p.fuel
                if (burn > 0.01f) { burns.add(burn); if (burns.size > 3) burns.removeAt(0) }
            }
        }
        lap = p.lap
        lapStartNs = nowNs
        dist = 0.0
        lastStoredDist = -100.0
        curD = ArrayList(); curT = ArrayList()
        fuelAtLapStart = p.fuel
    }

    private fun computeDelta(d: Double, t: Double): Double? {
        val rd = refD ?: return null
        val rt = refT ?: return null
        if (rd.isEmpty() || d < rd[0] || d > rd[rd.size - 1]) return null
        var lo = 0
        var hi = rd.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (rd[mid] <= d) lo = mid else hi = mid
        }
        val span = rd[hi] - rd[lo]
        val f = if (span > 0f) ((d - rd[lo]) / span) else 0.0
        val refTime = rt[lo] + f * (rt[hi] - rt[lo])
        return t - refTime
    }

    private fun updateFuel(p: Telemetry) {
        if (burns.isEmpty()) { lapsLeft = -1f; return }
        val avg = burns.sum() / burns.size
        lapsLeft = if (avg > 0f) p.fuel / avg else -1f
    }
}
