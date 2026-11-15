package com.example.assistant

import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.core.xmlgen.ResContainer
import java.io.File

// Dusre ऐप ka code Java jaisa padhne layak (jadx) aur XML / resources.arsc ko saaf naamon ke saath
object JadxView {
    private var dec: JadxDecompiler? = null
    private var path: String? = null

    @Synchronized
    private fun open(apk: String): JadxDecompiler {
        val cur = dec
        if (cur != null && path == apk) return cur
        try { cur?.close() } catch (e: Throwable) { }
        dec = null
        val a = JadxArgs()
        a.setInputFile(File(apk))
        a.setSkipResources(false)
        a.setThreadsCount(2)
        a.setShowInconsistentCode(true)
        val d = JadxDecompiler(a)
        d.load()
        dec = d
        path = apk
        return d
    }

    @Synchronized
    fun javaOf(apk: String, dotted: String): String = try {
        val d = open(apk)
        val cls = d.searchJavaClassByOrigFullName(dotted)
        if (cls == null) "(Java mein ye class nahi mili: $dotted)"
        else cls.topParentClass.code
    } catch (e: Throwable) {
        "Java nahi ban paya (" + e.javaClass.simpleName + ": " + (e.message ?: "") + ")\nSmali dekho."
    }

    private fun collect(rc: ResContainer, sb: StringBuilder) {
        val t = try { rc.text?.codeStr } catch (e: Throwable) { null }
        if (!t.isNullOrBlank()) sb.append("==== ").append(rc.name).append(" ====\n").append(t).append("\n\n")
        for (s in rc.subFiles) collect(s, sb)
    }

    // ek resource file (XML ya resources.arsc) saaf naamon ke saath
    @Synchronized
    fun resourceOf(apk: String, name: String): String = try {
        val d = open(apk)
        val rf = d.resources.firstOrNull { it.originalName == name }
        if (rf == null) "(Resource nahi mila: $name)" else {
            val sb = StringBuilder()
            collect(rf.loadContent(), sb)
            if (sb.isEmpty()) "(khali ya padhne layak nahi)" else sb.toString()
        }
    } catch (e: Throwable) {
        "Decode nahi ho paya (" + e.javaClass.simpleName + ": " + (e.message ?: "") + ")"
    }

    // poore ऐप ka saara Java source (aur resources) folder mein
    fun exportAll(apk: String, out: File, onProgress: (Int) -> Unit) {
        close()
        val a = JadxArgs()
        a.setInputFile(File(apk))
        a.setOutDir(out)
        a.setSkipResources(false)
        a.setThreadsCount(2)
        a.setShowInconsistentCode(true)
        val d = JadxDecompiler(a)
        try {
            d.load()
            d.save(400) { done, total -> if (total > 0) onProgress(((done * 100) / total).toInt()) }
        } finally {
            try { d.close() } catch (e: Throwable) { }
        }
    }

    fun zipDir(dir: File, out: java.io.OutputStream) {
        java.util.zip.ZipOutputStream(out.buffered()).use { zo ->
            val base = dir.absolutePath.length + 1
            dir.walkTopDown().filter { it.isFile }.forEach { f ->
                zo.putNextEntry(java.util.zip.ZipEntry(f.absolutePath.substring(base)))
                f.inputStream().use { it.copyTo(zo) }
                zo.closeEntry()
            }
        }
    }

    @Synchronized
    fun close() { try { dec?.close() } catch (e: Throwable) { }; dec = null; path = null }
}
