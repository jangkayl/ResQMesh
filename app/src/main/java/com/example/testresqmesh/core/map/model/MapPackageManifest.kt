package com.example.testresqmesh.core.map.model

import org.json.JSONObject

/**
 * Metadata manifest describing an offline map package (e.g., Cebu City vector PMTiles).
 *
 * Published alongside the package zip and an Ed25519 signature:
 * - cebu-vN.zip
 * - cebu-vN.manifest.json
 * - cebu-vN.manifest.sig
 */
data class MapPackageManifest(
    val packageId: String,
    val version: Int,
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double,
    val minZoom: Int,
    val maxZoom: Int,
    val byteLength: Long,
    val sha256: String,
    val releaseDate: String,
    val attribution: String,
    val assetUrl: String
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("packageId", packageId)
            put("version", version)
            put("minLat", minLat)
            put("minLng", minLng)
            put("maxLat", maxLat)
            put("maxLng", maxLng)
            put("minZoom", minZoom)
            put("maxZoom", maxZoom)
            put("byteLength", byteLength)
            put("sha256", sha256)
            put("releaseDate", releaseDate)
            put("attribution", attribution)
            put("assetUrl", assetUrl)
        }.toString(2)
    }

    companion object {
        fun fromJson(jsonStr: String): MapPackageManifest {
            val json = JSONObject(jsonStr)
            return MapPackageManifest(
                packageId = json.getString("packageId"),
                version = json.getInt("version"),
                minLat = json.getDouble("minLat"),
                minLng = json.getDouble("minLng"),
                maxLat = json.getDouble("maxLat"),
                maxLng = json.getDouble("maxLng"),
                minZoom = json.getInt("minZoom"),
                maxZoom = json.getInt("maxZoom"),
                byteLength = json.getLong("byteLength"),
                sha256 = json.getString("sha256"),
                releaseDate = json.getString("releaseDate"),
                attribution = json.getString("attribution"),
                assetUrl = json.getString("assetUrl")
            )
        }
    }
}
