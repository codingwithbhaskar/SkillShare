package com.skillshare.skillsharebackend.repository;

import com.skillshare.skillsharebackend.domain.Service;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ServiceRepository extends JpaRepository<Service, Long> {
    Optional<Service> findByServiceName(String serviceName);

    /** Duplicate-name check on update - excludes the row being updated
     *  itself, so renaming "Plumbing" to "Plumbing" (a no-op edit) isn't
     *  mistaken for a collision with another service. */
    Optional<Service> findByServiceNameAndServiceIdNot(String serviceName, Long serviceId);
}
