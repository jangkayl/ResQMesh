package edu.cit.resqmesh.feature.devtools.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import edu.cit.resqmesh.core.ui.components.feedback.ResQStatusChip
import edu.cit.resqmesh.core.ui.components.feedback.ResQStatusTone
import edu.cit.resqmesh.core.ui.theme.Spacing
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Keeps a newest-first list on its latest row. Scrolling toward older rows leaves live mode, and
 * returning to the top or using Go to latest re-enters it. New rows never pull a reader back.
 * [newerThan] counts the rows newer than a key, for the button shown while reading.
 */
@Composable
internal fun LiveListBox(
    state: LazyListState,
    newestKey: Long?,
    paused: Boolean,
    newerThan: (Long) -> Int,
    content: @Composable BoxScope.(Modifier) -> Unit
) {
    var live by remember(state) { mutableStateOf(true) }
    var leftLiveAt by remember(state) { mutableStateOf<Long?>(null) }
    val currentNewest by rememberUpdatedState(newestKey)
    val scope = rememberCoroutineScope()

    // Inserted rows also move the index, so only a real user scroll may turn live mode off.
    val connection = remember(state) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed.y < 0f && live) {
                    leftLiveAt = currentNewest
                    live = false
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0 }
            .distinctUntilChanged()
            .filter { atTop -> atTop }
            .collect { live = true }
    }

    LaunchedEffect(state, newestKey) {
        if (live && newestKey != null) state.scrollToItem(0)
    }

    Box(Modifier.fillMaxSize()) {
        content(Modifier.fillMaxSize().nestedScroll(connection))
        Box(Modifier.align(Alignment.BottomEnd).padding(Spacing.Medium)) {
            when {
                !live -> Button(onClick = { live = true; scope.launch { state.scrollToItem(0) } }) {
                    val newCount = leftLiveAt?.let(newerThan) ?: 0
                    Text(if (newCount > 0) "Go to latest ($newCount)" else "Go to latest")
                }
                paused -> ResQStatusChip("PAUSED", ResQStatusTone.Warning)
                else -> ResQStatusChip("LIVE", ResQStatusTone.Success)
            }
        }
    }
}
