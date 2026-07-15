package fr.easter.brewhome.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object Vitrine {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * URL GitHub Pages de la vitrine, à partir du réglage serveur
     * `gh_vitrine_targets` (même logique que openVitrineTab() dans bh-ui.js :
     * premier target GitHub — pas « custom » — avec un repo owner/repo).
     */
    fun pagesUrl(targetsJson: String?): String? {
        if (targetsJson.isNullOrBlank()) return null
        return try {
            val target = json.parseToJsonElement(targetsJson).jsonArray
                .map { it.jsonObject }
                .firstOrNull { t ->
                    val provider = t["provider"]?.jsonPrimitive?.contentOrNull ?: "github"
                    val repo = t["repo"]?.jsonPrimitive?.contentOrNull
                    provider != "custom" && !repo.isNullOrBlank() && repo.contains("/")
                } ?: return null
            val full = target["repo"]!!.jsonPrimitive.content
            val user = full.substringBefore("/")
            val repo = full.substringAfter("/")
            if (repo.equals("$user.github.io", ignoreCase = true)) "https://$user.github.io/"
            else "https://$user.github.io/$repo/"
        } catch (e: Exception) {
            null
        }
    }
}
