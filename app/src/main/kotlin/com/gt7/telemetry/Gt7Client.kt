package com.gt7.telemetry

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/**
 * Talks to the PS5: sends a heartbeat byte to port 33739 and receives telemetry on port 33740.
 * The phone and the PS5 must be on the same network.
 */
class Gt7Client(
    private val ip: String,
    private val onPacket: (Telemetry) -> Unit,
    private val onStatus: (String) -> Unit,
) {
    @Volatile private var running = false
    @Volatile private var socket: DatagramSocket? = null
    private var thread: Thread? = null

    fun start() {
        running = true
        thread = Thread {
            try {
                val s = DatagramSocket(null)
                s.reuseAddress = true
                s.bind(InetSocketAddress(33740))
                s.soTimeout = 1000
                socket = s
                val addr = InetAddress.getByName(ip)
                val hb = "A".toByteArray(Charsets.US_ASCII)
                val buf = ByteArray(2048)
                var count = 0
                onStatus("Waiting for PS5…")
                s.send(DatagramPacket(hb, hb.size, addr, 33739))
                while (running) {
                    try {
                        val pkt = DatagramPacket(buf, buf.size)
                        s.receive(pkt)
                        count++
                        if (count % 100 == 0) s.send(DatagramPacket(hb, hb.size, addr, 33739))
                        val t = Gt7Packet.decode(buf.copyOf(pkt.length))
                        if (t != null) onPacket(t)
                    } catch (e: SocketTimeoutException) {
                        s.send(DatagramPacket(hb, hb.size, addr, 33739))
                        onStatus("Waiting for PS5… start a race or time trial")
                    }
                }
            } catch (e: Exception) {
                if (running) onStatus("Connection error: " + (e.message ?: e.javaClass.simpleName))
            } finally {
                socket?.close()
            }
        }.also {
            it.isDaemon = true
            it.start()
        }
    }

    fun stop() {
        running = false
        socket?.close()
    }
}
