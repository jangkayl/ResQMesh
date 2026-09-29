package com.example.testresqmesh.feature.setup

import android.content.Context

enum class GuideState { Pending, Complete }

/** Keeps the guide separate from mesh identity and permission state. */
object FirstLaunchGuideStore {
    private const val PREFS = "resqmesh_prefs"
    private const val KEY = "first_launch_guide_v1"

    fun load(context: Context): GuideState {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY, null)
        if (saved == GuideState.Pending.name) return GuideState.Pending
        if (saved == GuideState.Complete.name) return GuideState.Complete

        val initial = initialState(prefs.getString("custom_name", null))
        prefs.edit().putString(KEY, initial.name).commit()
        return initial
    }

    fun complete(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, GuideState.Complete.name).commit()
    }

    /** Existing named users should not receive a new tutorial just because the app updated. */
    fun initialState(existingName: String?): GuideState =
        if (existingName.isNullOrBlank()) GuideState.Pending else GuideState.Complete
}
