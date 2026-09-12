package com.music.vivi.playback

import com.music.vivi.constants.CrossfadeCurve
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [SmartCrossfadeEngine.createTransition].
 *
 * These pin down that the engine is currently "dumb on purpose": given the
 * same inputs MusicService already resolves for a plain AUTO crossfade, it
 * must produce the exact same [TransitionPlan] the inline code built before
 * this class existed (see [TransitionPlanFactoryTest] for the equivalent
 * manual-path coverage). No audio analysis, no randomness, no hidden state.
 */
class SmartCrossfadeEngineTest {

    private val engine = SmartCrossfadeEngine()

    @Test
    fun `uses the given auto duration`() {
        val plan = engine.createTransition(
            autoDurationMs = 5_000L,
            curve = CrossfadeCurve.EASE_OUT_QUAD,
            outgoingPositionMs = 0L,
        )

        assertEquals(5_000L, plan.durationMs)
    }

    @Test
    fun `always starts the incoming track from position zero for now`() {
        // No entry-point analysis exists yet — see the class doc comment.
        // This test exists so that the day someone adds one, they have to
        // consciously change this assertion instead of silently drifting.
        val plan = engine.createTransition(
            autoDurationMs = 5_000L,
            curve = CrossfadeCurve.EASE_OUT_QUAD,
            outgoingPositionMs = 187_000L,
        )

        assertEquals(0L, plan.incomingPositionMs)
    }

    @Test
    fun `carries outgoingPositionMs through unchanged`() {
        val plan = engine.createTransition(
            autoDurationMs = 5_000L,
            curve = CrossfadeCurve.EASE_OUT_QUAD,
            outgoingPositionMs = 187_000L,
        )

        assertEquals(187_000L, plan.outgoingPositionMs)
    }

    @Test
    fun `carries the curve through unchanged`() {
        val plan = engine.createTransition(
            autoDurationMs = 5_000L,
            curve = CrossfadeCurve.SMOOTHSTEP,
            outgoingPositionMs = 0L,
        )

        assertEquals(CrossfadeCurve.SMOOTHSTEP, plan.curve)
    }

    @Test
    fun `is deterministic - same inputs always produce an equal plan`() {
        val a = engine.createTransition(
            autoDurationMs = 8_400L,
            curve = CrossfadeCurve.EQUAL_POWER,
            outgoingPositionMs = 42_000L,
        )
        val b = engine.createTransition(
            autoDurationMs = 8_400L,
            curve = CrossfadeCurve.EQUAL_POWER,
            outgoingPositionMs = 42_000L,
        )

        assertEquals(a, b)
    }

    @Test
    fun `matches what TransitionPlanFactory builds for a non-manual transition with the same inputs`() {
        // Regression guard for the seam itself: plugging SmartCrossfadeEngine
        // into MusicService.startCrossfade's AUTO branch must not change
        // behavior. manualDurationMs is irrelevant here since
        // isManualTransition is false — passing a deliberately different
        // value proves it's ignored, not just coincidentally equal.
        val autoDurationMs = 6_500L
        val curve = CrossfadeCurve.EASE_OUT_CUBIC
        val outgoingPositionMs = 99_000L

        val fromEngine = engine.createTransition(
            autoDurationMs = autoDurationMs,
            curve = curve,
            outgoingPositionMs = outgoingPositionMs,
        )
        val fromFactory = TransitionPlanFactory.forCurrentSettings(
            isManualTransition = false,
            autoDurationMs = autoDurationMs,
            manualDurationMs = 999_000L,
            curve = curve,
            outgoingPositionMs = outgoingPositionMs,
        )

        assertEquals(fromFactory, fromEngine)
    }
}
