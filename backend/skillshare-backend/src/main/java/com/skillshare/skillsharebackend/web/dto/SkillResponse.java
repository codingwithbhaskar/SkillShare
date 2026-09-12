package com.skillshare.skillsharebackend.web.dto;

import com.skillshare.skillsharebackend.domain.Skill;

/** Response shape for {@code GET /api/catalog/skills} - same rationale as
 *  {@link ServiceResponse}. {@code category} (added V8) lets the
 *  booking-creation form filter skills down to the ones relevant to the
 *  service already picked. */
public record SkillResponse(Long skillId, String skillName, String category, String description) {

    public static SkillResponse from(Skill skill) {
        return new SkillResponse(
                skill.getSkillId(), skill.getSkillName(), skill.getCategory(), skill.getDescription());
    }
}
