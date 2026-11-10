package com.example.assistant

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

// APK ke andar ki files aur code ko GitHub jaise folder / file mein dikhata hai.
// Dhyan: APK mein Java source nahi hota, sirf compile kiya hua code (dex). Isliye class ka dhancha (naam, fields, methods) dikhta hai.
class CNode(val name: String, val dir: Boolean, val size: Long)

class Dex(private val b: ByteBuffer) {
    private val strOff: Int; private val strN: Int
    private val typeOff: Int; private val typeN: Int
    private val protoOff: Int; private val fieldOff: Int; private val methOff: Int
    private val clsOff: Int; val clsN: Int
    private val strCache = HashMap<Int, String>()
    init {
        b.order(ByteOrder.LITTLE_ENDIAN)
        strN = b.getInt(56); strOff = b.getInt(60); typeN = b.getInt(64); typeOff = b.getInt(68)
        protoOff = b.getInt(76); fieldOff = b.getInt(84); methOff = b.getInt(92); clsN = b.getInt(96); clsOff = b.getInt(100)
    }
    private fun u(i: Int) = b.getInt(i)
    private fun uleb(pos: IntArray): Int {
        var r = 0; var s = 0
        while (true) { val x = b.get(pos[0]++).toInt() and 0xff; r = r or ((x and 0x7f) shl s); if (x and 0x80 == 0) break; s += 7 }
        return r
    }
    fun str(i: Int): String {
        if (i < 0 || i >= strN) return "?"
        return strCache.getOrPut(i) {
            val pos = intArrayOf(u(strOff + i * 4)); uleb(pos)
            val st = pos[0]; var e = st
            while (e < b.limit() && b.get(e).toInt() != 0) e++
            val a = ByteArray(e - st); for (k in a.indices) a[k] = b.get(st + k)
            String(a, Charsets.UTF_8)
        }
    }
    private fun type(i: Int): String = if (i < 0 || i >= typeN) "?" else pretty(str(u(typeOff + i * 4)))
    private fun pretty(d: String): String {
        var dims = 0; var s = d
        while (s.startsWith("[")) { dims++; s = s.drop(1) }
        val base = when (s) { "V" -> "void"; "Z" -> "boolean"; "B" -> "byte"; "S" -> "short"; "C" -> "char"; "I" -> "int"; "J" -> "long"; "F" -> "float"; "D" -> "double"
            else -> s.removePrefix("L").removeSuffix(";").replace('/', '.') }
        return base + "[]".repeat(dims)
    }
    fun className(ci: Int): String = type(u(clsOff + ci * 32))
    private fun proto(i: Int): String {
        val o = protoOff + i * 12
        val ret = type(u(o + 4)); val po = u(o + 8)
        val ps = ArrayList<String>()
        if (po != 0) { val n = u(po); for (k in 0 until n) ps.add(type(b.getShort(po + 4 + k * 2).toInt() and 0xffff)) }
        return "(" + ps.joinToString(", ") + "): " + ret
    }
    private fun mname(i: Int): String { val o = methOff + i * 8; return str(u(o + 4)) + proto(b.getShort(o + 2).toInt() and 0xffff) }
    private fun fname(i: Int): String { val o = fieldOff + i * 8; return str(u(o + 4)) + ": " + type(b.getShort(o + 2).toInt() and 0xffff) }
    private fun acc(a: Int): String {
        val l = ArrayList<String>()
        if (a and 1 != 0) l.add("public"); if (a and 2 != 0) l.add("private"); if (a and 4 != 0) l.add("protected")
        if (a and 8 != 0) l.add("static"); if (a and 0x10 != 0) l.add("final"); if (a and 0x400 != 0) l.add("abstract"); if (a and 0x100 != 0) l.add("native")
        return l.joinToString(" ")
    }
    fun describe(ci: Int): String {
        val o = clsOff + ci * 32
        val sb = StringBuilder()
        val sup = u(o + 8); val ifo = u(o + 12); val src = u(o + 16); val cd = u(o + 24)
        sb.append(acc(u(o + 4))).append(" class ").append(className(ci))
        if (sup != -1 && sup != 0xffffffff.toInt()) sb.append("\n  extends ").append(type(sup))
        if (ifo != 0) { val n = u(ifo); for (k in 0 until n) sb.append("\n  implements ").append(type(b.getShort(ifo + 4 + k * 2).toInt() and 0xffff)) }
        if (src != -1) sb.append("\n  source file: ").append(str(src))
        if (cd != 0) {
            val p = intArrayOf(cd)
            val sf = uleb(p); val inf = uleb(p); val dm = uleb(p); val vm = uleb(p)
            fun fields(n: Int, label: String) {
                var idx = 0
                if (n > 0) sb.append("\n\n  // ").append(label)
                for (k in 0 until n) { idx += uleb(p); val a = uleb(p); sb.append("\n  ").append(acc(a)).append(" ").append(fname(idx)) }
            }
            fun meths(n: Int, label: String) {
                var idx = 0
                if (n > 0) sb.append("\n\n  // ").append(label)
                for (k in 0 until n) { idx += uleb(p); val a = uleb(p); val code = uleb(p); sb.append("\n  ").append(acc(a)).append(" ").append(mname(idx)).append(if (code == 0) "" else "  {code}") }
            }
            fields(sf, "static fields"); fields(inf, "fields"); meths(dm, "direct methods (constructor / private / static)"); meths(vm, "methods")
        }
        return sb.toString()
    }
}

object Axml {
    fun isAxml(d: ByteArray) = d.size > 8 && d[0].toInt() == 3 && d[1].toInt() == 0 && d[2].toInt() == 8 && d[3].toInt() == 0
    fun decode(d: ByteArray): String {
        val b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 8
        var strs = arrayOf<String>()
        val sb = StringBuilder()
        var depth = 0
        fun s(i: Int) = if (i < 0 || i >= strs.size) "" else strs[i]
        while (pos + 8 <= d.size) {
            val type = b.getShort(pos).toInt() and 0xffff
            val size = b.getInt(pos + 4)
            if (size <= 0) break
            when (type) {
                0x0001 -> {
                    val n = b.getInt(pos + 8); val flags = b.getInt(pos + 16); val sOff = b.getInt(pos + 20)
                    val utf8 = flags and 0x100 != 0
                    val base = pos + sOff
                    strs = Array(n) { i ->
                        var p = base + b.getInt(pos + 28 + i * 4)
                        if (utf8) {
                            var l = b.get(p).toInt() and 0xff; p += if (l and 0x80 != 0) 2 else 1
                            l = b.get(p).toInt() and 0xff; var add = 1
                            if (l and 0x80 != 0) { l = ((l and 0x7f) shl 8) or (b.get(p + 1).toInt() and 0xff); add = 2 }
                            p += add; String(d, p, l, Charsets.UTF_8)
                        } else {
                            var l = b.getShort(p).toInt() and 0xffff; p += 2
                            if (l and 0x8000 != 0) { l = ((l and 0x7fff) shl 16) or (b.getShort(p).toInt() and 0xffff); p += 2 }
                            String(d, p, l * 2, Charsets.UTF_16LE)
                        }
                    }
                }
                0x0102 -> {
                    val name = s(b.getInt(pos + 20))
                    val cnt = b.getShort(pos + 28).toInt() and 0xffff
                    sb.append("  ".repeat(depth)).append("<").append(name)
                    for (i in 0 until cnt) {
                        val a = pos + 36 + i * 20
                        val ns = b.getInt(a); val an = s(b.getInt(a + 4)); val raw = b.getInt(a + 8)
                        val dt = b.get(a + 15).toInt() and 0xff; val data = b.getInt(a + 16)
                        val v = if (raw != -1) s(raw) else when (dt) {
                            3 -> s(data); 1 -> "@0x" + Integer.toHexString(data); 16 -> data.toString()
                            17 -> "0x" + Integer.toHexString(data); 18 -> if (data != 0) "true" else "false"
                            4 -> java.lang.Float.intBitsToFloat(data).toString(); else -> "0x" + Integer.toHexString(data)
                        }
                        sb.append("\n").append("  ".repeat(depth + 2)).append(if (ns != -1) "android:" else "").append(an).append("=\"").append(v).append("\"")
                    }
                    sb.append(">\n"); depth++
                }
                0x0103 -> { depth = maxOf(0, depth - 1); sb.append("  ".repeat(depth)).append("</").append(s(b.getInt(pos + 20))).append(">\n") }
                0x0104 -> { val t = s(b.getInt(pos + 16)).trim(); if (t.isNotEmpty()) sb.append("  ".repeat(depth)).append(t).append("\n") }
            }
            pos += size
        }
        return sb.toString()
    }
}

class ApkSource(private val apk: String) {
    private val names = ArrayList<Pair<String, Long>>()
    private val dexes = HashMap<String, Dex>()
    private val dexClasses = HashMap<String, List<Pair<String, Int>>>()   // dex -> (path/with/slash, index)

    init { ZipFile(apk).use { z -> for (e in z.entries()) if (!e.isDirectory) names.add(e.name to e.size) } }

    private fun isDex(n: String) = n.startsWith("classes") && n.endsWith(".dex")
    private fun dex(name: String): Dex? = dexes[name] ?: try {
        ZipFile(apk).use { z ->
            val e = z.getEntry(name) ?: return null
            val bytes = z.getInputStream(e).use { it.readBytes() }
            Dex(ByteBuffer.wrap(bytes)).also { dexes[name] = it }
        }
    } catch (e: Throwable) { null }

    private fun classes(name: String): List<Pair<String, Int>> = dexClasses.getOrPut(name) {
        val d = dex(name) ?: return@getOrPut emptyList()
        (0 until d.clsN).map { d.className(it).replace('.', '/') to it }.sortedBy { it.first }
    }

    fun list(path: String): List<CNode> {
        val seg = path.split("/").filter { it.isNotEmpty() }
        if (seg.isNotEmpty() && isDex(seg[0])) {
            val pre = seg.drop(1).joinToString("/")
            val out = LinkedHashMap<String, CNode>()
            for ((p, _) in classes(seg[0])) {
                if (pre.isNotEmpty() && !p.startsWith("$pre/")) continue
                val rest = if (pre.isEmpty()) p else p.substring(pre.length + 1)
                val first = rest.substringBefore("/")
                if (rest.contains("/")) out.putIfAbsent(first, CNode(first, true, 0)) else out.putIfAbsent(first, CNode(first, false, 0))
            }
            return out.values.sortedWith(compareBy({ !it.dir }, { it.name }))
        }
        val pre = if (path.isEmpty()) "" else "$path/"
        val out = LinkedHashMap<String, CNode>()
        for ((n, sz) in names) {
            if (!n.startsWith(pre)) continue
            val rest = n.substring(pre.length)
            val first = rest.substringBefore("/")
            if (rest.contains("/")) out.putIfAbsent(first, CNode(first, true, 0))
            else out[first] = CNode(first, isDex(n), sz)
        }
        return out.values.sortedWith(compareBy({ !it.dir }, { it.name }))
    }

    // file ka padhne layak text
    fun read(path: String): String {
        val seg = path.split("/").filter { it.isNotEmpty() }
        if (seg.isNotEmpty() && isDex(seg[0])) {
            val d = dex(seg[0]) ?: return "Dex padh nahi paya"
            val want = seg.drop(1).joinToString("/")
            val c = classes(seg[0]).firstOrNull { it.first == want } ?: return "Class nahi mili"
            return d.describe(c.second)
        }
        return try {
            ZipFile(apk).use { z ->
                val e = z.getEntry(path) ?: return "File nahi mili"
                if (e.size > 3_000_000L) return "File badi hai (${e.size / 1024} KB), yahan nahi dikhayi"
                val bytes = z.getInputStream(e).use { it.readBytes() }
                if (Axml.isAxml(bytes)) return Axml.decode(bytes)
                val n = minOf(bytes.size, 2000)
                var bad = 0
                for (i in 0 until n) { val x = bytes[i].toInt() and 0xff; if (x < 9 || (x in 14..31) ) bad++ }
                if (bad > n / 20) hex(bytes) else String(bytes, Charsets.UTF_8)
            }
        } catch (e: Throwable) { "Padh nahi paya: ${e.message}" }
    }

    private fun hex(b: ByteArray): String {
        val sb = StringBuilder("(binary file, ${b.size} bytes) pehle 1024 bytes:\n\n")
        for (i in 0 until minOf(1024, b.size) step 16) {
            sb.append("%06x  ".format(i))
            for (j in i until minOf(i + 16, b.size)) sb.append("%02x ".format(b[j].toInt() and 0xff))
            sb.append(" ")
            for (j in i until minOf(i + 16, b.size)) { val c = b[j].toInt() and 0xff; sb.append(if (c in 32..126) c.toChar() else '.') }
            sb.append("\n")
        }
        return sb.toString()
    }

    // saari code files zip mein: dex ka dhancha, manifest, res xml, text assets, asli classes.dex
    fun exportZip(out: OutputStream, onProgress: (String) -> Unit) {
        ZipOutputStream(out.buffered()).use { zo ->
            fun put(name: String, data: ByteArray) { zo.putNextEntry(ZipEntry(name)); zo.write(data); zo.closeEntry() }
            ZipFile(apk).use { z ->
                for ((n, sz) in names) {
                    val e = z.getEntry(n) ?: continue
                    if (isDex(n)) {
                        onProgress(n)
                        val raw = z.getInputStream(e).use { it.readBytes() }
                        put("raw/$n", raw)
                        val d = dex(n)
                        if (d != null) {
                            val sb = StringBuilder()
                            for (i in 0 until d.clsN) { sb.append(d.describe(i)).append("\n\n------------------------------\n\n") }
                            put("readable/${n}_classes.txt", sb.toString().toByteArray())
                        }
                    } else if (n == "AndroidManifest.xml" || (n.endsWith(".xml") && n.startsWith("res/")) || (n.startsWith("assets/") && sz < 1_000_000L && !n.endsWith(".so") && !n.endsWith(".apk") && !n.endsWith(".dex"))) {
                        val raw = z.getInputStream(e).use { it.readBytes() }
                        if (Axml.isAxml(raw)) put("readable/$n.txt", Axml.decode(raw).toByteArray())
                        else if (n.startsWith("assets/")) put(n, raw)
                    }
                }
            }
        }
    }

    companion object {
        // Downloads/AppCode mein file likhta hai, jagah ka naam lautata hai
        fun saveToDownloads(c: Context, name: String, mime: String, writer: (OutputStream) -> Unit): String {
            if (Build.VERSION.SDK_INT >= 29) {
                val v = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name); put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AppCode")
                }
                val uri = c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: throw RuntimeException("file nahi bani")
                c.contentResolver.openOutputStream(uri)!!.use(writer)
                return "Downloads/AppCode/$name"
            }
            val dir = File(c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "AppCode"); dir.mkdirs()
            File(dir, name).outputStream().use(writer)
            return "${dir.absolutePath}/$name"
        }
    }
}
