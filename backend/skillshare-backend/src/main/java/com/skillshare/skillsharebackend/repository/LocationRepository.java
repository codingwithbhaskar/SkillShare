package com.skillshare.skillsharebackend.repository;

import com.skillshare.skillsharebackend.domain.Location;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, Long> {
}
