package com.skillshare.skillsharebackend.domain;

import com.skillshare.skillsharebackend.domain.enums.ProficiencyLevel;
import com.skillshare.skillsharebackend.domain.ids.WorkerSkillId;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/** Maps to `worker_skills`, the M:N junction between workers and skills.
 *  Skill match is a SOFT scoring factor in the intelligent allocator
 *  (fn_score_candidate) but a HARD filter in the traditional baseline
 *  allocator (see allocation/TraditionalAllocator.java) — that
 *  difference is the whole point of having both. */
@Entity
@Table(name = "worker_skills")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkerSkill {

    @EmbeddedId
    private WorkerSkillId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("workerId")
    @JoinColumn(name = "worker_id")
    private Worker worker;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("skillId")
    @JoinColumn(name = "skill_id")
    private Skill skill;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "proficiency_level", nullable = false)
    private ProficiencyLevel proficiencyLevel;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
