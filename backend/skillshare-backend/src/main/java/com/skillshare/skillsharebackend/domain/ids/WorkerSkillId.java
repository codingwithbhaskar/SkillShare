package com.skillshare.skillsharebackend.domain.ids;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Composite PK for worker_skills (worker_id, skill_id). */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class WorkerSkillId implements Serializable {

    @Column(name = "worker_id")
    private Long workerId;

    @Column(name = "skill_id")
    private Long skillId;
}
