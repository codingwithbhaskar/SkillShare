package com.skillshare.skillsharebackend.web.dto;

import com.skillshare.skillsharebackend.domain.Skill;

/** Response shape for {@code GET /api/catalog/skills} - same rationale as
 *  {@link ServiceResponse}. */
public record SkillResponse(Long skillId, String skillName, String description) {

    public static SkillResponse from(Skill skill) {
        return new SkillResponse(skill.getSkillId(), skill.getSkillName(), skill.getDescription());
    }
}
