package edu.cit.resqmesh.core.audio

/** Longest recording per voice note, so one note cannot hold the mesh for minutes. */
const val MAX_VOICE_NOTE_MS = 30_000

private const val MS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L

/** Elapsed recording time against the limit, for example "0:24 / 0:30". */
fun voiceNoteElapsedLabel(elapsedMs: Long): String {
    val elapsed = elapsedMs.coerceIn(0L, MAX_VOICE_NOTE_MS.toLong())
    return "${clock(elapsed)} / ${clock(MAX_VOICE_NOTE_MS.toLong())}"
}

private fun clock(ms: Long): String {
    val seconds = ms / MS_PER_SECOND
    return "${seconds / SECONDS_PER_MINUTE}:${(seconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
}
