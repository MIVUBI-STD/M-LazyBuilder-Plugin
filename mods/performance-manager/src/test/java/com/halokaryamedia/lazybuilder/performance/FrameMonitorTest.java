package com.halokaryamedia.lazybuilder.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameMonitorTest {

    @Test
    void worldSessionResetClearsPressureHistory() {
        FrameMonitor monitor = new FrameMonitor();
        monitor.recordFrameTimeMs(80.0D);
        monitor.recordFrameTimeMs(80.0D);
        monitor.recordFrameTimeMs(80.0D);

        assertEquals(FramePressure.HEAVY, monitor.pressure());
        assertTrue(monitor.sampleCount() > 0);

        monitor.resetSession();

        assertEquals(FramePressure.NORMAL, monitor.pressure());
        assertEquals(0, monitor.sampleCount());
        assertEquals(0.0D, monitor.averageFrameTimeMs(), 0.0001D);
        assertEquals(0.0D, monitor.worstRecentFrameTimeMs(), 0.0001D);
        assertEquals(0L, monitor.currentFrameNanos());
    }

    @Test
    void raisesPressureAfterSustainedBadFramesAtSixtyFpsTarget() {
        FrameMonitor monitor = new FrameMonitor();

        monitor.recordFrameTimeMs(30.0D);
        monitor.recordFrameTimeMs(30.0D);
        monitor.recordFrameTimeMs(30.0D);

        assertEquals(FramePressure.ELEVATED, monitor.pressure());
    }

    @Test
    void intentionalThirtyFpsTargetDoesNotLookLikeLag() {
        FrameMonitor monitor = new FrameMonitor();
        double targetFrameMs = 1000.0D / 30.0D;

        for (int index = 0; index < 120; index++) {
            monitor.recordFrameTimeMs(targetFrameMs, targetFrameMs);
        }

        assertEquals(FramePressure.NORMAL, monitor.pressure());
    }

    @Test
    void raisesHeavyPressureAfterSevereFrames() {
        FrameMonitor monitor = new FrameMonitor();

        monitor.recordFrameTimeMs(80.0D);
        monitor.recordFrameTimeMs(80.0D);
        monitor.recordFrameTimeMs(80.0D);

        assertEquals(FramePressure.HEAVY, monitor.pressure());
        assertTrue(monitor.worstRecentFrameTimeMs() >= 80.0D);
    }

    @Test
    void rollingWindowIsBounded() {
        FrameMonitor monitor = new FrameMonitor();
        for (int index = 0; index < 120; index++) {
            monitor.recordFrameTimeMs(16.0D);
        }

        assertEquals(60, monitor.sampleCount());
        assertEquals(16.0D, monitor.averageFrameTimeMs(), 0.0001D);
    }

    @Test
    void exposesLatestRenderedFrameTimestamp() {
        FrameMonitor monitor = new FrameMonitor();
        monitor.recordFrame(1_000_000_000L, 60);
        assertEquals(1_000_000_000L, monitor.currentFrameNanos());
        monitor.recordFrame(1_016_000_000L, 60);
        assertEquals(1_016_000_000L, monitor.currentFrameNanos());
    }

    @Test
    void pausedClockDoesNotTurnBackgroundGapIntoFrameSpike() {
        FrameMonitor monitor = new FrameMonitor();

        monitor.recordFrame(1_000_000_000L, 60);
        monitor.recordFrame(1_016_000_000L, 60);
        monitor.pauseFrameClock();
        assertEquals(0L, monitor.currentFrameNanos());
        monitor.recordFrame(10_000_000_000L, 60);
        monitor.recordFrame(10_016_000_000L, 60);

        assertEquals(2, monitor.sampleCount());
        assertEquals(16.0D, monitor.averageFrameTimeMs(), 0.0001D);
        assertEquals(FramePressure.NORMAL, monitor.pressure());
    }

    @Test
    void longRenderGapIsTreatedAsDiscontinuity() {
        FrameMonitor monitor = new FrameMonitor();

        monitor.recordFrame(1_000_000_000L, 60);
        monitor.recordFrame(1_016_000_000L, 60);
        monitor.recordFrame(3_000_000_000L, 60);
        monitor.recordFrame(3_016_000_000L, 60);

        assertEquals(2, monitor.sampleCount());
        assertEquals(16.0D, monitor.averageFrameTimeMs(), 0.0001D);
        assertEquals(FramePressure.NORMAL, monitor.pressure());
    }
    @Test
    void exposesFramePercentilesAndStutterCountsOnDemand() {
        FrameMonitor monitor = new FrameMonitor();
        for (int index = 0; index < 50; index++) monitor.recordFrameTimeMs(10.0D);
        for (int index = 0; index < 5; index++) monitor.recordFrameTimeMs(20.0D);
        for (int index = 0; index < 3; index++) monitor.recordFrameTimeMs(30.0D);
        monitor.recordFrameTimeMs(40.0D);
        monitor.recordFrameTimeMs(60.0D);

        FrameMonitor.TimingSnapshot snapshot = monitor.timingSnapshot();

        assertEquals(60, snapshot.samples());
        assertEquals(10.0D, snapshot.p50Ms(), 0.0001D);
        assertTrue(snapshot.p95Ms() >= 20.0D);
        assertTrue(snapshot.p99Ms() >= 40.0D);
        assertEquals(10, snapshot.framesOver16_67Ms());
        assertEquals(5, snapshot.framesOver25Ms());
        assertEquals(2, snapshot.framesOver33_33Ms());
        assertEquals(1, snapshot.framesOver50Ms());
    }
}
