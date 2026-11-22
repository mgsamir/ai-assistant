package com.example.assistant

import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Apne hi app ke GitHub repo se file padhna/badalna. Hamesha ek alag "assistant-edits"
// branch par likhta hai, "main" par kabhi seedha nahi — user khud dekh kar merge karega.
object GitHub {
    private const val OWNER = "mgsamir"
    private const val REPO = "ai-assistant"
    private const val BRANCH = "assistant-edits"
    private const val BASE_BRANCH = "main"

    fun token(p: SharedPreferences): String = p.getString("gh_token", "") ?: ""

    private fun api(path: String, token: String, method: String = "GET", body: JSONObject? = null): Pair<Int, String> {
        val c = URL("https://api.github.com$path").openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 15000
        c.requestMethod = method
        c.setRequestProperty("Authorization", "Bearer $token")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        val code = c.responseCode
        val text = try { (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: "" } catch (e: Exception) { "" }
        return code to text
    }

    private fun encPath(path: String): String = path.trim('/').split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    // assistant-edits branch na ho to main se bana do
    private fun ensureBranch(token: String): String? {
        val (c1, t1) = api("/repos/$OWNER/$REPO/git/ref/heads/$BRANCH", token)
        if (c1 == 200) return null
        val (c2, t2) = api("/repos/$OWNER/$REPO/git/ref/heads/$BASE_BRANCH", token)
        if (c2 != 200) return "Repo ka \"$BASE_BRANCH\" branch nahi mila ($c2)"
        val sha = JSONObject(t2).optJSONObject("object")?.optString("sha") ?: return "Branch sha nahi mila"
        val (c3, t3) = api("/repos/$OWNER/$REPO/git/refs", token, "POST", JSONObject().put("ref", "refs/heads/$BRANCH").put("sha", sha))
        if (c3 !in 200..201) return "Naya branch nahi ban paya ($c3): " + t3.take(200)
        return null
    }

    class FileResult(val ok: Boolean, val text: String, val content: String = "", val sha: String = "")

    // Poori repo ki file list (taaki assistant ko pata chale kaunsi file me kya hai, naam poochna na pade)
    fun listFiles(p: SharedPreferences): FileResult {
        val token = token(p)
        if (token.isEmpty()) return FileResult(false, "GitHub token Settings mein daalo pehle.")
        val err = ensureBranch(token)
        if (err != null) return FileResult(false, err)
        val (code, body) = api("/repos/$OWNER/$REPO/git/trees/$BRANCH?recursive=1", token)
        if (code != 200) return FileResult(false, "Repo ki file list nahi mili ($code)")
        val arr = JSONObject(body).optJSONArray("tree") ?: return FileResult(false, "Repo khaali dikh raha hai")
        val paths = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("type") == "blob") paths.add(o.optString("path"))
        }
        return FileResult(true, paths.sorted().joinToString("\n"))
    }

    // Repo ke code mein koi shabd/function/variable dhoondo (file ka naam pata na ho tab)
    fun searchCode(p: SharedPreferences, query: String): FileResult {
        val token = token(p)
        if (token.isEmpty()) return FileResult(false, "GitHub token Settings mein daalo pehle.")
        val q = URLEncoder.encode("$query repo:$OWNER/$REPO", "UTF-8")
        val (code, body) = api("/search/code?q=$q&per_page=15", token)
        if (code != 200) return FileResult(false, "Search nahi ho paya ($code)")
        val items = JSONObject(body).optJSONArray("items")
        if (items == null || items.length() == 0) return FileResult(true, "\"$query\" se koi file nahi mili.")
        val paths = ArrayList<String>()
        for (i in 0 until items.length()) paths.add(items.getJSONObject(i).optString("path"))
        return FileResult(true, paths.joinToString("\n"))
    }

    fun readFile(p: SharedPreferences, path: String): FileResult {
        val token = token(p)
        if (token.isEmpty()) return FileResult(false, "GitHub token Settings mein daalo pehle.")
        val err = ensureBranch(token)
        if (err != null) return FileResult(false, err)
        val (code, body) = api("/repos/$OWNER/$REPO/contents/${encPath(path)}?ref=$BRANCH", token)
        if (code == 404) return FileResult(false, "File nahi mili: $path")
        if (code != 200) return FileResult(false, "GitHub se file nahi mil payi ($code)")
        val o = JSONObject(body)
        val raw = o.optString("content", "").replace("\n", "")
        val decoded = try { String(Base64.decode(raw, Base64.DEFAULT)) } catch (e: Exception) { return FileResult(false, "File padhi nahi gayi") }
        return FileResult(true, decoded, decoded, o.optString("sha"))
    }

    fun writeFile(p: SharedPreferences, path: String, newContent: String, message: String): FileResult {
        val token = token(p)
        if (token.isEmpty()) return FileResult(false, "GitHub token Settings mein daalo pehle.")
        val err = ensureBranch(token)
        if (err != null) return FileResult(false, err)
        val (c0, b0) = api("/repos/$OWNER/$REPO/contents/${encPath(path)}?ref=$BRANCH", token)
        val sha = if (c0 == 200) JSONObject(b0).optString("sha") else null
        val enc = Base64.encodeToString(newContent.toByteArray(), Base64.NO_WRAP)
        val body = JSONObject().put("message", message.ifBlank { "Assistant edit: $path" }).put("content", enc).put("branch", BRANCH)
        if (sha != null) body.put("sha", sha)
        val (code, resp) = api("/repos/$OWNER/$REPO/contents/${encPath(path)}", token, "PUT", body)
        if (code !in 200..201) return FileResult(false, "Save nahi hua ($code): " + resp.take(200))
        return FileResult(true, "\"$path\" \"$BRANCH\" branch par save ho gaya. GitHub par jaake dekh ke \"$BASE_BRANCH\" mein merge karna.")
    }
}
