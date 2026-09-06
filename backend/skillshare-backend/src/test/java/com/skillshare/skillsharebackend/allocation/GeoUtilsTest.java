package com.skillshare.skillsharebackend.allocation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the plain-Java Haversine distance calculation used by
 * {@link TraditionalAllocator}. These are analytically verifiable (no
 * external "ground truth" coordinates needed): on a sphere of radius
 * 6371 km, one degree of arc - along either a meridian (latitude) or the
 * equator (longitude) - subtends very close to 111.19 km, which is the
 * property most of these tests check against.
 */
class GeoUtilsTest {

    private static final double DELTA_KM = 0.5;

    @Test
    void sameCoordinates_returnsZeroDistance() {
        BigDecimal lat = new BigDecimal("19.997454");
        BigDecimal lon = new BigDecimal("73.789803");

        double distance = GeoUtils.haversineKm(lat, lon, lat, lon);

        assertEquals(0.0, distance, 0.0);
    }

    @Test
    void oneDegreeOfLatitude_isApproximately111Km() {
        BigDecimal lat1 = new BigDecimal("0.0");
        BigDecimal lon1 = new BigDecimal("0.0");
        BigDecimal lat2 = new BigDecimal("1.0");
        BigDecimal lon2 = new BigDecimal("0.0");

        double distance = GeoUtils.haversineKm(lat1, lon1, lat2, lon2);

        assertEquals(111.19, distance, DELTA_KM);
    }

    @Test
    void oneDegreeOfLongitudeAtEquator_isApproximately111Km() {
        BigDecimal lat1 = new BigDecimal("0.0");
        BigDecimal lon1 = new BigDecimal("0.0");
        BigDecimal lat2 = new BigDecimal("0.0");
        BigDecimal lon2 = new BigDecimal("1.0");

        double distance = GeoUtils.haversineKm(lat1, lon1, lat2, lon2);

        assertEquals(111.19, distance, DELTA_KM);
    }

    @Test
    void oneDegreeOfLongitudeAwayFromEquator_isShorterThanAtEquator() {
        // Longitude lines converge toward the poles, so the same 1-degree
        // longitude delta covers less ground distance at 45 degrees north
        // than it does at the equator - a sanity check that latitude is
        // actually factored in via the cos(phi) term, not just the raw
        // longitude delta on its own.
        BigDecimal equatorLat = new BigDecimal("0.0");
        BigDecimal midLat = new BigDecimal("45.0");
        BigDecimal lon1 = new BigDecimal("0.0");
        BigDecimal lon2 = new BigDecimal("1.0");

        double atEquator = GeoUtils.haversineKm(equatorLat, lon1, equatorLat, lon2);
        double at45North = GeoUtils.haversineKm(midLat, lon1, midLat, lon2);

        assertTrue(at45North < atEquator,
                "expected 1 degree of longitude at 45N (" + at45North
                        + " km) to be shorter than at the equator (" + atEquator + " km)");
    }

    @Test
    void isSymmetric_regardlessOfPointOrder() {
        BigDecimal lat1 = new BigDecimal("19.997454");
        BigDecimal lon1 = new BigDecimal("73.789803");
        BigDecimal lat2 = new BigDecimal("18.520430");
        BigDecimal lon2 = new BigDecimal("73.856743");

        double forward = GeoUtils.haversineKm(lat1, lon1, lat2, lon2);
        double backward = GeoUtils.haversineKm(lat2, lon2, lat1, lon1);

        assertEquals(forward, backward, 1e-9);
    }

    @Test
    void knownNashikToPuneDistance_isPlausible() {
        // Nashik and Pune (both used as seed-data worker locations in
        // 06_seed_data_v3.sql) are commonly cited as roughly 165-215 km
        // apart by road; straight-line/great-circle distance should be
        // in that neighborhood or a bit shorter. Coarse sanity check,
        // not a precision assertion.
        BigDecimal nashikLat = new BigDecimal("19.997454");
        BigDecimal nashikLon = new BigDecimal("73.789803");
        BigDecimal puneLat = new BigDecimal("18.520430");
        BigDecimal puneLon = new BigDecimal("73.856743");

        double distance = GeoUtils.haversineKm(nashikLat, nashikLon, puneLat, puneLon);

        assertTrue(distance > 140.0 && distance < 200.0,
                "expected Nashik-Pune great-circle distance to be 140-200 km, was " + distance);
    }
}
