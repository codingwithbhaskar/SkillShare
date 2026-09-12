package com.skillshare.skillsharebackend.web.dto;

/** Body for {@code POST/PUT /api/admin/skills} - same rationale as
 *  {@link ServiceRequest}. {@code category} added V8, same pattern as
 *  {@link ServiceRequest#category()} - free text, nullable. */
public record SkillRequest(String skillName, String category, String description) {}
