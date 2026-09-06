package com.skillshare.skillsharebackend.domain;

import com.skillshare.skillsharebackend.domain.enums.CriteriaType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Maps to `allocation_criteria` — the scoring weights used by
 *  fn_score_candidate (05_functions_procedures_v3.sql), one row per
 *  CriteriaType. Not consumed by the traditional baseline allocator in
 *  this phase (that allocator doesn't score at all, by design) — this
 *  is here for the repository layer and for Phase 4's intelligent
 *  allocation service. */
@Entity
@Table(name = "allocation_criteria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AllocationCriteria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "criteria_id")
    private Long criteriaId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "criteria_type", nullable = false, unique = true)
    private CriteriaType criteriaType;

    @Column(name = "skill_weight", nullable = false, precision = 3, scale = 2)
    private BigDecimal skillWeight;

    @Column(name = "rating_weight", nullable = false, precision = 3, scale = 2)
    private BigDecimal ratingWeight;

    @Column(name = "distance_weight", nullable = false, precision = 3, scale = 2)
    private BigDecimal distanceWeight;

    @Column(name = "workload_weight", nullable = false, precision = 3, scale = 2)
    private BigDecimal workloadWeight;

    @Column(name = "price_weight", nullable = false, precision = 3, scale = 2)
    private BigDecimal priceWeight;

    @Column(name = "experience_weight", nullable = false, precision = 3, scale = 2)
    private BigDecimal experienceWeight;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
