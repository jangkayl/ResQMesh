package edu.cit.resqmesh.data.repository.adapter

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Maps a [StateFlow] so the first frame already holds the mapped current value. */
internal fun <T, R> StateFlow<T>.mapState(scope: CoroutineScope, transform: (T) -> R): StateFlow<R> =
    map(transform).stateIn(scope, SharingStarted.Eagerly, transform(value))
