package app.gamenative.wow.product

import java.io.File

/**
 * Adds a second product to an existing install. `Data/` is shared, so only `.build.info`, the new
 * flavor folder and its client binaries are needed; callers fetch the binaries afterwards with
 * `WowClientDownloader.download(root, to, ...)`.
 */
object ProductConverter {

    fun applyLocal(root: File, from: WowProduct, to: WowProduct) {
        val info = checkNotNull(BuildInfo.read(root)) { "No ${BuildInfo.FILE_NAME} in $root" }
        val converted = info.withProduct(from, to)
        if (converted != info) {
            val temp = File(root, "${BuildInfo.FILE_NAME}.tmp")
            temp.writeText(converted.serialize())
            check(temp.renameTo(File(root, BuildInfo.FILE_NAME))) { "Could not update ${BuildInfo.FILE_NAME}" }
        }

        val source = File(root, from.flavorDir)
        val target = File(root, to.flavorDir).apply { mkdirs() }
        val configWtf = File(target, "WTF/Config.wtf")
        val copyingConfig = !configWtf.exists()
        File(target, ".flavor.info").takeUnless { it.exists() }?.writeText("Product Flavor!STRING:0\n${to.code}\n")
        for (dir in listOf("WTF", "Interface/AddOns")) {
            File(source, dir).takeIf { it.isDirectory }?.copyRecursively(File(target, dir), overwrite = false) { _, e ->
                if (e is FileAlreadyExistsException) OnErrorAction.SKIP else throw e
            }
        }
        // The beta's portal and agent point at the test product; drop them so the launcher writes the new product's.
        configWtf.takeIf { copyingConfig && it.isFile }?.let { config ->
            val lines = config.readLines()
            val kept = lines.filterNot { it.startsWith("SET portal ", ignoreCase = true) || it.startsWith("SET agentUID ", ignoreCase = true) }
            if (kept.size != lines.size) config.writeText(kept.joinToString("\n", postfix = "\n"))
        }
    }
}
