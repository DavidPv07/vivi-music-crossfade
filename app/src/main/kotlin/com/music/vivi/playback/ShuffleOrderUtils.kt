package com.music.vivi.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline

/**
 * Pure, side-effect-free shuffle-order math extracted out of [MusicService] so
 * it can be unit-tested directly in a plain JVM test, without needing a real
 * ExoPlayer instance, an Android [android.content.Context], or DataStore.
 *
 * This is exactly the calculation most likely to hide a subtle off-by-one or
 * duplicate-index bug: capturing the *live* shuffle traversal from a player's
 * [Timeline] so it can be replayed verbatim elsewhere (e.g. on the secondary
 * player during a crossfade swap) instead of being regenerated at random.
 */
internal object ShuffleOrderUtils {

    /**
     * Reads the currently-active shuffle traversal order straight from
     * [timeline] (using [Timeline.getPreviousWindowIndex] / [Timeline.getNextWindowIndex]
     * with shuffle enabled — the same trick [MusicService]'s `playNext` logic
     * uses) and returns it as an explicit index array reproducing the full
     * active traversal order, with [currentIndex] at its real position in that
     * order (not necessarily first).
     *
     * ExoPlayer has no public getter for the live `ShuffleOrder`, so this is
     * how a player's order is captured just before a crossfade player swap in
     * order to replay the *same* order on the secondary player — instead of
     * generating a fresh random order, which re-randomizes shuffle on every
     * skip / song selection and lets already-played tracks reappear.
     *
     * Returns `null` if [timeline] is empty or [currentIndex] is
     * [C.INDEX_UNSET].
     */
    fun currentTraversal(timeline: Timeline, currentIndex: Int): IntArray? {
        if (timeline.isEmpty) return null
        if (currentIndex == C.INDEX_UNSET) return null

        val before = mutableListOf<Int>()
        var prev = currentIndex
        while (true) {
            prev = timeline.getPreviousWindowIndex(prev, Player.REPEAT_MODE_OFF, true)
            if (prev == C.INDEX_UNSET) break
            before.add(prev)
        }
        before.reverse()

        val after = mutableListOf<Int>()
        var next = currentIndex
        while (true) {
            next = timeline.getNextWindowIndex(next, Player.REPEAT_MODE_OFF, true)
            if (next == C.INDEX_UNSET) break
            after.add(next)
        }

        return (before + currentIndex + after).toIntArray()
    }
}
