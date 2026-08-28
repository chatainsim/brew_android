package fr.easter.brewhome.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
)

data class UpdateStatus(val updateAvailable: Boolean, val latestVersion: String?, val releaseUrl: String)

private const val REPO = "chatainsim/brew_android"

/** Vérifie s'il existe une release GitHub plus récente que [currentVersion]
 * (versionName local, ex. "1.78") - même principe et même comparaison semver
 * que check_app_version() côté serveur BrewHome (blueprints/admin.py), version
 * client léger : pas de cache TTL, un appel = un appel (utilisé sur l'ouverture
 * de l'écran Réglages, pas en tâche de fond). Ne lève jamais : un échec réseau
 * ou une réponse inattendue retombe silencieusement sur "pas de mise à jour
 * connue" plutôt que de faire planter l'écran Réglages.
 */
object UpdateChecker {
    val releasesUrl = "https://github.com/$REPO/releases"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(currentVersion: String): UpdateStatus = withContext(Dispatchers.IO) {
        val fallback = UpdateStatus(updateAvailable = false, latestVersion = null, releaseUrl = releasesUrl)
        runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$REPO/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "BrewHome-Android/$currentVersion")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use fallback
                val body = resp.body?.string() ?: return@use fallback
                val release = json.decodeFromString<GithubRelease>(body)
                val latest = release.tagName?.trim()?.removePrefix("v")
                UpdateStatus(
                    updateAvailable = latest != null && isNewer(latest, currentVersion),
                    latestVersion = latest,
                    releaseUrl = release.htmlUrl ?: releasesUrl,
                )
            }
        }.getOrDefault(fallback)
    }

    internal fun parseVersion(v: String): List<Int> =
        v.trim().removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }.ifEmpty { listOf(0) }

    /** Comparaison composant par composant façon semver (1.9 < 1.10), pas une
     * comparaison de chaînes qui classerait "1.9" après "1.10" par erreur. */
    internal fun isNewer(remote: String, local: String): Boolean {
        val r = parseVersion(remote)
        val l = parseVersion(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }
}
