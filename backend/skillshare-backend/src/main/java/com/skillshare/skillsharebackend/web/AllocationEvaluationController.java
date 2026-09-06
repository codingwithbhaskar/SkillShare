package com.skillshare.skillsharebackend.web;

import com.skillshare.skillsharebackend.evaluation.AllocationEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Phase 10 (Step 11) — the baseline comparison suite's HTTP surface.
 * Under {@code /api/admin/**}, so gated to ROLE_ADMIN entirely at the path
 * level by {@code SecurityConfig}, same as {@link AdminController} — this
 * is a diagnostic/research tool, not a customer- or worker-facing feature.
 *
 * <p>Each call seeds a fresh, uniquely-tagged batch of synthetic workers/
 * customers/bookings directly into THIS database (see {@link
 * com.skillshare.skillsharebackend.evaluation.AllocationEvaluationSeeder}
 * — every synthetic email is tagged {@code eval.*.<runTag>@skillshare.eval})
 * and runs all 6 allocation strategies against it. Nothing is deleted
 * automatically — repeated calls accumulate more tagged rows, same
 * convention already established for the {@code test.alloc.*} accounts
 * documented in phase-9-admin-panel-complete.md. Safe to call repeatedly;
 * each run is independent (its own fresh booking batch), never touches
 * real customer/worker data, and never commits an actual allocation
 * (every strategy here only reads and proposes — see {@code
 * IntelligentAllocationStrategy}'s javadoc).
 */
@RestController
@RequestMapping("/api/admin/evaluation")
@RequiredArgsConstructor
public class AllocationEvaluationController {

    private final AllocationEvaluationService allocationEvaluationService;

    /**
     * {@code strategies} is optional — a comma-separated subset of
     * {random, nearest_worker, rating_only, simple_weighted, traditional,
     * intelligent}, e.g. {@code ?strategies=traditional,intelligent}.
     * Omitted (or blank) runs all 6, unchanged from before this param
     * existed. Unknown keys are rejected with a 400 (see the service).
     */
    @PostMapping("/run")
    public AllocationEvaluationService.EvaluationRunResult run(
            @RequestParam(defaultValue = "60") int workerCount,
            @RequestParam(defaultValue = "150") int bookingCount,
            @RequestParam(required = false) List<String> strategies) {
        return allocationEvaluationService.run(workerCount, bookingCount, strategies);
    }
}
