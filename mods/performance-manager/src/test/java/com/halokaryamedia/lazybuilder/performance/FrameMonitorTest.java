package com.halokaryamedia.lazybuilder.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameMonitorTest {
    @Test
    void raisesPressureAfterSustainedBadFrames() {
        FrameMonitor monitor = new FrameMonitor();

        monitor.recordFrameTimeMs(30.0D);
        monitor.recordFrameTimeMs(30.0D);
        monitor.recordFrameTimeMs(30.0D);

        assertEquals(FramePressure.ELEVATED, monitor.pressure());
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
    void pausedClockDoesNotTurnBackgroundGapIntoFrameSpike() {
        FrameMonitor monitor = new FrameMonitor();

        monitor.recordFrame(1_000_000_000L);
        monitor.recordFrame(1_016_000_000L);
        monitor.pauseFrameClock();
        monitor.recordFrame(10_000_000_000L);
        monitor.recordFrame(10_016_000_000L);

        assertEquals(2, monitor.sampleCount());
        assertEquals(16.0D, monitor.averageFrameTimeMs(), 0.0001D);
        assertEquals(FramePressure.NORMAL, monitor.pressure());
    }
}
