package com.skillshare.skillsharebackend.evaluation;

import com.skillshare.skillsharebackend.allocation.AllocationStrategy;
import com.skillshare.skillsharebackend.allocation.GeoUtils;
import com.skillshare.skillsharebackend.allocation.IntelligentAllocationStrategy;
import com.skillshare.skillsharebackend.allocation.NearestWorkerStrategy;
import com.skillshare.skillsharebackend.allocation.RandomAllocationStrategy;
import com.skillshare.skillsharebackend.allocation.RatingOnlyStrategy;
import com.skillshare.skillsharebackend.allocation.SimpleWeightedStrategy;
import com.skillshare.skillsharebackend.allocation.TraditionalAllocator;
import com.skillshare.skillsharebackend.domain.Booking;
import com.skillshare.skillsharebackend.domain.Worker;
import com.skillshare.skillsharebackend.repository.AllocationScoreProjection;
import com.skillshare.skillsharebackend.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Phase 10 (Step 11) — the baseline comparison suite. Seeds a self-
 * contained synthetic dataset ({@link AllocationEvaluationSeeder}), then
 * runs all 6 {@link AllocationStrategy} implementations this project has
 * (4 new baseline arms + {@link TraditionalAllocator} + {@link
 * IntelligentAllocationStrategy}) against the exact same fixed batch of
 * unassigned bookings, computing ASR/AMS/ATD/ART/WU + a documented
 * satisfaction proxy for each — see {@link StrategyMetrics}'s javadoc for
 * what each metric means and how it's computed.
 *
 * <p>Nothing any strategy picks is ever committed (every {@link
 * AllocationStrategy#selectWorker} implementation here is read-only — see
 * {@link IntelligentAllocationStrategy}'s javadoc on why that matters):
 * this is what lets all 6 strategies see the IDENTICAL batch rather than
 * the world changing out from under strategy #2 because strategy #1
 * already "took" a booking.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AllocationEvaluationService {

    /** Matches fn_find_candidates'/fn_score_candidate's hardcoded 30km
     *  search radius — used only to scale the satisfaction proxy, see
     *  {@link StrategyMetrics}'s javadoc. */
    private static final double SEARCH_RADIUS_KM = 30.0;

    private final AllocationEvaluationSeeder seeder;
    private final WorkerRepository workerRepository;
    private final TraditionalAllocator traditionalAllocator;
    private final RandomAllocationStrategy randomAllocationStrategy;
    private final NearestWorkerStrategy nearestWorkerStrategy;
    private final RatingOnlyStrategy ratingOnlyStrategy;
    private final SimpleWeightedStrategy simpleWeightedStrategy;
    private final IntelligentAllocationStrategy intelligentAllocationStrategy;

    public record EvaluationRunResult(
            String runTag,
            int workersSeeded,
            int customersSeeded,
            int bookingsEvaluated,
            int eligibleWorkerPoolSize,
            List<StrategyMetrics> strategies) {
    }

    /**
     * @param requestedStrategies optional subset of strategy keys to run
     *     (see {@link #allStrategies()} for the valid keys); {@code null}
     *     or empty runs all 6, same as before this param existed. An
     *     unknown key is rejected with a 400 rather than silently ignored.
     */
    @Transactional
    public EvaluationRunResult run(int workerCount, int bookingCount, List<String> requestedStrategies) {
        AllocationEvaluationSeeder.SeedResult seedResult = seeder.seed(workerCount, bookingCount);
        List<Booking> bookings = seedResult.evaluationBookings();

        // Shared Worker Utilization denominator: the union of every worker
        // who is a candidate for AT LEAST ONE booking in the batch, per
        // fn_find_candidates — computed once here (not per-strategy) purely
        // for this metric. See StrategyMetrics' javadoc for why
        // TraditionalAllocator's own pool can differ from this.
        Set<Long> eligibleWorkerUnion = new HashSet<>();
        for (Booking booking : bookings) {
            eligibleWorkerUnion.addAll(workerRepository.findCandidateWorkerIds(booking.getBookingId()));
        }
        int eligiblePoolSize = eligibleWorkerUnion.size();

        LinkedHashMap<String, AllocationStrategy> strategies = selectStrategies(requestedStrategies);

        List<StrategyMetrics> results = new ArrayList<>();
        for (Map.Entry<String, AllocationStrategy> entry : strategies.entrySet()) {
            StrategyMetrics metrics = evaluateStrategy(entry.getKey(), entry.getValue(), bookings, eligiblePoolSize);
            results.add(metrics);
            log.info("AllocationEvaluationService: {} -> ASR={} AMS={} ATD={}km ART={}ms WU={}",
                    entry.getKey(),
                    String.format("%.3f", metrics.assignmentSuccessRate()),
                    String.format("%.3f", metrics.avgMatchingScore()),
                    String.format("%.2f", metrics.avgTravelDistanceKm()),
                    String.format("%.2f", metrics.avgResponseTimeMs()),
                    String.format("%.3f", metrics.workerUtilization()));
        }

        return new EvaluationRunResult(seedResult.runTag(), seedResult.workersCreated(),
                seedResult.customersCreated(), bookingCount, eligiblePoolSize, results);
    }

    private LinkedHashMap<String, AllocationStrategy> allStrategies() {
        LinkedHashMap<String, AllocationStrategy> all = new LinkedHashMap<>();
        all.put("random", randomAllocationStrategy);
        all.put("nearest_worker", nearestWorkerStrategy);
        all.put("rating_only", ratingOnlyStrategy);
        all.put("simple_weighted", simpleWeightedStrategy);
        all.put("traditional", traditionalAllocator);
        all.put("intelligent", intelligentAllocationStrategy);
        return all;
    }

    private LinkedHashMap<String, AllocationStrategy> selectStrategies(List<String> requested) {
        LinkedHashMap<String, AllocationStrategy> all = allStrategies();
        if (requested == null || requested.isEmpty()) {
            return all;
        }
        LinkedHashMap<String, AllocationStrategy> selected = new LinkedHashMap<>();
        for (String key : requested) {
            String trimmed = key.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            AllocationStrategy strategy = all.get(trimmed);
            if (strategy == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown strategy key: '" + trimmed + "'. Valid keys: " + all.keySet());
            }
            selected.put(trimmed, strategy);
        }
        if (selected.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "strategies param was given but contained no valid keys. Valid keys: " + all.keySet());
        }
        return selected;
    }

    private StrategyMetrics evaluateStrategy(
            String strategyKey, AllocationStrategy strategy, List<Booking> bookings, int eligiblePoolSize) {

        int total = bookings.size();
        int successes = 0;
        double totalMatchScore = 0.0;
        double totalDistanceKm = 0.0;
        long totalResponseTimeNanos = 0L;
        Set<Long> distinctWorkersUsed = new HashSet<>();

        for (Booking booking : bookings) {
            long t0 = System.nanoTime();
            Optional<Worker> picked = strategy.selectWorker(booking);
            totalResponseTimeNanos += (System.nanoTime() - t0);

            if (picked.isEmpty()) {
                continue;
            }
            Worker worker = picked.get();
            successes++;
            distinctWorkersUsed.add(worker.getWorkerId());

            // Graded on the SAME yardstick (fn_score_candidate) regardless
            // of which strategy made the pick — see StrategyMetrics' javadoc.
            AllocationScoreProjection score = workerRepository.scoreCandidate(booking.getBookingId(), worker.getWorkerId());
            if (score != null && score.getTotalScore() != null) {
                totalMatchScore += score.getTotalScore().doubleValue();
            }
            if (worker.getLocation() != null && booking.getLocation() != null) {
                totalDistanceKm += GeoUtils.haversineKm(
                        worker.getLocation().getLatitude(), worker.getLocation().getLongitude(),
                        booking.getLocation().getLatitude(), booking.getLocation().getLongitude());
            }
        }

        double asr = total == 0 ? 0.0 : (double) successes / total;
        double ams = successes == 0 ? 0.0 : totalMatchScore / successes;
        double atd = successes == 0 ? 0.0 : totalDistanceKm / successes;
        double art = total == 0 ? 0.0 : (totalResponseTimeNanos / 1_000_000.0) / total;
        double wu = eligiblePoolSize == 0 ? 0.0 : (double) distinctWorkersUsed.size() / eligiblePoolSize;
        double satisfactionProxy = (0.7 * ams) + (0.3 * Math.max(0.0, 1.0 - (atd / SEARCH_RADIUS_KM)));

        return new StrategyMetrics(strategyKey, total, successes, asr, ams, atd, art,
                distinctWorkersUsed.size(), eligiblePoolSize, wu, satisfactionProxy);
    }
}
