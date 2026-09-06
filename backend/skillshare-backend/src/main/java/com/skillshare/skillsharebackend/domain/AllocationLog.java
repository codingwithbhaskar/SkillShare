package com.skillshare.skillsharebackend.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Maps to `allocation_log` — every candidate considered per booking
 *  (not just the winner), written by sp_allocate_worker. This is the
 *  "historical feedback" data for future re-tuning of scoring weights,
 *  and the eventual source for the baseline-vs-intelligent comparison
 *  in Phase 9. Read-only from the app's perspective in this phase. */
@Entity
@Table(name = "allocation_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AllocationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @Column(name = "skill_score", precision = 5, scale = 4)
    private BigDecimal skillScore;

    @Column(name = "rating_score", precision = 5, scale = 4)
    private BigDecimal ratingScore;

    @Column(name = "distance_score", precision = 5, scale = 4)
    private BigDecimal distanceScore;

    @Column(name = "workload_score", precision = 5, scale = 4)
    private BigDecimal workloadScore;

    @Column(name = "price_score", precision = 5, scale = 4)
    private BigDecimal priceScore;

    @Column(name = "experience_score", precision = 5, scale = 4)
    private BigDecimal experienceScore;

    @Column(name = "total_score", precision = 6, scale = 4)
    private BigDecimal totalScore;

    @Column(name = "is_selected", nullable = false)
    private Boolean isSelected;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
