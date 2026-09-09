package com.music.vivi.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [ShuffleOrderUtils.currentTraversal].
 *
 * Real ExoPlayer playlist timelines delegate shuffle-aware window navigation
 * ([Timeline.getNextWindowIndex] / [Timeline.getPreviousWindowIndex] with
 * `shuffleModeEnabled = true`) to a [ShuffleOrder]. [FakeShuffledTimeline]
 * below reproduces exactly that delegation — verified against media3's own
 * `Timeline` source, which otherwise defaults to plain sequential
 * `windowIndex + 1` / `windowIndex - 1` navigation regardless of shuffle —
 * so these tests exercise the same code path [MusicService] relies on at
 * runtime, without needing a real ExoPlayer instance.
 */
class ShuffleOrderUtilsTest {

    @Test
    fun `empty timeline returns null`() {
        val timeline = FakeShuffledTimeline(DefaultShuffleOrder(IntArray(0), 0L))
        assertNull(ShuffleOrderUtils.currentTraversal(timeline, currentIndex = 0))
    }

    @Test
    fun `unset current index returns null`() {
        val timeline = FakeShuffledTimeline(DefaultShuffleOrder(intArrayOf(0, 1, 2), 0L))
        assertNull(ShuffleOrderUtils.currentTraversal(timeline, currentIndex = C.INDEX_UNSET))
    }

    @Test
    fun `single item timeline returns just that item`() {
        val timeline = FakeShuffledTimeline(DefaultShuffleOrder(intArrayOf(0), 0L))
        assertArrayEquals(intArrayOf(0), ShuffleOrderUtils.currentTraversal(timeline, currentIndex = 0))
    }

    @Test
    fun `sequential order is reproduced verbatim when current item is at the start`() {
        // Shuffle order happens to equal the original (unshuffled) order.
        val timeline = FakeShuffledTimeline(DefaultShuffleOrder(intArrayOf(0, 1, 2, 3, 4), 0L))
        assertArrayEquals(
            intArrayOf(0, 1, 2, 3, 4),
            ShuffleOrderUtils.currentTraversal(timeline, currentIndex = 0),
        )
    }

    @Test
    fun `traversal captures full shuffled order with current item not first`() {
        // Media items 0..4 play in this shuffled order: 3, 1, 4, 0, 2.
        // The user is currently on media item 4, which sits in the middle of
        // that order (position 2) — this is the exact "current item not
        // necessarily first" case the function's doc comment calls out.
        val shuffledOrder = intArrayOf(3, 1, 4, 0, 2)
        val timeline = FakeShuffledTimeline(DefaultShuffleOrder(shuffledOrder, 0L))

        val result = ShuffleOrderUtils.currentTraversal(timeline, currentIndex = 4)

        assertArrayEquals(shuffledOrder, result)
    }

    @Test
    fun `traversal starting from the last item in shuffle order has nothing after it`() {
        val shuffledOrder = intArrayOf(3, 1, 4, 0, 2)
        val timeline = FakeShuffledTimeline(DefaultShuffleOrder(shuffledOrder, 0L))

        // Currently on media item 2, the LAST item in the shuffled order.
        val result = ShuffleOrderUtils.currentTraversal(timeline, currentIndex = 2)

        assertArrayEquals(shuffledOrder, result)
    }

    @Test
    fun `traversal starting from the first item in shuffle order has nothing before it`() {
        val shuffledOrder = intArrayOf(3, 1, 4, 0, 2)
        val timeline = FakeShuffledTimeline(DefaultShuffleOrder(shuffledOrder, 0L))

        // Currently on media item 3, the FIRST item in the shuffled order.
        val result = ShuffleOrderUtils.currentTraversal(timeline, currentIndex = 3)

        assertArrayEquals(shuffledOrder, result)
    }

    /**
     * Minimal fake [Timeline] used only by this test. Delegates
     * shuffle-mode-enabled window navigation to a [ShuffleOrder] — mirroring
     * how ExoPlayer's real internal playlist timeline does it — while
     * everything not exercised by [ShuffleOrderUtils.currentTraversal]
     * (period/window content, non-shuffle navigation) is left at whatever the
     * minimal, valid default is, since it's never read by these tests.
     */
    private class FakeShuffledTimeline(private val shuffleOrder: ShuffleOrder) : Timeline() {
        private val length = shuffleOrder.length

        override fun getWindowCount(): Int = length

        override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window =
            window.set(
                /* uid = */ windowIndex,
                /* mediaItem = */ null,
                /* manifest = */ null,
                /* presentationStartTimeMs = */ C.TIME_UNSET,
                /* windowStartTimeMs = */ C.TIME_UNSET,
                /* elapsedRealtimeEpochOffsetMs = */ C.TIME_UNSET,
                /* isSeekable = */ true,
                /* isDynamic = */ false,
                /* liveConfiguration = */ null,
                /* defaultPositionUs = */ 0L,
                /* durationUs = */ C.TIME_UNSET,
                /* firstPeriodIndex = */ windowIndex,
                /* lastPeriodIndex = */ windowIndex,
                /* positionInFirstPeriodUs = */ 0L,
            )

        override fun getPeriodCount(): Int = length

        override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period =
            period.set(
                /* id = */ if (setIds) periodIndex else null,
                /* uid = */ if (setIds) periodIndex else null,
                /* windowIndex = */ periodIndex,
                /* durationUs = */ C.TIME_UNSET,
                /* positionInWindowUs = */ 0L,
            )

        override fun getIndexOfPeriod(uid: Any): Int = uid as? Int ?: C.INDEX_UNSET

        override fun getUidOfPeriod(periodIndex: Int): Any = periodIndex

        override fun getFirstWindowIndex(shuffleModeEnabled: Boolean): Int =
            if (shuffleModeEnabled) shuffleOrder.firstIndex else super.getFirstWindowIndex(false)

        override fun getLastWindowIndex(shuffleModeEnabled: Boolean): Int =
            if (shuffleModeEnabled) shuffleOrder.lastIndex else super.getLastWindowIndex(false)

        override fun getNextWindowIndex(
            windowIndex: Int,
            repeatMode: Int,
            shuffleModeEnabled: Boolean,
        ): Int {
            if (!shuffleModeEnabled || repeatMode != Player.REPEAT_MODE_OFF) {
                return super.getNextWindowIndex(windowIndex, repeatMode, shuffleModeEnabled)
            }
            return shuffleOrder.getNextIndex(windowIndex)
        }

        override fun getPreviousWindowIndex(
            windowIndex: Int,
            repeatMode: Int,
            shuffleModeEnabled: Boolean,
        ): Int {
            if (!shuffleModeEnabled || repeatMode != Player.REPEAT_MODE_OFF) {
                return super.getPreviousWindowIndex(windowIndex, repeatMode, shuffleModeEnabled)
            }
            return shuffleOrder.getPreviousIndex(windowIndex)
        }
    }
}
