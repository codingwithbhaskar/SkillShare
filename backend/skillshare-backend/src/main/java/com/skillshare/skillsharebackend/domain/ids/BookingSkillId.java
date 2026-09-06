package com.skillshare.skillsharebackend.domain.ids;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Composite PK for booking_skills (booking_id, skill_id). */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BookingSkillId implements Serializable {

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "skill_id")
    private Long skillId;
}
