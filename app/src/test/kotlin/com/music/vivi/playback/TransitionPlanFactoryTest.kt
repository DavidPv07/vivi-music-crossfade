package com.music.vivi.playback

import com.music.vivi.constants.CrossfadeCurve
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [TransitionPlanFactory.forCurrentSettings] and the
 * [TransitionPlan] it builds.
 *
 * These exercise the exact decision every crossfade entry point in
 * [MusicService] makes (see `startCrossfade` and friends) without needing a
 * real [androidx.media3.exoplayer.ExoPlayer], [android.content.Context], or
 * DataStore — the factory is a pure function over already-resolved values.
 *
 * In particular, [nonZeroIncomingPositionIsPreservedExactly] is the
 * regression test for `incomingPositionMs` actually being wired end to end:
 * MusicService.setupSecondaryFromPrimaryQueue and the two direct
 * `setMediaItem(s)` call sites all read `plan.incomingPositionMs` (verified
 * by inspection — see MusicService's TransitionPlanFactory.forCurrentSettings
 * call sites), so a plan that carries a non-zero value here is the value
 * that will eventually reach ExoPlayer's own `seekTo`/`setMediaItem(s)` call.
 * No AutoMix decision-maker produces a non-zero value yet — this only proves
 * the pipe doesn't leak if/when one does.
 */
class TransitionPlanFactoryTest {

    private val curve = CrossfadeCurve.EASE_OUT_QUAD

    @Test
    fun `AUTO transition uses the auto duration, not the manual one`() {
        val plan = TransitionPlanFactory.forCurrentSettings(
            isManualTransition = false,
            autoDurationMs = 5_000L,
            manualDurationMs = 2_000L,
            curve = curve,
            outgoingPositionMs = 0L,
        )

        assertEquals(5_000L, plan.durationMs)
    }

    @Test
    fun `manual transition uses the manual duration, not the auto one`() {
        val plan = TransitionPlanFactory.forCurrentSettings(
            isManualTransition = true,
            autoDurationMs = 5_000L,
            manualDurationMs = 2_000L,
            curve = curve,
            outgoingPositionMs = 0L,
        )

        assertEquals(2_000L, plan.durationMs)
    }

    @Test
    fun `incomingPositionMs defaults to zero when the caller does not specify one`() {
        val plan = TransitionPlanFactory.forCurrentSettings(
            isManualTransition = true,
            autoDurationMs = 5_000L,
            manualDurationMs = 2_000L,
            curve = curve,
            outgoingPositionMs = 0L,
        )

        assertEquals(0L, plan.incomingPositionMs)
    }

    @Test
    fun `non-zero incomingPositionMs is preserved exactly`() {
        // Mirrors startQueueCrossfade, the one real caller that passes a
        // non-default value today (a synced/saved queue resume position) —
        // and stands in for a future AutoMix decision, e.g. "start the next
        // track at 12.4s instead of the beginning".
        val plan = TransitionPlanFactory.forCurrentSettings(
            isManualTransition = true,
            autoDurationMs = 5_000L,
            manualDurationMs = 2_000L,
            curve = curve,
            outgoingPositionMs = 0L,
            incomingPositionMs = 12_400L,
        )

        assertEquals(12_400L, plan.incomingPositionMs)
    }

    @Test
    fun `outgoingPositionMs is carried through unchanged`() {
        val plan = TransitionPlanFactory.forCurrentSettings(
            isManualTransition = false,
            autoDurationMs = 5_000L,
            manualDurationMs = 2_000L,
            curve = curve,
            outgoingPositionMs = 187_000L,
        )

        assertEquals(187_000L, plan.outgoingPositionMs)
    }

    @Test
    fun `curve is carried through unchanged`() {
        val plan = TransitionPlanFactory.forCurrentSettings(
            isManualTransition = false,
            autoDurationMs = 5_000L,
            manualDurationMs = 2_000L,
            curve = CrossfadeCurve.SMOOTHSTEP,
            outgoingPositionMs = 0L,
        )

        assertEquals(CrossfadeCurve.SMOOTHSTEP, plan.curve)
    }

    @Test
    fun `two plans built from the same inputs are equal`() {
        // TransitionPlan is a plain data class; this just pins down that
        // equality (relied on nowhere critical today, but cheap to guard)
        // keeps working if a field is ever added.
        val a = TransitionPlanFactory.forCurrentSettings(
            isManualTransition = true,
            autoDurationMs = 5_000L,
            manualDurationMs = 2_000L,
            curve = curve,
            outgoingPositionMs = 42_000L,
            incomingPositionMs = 12_400L,
        )
        val b = TransitionPlanFactory.forCurrentSettings(
            isManualTransition = true,
            autoDurationMs = 5_000L,
            manualDurationMs = 2_000L,
            curve = curve,
            outgoingPositionMs = 42_000L,
            incomingPositionMs = 12_400L,
        )

        assertEquals(a, b)
    }
}
