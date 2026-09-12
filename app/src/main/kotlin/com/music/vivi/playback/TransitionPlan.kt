package com.music.vivi.playback

import com.music.vivi.constants.CrossfadeCurve

/**
 * Describes a single crossfade transition: how long it should take, where
 * each player should be positioned, and what volume curve to use.
 *
 * This is the boundary between transition *decision* and transition
 * *execution*. Whatever decides that a transition should happen — today, the
 * user's crossfade settings (see [MusicService.startCrossfade] and friends)
 * or [SmartCrossfadeEngine]; later, real audio-analysis-driven logic — builds
 * a [TransitionPlan]. The crossfade engine ([MusicService.beginCrossfadeSwap]
 * / [MusicService.performCrossfadeSwap]) only ever reads one; it never
 * reaches back into settings, DataStore, or track-analysis state to
 * re-derive timing on its own.
 *
 * NOTE ON NAMING: this is unrelated to [MusicService.automixItems] /
 * [MusicService.getAutomix] — YouTube Music's "Automix" feature that
 * continues a queue with related songs (song *selection*). This file is
 * about transition *timing/blending* between two already-chosen songs.
 * [SmartCrossfadeEngine] is named to avoid that collision on purpose.
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
     * value so a future smarter decision-maker can pick a better entry point
     * into the next track — nothing produces a non-default value here yet.
     */
    val incomingPositionMs: Long,
    /** Volume ramp shape to use for this specific transition. */
    val curve: CrossfadeCurve,
)

/**
 * Builds the transition plan for every *manual* crossfade entry point, and
 * (indirectly, via [SmartCrossfadeEngine]) the automatic one too — pick the
 * AUTO or manual-skip duration, use the user's configured curve, and record
 * where the outgoing track currently sits.
 *
 * This is intentionally the exact same decision every call site made inline
 * before this was extracted (see MusicService.startCrossfade and friends):
 * a plain function of already-resolved values, with no Player, DataStore, or
 * MusicService reference — so it's unit-testable on its own, and so that a
 * smarter decision-maker can be swapped in later by changing what calls this
 * (or replacing the call entirely), never touching the crossfade engine that
 * executes whatever plan it's handed.
 */
internal object TransitionPlanFactory {
    /**
     * @param isManualTransition true for a user-initiated skip/tap (next,
     *   previous, queue selection, playlist tap) as opposed to an unattended
     *   end-of-track AUTO crossfade — see [MusicService.CrossfadeTrigger].
     * @param incomingPositionMs defaults to 0 (start of the incoming track).
     *   `startQueueCrossfade` is the one caller that passes something else: a
     *   real resume position from a synced/saved queue, unrelated to this
     *   file's [SmartCrossfadeEngine].
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

/**
 * Minimal, reliably-available information about the two tracks involved in
 * a transition, passed to a decision-maker like [SmartCrossfadeEngine].
 *
 * Deliberately just durations for now — no tempo, energy, silence, or any
 * other analysis. Both fields are nullable because neither source is
 * guaranteed:
 * - [outgoingDurationMs] comes from the primary player's own measured
 *   duration of the currently-playing track (`player.duration`) — null if
 *   ExoPlayer hasn't resolved a duration yet (`C.TIME_UNSET`).
 * - [incomingDurationMs] comes from the catalog metadata already attached to
 *   the target `MediaItem` (see `com.music.vivi.extensions.metadata`), not
 *   from ExoPlayer — the incoming track is often not buffered yet, so its
 *   own `Timeline.Window` duration would frequently be unset. Catalog data
 *   is accurate in practice, but this file can't guarantee every MediaItem
 *   construction path in the app attaches it.
 *
 * Add fields here only when a real decision needs to consume them — see
 * [TransitionPlan]'s own doc comment for the same rule.
 */
data class TransitionContext(
    val outgoingDurationMs: Long? = null,
    val incomingDurationMs: Long? = null,
)

/**
 * Decides the [TransitionPlan] for an automatic (unattended, end-of-track)
 * crossfade. This is the "decision-maker" seam the architecture is built
 * around: [MusicService] calls this instead of building the plan inline, so
 * that smarter decisions (energy/silence/tempo-aware timing, eventually) can
 * live entirely inside this class later without the crossfade engine
 * changing at all.
 *
 * NAMING: deliberately NOT called "AutoMix" — see the note on
 * [MusicService.automixItems] above. This has nothing to do with queue
 * continuation; it only decides *when and how* a transition between two
 * already-chosen tracks happens.
 *
 * Deliberately dumb today: [createTransition] reproduces exactly the plan
 * [MusicService.startCrossfade] built inline for `CrossfadeTrigger.AUTO`
 * before this class existed — same duration, same curve, incoming position
 * 0. Plugging this in changes nothing about what actually plays; it only
 * moves the decision to a class that's small, deterministic, and testable
 * without a real player or DataStore. No audio analysis, scoring, or any
 * other "smart" behavior lives here yet — that's future work, done inside
 * this same method once there's something real to base a decision on.
 *
 * [context] is accepted (and required — every real caller must resolve one)
 * so the data path from MusicService all the way to this decision point
 * exists and is exercised, but its contents are deliberately not read yet.
 * That's the point of this stage: wire the pipe before anything flows
 * through it that changes behavior.
 *
 * Like [TransitionPlanFactory], this NEVER touches ExoPlayer, DataStore, or
 * playback state directly: every input it needs is passed in by the caller,
 * and its only output is a [TransitionPlan].
 *
 * Manual crossfades (skip next/previous, tapping a queue/playlist entry)
 * never go through this class — see [MusicService.startCrossfade] and the
 * other entry points, which keep using [TransitionPlanFactory] directly for
 * those, unchanged.
 */
class SmartCrossfadeEngine {
    /**
     * @param autoDurationMs the user's configured AUTO crossfade duration
     *   (`CrossfadeDurationKey`), resolved by the caller — this class never
     *   reads DataStore itself.
     * @param curve the user's configured crossfade curve, resolved by the
     *   caller.
     * @param outgoingPositionMs where the outgoing track is positioned right
     *   now; see [TransitionPlan.outgoingPositionMs] — informational only.
     * @param context reliably-available info about both tracks (currently
     *   just their durations) — read by nothing in this method yet; see the
     *   class doc comment.
     */
    fun createTransition(
        autoDurationMs: Long,
        curve: CrossfadeCurve,
        outgoingPositionMs: Long,
        context: TransitionContext,
    ): TransitionPlan = TransitionPlan(
        durationMs = autoDurationMs,
        outgoingPositionMs = outgoingPositionMs,
        incomingPositionMs = 0L,
        curve = curve,
    )
}
