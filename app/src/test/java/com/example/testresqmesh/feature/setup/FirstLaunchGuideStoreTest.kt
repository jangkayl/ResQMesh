package com.example.testresqmesh.feature.setup

import org.junit.Assert.assertEquals
import org.junit.Test

class FirstLaunchGuideStoreTest {
    @Test
    fun newOrClearedAppDataStartsWithGuide() {
        assertEquals(GuideState.Pending, FirstLaunchGuideStore.initialState(null))
        assertEquals(GuideState.Pending, FirstLaunchGuideStore.initialState("  "))
    }

    @Test
    fun existingNamedUserDoesNotGetGuideOnUpdate() {
        assertEquals(GuideState.Complete, FirstLaunchGuideStore.initialState("Ari Santos"))
    }
}
