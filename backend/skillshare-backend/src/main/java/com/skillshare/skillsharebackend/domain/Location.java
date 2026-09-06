package com.skillshare.skillsharebackend.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Maps to `locations` (01_schema_v3.sql). Reused by both
 * workers.location_id (base/registered location) and bookings.location_id
 * (per-booking job address) — see location-map-feature-design.md.
 *
 * The `geom` geography(Point,4326) column is deliberately NOT mapped here:
 * it's maintained entirely by the fn_sync_location_geom trigger from
 * latitude/longitude, and Hibernate Spatial support for it is Phase 3
 * work (dev-status-and-next-steps.md, step 3). Leaving it unmapped is
 * safe under `ddl-auto: validate` — Hibernate only validates columns it
 * knows about.
 */
@Entity
@Table(name = "locations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "address_line", nullable = false, columnDefinition = "TEXT")
    private String addressLine;

    @Column(name = "landmark", columnDefinition = "TEXT")
    private String landmark;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", nullable = false, length = 100)
    private String state;

    @Column(name = "pincode", length = 12)
    private String pincode;

    @Column(name = "latitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
