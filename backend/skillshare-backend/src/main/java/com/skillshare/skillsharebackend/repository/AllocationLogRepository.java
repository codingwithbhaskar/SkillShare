package com.skillshare.skillsharebackend.repository;

import com.skillshare.skillsharebackend.domain.AllocationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AllocationLogRepository extends JpaRepository<AllocationLog, Long> {
    List<AllocationLog> findByBooking_BookingIdOrderByTotalScoreDesc(Long bookingId);
}
