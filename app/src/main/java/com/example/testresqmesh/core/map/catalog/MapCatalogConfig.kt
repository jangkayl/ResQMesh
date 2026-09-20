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
         * Embedded maintainer public key for offline map manifest verification.
         * Universal ECDSA (NIST P-256 / secp256r1) X.509 DER encoded key.
         * Verified offline by [com.example.testresqmesh.core.map.verifier.ManifestVerifier]
         * across all Android versions (API 14+).
         */
        val DEFAULT_MAINTAINER_PUBLIC_KEY: ByteArray = byteArrayOf(
            0x30.toByte(), 0x59.toByte(), 0x30.toByte(), 0x13.toByte(), 0x06.toByte(), 0x07.toByte(), 0x2a.toByte(), 0x86.toByte(), 0x48.toByte(), 0xce.toByte(), 0x3d.toByte(), 0x02.toByte(), 0x01.toByte(), 0x06.toByte(), 0x08.toByte(), 0x2a.toByte(), 0x86.toByte(), 0x48.toByte(), 0xce.toByte(), 0x3d.toByte(), 0x03.toByte(), 0x01.toByte(), 0x07.toByte(), 0x03.toByte(), 0x42.toByte(), 0x00.toByte(), 0x04.toByte(), 0x16.toByte(), 0x53.toByte(), 0xce.toByte(), 0xaa.toByte(), 0x47.toByte(), 0xf2.toByte(), 0x98.toByte(), 0x2f.toByte(), 0x57.toByte(), 0x20.toByte(), 0xc2.toByte(), 0x88.toByte(), 0x71.toByte(), 0x43.toByte(), 0x94.toByte(), 0xfe.toByte(), 0x30.toByte(), 0x66.toByte(), 0x6d.toByte(), 0x04.toByte(), 0x7a.toByte(), 0x85.toByte(), 0x08.toByte(), 0x89.toByte(), 0xf9.toByte(), 0x40.toByte(), 0xf2.toByte(), 0x09.toByte(), 0x8a.toByte(), 0x92.toByte(), 0xd7.toByte(), 0xf9.toByte(), 0x49.toByte(), 0x19.toByte(), 0x6a.toByte(), 0xc9.toByte(), 0xce.toByte(), 0x57.toByte(), 0xc4.toByte(), 0xc0.toByte(), 0x38.toByte(), 0x56.toByte(), 0x52.toByte(), 0x49.toByte(), 0xa9.toByte(), 0xc8.toByte(), 0xb7.toByte(), 0xa2.toByte(), 0x15.toByte(), 0x4d.toByte(), 0x67.toByte(), 0x72.toByte(), 0x43.toByte(), 0x47.toByte(), 0xe2.toByte(), 0xd0.toByte(), 0xd4.toByte(), 0xfb.toByte(), 0x3d.toByte(), 0x85.toByte(), 0x4a.toByte(), 0x83.toByte(), 0x9e.toByte(), 0x80.toByte()
        )
    }
}
