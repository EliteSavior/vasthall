package com.elitesavior.vasthall.iso;

/**
 * Logical ground-plane tile grid in XZ (Y up). Default 32×32 cells from
 * the origin to {@link #size()} on each axis.
 */
public final class IsoGrid {
    public static final int DEFAULT_SIZE = 32;

    public interface LineVisitor {
        void line(float x0, float y0, float z0, float x1, float y1, float z1);
    }

    private final int size;

    public IsoGrid() {
        this(DEFAULT_SIZE);
    }

    public IsoGrid(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("grid size");
        }
        this.size = size;
    }

    public int size() {
        return size;
    }

    public int lineCount() {
        return (size + 1) * 2;
    }

    public void forEachLine(LineVisitor visitor) {
        if (visitor == null) {
            return;
        }
        float extent = size;
        for (int i = 0; i <= size; i++) {
            float t = i;
            visitor.line(0.0f, 0.0f, t, extent, 0.0f, t);
            visitor.line(t, 0.0f, 0.0f, t, 0.0f, extent);
        }
    }
}
