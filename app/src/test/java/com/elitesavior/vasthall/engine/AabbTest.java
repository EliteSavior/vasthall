package com.elitesavior.vasthall.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AabbTest {
    @Test
    public void fromCenterExtentBuildsMinMax() {
        Aabb box = Aabb.fromCenterExtent(1.0f, 2.0f, 3.0f, 0.5f, 1.0f, 1.5f);
        assertEquals(0.5f, box.min.x, 0.0001f);
        assertEquals(1.0f, box.min.y, 0.0001f);
        assertEquals(1.5f, box.min.z, 0.0001f);
        assertEquals(1.5f, box.max.x, 0.0001f);
        assertEquals(3.0f, box.max.y, 0.0001f);
        assertEquals(4.5f, box.max.z, 0.0001f);
    }

    @Test
    public void negativeExtentIsAbsorbed() {
        Aabb box = Aabb.fromCenterExtent(0.0f, 0.0f, 0.0f, -2.0f, -0.5f, -1.0f);
        assertEquals(-2.0f, box.min.x, 0.0001f);
        assertEquals(2.0f, box.max.x, 0.0001f);
        assertEquals(-0.5f, box.min.y, 0.0001f);
        assertEquals(0.5f, box.max.y, 0.0001f);
    }

    @Test
    public void overlappingBoxesIntersect() {
        Aabb a = Aabb.fromCenterExtent(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f);
        Aabb b = Aabb.fromCenterExtent(1.5f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f);
        assertTrue(a.intersects(b));
        assertTrue(b.intersects(a));
    }

    @Test
    public void touchingEdgesCountAsOverlap() {
        Aabb a = Aabb.fromCenterExtent(0.0f, 0.0f, 0.0f, 0.5f, 0.5f, 0.5f);
        Aabb b = Aabb.fromCenterExtent(1.0f, 0.0f, 0.0f, 0.5f, 0.5f, 0.5f);
        assertTrue(a.intersects(b));
    }

    @Test
    public void separatedBoxesDoNotIntersect() {
        Aabb a = Aabb.fromCenterExtent(0.0f, 0.0f, 0.0f, 0.5f, 0.5f, 0.5f);
        Aabb b = Aabb.fromCenterExtent(2.0f, 0.0f, 0.0f, 0.5f, 0.5f, 0.5f);
        assertFalse(a.intersects(b));
        assertFalse(b.intersects(a));
    }

    @Test
    public void closestPointClampsInsideBox() {
        Aabb box = Aabb.fromCenterExtent(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f);
        Vec3 inside = box.closestPoint(0.2f, -0.3f, 0.0f);
        assertEquals(0.2f, inside.x, 0.0001f);
        assertEquals(-0.3f, inside.y, 0.0001f);
        Vec3 outside = box.closestPoint(4.0f, -3.0f, 0.5f);
        assertEquals(1.0f, outside.x, 0.0001f);
        assertEquals(-1.0f, outside.y, 0.0001f);
        assertEquals(0.5f, outside.z, 0.0001f);
    }

    @Test
    public void sphereOverlapUsesClosestPoint() {
        Aabb box = Aabb.fromCenterExtent(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f);
        assertTrue(box.intersectsSphere(1.5f, 0.0f, 0.0f, 0.5f));
        assertTrue(box.intersectsSphere(1.5f, 0.0f, 0.0f, 0.5f + 0.0001f));
        assertFalse(box.intersectsSphere(2.0f, 0.0f, 0.0f, 0.5f));
        assertTrue(Aabb.spheresOverlap(0.0f, 0.0f, 0.0f, 1.0f, 1.5f, 0.0f, 0.0f, 0.5f));
        assertFalse(Aabb.spheresOverlap(0.0f, 0.0f, 0.0f, 0.4f, 1.5f, 0.0f, 0.0f, 0.4f));
    }
}
