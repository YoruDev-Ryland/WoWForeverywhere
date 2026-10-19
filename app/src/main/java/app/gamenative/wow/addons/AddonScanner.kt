package app.gamenative.wow.addons

import java.io.File

/** Detects the logical addons in an `Interface/AddOns` folder. */
object AddonScanner {
    private class FolderInfo(val name: String, val toc: Toc, val tocs: List<Toc>)

    /**
     * Returns [known] records (pruned to folders that still exist; records with no folders left are
     * dropped) followed by new records for every remaining folder that has a usable TOC.
     *
     * Unknown folders are grouped into one addon when they share an `X-Curse-Project-ID` or
     * `X-Wago-ID`, or when one depends on a sibling with the same name prefix (`DBM-Core` +
     * `DBM-StatusBarTimers`).
     */
    fun scan(addonsDir: File, known: List<InstalledAddon>, now: Long = System.currentTimeMillis()): List<InstalledAddon> {
        val present = addonsDir.listFiles()
            .orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .associateBy { it.name }

        val kept = known.mapNotNull { record ->
            val folders = record.folders.filter { it in present }
            if (folders.isEmpty()) null else record.copy(folders = folders)
        }
        val claimed = kept.flatMap { it.folders }.toSet()

        val infos = present.values
            .filter { it.name !in claimed }
            .sortedBy { it.name.lowercase() }
            .mapNotNull { dir ->
                val toc = FlavorRules.pickToc(dir) ?: return@mapNotNull null
                FolderInfo(dir.name, toc, FlavorRules.readTocs(dir))
            }

        return kept + group(infos).map { toRecord(it, now) }
    }

    private fun group(infos: List<FolderInfo>): List<List<FolderInfo>> {
        val parent = IntArray(infos.size) { it }
        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }
        fun union(a: Int, b: Int) {
            parent[find(a)] = find(b)
        }

        val index = infos.withIndex().associate { it.value.name.lowercase() to it.index }
        val byId = HashMap<String, Int>()
        infos.forEachIndexed { i, info ->
            listOfNotNull(info.toc.curseId?.let { "c:$it" }, info.toc.wagoId?.let { "w:$it" }).forEach { key ->
                byId[key]?.let { union(i, it) } ?: byId.put(key, i)
            }
            info.toc.dependencies.forEach { dep ->
                val j = index[dep.lowercase()] ?: return@forEach
                // A plugin with its own project id (ElvUI_WindTools, Details_TinyThreat) is a separate addon.
                val mine = ids(info)
                val theirs = ids(infos[j])
                val separate = mine.isNotEmpty() && theirs.isNotEmpty() && (mine intersect theirs).isEmpty()
                if (!separate && prefix(info.name).equals(prefix(infos[j].name), ignoreCase = true)) union(i, j)
            }
        }
        return infos.indices.groupBy { find(it) }.values.map { members -> members.map { infos[it] } }
    }

    private fun ids(info: FolderInfo): Set<String> =
        listOfNotNull(info.toc.curseId?.let { "c:$it" }, info.toc.wagoId?.let { "w:$it" }, info.toc.wowiId?.let { "i:$it" }).toSet()

    private fun prefix(folder: String): String = folder.split('-', '_').first()

    private fun toRecord(group: List<FolderInfo>, now: Long): InstalledAddon {
        val names = group.map { it.name }.toSet()
        // Main folder: prefer one with no dependencies inside the group, then the shortest name.
        val main = group.minWith(
            compareBy<FolderInfo> { info -> info.toc.dependencies.count { it in names } }
                .thenBy { it.name.length }
                .thenBy { it.name },
        )
        val source = sourceOf(main.toc) ?: group.firstNotNullOfOrNull { sourceOf(it.toc) }
        return InstalledAddon(
            id = source?.let { "${it.provider}:${it.projectId}" } ?: "${ProviderIds.LOCAL}:${main.name}",
            name = main.toc.title ?: main.name,
            folders = group.map { it.name }.sortedWith(compareBy({ it != main.name }, { it })),
            version = main.toc.version,
            flavor = FlavorRules.classify(group.flatMap { it.tocs }),
            source = source,
            installedAt = now,
        )
    }

    private fun sourceOf(toc: Toc): SourceRef? = when {
        toc.curseId != null -> SourceRef(ProviderIds.CURSEFORGE, toc.curseId)
        toc.wagoId != null -> SourceRef(ProviderIds.WAGO, toc.wagoId)
        toc.wowiId != null -> SourceRef(ProviderIds.WOWINTERFACE, toc.wowiId)
        else -> null
    }
}
