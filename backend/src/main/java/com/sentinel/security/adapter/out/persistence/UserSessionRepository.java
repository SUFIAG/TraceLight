package com.sentinel.security.adapter.out.persistence;

import com.sentinel.security.domain.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);

    Optional<UserSession> findByPreviousTokenHash(String previousTokenHash);

    List<UserSession> findByUserIdOrderByLastSeenAtDesc(UUID userId);

    List<UserSession> findByUserIdAndRevokedAtIsNull(UUID userId);

    long countByOrganizationIdAndRevokedAtIsNullAndExpiresAtAfter(UUID organizationId, Instant now);

    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :now, s.revokedReason = :reason " +
            "WHERE s.userId = :userId AND s.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") UUID userId,
                         @Param("now") Instant now,
                         @Param("reason") String reason);

    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
