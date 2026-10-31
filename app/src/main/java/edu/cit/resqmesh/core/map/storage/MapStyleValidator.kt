package edu.cit.resqmesh.core.map.storage

import edu.cit.resqmesh.core.logging.AppLogger
import org.json.JSONObject
import java.io.File

/**
 * Validates offline map styles for zero-network compliance and local asset dependency integrity.
 *
 * Guarantees:
 * 1. Zero network calls: Rejects styles containing remote network URLs (http:// or https://).
 * 2. Vector source integrity: Ensures referenced vector tile archives (e.g. cebu.pmtiles) exist locally.
 * 3. Sprite dependency integrity: If sprite base is declared, verifies both .json and .png exist locally.
 * 4. Glyph dependency integrity: If glyphs template is declared, verifies font directory exists and contains .pbf files.
 * 5. Runtime URL resolution: Rewrites relative package asset paths to absolute file:// URIs for MapLibre Native.
 */
object MapStyleValidator {

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    /**
     * Validates a style.json file and its referenced dependencies within [packageDir].
     */
    fun validate(packageDir: File, styleFile: File): ValidationResult {
        if (!styleFile.exists() || styleFile.length() == 0L) {
            return ValidationResult.Invalid("style.json does not exist or is empty")
        }

        val styleContent = try {
            styleFile.readText()
        } catch (e: Exception) {
            return ValidationResult.Invalid("Failed to read style.json: ${e.message}")
        }

        // 1. Strict zero-network policy check
        if (styleContent.contains("http://", ignoreCase = true) ||
            styleContent.contains("https://", ignoreCase = true)
        ) {
            return ValidationResult.Invalid(
                "Policy violation: style.json contains remote network URLs (http:// or https://). Offline map styles must be fully self-contained."
            )
        }

        // 2. Parse JSON
        val root = try {
            JSONObject(styleContent)
        } catch (e: Exception) {
            return ValidationResult.Invalid("style.json is not valid JSON: ${e.message}")
        }

        return validateSources(packageDir, root) ?: validateSprite(packageDir, root) ?: validateGlyphs(packageDir, root)
            ?: ValidationResult.Valid
    }

    private fun validateSources(packageDir: File, root: JSONObject): ValidationResult.Invalid? {
        val sources = root.optJSONObject("sources") ?: return null
        val keys = sources.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val sourceObj = sources.optJSONObject(key) ?: continue
            val url = sourceObj.optString("url", "")
            if (url.isBlank()) continue
            val cleanPath = url.removePrefix("pmtiles://").stripLocalPrefixes()
            if (cleanPath.endsWith(".pmtiles") && !File(packageDir, cleanPath).exists()) {
                return ValidationResult.Invalid("Source '$key' references missing PMTiles file: $cleanPath")
            }
        }
        return null
    }

    private fun validateSprite(packageDir: File, root: JSONObject): ValidationResult.Invalid? {
        if (!root.has("sprite")) return null
        val spriteBase = root.optString("sprite", "").stripLocalPrefixes()
        if (spriteBase.isBlank()) return null
        if (!File(packageDir, "$spriteBase.json").exists()) {
            return ValidationResult.Invalid("Missing referenced sprite JSON: $spriteBase.json")
        }
        if (!File(packageDir, "$spriteBase.png").exists()) {
            return ValidationResult.Invalid("Missing referenced sprite PNG: $spriteBase.png")
        }
        return null
    }

    private fun validateGlyphs(packageDir: File, root: JSONObject): ValidationResult.Invalid? {
        if (!root.has("glyphs")) return null
        val glyphsTemplate = root.optString("glyphs", "")
        if (glyphsTemplate.isBlank()) return null
        // Extract directory prefix before {fontstack} or {range}
        val dirPrefix = glyphsTemplate.substringBefore("{").trimEnd('/').stripLocalPrefixes()
        val glyphsDir = if (dirPrefix.isNotBlank()) File(packageDir, dirPrefix) else File(packageDir, "glyphs")
        if (!glyphsDir.exists() || !glyphsDir.isDirectory) {
            return ValidationResult.Invalid("Missing referenced glyphs directory: ${glyphsDir.name}")
        }
        if (!hasPbfFiles(glyphsDir)) {
            return ValidationResult.Invalid("Referenced glyphs directory contains no .pbf font files: ${glyphsDir.name}")
        }
        return null
    }

    private fun String.stripLocalPrefixes() = removePrefix("file://").removePrefix("./").removePrefix("{PACKAGE_DIR}/")

    private fun hasPbfFiles(dir: File): Boolean {
        val files = dir.listFiles() ?: return false
        for (f in files) {
            if (f.isDirectory && hasPbfFiles(f)) return true
            if (f.isFile && f.name.endsWith(".pbf", ignoreCase = true)) return true
        }
        return false
    }

    private fun resolveLocalAsset(root: JSONObject, field: String, packageUri: String) {
        if (!root.has(field)) return
        val value = root.getString(field)
        if (value.startsWith("file://") || value.startsWith("asset://")) return
        val clean = value.removePrefix("./").removePrefix("{PACKAGE_DIR}/")
        root.put(field, "$packageUri/$clean")
    }

    private fun resolveVectorSources(root: JSONObject, packageDir: File) {
        val sources = root.optJSONObject("sources") ?: return
        val packagePath = packageDir.canonicalPath.replace('\\', '/')
        val keys = sources.keys()
        while (keys.hasNext()) {
            val src = sources.getJSONObject(keys.next())
            if (!src.has("url")) continue
            val url = src.getString("url")
            if (!url.startsWith("pmtiles://") || url.contains(packagePath)) continue
            val path = url.removePrefix("pmtiles://").removePrefix("./").removePrefix("{PACKAGE_DIR}/")
            src.put("url", "pmtiles://file://$packagePath/$path")
        }
    }

    /**
     * Resolves relative paths or {PACKAGE_DIR} placeholders into absolute file:// URIs
     * for MapLibre Native loading.
     */
    fun resolveStyleForRuntime(packageDir: File, styleFile: File): String {
        val content = try {
            styleFile.readText()
        } catch (e: Exception) {
            AppLogger.d("MapStyleValidator", "Failed to read style for runtime resolution: ${e.javaClass.simpleName}")
            return ""
        }

        return try {
            val packageUri = "file://${packageDir.canonicalPath.replace('\\', '/')}"
            val root = JSONObject(content)

            resolveLocalAsset(root, "sprite", packageUri)
            resolveLocalAsset(root, "glyphs", packageUri)
            resolveVectorSources(root, packageDir)

            root.toString()
        } catch (e: Exception) {
            AppLogger.d("MapStyleValidator", "JSON manipulation failed, returning raw text: ${e.javaClass.simpleName}")
            content
        }
    }
}
