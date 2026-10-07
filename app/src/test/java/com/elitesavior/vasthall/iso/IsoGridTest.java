package com.elitesavior.vasthall.iso;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class IsoGridTest {
    @Test
    public void defaultGridEmitsThirtyTwoByThirtyTwoGroundLines() {
        IsoGrid grid = new IsoGrid();
        assertEquals(32, grid.size());
        List<float[]> lines = new ArrayList<>();
        grid.forEachLine((x0, y0, z0, x1, y1, z1) ->
                lines.add(new float[] {x0, y0, z0, x1, y1, z1}));
        assertEquals(66, lines.size());
        assertEquals(66, grid.lineCount());
        boolean originX = false;
        boolean originZ = false;
        boolean farCorner = false;
        for (float[] line : lines) {
            assertEquals(0.0f, line[1], 0.0f);
            assertEquals(0.0f, line[4], 0.0f);
            if (line[0] == 0 && line[2] == 0 && line[3] == 32 && line[5] == 0) {
                originX = true;
            }
            if (line[0] == 0 && line[2] == 0 && line[3] == 0 && line[5] == 32) {
                originZ = true;
            }
            if (line[0] == 0 && line[2] == 32 && line[3] == 32 && line[5] == 32) {
                farCorner = true;
            }
            if (line[0] == 32 && line[2] == 0 && line[3] == 32 && line[5] == 32) {
                farCorner = true;
            }
        }
        assertTrue(originX);
        assertTrue(originZ);
        assertTrue(farCorner);
    }
}
