package com.skillshare.skillsharebackend.allocation;

import com.skillshare.skillsharebackend.domain.Booking;
import com.skillshare.skillsharebackend.domain.Worker;
import com.skillshare.skillsharebackend.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Phase 10 (Step 11) baseline arm #1 — the "control": pick uniformly at
 * random from the SAME hard-filtered candidate pool {@code
 * fn_find_candidates} defines (active status, offers the service,
 * available in-window, within 30km, no conflicting booking) — the exact
 * pool {@link IntelligentAllocationStrategy} and the other three baseline
 * arms all draw from too.
 *
 * <p>Deliberately using the shared pool rather than "any worker who
 * offers this service, no other filter" — a weaker random baseline would
 * conflate two different questions ("does filtering help?" and "does
 * smart SELECTION within an already-sane pool help?"). Holding the pool
 * identical across strategies isolates the second question, which is
 * what the evaluation is actually about: given the same eligible workers,
 * how much better is intelligent scoring than picking blindly among them?
 */
@Service("randomAllocator")
@RequiredArgsConstructor
public class RandomAllocationStrategy implements AllocationStrategy {

    private final WorkerRepository workerRepository;

    @Override
    public Optional<Worker> selectWorker(Booking booking) {
        List<Long> candidateIds = workerRepository.findCandidateWorkerIds(booking.getBookingId());
        if (candidateIds.isEmpty()) {
            return Optional.empty();
        }
        Long pickedId = candidateIds.get(ThreadLocalRandom.current().nextInt(candidateIds.size()));
        return workerRepository.findById(pickedId);
    }
}
