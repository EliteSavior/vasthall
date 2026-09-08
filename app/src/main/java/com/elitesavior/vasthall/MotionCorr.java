package com.elitesavior.vasthall;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Input → JNI → motion correlation for sticky-stick dumps. Does not change
 * stick ownership, Flat binding, or native look/move math.
 */
final class MotionCorr {
    static final float AXIS_EPS = 0.04f;
    static final float VEL_EPS = 0.05f;
    static final float LOOK_RATE_EPS = 0.15f;
    static final long STALE_MS = 80L;
    static final long LATCH_HOLD_MS = 200L;
    static final long STALE_AXIS_HOLD_MS = 150L;
    static final int RING_CAP = 300;
    static final long RING_WINDOW_MS = 5_000L;
    static final int SPARK_CAP = 60;

    static final int INPUT_NONZERO_MOTION_ZERO = 1;
    static final int INPUT_ZERO_MOTION_NONZERO = 2;
    static final int PUBLISH_NE_CONSUME = 4;
    static final int STALE_SAMPLE = 8;

    static final String WHY_MOTION_WITHOUT_INPUT = "MOTION_WITHOUT_INPUT";
    static final String WHY_STALE_AXIS = "STALE_AXIS";

    static final String CSV_HEADER = "t_ms,scheme,left.x,left.y,right.x,right.y,jump,"
            + "ownM,ownL,ownJ,sampleAgeMs,whoZeroed,"
            + "pubMx,pubMy,pubLx,pubLy,conMx,conMy,conLx,conLy,jniLagMs,"
            + "pawn.x,pawn.y,pawn.z,vel.x,vel.z,yaw,pitch,dYaw,dPitch,flags,"
            + "cmdMoveHeading,actMoveHeading,headingErrDeg,cmdLookRate,actLookRate";

    private MotionCorr() {
    }

    static boolean has(int flags, int bit) {
        return (flags & bit) != 0;
    }

    static float mag(float x, float y) {
        return (float) Math.hypot(x, y);
    }

    static float inputMag(Sample sample) {
        return Math.max(mag(sample.leftX, sample.leftY), mag(sample.rightX, sample.rightY));
    }

    static float velMag(Sample sample) {
        return mag(sample.velX, sample.velZ);
    }

    static float lookRate(Sample sample) {
        float dt = sample.dtSec <= 0.0f ? DebugHub.DT_MIN : sample.dtSec;
        return (Math.abs(sample.dYaw) + Math.abs(sample.dPitch)) / dt;
    }

    static int flags(Sample sample) {
        int bits = 0;
        float inMag = inputMag(sample);
        float vMag = velMag(sample);
        float look = lookRate(sample);
        boolean moving = vMag > VEL_EPS || look > LOOK_RATE_EPS;
        if (inMag > AXIS_EPS && !moving) {
            bits |= INPUT_NONZERO_MOTION_ZERO;
        }
        if (inMag <= AXIS_EPS && moving) {
            bits |= INPUT_ZERO_MOTION_NONZERO;
        }
        if (axisDelta(sample.pubMoveX, sample.conMoveX)
                || axisDelta(sample.pubMoveY, sample.conMoveY)
                || axisDelta(sample.pubLookX, sample.conLookX)
                || axisDelta(sample.pubLookY, sample.conLookY)) {
            bits |= PUBLISH_NE_CONSUME;
        }
        boolean axesLive = inMag > AXIS_EPS
                || mag(sample.pubMoveX, sample.pubMoveY) > AXIS_EPS
                || mag(sample.pubLookX, sample.pubLookY) > AXIS_EPS
                || mag(sample.conMoveX, sample.conMoveY) > AXIS_EPS
                || mag(sample.conLookX, sample.conLookY) > AXIS_EPS;
        if (axesLive && (sample.sampleAgeMs >= STALE_MS || sample.jniLagMs >= STALE_MS)) {
            bits |= STALE_SAMPLE;
        }
        return bits;
    }

    static String flagNames(int flags) {
        if (flags == 0) {
            return "-";
        }
        StringBuilder out = new StringBuilder();
        appendFlag(out, flags, INPUT_NONZERO_MOTION_ZERO, "INPUT_NONZERO_MOTION_ZERO");
        appendFlag(out, flags, INPUT_ZERO_MOTION_NONZERO, "INPUT_ZERO_MOTION_NONZERO");
        appendFlag(out, flags, PUBLISH_NE_CONSUME, "PUBLISH_NE_CONSUME");
        appendFlag(out, flags, STALE_SAMPLE, "STALE_SAMPLE");
        return out.toString();
    }

    /**
     * Camera-relative stick (x = strafe, y = forward) rotated by yaw (radians)
     * into world XZ. Heading is degrees clockwise from +Z (forward at yaw=0).
     */
    static float worldHeadingDeg(float stickX, float stickY, float yawRad) {
        float mag = mag(stickX, stickY);
        if (mag < AXIS_EPS) {
            return Float.NaN;
        }
        float sin = (float) Math.sin(yawRad);
        float cos = (float) Math.cos(yawRad);
        float worldX = stickX * cos + stickY * sin;
        float worldZ = -stickX * sin + stickY * cos;
        return (float) Math.toDegrees(Math.atan2(worldX, worldZ));
    }

    static float headingErrDeg(float cmdDeg, float actDeg) {
        if (Float.isNaN(cmdDeg) || Float.isNaN(actDeg)) {
            return Float.NaN;
        }
        float err = cmdDeg - actDeg;
        while (err > 180.0f) {
            err -= 360.0f;
        }
        while (err < -180.0f) {
            err += 360.0f;
        }
        return err;
    }

    static void fillHeadings(Sample sample) {
        sample.cmdMoveHeading = worldHeadingDeg(sample.leftX, sample.leftY, sample.yaw);
        sample.actMoveHeading = worldHeadingDeg(sample.velX, sample.velZ, 0.0f);
        sample.headingErrDeg = headingErrDeg(sample.cmdMoveHeading, sample.actMoveHeading);
    }

    static String formatCsvLine(Sample sample) {
        return sample.tMs + ","
                + nz(sample.scheme) + ","
                + f(sample.leftX) + "," + f(sample.leftY) + ","
                + f(sample.rightX) + "," + f(sample.rightY) + ","
                + (sample.jump ? 1 : 0) + ","
                + sample.moveOwner + "," + sample.lookOwner + "," + sample.jumpOwner + ","
                + sample.sampleAgeMs + ","
                + csvCell(sample.whoZeroed) + ","
                + f(sample.pubMoveX) + "," + f(sample.pubMoveY) + ","
                + f(sample.pubLookX) + "," + f(sample.pubLookY) + ","
                + f(sample.conMoveX) + "," + f(sample.conMoveY) + ","
                + f(sample.conLookX) + "," + f(sample.conLookY) + ","
                + sample.jniLagMs + ","
                + f(sample.pawnX) + "," + f(sample.pawnY) + "," + f(sample.pawnZ) + ","
                + f(sample.velX) + "," + f(sample.velZ) + ","
                + f(sample.yaw) + "," + f(sample.pitch) + ","
                + f(sample.dYaw) + "," + f(sample.dPitch) + ","
                + flagNames(sample.flags) + ","
                + f(sample.cmdMoveHeading) + "," + f(sample.actMoveHeading) + ","
                + f(sample.headingErrDeg) + ","
                + f(sample.cmdLookRate) + "," + f(sample.actLookRate);
    }

    static String formatLatchSummary(LatchSummary summary) {
        StringBuilder out = new StringBuilder();
        out.append("[LATCH_SUMMARY]\n");
        if (summary == null || !summary.latched) {
            out.append("latched=0\n");
            return out.toString();
        }
        out.append("latched=1\n");
        out.append("why=").append(nz(summary.why)).append('\n');
        out.append("first_t_ms=").append(summary.firstTMs).append('\n');
        out.append("last_t_ms=").append(summary.lastTMs).append('\n');
        out.append("peakAxesWhileOwnersEmpty=").append(f(summary.peakAxesWhileOwnersEmpty)).append('\n');
        out.append("peakJniLagMs=").append(summary.peakJniLagMs).append('\n');
        out.append("pauseCleared=").append(summary.pauseCleared ? 1 : 0).append('\n');
        out.append("frozen=").append(summary.frozen ? 1 : 0).append('\n');
        return out.toString();
    }

    static String lockupLine(long tMs, String why, String whoZeroed, float axisX, float axisY) {
        String who = whoZeroed == null || whoZeroed.isEmpty() ? "-" : whoZeroed;
        return "t_ms=" + tMs
                + " whoZeroed=" + who
                + " why=" + nz(why)
                + " zone=motion"
                + " axes=" + f(axisX) + "," + f(axisY);
    }

    static boolean ownersEmpty(Sample sample) {
        return sample.moveOwner < 0 && sample.lookOwner < 0 && sample.jumpOwner < 0;
    }

    static float axesMag(Sample sample) {
        return Math.max(
                Math.max(mag(sample.leftX, sample.leftY), mag(sample.rightX, sample.rightY)),
                Math.max(mag(sample.conMoveX, sample.conMoveY), mag(sample.conLookX, sample.conLookY)));
    }

    private static boolean axisDelta(float a, float b) {
        return Math.abs(a - b) > AXIS_EPS;
    }

    private static void appendFlag(StringBuilder out, int flags, int bit, String name) {
        if ((flags & bit) == 0) {
            return;
        }
        if (out.length() > 0) {
            out.append('|');
        }
        out.append(name);
    }

    static String f(float value) {
        if (Float.isNaN(value)) {
            return "nan";
        }
        return String.format(Locale.US, "%.4f", value);
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static String csvCell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.indexOf(',') >= 0 || value.indexOf(' ') >= 0) {
            return value.replace(',', '_').replace(' ', '_');
        }
        return value;
    }

    static final class Sample {
        long tMs;
        String scheme = "";
        float leftX;
        float leftY;
        float rightX;
        float rightY;
        boolean jump;
        int moveOwner = -1;
        int lookOwner = -1;
        int jumpOwner = -1;
        long sampleAgeMs;
        String whoZeroed = "";
        float pubMoveX;
        float pubMoveY;
        float pubLookX;
        float pubLookY;
        float conMoveX;
        float conMoveY;
        float conLookX;
        float conLookY;
        long jniLagMs;
        float pawnX;
        float pawnY;
        float pawnZ;
        float velX;
        float velZ;
        float yaw;
        float pitch;
        float dYaw;
        float dPitch;
        float dtSec;
        int flags;
        float cmdMoveHeading = Float.NaN;
        float actMoveHeading = Float.NaN;
        float headingErrDeg = Float.NaN;
        float cmdLookRate;
        float actLookRate;
    }

    static final class Ring {
        private final Sample[] slots;
        private int next;
        private int count;
        private Sample[] frozenSlots;
        private int frozenCount;

        Ring() {
            this(RING_CAP);
        }

        Ring(int cap) {
            this.slots = new Sample[Math.max(1, cap)];
        }

        void push(Sample sample) {
            slots[next] = copyOf(sample);
            next = (next + 1) % slots.length;
            if (count < slots.length) {
                count++;
            }
            pruneWindow(sample.tMs);
        }

        private void pruneWindow(long nowMs) {
            long cutoff = nowMs - RING_WINDOW_MS;
            while (count > 0) {
                int oldest = (next - count + slots.length) % slots.length;
                Sample first = slots[oldest];
                if (first == null || first.tMs >= cutoff) {
                    break;
                }
                count--;
            }
        }

        int size() {
            return count;
        }

        void clear() {
            next = 0;
            count = 0;
            frozenSlots = null;
            frozenCount = 0;
        }

        void freeze() {
            List<Sample> live = snapshot();
            frozenCount = live.size();
            frozenSlots = live.toArray(new Sample[0]);
        }

        boolean frozen() {
            return frozenSlots != null;
        }

        List<Sample> snapshot() {
            List<Sample> out = new ArrayList<>(count);
            int start = (next - count + slots.length) % slots.length;
            for (int i = 0; i < count; i++) {
                out.add(slots[(start + i) % slots.length]);
            }
            return out;
        }

        String toCsv() {
            return csv(snapshot());
        }

        String frozenCsv() {
            if (frozenSlots == null) {
                return csv(snapshot());
            }
            List<Sample> frozen = new ArrayList<>(frozenCount);
            for (int i = 0; i < frozenCount; i++) {
                frozen.add(frozenSlots[i]);
            }
            return csv(frozen);
        }

        private static String csv(List<Sample> samples) {
            StringBuilder out = new StringBuilder();
            out.append(CSV_HEADER).append('\n');
            for (Sample sample : samples) {
                out.append(formatCsvLine(sample)).append('\n');
            }
            return out.toString();
        }
    }

    static Sample copyOf(Sample src) {
        Sample dst = new Sample();
        dst.tMs = src.tMs;
        dst.scheme = src.scheme;
        dst.leftX = src.leftX;
        dst.leftY = src.leftY;
        dst.rightX = src.rightX;
        dst.rightY = src.rightY;
        dst.jump = src.jump;
        dst.moveOwner = src.moveOwner;
        dst.lookOwner = src.lookOwner;
        dst.jumpOwner = src.jumpOwner;
        dst.sampleAgeMs = src.sampleAgeMs;
        dst.whoZeroed = src.whoZeroed;
        dst.pubMoveX = src.pubMoveX;
        dst.pubMoveY = src.pubMoveY;
        dst.pubLookX = src.pubLookX;
        dst.pubLookY = src.pubLookY;
        dst.conMoveX = src.conMoveX;
        dst.conMoveY = src.conMoveY;
        dst.conLookX = src.conLookX;
        dst.conLookY = src.conLookY;
        dst.jniLagMs = src.jniLagMs;
        dst.pawnX = src.pawnX;
        dst.pawnY = src.pawnY;
        dst.pawnZ = src.pawnZ;
        dst.velX = src.velX;
        dst.velZ = src.velZ;
        dst.yaw = src.yaw;
        dst.pitch = src.pitch;
        dst.dYaw = src.dYaw;
        dst.dPitch = src.dPitch;
        dst.dtSec = src.dtSec;
        dst.flags = src.flags;
        dst.cmdMoveHeading = src.cmdMoveHeading;
        dst.actMoveHeading = src.actMoveHeading;
        dst.headingErrDeg = src.headingErrDeg;
        dst.cmdLookRate = src.cmdLookRate;
        dst.actLookRate = src.actLookRate;
        return dst;
    }

    static final class LatchSummary {
        boolean latched;
        String why = "";
        long firstTMs = -1L;
        long lastTMs = -1L;
        float peakAxesWhileOwnersEmpty;
        long peakJniLagMs;
        boolean pauseCleared;
        boolean frozen;
    }

    static final class LatchWatch {
        private long motionWithoutInputStart = -1L;
        private long staleAxisStart = -1L;
        private boolean latched;
        private String why = "";
        private long firstTMs = -1L;
        private long lastTMs = -1L;
        private float peakAxesWhileOwnersEmpty;
        private long peakJniLagMs;
        private boolean pauseSeen;
        private boolean pauseCleared;
        private boolean stamped;

        void clear() {
            motionWithoutInputStart = -1L;
            staleAxisStart = -1L;
            latched = false;
            why = "";
            firstTMs = -1L;
            lastTMs = -1L;
            peakAxesWhileOwnersEmpty = 0.0f;
            peakJniLagMs = 0L;
            pauseSeen = false;
            pauseCleared = false;
            stamped = false;
        }

        void onPause(boolean paused) {
            if (paused && latched) {
                pauseSeen = true;
            }
        }

        void onSample(Sample sample, boolean paused) {
            if (sample.jniLagMs > peakJniLagMs) {
                peakJniLagMs = sample.jniLagMs;
            }
            if (ownersEmpty(sample)) {
                float mag = axesMag(sample);
                if (mag > peakAxesWhileOwnersEmpty) {
                    peakAxesWhileOwnersEmpty = mag;
                }
            }
            boolean motionWithout = has(sample.flags, INPUT_ZERO_MOTION_NONZERO)
                    || (ownersEmpty(sample) && axesMag(sample) > AXIS_EPS);
            boolean stale = has(sample.flags, STALE_SAMPLE)
                    && (sample.jniLagMs >= STALE_MS || ownersEmpty(sample));
            if (motionWithout) {
                if (motionWithoutInputStart < 0L) {
                    motionWithoutInputStart = sample.tMs;
                }
            } else {
                motionWithoutInputStart = -1L;
            }
            if (stale) {
                if (staleAxisStart < 0L) {
                    staleAxisStart = sample.tMs;
                }
            } else {
                staleAxisStart = -1L;
            }
            if (!latched) {
                if (motionWithoutInputStart >= 0L
                        && sample.tMs - motionWithoutInputStart >= LATCH_HOLD_MS) {
                    latch(sample, WHY_MOTION_WITHOUT_INPUT, motionWithoutInputStart);
                } else if (staleAxisStart >= 0L
                        && sample.tMs - staleAxisStart >= STALE_AXIS_HOLD_MS) {
                    latch(sample, WHY_STALE_AXIS, staleAxisStart);
                }
            }
            if (latched) {
                lastTMs = sample.tMs;
                if (pauseSeen && paused && !has(sample.flags, INPUT_ZERO_MOTION_NONZERO)
                        && axesMag(sample) <= AXIS_EPS) {
                    pauseCleared = true;
                }
            }
        }

        boolean latched() {
            return latched;
        }

        String why() {
            return why;
        }

        boolean consumeStamp() {
            if (!latched || stamped) {
                return false;
            }
            stamped = true;
            return true;
        }

        LatchSummary summary() {
            LatchSummary summary = new LatchSummary();
            summary.latched = latched;
            summary.why = why;
            summary.firstTMs = firstTMs;
            summary.lastTMs = lastTMs;
            summary.peakAxesWhileOwnersEmpty = peakAxesWhileOwnersEmpty;
            summary.peakJniLagMs = peakJniLagMs;
            summary.pauseCleared = pauseCleared;
            summary.frozen = latched;
            return summary;
        }

        private void latch(Sample sample, String reason, long firstMs) {
            latched = true;
            why = reason;
            firstTMs = firstMs;
            lastTMs = sample.tMs;
        }
    }

    static final class Spark {
        private final float[] values;
        private int next;
        private int count;

        Spark() {
            this(SPARK_CAP);
        }

        Spark(int cap) {
            this.values = new float[Math.max(1, cap)];
        }

        void push(float value) {
            values[next] = value;
            next = (next + 1) % values.length;
            if (count < values.length) {
                count++;
            }
        }

        void clear() {
            next = 0;
            count = 0;
        }

        int size() {
            return count;
        }

        float[] snapshot() {
            float[] out = new float[count];
            int start = count < values.length ? 0 : next;
            for (int i = 0; i < count; i++) {
                out[i] = values[(start + i) % values.length];
            }
            return out;
        }

        String strip() {
            StringBuilder out = new StringBuilder();
            float[] live = snapshot();
            for (int i = 0; i < live.length; i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append(f(live[i]));
            }
            return out.toString();
        }
    }
}
