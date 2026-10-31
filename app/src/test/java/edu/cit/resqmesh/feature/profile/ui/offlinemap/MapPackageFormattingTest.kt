package edu.cit.resqmesh.feature.profile.ui.offlinemap

import org.junit.Assert.assertEquals
import org.junit.Test

class MapPackageFormattingTest {

    @Test
    fun downloaderMessagesBecomePlainLanguage() {
        assertEquals(
            "We couldn't reach the map download. Check your internet and try again.",
            friendlyMapError("Failed to fetch map manifest or signature.")
        )
        assertEquals(
            "The download stopped before it finished. Check your internet and try again.",
            friendlyMapError("Download interrupted or failed.")
        )
        assertEquals(
            "The map couldn't be installed. Please try again.",
            friendlyMapError("Failed to extract offline map package assets.")
        )
        assertEquals(
            "The map couldn't be installed. Please try again.",
            friendlyMapError("Failed to atomically activate new map package.")
        )
    }

    @Test
    fun unknownErrorsNeverLeakTechnicalDetail() {
        assertEquals(
            "Something went wrong with the download. Please try again.",
            friendlyMapError("Failed to parse manifest JSON: unexpected token")
        )
    }
}
