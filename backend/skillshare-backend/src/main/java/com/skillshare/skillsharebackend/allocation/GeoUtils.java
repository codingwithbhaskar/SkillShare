package com.skillshare.skillsharebackend.allocation;

import java.math.BigDecimal;

/**
 * Plain-Java great-circle distance, deliberately NOT using PostGIS/
 * Hibernate Spatial — the whole point of the traditional baseline
 * allocator is that it's an ordinary application-layer implementation,
 * for the research comparison against the PL/pgSQL intelligent allocator
 * to be meaningful. Hibernate Spatial support for the geography(Point,
 * 4326) column is Phase 3 work and belongs to the intelligent-allocator
 * side, not here.
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoUtils() {
    }

    /** Haversine great-circle distance in kilometers between two
     *  lat/long points. */
    public static double haversineKm(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        double phi1 = Math.toRadians(lat1.doubleValue());
        double phi2 = Math.toRadians(lat2.doubleValue());
        double deltaPhi = Math.toRadians(lat2.subtract(lat1).doubleValue());
        double deltaLambda = Math.toRadians(lon2.subtract(lon1).doubleValue());

        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2)
                * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }
}
