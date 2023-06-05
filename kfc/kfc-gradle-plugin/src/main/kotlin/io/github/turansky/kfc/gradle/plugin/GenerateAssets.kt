package io.github.turansky.kfc.gradle.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import java.io.File

open class GenerateAssets : DefaultTask() {
    init {
        group = DEFAULT_TASK_GROUP
    }

    @get:Input
    var pkg: String? = null

    @get:Input
    var factoryName: String? = null

    @Optional
    @get:Input
    var templateColor: String? = null

    @get:InputDirectory
    var resourcesDirectory: File? = null

    @get:OutputDirectory
    val outputDirectory: File
        get() = temporaryDir.resolve("src")

    @TaskAction
    private fun generateAssets() {
        outputDirectory.deleteRecursively()

        val assetsPackage = requireNotNull(pkg)
        val assetFactory = requireNotNull(factoryName)
        val resourcesDirectory = requireNotNull(resourcesDirectory)

        val svgFiles = resourcesDirectory.walkTopDown()
            .filter { it.isFile }
            .filter { it.extension == "svg" }
            .toList()

        fun createFile(
            path: String,
            content: String,
        ) {
            val file = outputDirectory.resolve(path)
            file.parentFile.mkdirs()
            file.writeText("package $assetsPackage\n\n$content")
        }

        val paths = mutableListOf<String>()
        val symbolConstants = mutableListOf<String>()

        for (file in svgFiles) {
            val fullPath = file.toRelativeString(resourcesDirectory)
            val path = fullPath.substringBeforeLast(".")
            val name = path
                .replace("/", "__")
                .replace("-", "_")
                .uppercase() + "_CONTENT"

            val symbolId = "kfc-gis__" + path.replace("/", "__")
            val content = SVG.symbol(
                id = symbolId,
                source = file.readText(),
                templateColor = templateColor,
            )
            val declaration = "internal const val $name = \"\"\"$content\"\"\""

            createFile(
                path = "${path}.kt",
                content = declaration,
            )

            paths += path
            symbolConstants += name
        }

        createFile(
            path = "Icons.kt",
            content = iconsContent(assetFactory, paths),
        )

        createFile(
            path = "AssetRegistry.kt",
            content = assetRegistryContent(symbolConstants),
        )
    }
}

private fun iconsContent(
    factoryName: String,
    paths: List<String>,
): String {
    val content = paths.joinToString("\n") { path ->
        val name = path.splitToSequence("/")
            .map { part ->
                part.splitToSequence("-").joinToString("") {
                    when (it) {
                        "2d", "3d",
                        -> it.uppercase()

                        // TODO: use custom `capitalized`
                        else -> it.replaceFirstChar { char ->
                            char.uppercase()
                        }
                    }
                }
            }.joinToString("_")

        val symbolId = "kfc-gis__" + path.replace("/", "__")

        "    val $name: $factoryName = $factoryName(\"$symbolId\")"
    }

    return "object Icons {\n" +
            content +
            "\n}\n"
}

// language=kotlin
private val ASSET_REGISTRY_TEMPLATE = """
internal object AssetRegistry {

const val SYMBOLS_CONTENT: String = ${"\"\"\""}
__SYMBOLS__
${"\"\"\""}
    
    fun getSymbolId(path: String): String = 
        "kfc-gis__" + path.replace("/", "__")
}
"""

private fun assetRegistryContent(
    symbolNames: List<String>,
): String {
    val symbolsContent = symbolNames.joinToString("\n") { name ->
        "${'$'}$name"
    }

    return ASSET_REGISTRY_TEMPLATE.replace("__SYMBOLS__", symbolsContent)
}
