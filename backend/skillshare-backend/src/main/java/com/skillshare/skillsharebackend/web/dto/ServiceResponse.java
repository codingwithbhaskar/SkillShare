package com.skillshare.skillsharebackend.web.dto;

import com.skillshare.skillsharebackend.domain.Service;

/** Response shape for {@code GET /api/catalog/services} - the service
 *  catalogue the booking-creation form picks from. Added alongside the
 *  Phase 8 frontend; no earlier phase needed a "list all services"
 *  endpoint (seed data / live-test scripts always used a hardcoded
 *  serviceId). */
public record ServiceResponse(Long serviceId, String serviceName, String category, String description) {

    public static ServiceResponse from(Service service) {
        return new ServiceResponse(
                service.getServiceId(), service.getServiceName(), service.getCategory(), service.getDescription());
    }
}
