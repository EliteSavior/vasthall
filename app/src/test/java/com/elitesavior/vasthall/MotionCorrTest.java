package com.elitesavior.vasthall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class MotionCorrTest {
    @Test
    public void flagsInputNonzeroMotionZero() {
        MotionCorr.Sample sample = base(10L);
        sample.leftX = 0.80f;
        sample.leftY = 0.0f;
        sample.velX = 0.0f;
        sample.velZ = 0.0f;
        sample.dYaw = 0.0f;
        sample.dPitch = 0.0f;
        sample.dtSec = 0.016f;
        int flags = MotionCorr.flags(sample);
        assertTrue(MotionCorr.has(flags, MotionCorr.INPUT_NONZERO_MOTION_ZERO));
        assertFalse(MotionCorr.has(flags, MotionCorr.INPUT_ZERO_MOTION_NONZERO));
    }

    @Test
    public void flagsInputZeroMotionNonzeroIsLatchClass() {
        MotionCorr.Sample sample = base(20L);
        sample.leftX = 0.0f;
        sample.leftY = 0.0f;
        sample.rightX = 0.0f;
        sample.rightY = 0.0f;
        sample.velX = 0.70f;
        sample.velZ = 0.10f;
        sample.dtSec = 0.016f;
        int flags = MotionCorr.flags(sample);
        assertTrue(MotionCorr.has(flags, MotionCorr.INPUT_ZERO_MOTION_NONZERO));
        assertFalse(MotionCorr.has(flags, MotionCorr.INPUT_NONZERO_MOTION_ZERO));
    }

    @Test
    public void flagsPublishNeConsumeBeyondEpsilon() {
        MotionCorr.Sample sample = base(30L);
        sample.pubMoveX = 0.50f;
        sample.conMoveX = 0.00f;
        int flags = MotionCorr.flags(sample);
        assertTrue(MotionCorr.has(flags, MotionCorr.PUBLISH_NE_CONSUME));
    }

    @Test
    public void flagsStaleSampleWhileAxesNonzero() {
        MotionCorr.Sample sample = base(40L);
        sample.leftX = 0.60f;
        sample.sampleAgeMs = 120L;
        sample.jniLagMs = 10L;
        int flags = MotionCorr.flags(sample);
        assertTrue(MotionCorr.has(flags, MotionCorr.STALE_SAMPLE));
    }

    @Test
    public void flagsQuietWhenAligned() {
        MotionCorr.Sample sample = base(50L);
        sample.leftX = 0.50f;
        sample.pubMoveX = 0.50f;
        sample.conMoveX = 0.50f;
        sample.velX = 0.50f;
        sample.dtSec = 0.016f;
        sample.sampleAgeMs = 8L;
        sample.jniLagMs = 4L;
        int flags = MotionCorr.flags(sample);
        assertEquals(0, flags);
    }

    @Test
    public void ringWrapsAndFormatsCsvWithHeadings() {
        MotionCorr.Ring ring = new MotionCorr.Ring(4);
        for (int i = 0; i < 6; i++) {
            MotionCorr.Sample sample = base(100L * i);
            sample.leftX = i * 0.1f;
            sample.cmdMoveHeading = 45.0f;
            sample.actMoveHeading = 50.0f;
            sample.headingErrDeg = 5.0f;
            sample.cmdLookRate = 0.2f;
            sample.actLookRate = 0.1f;
            ring.push(sample);
        }
        String csv = ring.toCsv();
        assertTrue(csv.contains("t_ms,scheme,left.x,left.y,right.x,right.y,jump,ownM,ownL,ownJ,sampleAgeMs,whoZeroed,"));
        assertTrue(csv.contains("cmdMoveHeading,actMoveHeading,headingErrDeg,cmdLookRate,actLookRate"));
        assertFalse(csv.contains("\n0,"));
        assertTrue(csv.contains("\n200,"));
        assertTrue(csv.contains("\n500,"));
        assertEquals(4, ring.size());
    }

    @Test
    public void headingErrIsWorldStickVsVelocity() {
        float cmd = MotionCorr.worldHeadingDeg(1.0f, 0.0f, 0.0f);
        float act = MotionCorr.worldHeadingDeg(0.0f, 1.0f, 0.0f);
        float err = MotionCorr.headingErrDeg(cmd, act);
        assertEquals(90.0f, Math.abs(err), 0.5f);
    }

    @Test
    public void latchSummaryAfterMotionWithoutInputHold() {
        MotionCorr.LatchWatch watch = new MotionCorr.LatchWatch();
        MotionCorr.Ring ring = new MotionCorr.Ring(32);
        for (int i = 0; i < 20; i++) {
            MotionCorr.Sample sample = base(16L * i);
            sample.moveOwner = -1;
            sample.lookOwner = -1;
            sample.jumpOwner = -1;
            sample.conMoveX = 0.80f;
            sample.velX = 0.80f;
            sample.dtSec = 0.016f;
            sample.flags = MotionCorr.flags(sample);
            ring.push(sample);
            watch.onSample(sample, false);
        }
        assertTrue(watch.latched());
        assertEquals(MotionCorr.WHY_MOTION_WITHOUT_INPUT, watch.why());
        MotionCorr.LatchSummary summary = watch.summary();
        assertEquals(0L, summary.firstTMs);
        assertEquals(304L, summary.lastTMs);
        assertTrue(summary.peakAxesWhileOwnersEmpty > 0.7f);
        String dump = MotionCorr.formatLatchSummary(summary);
        assertTrue(dump.contains("[LATCH_SUMMARY]"));
        assertTrue(dump.contains("latched=1"));
        assertTrue(dump.contains("why=MOTION_WITHOUT_INPUT"));
        assertTrue(dump.contains("pauseCleared=0"));
        ring.freeze();
        assertTrue(ring.frozen());
        assertTrue(ring.frozenCsv().contains("0.8000"));
    }

    @Test
    public void staleAxisLatchAndPauseCleared() {
        MotionCorr.LatchWatch watch = new MotionCorr.LatchWatch();
        for (int i = 0; i < 15; i++) {
            MotionCorr.Sample sample = base(16L * i);
            sample.moveOwner = 7;
            sample.leftX = 0.70f;
            sample.pubMoveX = 0.70f;
            sample.conMoveX = 0.70f;
            sample.velX = 0.70f;
            sample.sampleAgeMs = 8L;
            sample.jniLagMs = 200L;
            sample.dtSec = 0.016f;
            sample.flags = MotionCorr.flags(sample);
            watch.onSample(sample, false);
        }
        assertTrue(watch.latched());
        assertEquals(MotionCorr.WHY_STALE_AXIS, watch.why());
        watch.onPause(true);
        MotionCorr.Sample quiet = base(400L);
        quiet.dtSec = 0.016f;
        quiet.flags = MotionCorr.flags(quiet);
        watch.onSample(quiet, true);
        MotionCorr.LatchSummary summary = watch.summary();
        assertTrue(summary.pauseCleared);
        assertTrue(MotionCorr.formatLatchSummary(summary).contains("pauseCleared=1"));
    }

    @Test
    public void lockupLineUsesMotionWhy() {
        String line = MotionCorr.lockupLine(
                1200L, MotionCorr.WHY_MOTION_WITHOUT_INPUT, "TIMEOUT", 0.0f, 0.82f);
        assertTrue(line.contains("why=MOTION_WITHOUT_INPUT"));
        assertTrue(line.contains("whoZeroed=TIMEOUT"));
        assertTrue(line.contains("t_ms=1200"));
        assertTrue(line.contains("axes=0.0000,0.8200"));
    }

    @Test
    public void ownedHeldStillSampleAgeDoesNotLatch() {
        MotionCorr.LatchWatch watch = new MotionCorr.LatchWatch();
        for (int i = 0; i < 20; i++) {
            MotionCorr.Sample sample = base(16L * i);
            sample.moveOwner = 7;
            sample.leftX = 0.70f;
            sample.pubMoveX = 0.70f;
            sample.conMoveX = 0.70f;
            sample.velX = 0.70f;
            sample.sampleAgeMs = 250L;
            sample.jniLagMs = 4L;
            sample.dtSec = 0.016f;
            sample.flags = MotionCorr.flags(sample);
            watch.onSample(sample, false);
        }
        assertTrue(MotionCorr.has(MotionCorr.flags(baseWithHold()), MotionCorr.STALE_SAMPLE));
        assertFalse(watch.latched());
    }

    @Test
    public void frozenCsvDoesNotChangeAfterLaterPushes() {
        MotionCorr.Ring ring = new MotionCorr.Ring(16);
        MotionCorr.Sample first = base(10L);
        first.conMoveX = 0.80f;
        ring.push(first);
        ring.freeze();
        String frozen = ring.frozenCsv();
        MotionCorr.Sample later = base(20L);
        later.conMoveX = 0.10f;
        ring.push(later);
        assertEquals(frozen, ring.frozenCsv());
        assertTrue(ring.toCsv().contains("0.1000"));
        assertFalse(ring.frozenCsv().contains("0.1000"));
    }

    private static MotionCorr.Sample baseWithHold() {
        MotionCorr.Sample sample = base(0L);
        sample.moveOwner = 7;
        sample.leftX = 0.70f;
        sample.sampleAgeMs = 250L;
        sample.jniLagMs = 4L;
        return sample;
    }

    private static MotionCorr.Sample base(long tMs) {
        MotionCorr.Sample sample = new MotionCorr.Sample();
        sample.tMs = tMs;
        sample.scheme = "flat";
        sample.moveOwner = -1;
        sample.lookOwner = -1;
        sample.jumpOwner = -1;
        sample.whoZeroed = "";
        sample.dtSec = 0.016f;
        return sample;
    }
}
