package com.music.vivi.playback

import com.music.vivi.constants.CrossfadeCurve

/**
 * Describes one crossfade transition: duration, each player's position, and
 * the volume curve. This is the boundary between *deciding* a transition
 * (today: [MusicService.startCrossfade]'s settings, or [SmartCrossfadeEngine];
 * later: real audio analysis) and *executing* one ([MusicService.beginCrossfadeSwap]
 * / [performCrossfadeSwap]) — the executor only ever reads a plan, never
 * settings or track-analysis state directly. A decision-maker must not touch
 * ExoPlayer, volume, listeners, DataStore, or cleanup; that stays in the
 * crossfade engine.
 *
 * NAMING: unrelated to [MusicService.automixItems] / [MusicService.getAutomix]
 * (YouTube Music's queue-continuation feature — song *selection*, not
 * transition *timing*). [SmartCrossfadeEngine] avoids the word "AutoMix" for
 * that reason.
 *
 * Deliberately minimal — only add fields once something real consumes them.
 */
data class TransitionPlan(
    /** How long the fade should take, in milliseconds. */
    val durationMs: Long,
    /**
     * Where the outgoing track sits (ms) when the transition begins.
     * Informational only — nothing seeks the outgoing player to this.
     */
    val outgoingPositionMs: Long,
    /**
     * Where the incoming track starts playback from, in milliseconds. Almost
     * always 0; `startQueueCrossfade` passes a real resume position instead.
     * Non-zero values are supported for a future smarter decision-maker, but
     * nothing produces one yet.
     */
    val incomingPositionMs: Long,
    /** Volume ramp shape for this transition. */
    val curve: CrossfadeCurve,
)

/**
 * Builds the plan for every manual crossfade entry point, and (via
 * [SmartCrossfadeEngine]) the automatic one too: picks the AUTO or
 * manual-skip duration and carries the curve/positions through. Pure
 * function of already-resolved values — no Player/DataStore/MusicService —
 * so it's unit-testable and swappable for a smarter decision-maker later
 * without touching the crossfade engine.
 */
internal object TransitionPlanFactory {
    /**
     * @param isManualTransition true for a user skip/tap, false for an
     *   unattended end-of-track AUTO crossfade.
     * @param incomingPositionMs defaults to 0; `startQueueCrossfade` is the
     *   one caller that passes a real resume position instead.
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
 * Reliably-available info about the two tracks in a transition, passed to a
 * decision-maker like [SmartCrossfadeEngine]. Durations only for now — no
 * tempo/energy/silence analysis. Both nullable: [outgoingDurationMs] comes
 * from `player.duration` (unset until ExoPlayer resolves it);
 * [incomingDurationMs] comes from catalog metadata on the target `MediaItem`
 * (see `com.music.vivi.extensions.metadata`) rather than ExoPlayer, since the
 * incoming track is often unbuffered — accurate in practice, but not
 * guaranteed for every MediaItem construction path.
 */
data class TransitionContext(
    val outgoingDurationMs: Long? = null,
    val incomingDurationMs: Long? = null,
)

/**
 * Decides the [TransitionPlan] for an automatic (unattended) crossfade —
 * the seam that lets smarter, audio-analysis-driven timing live here later
 * without the crossfade engine changing. See [TransitionPlan]'s doc comment
 * for why this isn't called "AutoMix".
 *
 * Deliberately dumb today: reproduces exactly what `startCrossfade` built
 * inline for `CrossfadeTrigger.AUTO` before this class existed — same
 * duration, curve, and incoming position 0. [context] is required so the
 * data path from MusicService is real and exercised, but nothing here reads
 * it yet.
 *
 * Like [TransitionPlanFactory], never touches ExoPlayer/DataStore/playback
 * state — only inputs in, a [TransitionPlan] out. Manual crossfades never go
 * through this class; they keep using [TransitionPlanFactory] directly.
 */
class SmartCrossfadeEngine {
    /**
     * @param autoDurationMs the configured AUTO crossfade duration, resolved
     *   by the caller.
     * @param curve the configured crossfade curve, resolved by the caller.
     * @param outgoingPositionMs see [TransitionPlan.outgoingPositionMs].
     * @param context durations for both tracks — unused here for now.
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
