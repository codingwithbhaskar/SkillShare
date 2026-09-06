package com.skillshare.skillsharebackend.domain;

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

/** Maps to `query_benchmark_results` — Phase 8's EXPLAIN ANALYZE
 *  timing/plan log (synthetic-data benchmark harness), mapped now for
 *  repository-layer completeness even though it isn't populated until
 *  Phase 8. Deliberately one table covering Query_Log + Query_Performance
 *  + Query_Plan + Query_Cost + Cardinality_Estimate — see
 *  master-entity-list-v3.md section F. */
@Entity
@Table(name = "query_benchmark_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueryBenchmarkResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "benchmark_id")
    private Long benchmarkId;

    @Column(name = "query_label", nullable = false, length = 150)
    private String queryLabel;

    @Column(name = "worker_count_scale")
    private Integer workerCountScale;

    @Column(name = "used_index")
    private Boolean usedIndex;

    @Column(name = "estimated_cost")
    private BigDecimal estimatedCost;

    @Column(name = "actual_time_ms")
    private BigDecimal actualTimeMs;

    @Column(name = "estimated_rows")
    private Long estimatedRows;

    @Column(name = "actual_rows")
    private Long actualRows;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "explain_plan", columnDefinition = "jsonb")
    private String explainPlan;

    @Column(name = "run_at", insertable = false, updatable = false)
    private OffsetDateTime runAt;
}
