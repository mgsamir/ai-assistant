package com.example.assistant

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

// GitHub Actions se naya APK banwana, uska status dekhna, download karke install prompt dikhana.
// Token wahi "gh_token" hai jo Settings mein daalte ho (fine-grained: Contents + Actions = Read and write).
object ApkUpdate {
    private const val OWNER = "mgsamir"
    private const val REPO = "ai-assistant"
    private const val WORKFLOW = "blank.yml"          // .github/workflows/ ki file ka naam
    private const val ARTIFACT = "my-assistant-apk"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun get(path: String, token: String): Pair<Int, String> {
        val r = Request.Builder().url("https://api.github.com$path")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28").build()
        http.newCall(r).execute().use { return it.code to (it.body?.string() ?: "") }
    }

    private fun parseTime(s: String): Long = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s)?.time ?: 0L
    } catch (e: Exception) { 0L }

    // 1) Workflow chalao (POST .../dispatches). Naye run ki id wapas deta hai, nahi mila to -1
    fun triggerGithubApkPatch(p: SharedPreferences, ref: String = "main", workflow: String = WORKFLOW): Long {
        val token = GitHub.token(p)
        if (token.isEmpty()) return -1
        val before = System.currentTimeMillis()
        val body = JSONObject().put("ref", ref).toString().toRequestBody("application/json".toMediaType())
        val r = Request.Builder().url("https://api.github.com/repos/$OWNER/$REPO/actions/workflows/$workflow/dispatches")
            .header("Authorization", "Bearer $token").header("Accept", "application/vnd.github+json")
            .post(body).build()
        http.newCall(r).execute().use { if (it.code != 204) return -1 }
        // dispatch run id nahi lautata, isliye abhi bana sabse naya run dhoondho
        repeat(10) {
            try { Thread.sleep(3000) } catch (e: Exception) { }
            val (c, b) = get("/repos/$OWNER/$REPO/actions/workflows/$workflow/runs?event=workflow_dispatch&branch=$ref&per_page=5", token)
            if (c == 200) {
                val arr = JSONObject(b).optJSONArray("workflow_runs")
                if (arr != null) for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (parseTime(o.optString("created_at")) >= before - 10_000) return o.optLong("id")
                }
            }
        }
        return -1
    }

    // 2) Run ka haal: ("queued"/"in_progress"/"completed", conclusion: "success"/"failure"/"")
    fun checkPatchStatus(p: SharedPreferences, runId: Long): Pair<String, String> {
        val token = GitHub.token(p)
        if (token.isEmpty()) return "unknown" to ""
        val (c, b) = get("/repos/$OWNER/$REPO/actions/runs/$runId", token)
        if (c != 200) return "unknown" to ""
        val o = JSONObject(b)
        return o.optString("status") to o.optString("conclusion")
    }

    // run poora hone tak intezaar (max ~6 min); true agar success
    fun waitUntilDone(p: SharedPreferences, runId: Long): Boolean {
        val end = System.currentTimeMillis() + 360_000
        while (System.currentTimeMillis() < end) {
            val (s, c) = checkPatchStatus(p, runId)
            if (s == "completed") return c == "success"
            try { Thread.sleep(8000) } catch (e: Exception) { }
        }
        return false
    }

    // main branch ka sabse naya safal build
    fun latestSuccessfulRunId(p: SharedPreferences): Long {
        val token = GitHub.token(p)
        if (token.isEmpty()) return -1
        val (c, b) = get("/repos/$OWNER/$REPO/actions/runs?branch=main&status=success&per_page=1", token)
        if (c != 200) return -1
        return JSONObject(b).optJSONArray("workflow_runs")?.optJSONObject(0)?.optLong("id") ?: -1
    }

    // 3) Artifact (zip) download -> APK nikalo -> install ka prompt. Galti ho to uska text, theek ho to null
    fun downloadAndInstallApk(ctx: Context, p: SharedPreferences, runId: Long): String? {
        val token = GitHub.token(p)
        if (token.isEmpty()) return "GitHub token Settings mein daalo pehle."
        try {
            val (c, b) = get("/repos/$OWNER/$REPO/actions/runs/$runId/artifacts", token)
            if (c != 200) return "Artifact list nahi mili ($c)"
            val arr = JSONObject(b).optJSONArray("artifacts")
            var id = 0L
            if (arr != null) for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("name") == ARTIFACT && !o.optBoolean("expired")) { id = o.optLong("id"); break }
            }
            if (id == 0L) return "Is run mein APK artifact nahi mila (ya expire ho gaya)"
            val file = File(ctx.cacheDir, "apk").apply { mkdirs() }.let { File(it, "update.apk") }
            file.delete()
            // OkHttp cross-host redirect par Authorization header khud hata deta hai, jo yahan chahiye
            val r = Request.Builder().url("https://api.github.com/repos/$OWNER/$REPO/actions/artifacts/$id/zip")
                .header("Authorization", "Bearer $token").header("Accept", "application/vnd.github+json").build()
            http.newCall(r).execute().use { resp ->
                if (!resp.isSuccessful) return "Download nahi hua (${resp.code})"
                ZipInputStream(resp.body!!.byteStream()).use { z ->
                    var e = z.nextEntry
                    while (e != null && !e.name.endsWith(".apk")) e = z.nextEntry
                    if (e == null) return "Zip mein APK nahi mila"
                    file.outputStream().use { z.copyTo(it) }
                }
            }
            if (!file.exists() || file.length() == 0L) return "APK khaali aayi"
            Handler(Looper.getMainLooper()).post { promptInstall(ctx) }
            return null
        } catch (e: Exception) {
            return "Download fail: " + (e.message ?: "error")
        }
    }

    private fun promptInstall(ctx: Context) {
        if (android.os.Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val uri = Uri.parse("content://com.example.assistant.apk/update.apk")
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.github+json".let { "application/vnd.android.package-archive" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

// Package installer ko downloaded APK padhne dene ke liye chhota provider (androidx ke bina)
class ApkProvider : ContentProvider() {
    private fun f(): File = File(File(context!!.cacheDir, "apk"), "update.apk")
    override fun onCreate() = true
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? = ParcelFileDescriptor.open(f(), ParcelFileDescriptor.MODE_READ_ONLY)
    override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sort: String?): Cursor {
        val c = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
        c.addRow(arrayOf("update.apk", f().length()))
        return c
    }
    override fun getType(uri: Uri) = "application/vnd.android.package-archive"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?) = 0
}
