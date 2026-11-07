package com.example.assistant

import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.security.SecureRandom
import javax.net.ssl.SSLSocketFactory
import kotlin.concurrent.thread

// Chhota sa WebSocket client (OkHttp ki jagah), taaki APK ka size na badhe.
class TinyWs(private val host: String, private val path: String, private val cb: Cb) {

    interface Cb {
        fun onOpen()
        fun onMessage(text: String)
        fun onClosed(code: Int, reason: String)
        fun onFailure(msg: String)
    }

    @Volatile private var sock: Socket? = null
    @Volatile private var out: OutputStream? = null
    @Volatile private var closed = false
    private val wlock = Any()
    private val rnd = SecureRandom()

    fun connect() {
        thread(name = "tiny-ws") {
            try {
                val s = SSLSocketFactory.getDefault().createSocket(host, 443)
                s.tcpNoDelay = true
                sock = s
                val o = s.getOutputStream()
                val i = s.getInputStream()
                val kb = ByteArray(16); rnd.nextBytes(kb)
                val key = android.util.Base64.encodeToString(kb, android.util.Base64.NO_WRAP)
                val req = "GET $path HTTP/1.1\r\nHost: $host\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\n\r\n"
                o.write(req.toByteArray()); o.flush()
                // jawab ki header padho
                val sb = StringBuilder()
                while (!sb.endsWith("\r\n\r\n")) {
                    val c = i.read()
                    if (c < 0) throw java.io.IOException("jawab nahi aaya")
                    sb.append(c.toChar())
                    if (sb.length > 8192) throw java.io.IOException("header bahut bada")
                }
                val first = sb.lineSequence().firstOrNull().orEmpty()
                if (!first.contains(" 101")) {
                    cb.onFailure(first.trim())
                    close0()
                    return@thread
                }
                out = o
                cb.onOpen()
                readLoop(i)
            } catch (e: Exception) {
                if (!closed) cb.onFailure(e.message ?: e.javaClass.simpleName)
                close0()
            }
        }
    }

    private fun readFully(i: InputStream, n: Int): ByteArray {
        val b = ByteArray(n)
        var r = 0
        while (r < n) {
            val k = i.read(b, r, n - r)
            if (k < 0) throw java.io.IOException("connection band")
            r += k
        }
        return b
    }

    private fun readLoop(i: InputStream) {
        val msg = java.io.ByteArrayOutputStream()
        var msgOp = 0
        while (!closed) {
            val h = readFully(i, 2)
            val fin = (h[0].toInt() and 0x80) != 0
            val op = h[0].toInt() and 0x0f
            var len = (h[1].toInt() and 0x7f).toLong()
            if (len == 126L) {
                val e = readFully(i, 2)
                len = (((e[0].toInt() and 0xff) shl 8) or (e[1].toInt() and 0xff)).toLong()
            } else if (len == 127L) {
                val e = readFully(i, 8)
                len = 0
                for (k in 0 until 8) len = (len shl 8) or (e[k].toLong() and 0xff)
            }
            val payload = readFully(i, len.toInt())
            when (op) {
                0, 1, 2 -> {
                    if (op != 0) { msg.reset(); msgOp = op }
                    msg.write(payload)
                    if (fin) {
                        cb.onMessage(String(msg.toByteArray(), Charsets.UTF_8))
                        msg.reset()
                    }
                }
                8 -> {
                    val code = if (payload.size >= 2) ((payload[0].toInt() and 0xff) shl 8) or (payload[1].toInt() and 0xff) else 1005
                    val reason = if (payload.size > 2) String(payload, 2, payload.size - 2, Charsets.UTF_8) else ""
                    closed = true
                    cb.onClosed(code, reason)
                    close0()
                    return
                }
                9 -> frame(10, payload)
                else -> {}
            }
        }
    }

    private fun frame(op: Int, data: ByteArray) {
        val o = out ?: return
        synchronized(wlock) {
            try {
                val n = data.size
                val hdr = java.io.ByteArrayOutputStream()
                hdr.write(0x80 or op)
                when {
                    n < 126 -> hdr.write(0x80 or n)
                    n < 65536 -> { hdr.write(0x80 or 126); hdr.write(n shr 8); hdr.write(n and 0xff) }
                    else -> { hdr.write(0x80 or 127); for (k in 7 downTo 0) hdr.write(((n.toLong() shr (8 * k)) and 0xff).toInt()) }
                }
                val mask = ByteArray(4); rnd.nextBytes(mask)
                hdr.write(mask)
                val m = ByteArray(n) { (data[it].toInt() xor mask[it % 4].toInt()).toByte() }
                hdr.write(m)
                o.write(hdr.toByteArray())
                o.flush()
            } catch (e: Exception) { }
        }
    }

    fun send(text: String): Boolean {
        if (closed || out == null) return false
        frame(1, text.toByteArray(Charsets.UTF_8))
        return true
    }

    fun close(code: Int = 1000, reason: String = "") {
        if (closed) return
        closed = true
        val r = reason.toByteArray()
        val p = ByteArray(2 + r.size)
        p[0] = (code shr 8).toByte(); p[1] = (code and 0xff).toByte()
        System.arraycopy(r, 0, p, 2, r.size)
        frame(8, p)
        close0()
    }

    private fun close0() {
        try { sock?.close() } catch (e: Exception) { }
    }
}
