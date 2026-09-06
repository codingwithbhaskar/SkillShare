package com.skillshare.skillsharebackend.repository;

import com.skillshare.skillsharebackend.domain.User;
import com.skillshare.skillsharebackend.domain.enums.AccountStatus;
import com.skillshare.skillsharebackend.domain.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    List<User> findByRole(UserRole role);

    long countByRole(UserRole role);
    long countByStatus(AccountStatus status);

    /**
     * Backs the admin "manage users" list/search - every filter is
     * optional (a null bind param short-circuits its own clause via the
     * {@code :x IS NULL OR ...} pattern, same technique
     * {@code WorkerRepository.findWorkersWithinRadius} already uses for
     * an unrelated reason). Kept as one JPQL query rather than a
     * Specification/QueryDSL predicate builder - the filter set is small
     * and fixed, so the extra abstraction wouldn't pay for itself here.
     */
    @Query("""
            SELECT u FROM User u
            WHERE (CAST(:role AS string) IS NULL OR u.role = :role)
              AND (CAST(:status AS string) IS NULL OR u.status = :status)
              AND (CAST(:search AS string) IS NULL
                   OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
            ORDER BY u.userId DESC
            """)
    // Every bare "(:x IS NULL OR ...)" check needs its own explicit CAST,
    // not just the CONCAT(...) uses: Postgres's extended-protocol PREPARE
    // infers each "?" placeholder's type independently from that
    // placeholder's own local SQL context, not from the JPQL parameter
    // name. "$1 is null" alone gives Postgres nothing to infer from, so
    // it fails with "could not determine data type of parameter $1" -
    // confirmed live 2026-09-01, and it's always $1 (the FIRST such bare
    // check, here :role) that gets reported first, which is why casting
    // only the CONCAT uses of :search earlier didn't fix this: :role's
    // own bare IS NULL check was the real remaining culprit. Casting to
    // string is safe even for the enum params (:role, :status) - the
    // cast target only pins a JDBC type for the "IS NULL" placeholder,
    // it's never compared against the actual enum column in that spot.
    List<User> search(@Param("role") UserRole role, @Param("status") AccountStatus status, @Param("search") String search);
}
