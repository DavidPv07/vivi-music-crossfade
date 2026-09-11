package com.music.vivi.playback

import com.music.vivi.constants.CrossfadeCurve

/**
 * Describes a single crossfade transition: how long it should take, where
 * each player should be positioned, and what volume curve to use.
 *
 * This is the boundary between transition *decision* and transition
 * *execution*. Whatever decides that a transition should happen — today, the
 * user's crossfade settings (see [MusicService.startCrossfade] and friends);
 * later, an AutoMix engine — builds a [TransitionPlan]. The crossfade engine
 * ([MusicService.beginCrossfadeSwap] / [MusicService.performCrossfadeSwap])
 * only ever reads one; it never reaches back into settings, DataStore, or
 * track-analysis state to re-derive timing on its own.
 *
 * A decision-maker that produces a [TransitionPlan] must NOT directly
 * manipulate: ExoPlayer instances, player volume, the secondary player's
 * lifecycle, player listeners, DataStore, playback state, or resource
 * cleanup. Those responsibilities stay in the playback/crossfade layer,
 * which is the only thing that consumes this plan.
 *
 * Deliberately minimal: only fields the current implementation actually
 * uses. Don't add tempo/confidence/beat-alignment-style fields
 * speculatively — add them only when something real needs to consume them.
 */
data class TransitionPlan(
    /** How long the fade should take, in milliseconds. */
    val durationMs: Long,
    /**
     * Where the outgoing track is expected to be positioned (ms) at the
     * moment this transition begins. Informational for now: nothing seeks
     * the outgoing player to this position, since it's already playing
     * wherever it is when the transition triggers. Recorded so a future
     * decision-maker can reason about, or log, *why* a transition started
     * when it did, without the executor needing to re-derive it.
     */
    val outgoingPositionMs: Long,
    /**
     * Where the incoming track should start playback from, in milliseconds.
     * Most current callers pass 0 (start from the beginning); one
     * (`startQueueCrossfade`, resuming a synced/saved queue position) passes
     * a real resume position. The architecture supports any non-negative
     * value so a future AutoMix can pick a better entry point into the next
     * track — nothing produces an AutoMix-chosen value yet.
     */
    val incomingPositionMs: Long,
    /** Volume ramp shape to use for this specific transition. */
    val curve: CrossfadeCurve,
)
