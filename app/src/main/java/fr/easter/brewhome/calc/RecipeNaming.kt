package fr.easter.brewhome.calc

/** Nommage des copies de recettes — port de duplicateRecipe() de bh-recettes.js. */
object RecipeNaming {

    private val versionSuffix = Regex("""\s+v\d+$""", RegexOption.IGNORE_CASE)
    private val copieSuffix = Regex("""\s*\(copie\)\s*$""", RegexOption.IGNORE_CASE)

    /**
     * Nom de la copie : on retire un « vN » ou « (copie) » final, puis on repart
     * à la version max + 1 parmi les recettes existantes de même base.
     */
    fun duplicateName(name: String, existingNames: List<String>): String {
        val base = name.replace(versionSuffix, "").replace(copieSuffix, "").trim()
        val re = Regex("^" + Regex.escape(base) + """(?:\s+v(\d+))?$""", RegexOption.IGNORE_CASE)
        var maxVer = 1
        existingNames.forEach { n ->
            val m = re.matchEntire(n.trim())
            if (m != null) {
                val v = m.groupValues[1].toIntOrNull() ?: 1
                if (v > maxVer) maxVer = v
            }
        }
        return "$base v${maxVer + 1}"
    }
}
