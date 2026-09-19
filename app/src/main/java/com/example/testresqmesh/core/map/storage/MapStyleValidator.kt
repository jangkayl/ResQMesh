package com.example.testresqmesh.core.map.storage

import com.example.testresqmesh.core.utils.AppLogger
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

        // 3. Validate sources
        if (root.has("sources")) {
            val sources = root.optJSONObject("sources")
            if (sources != null) {
                val keys = sources.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val sourceObj = sources.optJSONObject(key) ?: continue
                    val url = sourceObj.optString("url", "")
                    if (url.isNotBlank()) {
                        // Check pmtiles reference
                        val cleanPath = url
                            .removePrefix("pmtiles://")
                            .removePrefix("file://")
                            .removePrefix("./")
                            .removePrefix("{PACKAGE_DIR}/")
                        if (cleanPath.endsWith(".pmtiles")) {
                            val pmtilesFile = File(packageDir, cleanPath)
                            if (!pmtilesFile.exists()) {
                                return ValidationResult.Invalid(
                                    "Source '$key' references missing PMTiles file: $cleanPath"
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Validate sprite
        if (root.has("sprite")) {
            val spriteBase = root.optString("sprite", "")
                .removePrefix("file://")
                .removePrefix("./")
                .removePrefix("{PACKAGE_DIR}/")

            if (spriteBase.isNotBlank()) {
                val jsonFile = File(packageDir, "$spriteBase.json")
                val pngFile = File(packageDir, "$spriteBase.png")
                if (!jsonFile.exists()) {
                    return ValidationResult.Invalid("Missing referenced sprite JSON: $spriteBase.json")
                }
                if (!pngFile.exists()) {
                    return ValidationResult.Invalid("Missing referenced sprite PNG: $spriteBase.png")
                }
            }
        }

        // 5. Validate glyphs
        if (root.has("glyphs")) {
            val glyphsTemplate = root.optString("glyphs", "")
            if (glyphsTemplate.isNotBlank()) {
                // Extract directory prefix before {fontstack} or {range}
                val dirPrefix = glyphsTemplate.substringBefore("{")
                    .trimEnd('/')
                    .removePrefix("file://")
                    .removePrefix("./")
                    .removePrefix("{PACKAGE_DIR}/")

                val glyphsDir = if (dirPrefix.isNotBlank()) File(packageDir, dirPrefix) else File(packageDir, "glyphs")
                if (!glyphsDir.exists() || !glyphsDir.isDirectory) {
                    return ValidationResult.Invalid("Missing referenced glyphs directory: ${glyphsDir.name}")
                }
                val hasPbf = hasPbfFiles(glyphsDir)
                if (!hasPbf) {
                    return ValidationResult.Invalid("Referenced glyphs directory contains no .pbf font files: ${glyphsDir.name}")
                }
            }
        }

        return ValidationResult.Valid
    }

    private fun hasPbfFiles(dir: File): Boolean {
        val files = dir.listFiles() ?: return false
        for (f in files) {
            if (f.isDirectory && hasPbfFiles(f)) return true
            if (f.isFile && f.name.endsWith(".pbf", ignoreCase = true)) return true
        }
        return false
    }

    /**
     * Resolves relative paths or {PACKAGE_DIR} placeholders into absolute file:// URIs
     * for MapLibre Native loading.
     */
    fun resolveStyleForRuntime(packageDir: File, styleFile: File): String {
        val content = try {
            styleFile.readText()
        } catch (e: Exception) {
            AppLogger.d("MapStyleValidator", "Failed to read style for runtime resolution: ${e.message}")
            return ""
        }

        return try {
            val packageUri = "file://${packageDir.canonicalPath.replace('\\', '/')}"
            val root = JSONObject(content)

            // 1. Resolve sprite
            if (root.has("sprite")) {
                val sprite = root.getString("sprite")
                if (!sprite.startsWith("file://") && !sprite.startsWith("asset://")) {
                    val clean = sprite.removePrefix("./").removePrefix("{PACKAGE_DIR}/")
                    root.put("sprite", "$packageUri/$clean")
                }
            }

            // 2. Resolve glyphs
            if (root.has("glyphs")) {
                val glyphs = root.getString("glyphs")
                if (!glyphs.startsWith("file://") && !glyphs.startsWith("asset://")) {
                    val clean = glyphs.removePrefix("./").removePrefix("{PACKAGE_DIR}/")
                    root.put("glyphs", "$packageUri/$clean")
                }
            }

            // 3. Resolve vector sources
            if (root.has("sources")) {
                val sources = root.optJSONObject("sources")
                if (sources != null) {
                    val keys = sources.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val src = sources.getJSONObject(key)
                        if (src.has("url")) {
                            val url = src.getString("url")
                            if (url.startsWith("pmtiles://") && !url.contains(packageDir.canonicalPath.replace('\\', '/'))) {
                                val path = url.removePrefix("pmtiles://").removePrefix("./").removePrefix("{PACKAGE_DIR}/")
                                val targetUri = "file://${packageDir.canonicalPath.replace('\\', '/')}/$path"
                                src.put("url", "pmtiles://$targetUri")
                            }
                        }
                    }
                }
            }

            root.toString()
        } catch (e: Exception) {
            AppLogger.d("MapStyleValidator", "JSON manipulation failed, returning raw text: ${e.message}")
            content
        }
    }
}
