package com.web.labportalbackend.common.location;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GeoDistanceTest {

    @Test
    void returnsZeroForTheSamePoint() {
        assertEquals(0.0, GeoDistance.distanceMeters(10.762622, 106.660172, 10.762622, 106.660172), 0.001);
    }

    @Test
    void calculatesDistanceInMeters() {
        double distance = GeoDistance.distanceMeters(10.762622, 106.660172, 10.771000, 106.660172);

        assertEquals(929.0, distance, 3.0);
    }

    @Test
    void rejectsInvalidCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> GeoDistance.distanceMeters(91.0, 106.0, 10.0, 106.0));
        assertThrows(IllegalArgumentException.class,
                () -> GeoDistance.distanceMeters(10.0, 181.0, 10.0, 106.0));
    }
}
