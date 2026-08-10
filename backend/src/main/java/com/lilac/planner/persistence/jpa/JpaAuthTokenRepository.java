package com.lilac.planner.persistence.jpa;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Profile({"postgres", "mariadb", "jpa-test"})
public interface JpaAuthTokenRepository extends JpaRepository<JpaAuthToken, UUID> {
    Optional<JpaAuthToken> findFirstByTypeAndTokenHash(String type, String tokenHash);
    List<JpaAuthToken> findByTypeAndUserId(String type, String userId);

    /** Bulk delete returning the affected row count - atomic, so a token can only be spent once. */
    @Modifying
    @Query("delete from JpaAuthToken t where t.tokenHash = :tokenHash")
    int deleteByTokenHashReturningCount(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("delete from JpaAuthToken t where t.expiresAt < :now")
    int deleteByExpiresAtBefore(@Param("now") Instant now);
}
