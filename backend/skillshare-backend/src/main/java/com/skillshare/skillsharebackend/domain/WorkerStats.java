package com.skillshare.skillsharebackend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;

/** Maps to `mv_worker_stats`, a MATERIALIZED VIEW, not a base table
 *  (01_schema_v3.sql). @Immutable tells Hibernate never to issue
 *  INSERT/UPDATE/DELETE for this entity — the app refreshes it only via
 *  REFRESH MATERIALIZED VIEW CONCURRENTLY (raw SQL), never via JPA. */
@Entity
@Immutable
@Table(name = "mv_worker_stats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkerStats {

    @Id
    @Column(name = "worker_id")
    private Long workerId;

    @Column(name = "total_bookings")
    private Long totalBookings;

    @Column(name = "completed_bookings")
    private Long completedBookings;

    @Column(name = "active_bookings")
    private Long activeBookings;

    @Column(name = "review_count")
    private Long reviewCount;

    @Column(name = "avg_rating")
    private BigDecimal avgRating;
}
