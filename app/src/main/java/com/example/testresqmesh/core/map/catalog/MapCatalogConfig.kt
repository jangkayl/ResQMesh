package com.example.testresqmesh.core.map.catalog

/**
 * Metadata and versioned release asset configuration for the Cebu City offline vector map.
 *
 * Isolated catalog configuration for GitHub Release assets:
 * - Manifest URL: metadata JSON containing package ID, version, bounding box, SHA-256, and asset URL.
 * - Signature URL: Ed25519 digital signature of the manifest created with the maintainer's offline private key.
 * - Embedded Ed25519 Public Key: Embedded directly in the application binary to verify the manifest
 *   before any package binary is accepted.
 * - Local-only guarantee: Once verified and activated, MapLibre Native renders from local storage
 *   without contacting the network for tiles, fonts, glyphs, or styles.
 */
data class MapPackageCatalogEntry(
    val packageId: String = DEFAULT_PACKAGE_ID,
    val title: String = "Cebu Tactical Vector Map",
    val description: String = "High-contrast vector road network, waterways, POIs, and emergency infrastructure for Cebu Metropolitan Area and Cebu Province.",
    val coverageArea: String = "Cebu Province & Metropolitan Area (9.40°N - 11.40°N, 123.30°E - 124.20°E)",
    val targetVersion: Int = 1,
    val minZoom: Int = 6,
    val maxZoom: Int = 15,
    val estimatedDownloadSizeBytes: Long = 28L * 1024 * 1024, // ~28 MB
    val manifestUrl: String = DEFAULT_MANIFEST_URL,
    val signatureUrl: String = DEFAULT_SIGNATURE_URL,
    val maintainerPublicKey: ByteArray = DEFAULT_MAINTAINER_PUBLIC_KEY
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MapPackageCatalogEntry

        if (packageId != other.packageId) return false
        if (title != other.title) return false
        if (description != other.description) return false
        if (coverageArea != other.coverageArea) return false
        if (targetVersion != other.targetVersion) return false
        if (minZoom != other.minZoom) return false
        if (maxZoom != other.maxZoom) return false
        if (estimatedDownloadSizeBytes != other.estimatedDownloadSizeBytes) return false
        if (manifestUrl != other.manifestUrl) return false
        if (signatureUrl != other.signatureUrl) return false
        if (!maintainerPublicKey.contentEquals(other.maintainerPublicKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = packageId.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + coverageArea.hashCode()
        result = 31 * result + targetVersion
        result = 31 * result + minZoom
        result = 31 * result + maxZoom
        result = 31 * result + estimatedDownloadSizeBytes.hashCode()
        result = 31 * result + manifestUrl.hashCode()
        result = 31 * result + signatureUrl.hashCode()
        result = 31 * result + maintainerPublicKey.contentHashCode()
        return result
    }

    companion object {
        const val DEFAULT_PACKAGE_ID = "cebu-offline"

        /**
         * Versioned GitHub Release asset URLs for repository jangkayl/ResQMesh.
         * Real release assets are published under GitHub Releases (e.g. tag map-cebu-v1).
         * Development / test builds can override these paths or supply mock transports.
         */
        const val DEFAULT_MANIFEST_URL =
            "https://github.com/jangkayl/ResQMesh/releases/download/map-cebu-v1/cebu-v1.manifest.json"
        const val DEFAULT_SIGNATURE_URL =
            "https://github.com/jangkayl/ResQMesh/releases/download/map-cebu-v1/cebu-v1.manifest.sig"

        /**
         * Embedded Ed25519 public key of the map release signer (32 bytes).
         * This key is used by [com.example.testresqmesh.core.map.verifier.ManifestVerifier]
         * to verify manifest integrity offline before proceeding with binary download.
         */
        val DEFAULT_MAINTAINER_PUBLIC_KEY: ByteArray = byteArrayOf(
            0x8d.toByte(), 0xef.toByte(), 0x6d.toByte(), 0xc8.toByte(),
            0x64.toByte(), 0xe6.toByte(), 0xe4.toByte(), 0x88.toByte(),
            0x0f.toByte(), 0x79.toByte(), 0x1c.toByte(), 0xbd.toByte(),
            0x2c.toByte(), 0x72.toByte(), 0xdd.toByte(), 0x59.toByte(),
            0xd0.toByte(), 0x34.toByte(), 0x6e.toByte(), 0x69.toByte(),
            0xbe.toByte(), 0x71.toByte(), 0x83.toByte(), 0x51.toByte(),
            0x69.toByte(), 0x88.toByte(), 0x83.toByte(), 0x0d.toByte(),
            0x5c.toByte(), 0x9f.toByte(), 0xa6.toByte(), 0x2f.toByte()
        )
    }
}
