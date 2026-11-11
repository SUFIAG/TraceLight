package com.sentinel.security.adapter.out.persistence;

import com.sentinel.security.domain.model.AuthToken;
import com.sentinel.security.domain.model.AuthTokenType;
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
public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {

    Optional<AuthToken> findByTokenHashAndTokenType(String tokenHash, AuthTokenType tokenType);

    List<AuthToken> findByUserIdAndTokenTypeAndConsumedAtIsNull(UUID userId, AuthTokenType tokenType);

    long countByUserIdAndTokenTypeAndCreatedAtAfter(UUID userId, AuthTokenType tokenType, Instant after);

    /** Invalidates every outstanding token of a type, used when a new one supersedes them. */
    @Modifying
    @Query("UPDATE AuthToken t SET t.consumedAt = :now " +
            "WHERE t.userId = :userId AND t.tokenType = :type AND t.consumedAt IS NULL")
    void consumeOutstanding(@Param("userId") UUID userId,
                            @Param("type") AuthTokenType type,
                            @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM AuthToken t WHERE t.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
