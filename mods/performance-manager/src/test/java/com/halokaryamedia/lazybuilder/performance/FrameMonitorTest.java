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
}
