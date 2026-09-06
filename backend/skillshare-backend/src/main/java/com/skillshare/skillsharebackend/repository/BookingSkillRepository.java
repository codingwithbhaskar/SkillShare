package com.skillshare.skillsharebackend.repository;

import com.skillshare.skillsharebackend.domain.BookingSkill;
import com.skillshare.skillsharebackend.domain.ids.BookingSkillId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingSkillRepository extends JpaRepository<BookingSkill, BookingSkillId> {
    List<BookingSkill> findByBooking_BookingId(Long bookingId);

    /** Delete-guard for {@code AdminService.deleteSkill} - a skill a past
     *  booking required (`booking_skills.skill_id` has no ON DELETE
     *  action, i.e. RESTRICT - 01_schema_v3.sql) can never be removed,
     *  since booking history must stay intact. Checked here up front so
     *  the admin gets a clear 400 instead of a raw FK-violation 500/409
     *  from the database. */
    boolean existsBySkill_SkillId(Long skillId);
}
