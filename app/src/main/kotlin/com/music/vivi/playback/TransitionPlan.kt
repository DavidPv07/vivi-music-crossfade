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

/**
 * Builds the transition plan for every crossfade entry point *before*
 * AutoMix exists — pick the AUTO or manual-skip duration, use the user's
 * configured curve, and record where the outgoing track currently sits.
 *
 * This is intentionally the exact same decision every call site made inline
 * before this was extracted (see MusicService.startCrossfade and friends):
 * a plain function of already-resolved values, with no Player, DataStore, or
 * MusicService reference — so it's unit-testable on its own, and so that
 * swapping in an AutoMix-provided plan later only means changing what calls
 * this (or replacing the call with AutoMix's own decision), never touching
 * the crossfade engine that executes whatever plan it's handed.
 */
internal object TransitionPlanFactory {
    /**
     * @param isManualTransition true for a user-initiated skip/tap (next,
     *   previous, queue selection, playlist tap) as opposed to an unattended
     *   end-of-track AUTO crossfade — see [MusicService.CrossfadeTrigger].
     * @param incomingPositionMs defaults to 0 (start of the incoming track).
     *   `startQueueCrossfade` is the one caller that passes something else: a
     *   real resume position from a synced/saved queue, unrelated to AutoMix.
     */
    fun forCurrentSettings(
        isManualTransition: Boolean,
        autoDurationMs: Long,
        manualDurationMs: Long,
        curve: CrossfadeCurve,
        outgoingPositionMs: Long,
        incomingPositionMs: Long = 0L,
    ): TransitionPlan = TransitionPlan(
        durationMs = if (isManualTransition) manualDurationMs else autoDurationMs,
        outgoingPositionMs = outgoingPositionMs,
        incomingPositionMs = incomingPositionMs,
        curve = curve,
    )
}
