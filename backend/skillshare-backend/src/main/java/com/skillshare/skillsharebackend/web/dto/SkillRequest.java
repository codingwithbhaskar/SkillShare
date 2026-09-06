package com.skillshare.skillsharebackend.web.dto;

/** Body for {@code POST/PUT /api/admin/skills} - same rationale as
 *  {@link ServiceRequest}. */
public record SkillRequest(String skillName, String description) {}
