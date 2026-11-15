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

    @Synchronized
    fun close() { try { dec?.close() } catch (e: Throwable) { }; dec = null; path = null }
}
