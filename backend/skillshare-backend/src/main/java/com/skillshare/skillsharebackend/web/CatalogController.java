package com.skillshare.skillsharebackend.web;

import com.skillshare.skillsharebackend.repository.ServiceRepository;
import com.skillshare.skillsharebackend.repository.SkillRepository;
import com.skillshare.skillsharebackend.web.dto.ServiceResponse;
import com.skillshare.skillsharebackend.web.dto.SkillResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only catalogue endpoints — added for Phase 8's booking-creation
 * form, which needs a service/skill picker. No earlier phase needed this
 * (every live-test script used a hardcoded serviceId/skillId from the
 * seed data), so it never existed until a real browser form needed to
 * populate a dropdown. Deliberately not under {@code /api/workers/**} or
 * any role-restricted prefix — any authenticated user (customer included)
 * needs this list, and {@code SecurityConfig}'s {@code anyRequest()
 * .authenticated()} default already covers it correctly.
 */
@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final ServiceRepository serviceRepository;
    private final SkillRepository skillRepository;

    @GetMapping("/services")
    public List<ServiceResponse> services() {
        return serviceRepository.findAll().stream().map(ServiceResponse::from).toList();
    }

    @GetMapping("/skills")
    public List<SkillResponse> skills() {
        return skillRepository.findAll().stream().map(SkillResponse::from).toList();
    }
}
