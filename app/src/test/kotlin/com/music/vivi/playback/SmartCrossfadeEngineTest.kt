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
 *
 * The `context: TransitionContext`-specific tests near the bottom exist to
 * prove the data path from MusicService to this class is real (see
 * MusicService.startCrossfade's AUTO branch) without yet influencing any
 * decision — see TransitionContext's own doc comment for why it's nullable.
 */
class SmartCrossfadeEngineTest {

    private val engine = SmartCrossfadeEngine()

    // An arbitrary non-empty context, used by tests that don't care what's
    // in it, to make clear the assertions below hold regardless of content.
    private val sampleContext = TransitionContext(
        outgoingDurationMs = 210_000L,
        incomingDurationMs = 195_000L,
    )

    @Test
    fun `uses the given auto duration`() {
        val plan = engine.createTransition(
            autoDurationMs = 5_000L,
            curve = CrossfadeCurve.EASE_OUT_QUAD,
            outgoingPositionMs = 0L,
            context = sampleContext,
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
            context = sampleContext,
        )

        assertEquals(0L, plan.incomingPositionMs)
    }

    @Test
    fun `carries outgoingPositionMs through unchanged`() {
        val plan = engine.createTransition(
            autoDurationMs = 5_000L,
            curve = CrossfadeCurve.EASE_OUT_QUAD,
            outgoingPositionMs = 187_000L,
            context = sampleContext,
        )

        assertEquals(187_000L, plan.outgoingPositionMs)
    }

    @Test
    fun `carries the curve through unchanged`() {
        val plan = engine.createTransition(
            autoDurationMs = 5_000L,
            curve = CrossfadeCurve.SMOOTHSTEP,
            outgoingPositionMs = 0L,
            context = sampleContext,
        )

        assertEquals(CrossfadeCurve.SMOOTHSTEP, plan.curve)
    }

    @Test
    fun `is deterministic - same inputs always produce an equal plan`() {
        val a = engine.createTransition(
            autoDurationMs = 8_400L,
            curve = CrossfadeCurve.EQUAL_POWER,
            outgoingPositionMs = 42_000L,
            context = sampleContext,
        )
        val b = engine.createTransition(
            autoDurationMs = 8_400L,
            curve = CrossfadeCurve.EQUAL_POWER,
            outgoingPositionMs = 42_000L,
            context = sampleContext,
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
            context = sampleContext,
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

    // --- TransitionContext wiring: proves the parameter is connected but inert ---

    @Test
    fun `an empty TransitionContext produces the same plan as before this parameter existed`() {
        val plan = engine.createTransition(
            autoDurationMs = 5_000L,
            curve = CrossfadeCurve.EASE_OUT_QUAD,
            outgoingPositionMs = 42_000L,
            context = TransitionContext(),
        )

        val expected = TransitionPlan(
            durationMs = 5_000L,
            outgoingPositionMs = 42_000L,
            incomingPositionMs = 0L,
            curve = CrossfadeCurve.EASE_OUT_QUAD,
        )
        assertEquals(expected, plan)
    }

    @Test
    fun `a TransitionContext with real durations produces exactly the same plan`() {
        // Same autoDurationMs/curve/outgoingPositionMs as the previous test,
        // only the context differs (empty vs. real-looking durations) — the
        // resulting plan must be identical either way.
        val plan = engine.createTransition(
            autoDurationMs = 5_000L,
            curve = CrossfadeCurve.EASE_OUT_QUAD,
            outgoingPositionMs = 42_000L,
            context = TransitionContext(
                outgoingDurationMs = 210_000L,
                incomingDurationMs = 195_000L,
            ),
        )

        val expected = TransitionPlan(
            durationMs = 5_000L,
            outgoingPositionMs = 42_000L,
            incomingPositionMs = 0L,
            curve = CrossfadeCurve.EASE_OUT_QUAD,
        )
        assertEquals(expected, plan)
    }

    @Test
    fun `context is connected but does not affect the decision - varying it alone changes nothing`() {
        // Holds every other input fixed and only varies the context, across
        // several shapes (empty, only-outgoing-known, only-incoming-known,
        // both known, both wildly different from the other cases) — the plan
        // must come out equal every time. This is the explicit "wired but
        // inert" guarantee for this stage of the architecture.
        val contexts = listOf(
            TransitionContext(),
            TransitionContext(outgoingDurationMs = 210_000L),
            TransitionContext(incomingDurationMs = 195_000L),
            TransitionContext(outgoingDurationMs = 210_000L, incomingDurationMs = 195_000L),
            TransitionContext(outgoingDurationMs = 1L, incomingDurationMs = 999_999L),
        )

        val plans = contexts.map { context ->
            engine.createTransition(
                autoDurationMs = 7_200L,
                curve = CrossfadeCurve.SMOOTHSTEP,
                outgoingPositionMs = 15_000L,
                context = context,
            )
        }

        plans.forEach { plan -> assertEquals(plans.first(), plan) }
    }
}
