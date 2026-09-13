package com.music.vivi.playback

import com.music.vivi.constants.CrossfadeCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for step 2.3: the crossfade executor
 * (`beginCrossfadeSwap` / `performCrossfadeSwap`) treats [TransitionPlan] as
 * the sole source of truth for duration, curve, and positions.
 *
 * SCOPE AND LIMITS: `beginCrossfadeSwap`/`performCrossfadeSwap` are private
 * methods on `MusicService`, an Android Service wired to a real ExoPlayer,
 * MediaSession, and DataStore — invoking them directly would need Robolectric
 * or an instrumented test, neither of which this project has (and this patch
 * deliberately doesn't add). What these tests DO cover: every
 * [TransitionPlan] a real entry point can produce, reproducing each entry
 * point's own construction logic exactly (see `MusicService.startCrossfade`
 * and friends). Combined with a plain code-inspection fact — confirmed by
 * reading `performCrossfadeSwap`'s body — that its only reads for duration
 * and curve are `plan.durationMs`/`plan.curve` (no leftover
 * `crossfadeDuration`/`crossfadeCurve` reads anywhere in that function), a
 * plan verified correct here is the plan the executor actually runs with.
 *
 * `incomingPositionMs` is applied one step earlier, inside `configure()`
 * (invoked from `beginCrossfadeSwap`, before `secPlayer.prepare()`) rather
 * than inside `performCrossfadeSwap` itself — the correct place, since the
 * secondary player needs its start position before it can prepare.
 * `outgoingPositionMs` stays informational (no seek), by design.
 */
class CrossfadeExecutionRegressionTest {

    private val curve = CrossfadeCurve.EASE_OUT_QUAD
    private val engine = SmartCrossfadeEngine()

    // --- 1 & 2: duration and curve come from the plan, nothing else ---

    @Test
    fun `AUTO plan carries the configured auto duration and curve`() {
        // Mirrors startCrossfade's AUTO branch.
        val plan = engine.createTransition(
            autoDurationMs = 5_000L,
            curve = curve,
            outgoingPositionMs = 42_000L,
            context = TransitionContext(),
        )

        assertEquals(5_000L, plan.durationMs)
        assertEquals(curve, plan.curve)
    }

    @Test
    fun `manual plan carries the configured manual duration and curve`() {
        // Mirrors startCrossfade's MANUAL_NEXT/MANUAL_PREVIOUS branch, and
        // startCrossfadeToIndex/startQueueCrossfade/startQueueCrossfadeWithPreload,
        // all of which build their plan the same way.
        val plan = TransitionPlanFactory.forCurrentSettings(
            isManualTransition = true,
            autoDurationMs = 5_000L,
            manualDurationMs = 2_000L,
            curve = curve,
            outgoingPositionMs = 42_000L,
        )

        assertEquals(2_000L, plan.durationMs)
        assertEquals(curve, plan.curve)
    }

    // --- 3: incomingPositionMs reaches secondary-player configuration ---

    @Test
    fun `default manual plan starts the incoming track at position zero`() {
        // startCrossfade (manual), startCrossfadeToIndex, and
        // startQueueCrossfadeWithPreload all rely on this default — none of
        // them pass incomingPositionMs explicitly.
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
    fun `startQueueCrossfade's plan carries the real resume position, not zero`() {
        // The one entry point that passes a non-default incomingPositionMs
        // (a synced/saved queue position) — see startQueueCrossfade.
        val resumePositionMs = 37_500L
        val plan = TransitionPlanFactory.forCurrentSettings(
            isManualTransition = true,
            autoDurationMs = 5_000L,
            manualDurationMs = 2_000L,
            curve = curve,
            outgoingPositionMs = 0L,
            incomingPositionMs = resumePositionMs,
        )

        assertEquals(resumePositionMs, plan.incomingPositionMs)
    }

    // --- 4: every entry point still produces a valid plan ---

    @Test
    fun `every entry point produces a plan with a positive duration and the right duration source`() {
        val autoDurationMs = 5_000L
        val manualDurationMs = 2_000L

        val autoPlan = engine.createTransition(
            autoDurationMs = autoDurationMs,
            curve = curve,
            outgoingPositionMs = 0L,
            context = TransitionContext(),
        )
        val manualNextOrPrevious = TransitionPlanFactory.forCurrentSettings(
            isManualTransition = true,
            autoDurationMs = autoDurationMs,
            manualDurationMs = manualDurationMs,
            curve = curve,
            outgoingPositionMs = 0L,
        )
        val crossfadeToIndex = TransitionPlanFactory.forCurrentSettings(
            isManualTransition = true,
            autoDurationMs = autoDurationMs,
            manualDurationMs = manualDurationMs,
            curve = curve,
            outgoingPositionMs = 0L,
        )
        val queueCrossfade = TransitionPlanFactory.forCurrentSettings(
            isManualTransition = true,
            autoDurationMs = autoDurationMs,
            manualDurationMs = manualDurationMs,
            curve = curve,
            outgoingPositionMs = 0L,
            incomingPositionMs = 37_500L,
        )
        val queueCrossfadeWithPreload = TransitionPlanFactory.forCurrentSettings(
            isManualTransition = true,
            autoDurationMs = autoDurationMs,
            manualDurationMs = manualDurationMs,
            curve = curve,
            outgoingPositionMs = 0L,
        )

        val allPlans = listOf(autoPlan, manualNextOrPrevious, crossfadeToIndex, queueCrossfade, queueCrossfadeWithPreload)
        allPlans.forEach { plan ->
            assertTrue("duration must be positive: $plan", plan.durationMs > 0)
            assertTrue("incomingPositionMs must not be negative: $plan", plan.incomingPositionMs >= 0)
        }

        assertEquals(autoDurationMs, autoPlan.durationMs)
        listOf(manualNextOrPrevious, crossfadeToIndex, queueCrossfade, queueCrossfadeWithPreload).forEach { plan ->
            assertEquals(manualDurationMs, plan.durationMs)
        }
    }

    // --- 5: no behavior change from wiring TransitionPlan/TransitionContext through ---

    @Test
    fun `AUTO plan is unaffected by which TransitionContext accompanies it`() {
        val withRealData = engine.createTransition(
            autoDurationMs = 5_000L,
            curve = curve,
            outgoingPositionMs = 42_000L,
            context = TransitionContext(outgoingDurationMs = 210_000L, incomingDurationMs = 195_000L),
        )
        val withEmptyContext = engine.createTransition(
            autoDurationMs = 5_000L,
            curve = curve,
            outgoingPositionMs = 42_000L,
            context = TransitionContext(),
        )

        assertEquals(withEmptyContext, withRealData)
    }

    @Test
    fun `only the relevant duration is used - AUTO ignores manualDurationMs and manual ignores autoDurationMs`() {
        // A regression here would mean a manual skip silently started using
        // the AUTO duration, or vice versa.
        val autoPlan = TransitionPlanFactory.forCurrentSettings(
            isManualTransition = false,
            autoDurationMs = 5_000L,
            manualDurationMs = 999_000L,
            curve = curve,
            outgoingPositionMs = 0L,
        )
        val manualPlan = TransitionPlanFactory.forCurrentSettings(
            isManualTransition = true,
            autoDurationMs = 999_000L,
            manualDurationMs = 2_000L,
            curve = curve,
            outgoingPositionMs = 0L,
        )

        assertEquals(5_000L, autoPlan.durationMs)
        assertEquals(2_000L, manualPlan.durationMs)
    }
}
