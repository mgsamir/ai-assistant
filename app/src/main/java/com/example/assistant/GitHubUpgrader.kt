package com.example.assistant

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/** GitHub token encrypted with an Android Keystore AES-GCM key; never stores plaintext. */
class GitHubUpgrader(private val context: Context, private val prefs: SharedPreferences, private val upgrades: UpgradeManager) {
    data class Result(val ok: Boolean, val message: String, val prUrl: String = "")
    private val alias = "assistant.github.token.v1"
    private val owner = "mgsamir"; private val repo = "ai-assistant"

    fun saveToken(token: String): Boolean {
        if (!token.startsWith("github_pat_") || token.length < 30) return false
        return try {
            val nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(128, nonce))
            val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
            prefs.edit().putString("gh_token_cipher", Base64.encodeToString(nonce + encrypted, Base64.NO_WRAP)).remove("gh_token").apply()
            true
        } catch (_: Exception) { false }
    }
    fun hasToken(): Boolean = !readToken().isNullOrBlank()

    /** Approval only opens a PR with a user-provided patch. Never pushes to main or self-merges. */
    fun approveAndOpenPullRequest(id: String): Result {
        val token = readToken() ?: return Result(false, "GitHub token missing/invalid. Save a fine-grained token in secure settings first.")
        val proposal = upgrades.pending().firstOrNull { it.id == id } ?: return Result(false, "Pending upgrade request not found.")
        if (proposal.patchPath.isBlank() || proposal.patchContent.isBlank()) return Result(false, "This proposal has no reviewed file patch yet; no repository changes were made.")
        if (!validRepoPath(proposal.patchPath)) return Result(false, "Patch path must be a relative app source path; rejected.")
        return try {
            val base = api("/repos/$owner/$repo/git/ref/heads/main", token)
            if (base.first != 200) return Result(false, "Cannot read main branch (${base.first}). Check token Contents permission.")
            val sha = JSONObject(base.second).getJSONObject("object").getString("sha")
            val date = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
            val slug = proposal.title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(28).ifBlank { "proposal" }
            val branch = "upgrade-$date-$slug-${UUID.randomUUID().toString().take(6)}"
            val created = api("/repos/$owner/$repo/git/refs", token, "POST", JSONObject().put("ref", "refs/heads/$branch").put("sha", sha))
            if (created.first !in 200..201) return Result(false, "Could not create upgrade branch (${created.first}).")
            val encoded = Base64.encodeToString(proposal.patchContent.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val body = JSONObject().put("message", "Proposed upgrade: ${proposal.title.take(100)}").put("content", encoded).put("branch", branch)
            val put = api("/repos/$owner/$repo/contents/${encodePath(proposal.patchPath)}", token, "PUT", body)
            if (put.first !in 200..201) return Result(false, "Branch created but proposal commit failed (${put.first}); no main changes were made.")
            val prBody = "## Upgrade proposal\n\n**Reason:** ${proposal.reason}\n\n**What it does:** ${proposal.description}\n\n**Risk:** ${proposal.risk}\n\n**Location:** ${proposal.location}\n\n**Source:** ${proposal.source}\n\nGenerated from an explicitly approved request. Review before merging."
            val pr = api("/repos/$owner/$repo/pulls", token, "POST", JSONObject().put("title", "Upgrade: ${proposal.title.take(150)}").put("head", branch).put("base", "main").put("body", prBody))
            if (pr.first !in 200..201) return Result(false, "Change committed to $branch, but PR creation failed (${pr.first}); branch was not merged.")
            upgrades.approved(id)
            Result(true, "Upgrade branch and PR created. Merge remains manual.", JSONObject(pr.second).optString("html_url"))
        } catch (e: Exception) { Result(false, "GitHub request failed: ${e.javaClass.simpleName}") }
    }

    private fun readToken(): String? = try {
        val raw = Base64.decode(prefs.getString("gh_token_cipher", "") ?: return null, Base64.DEFAULT)
        if (raw.size < 29) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        cipher.doFinal(raw.copyOfRange(12, raw.size)).toString(Charsets.UTF_8).takeIf { it.startsWith("github_pat_") }
    } catch (_: Exception) { null }
    private fun key(): java.security.Key {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? java.security.Key)?.let { return it }
        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        generator.init(android.security.keystore.KeyGenParameterSpec.Builder(alias, android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }
    private fun validRepoPath(path: String) = !path.startsWith('/') && !path.contains("..") && !path.contains('\\') && path.startsWith("app/src/main/java/com/example/assistant/")
    private fun encodePath(path: String) = path.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
    private fun api(path: String, token: String, method: String = "GET", body: JSONObject? = null): Pair<Int, String> {
        val c = URL("https://api.github.com$path").openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 15000; c.requestMethod = method
        c.setRequestProperty("Authorization", "Bearer $token"); c.setRequestProperty("Accept", "application/vnd.github+json"); c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.outputStream.use { it.write(body.toString().toByteArray()) } }
        val code = c.responseCode
        return code to ((if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText().take(200_000) } ?: "")
    }
}
