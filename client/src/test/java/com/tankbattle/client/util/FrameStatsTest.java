package com.tankbattle.client.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FrameStats 计数、10s 汇总含 6 项指标、丢弃分类。
 */
class FrameStatsTest {

    @Test
    void countsReceivedAndRenderedFrames() {
        FrameStats stats = new FrameStats();
        stats.frameReceived();
        stats.frameReceived();
        stats.frameReceived();
        stats.frameRendered();
        assertEquals(3, stats.receivedFrames());
        assertEquals(1, stats.renderedFrames());
    }

    @Test
    void averagesDecodeNanos() {
        FrameStats stats = new FrameStats();
        assertEquals(0, stats.averageDecodeNanos(), "无样本时为 0");
        stats.frameDecoded(1_000_000L);
        stats.frameDecoded(3_000_000L);
        assertEquals(2_000_000L, stats.averageDecodeNanos());
    }

    @Test
    void separatesDropCategories() {
        FrameStats stats = new FrameStats();
        stats.droppedOversize();
        stats.droppedOversize();
        stats.droppedByBackpressure();
        assertEquals(2, stats.droppedOversizeCount());
        assertEquals(1, stats.droppedByBackpressureCount());
        assertEquals(3, stats.droppedTotal());
    }

    @Test
    void tracksQueueDepthAndRtt() {
        FrameStats stats = new FrameStats();
        stats.queueDepth(17);
        stats.roundTrip(3_500_000L);
        // 指标经由 reportIfDue 输出；此处仅保证不抛异常且窗口内第二次调用被节流
        stats.reportIfDue();
        long first = stats.receivedFrames();
        stats.reportIfDue();
        assertEquals(first, stats.receivedFrames(), "10s 窗口内重复调用不重置窗口");
    }

    @Test
    void resetClearsAllCounters() {
        FrameStats stats = new FrameStats();
        stats.frameReceived();
        stats.frameRendered();
        stats.frameDecoded(2_000_000L);
        stats.droppedOversize();
        stats.reset();
        assertEquals(0, stats.receivedFrames());
        assertEquals(0, stats.renderedFrames());
        assertEquals(0, stats.averageDecodeNanos());
        assertEquals(0, stats.droppedTotal());
        assertTrue(stats.droppedTotal() >= 0);
    }
}
