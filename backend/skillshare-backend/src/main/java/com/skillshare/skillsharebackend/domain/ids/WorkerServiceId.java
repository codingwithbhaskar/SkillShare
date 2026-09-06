package com.skillshare.skillsharebackend.domain.ids;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Composite PK for worker_services (worker_id, service_id). */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class WorkerServiceId implements Serializable {

    @Column(name = "worker_id")
    private Long workerId;

    @Column(name = "service_id")
    private Long serviceId;
}
