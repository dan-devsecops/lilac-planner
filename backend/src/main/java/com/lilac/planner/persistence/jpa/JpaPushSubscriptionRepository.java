package com.lilac.planner.persistence.jpa;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Profile({"postgres", "mariadb", "jpa-test"})
public interface JpaPushSubscriptionRepository extends JpaRepository<JpaPushSubscription, UUID> {
    Optional<JpaPushSubscription> findFirstByUserIdAndToken(String userId, String token);

    List<JpaPushSubscription> findByUserId(String userId);

    /** Bulk delete returning the affected row count - atomic, so ownership is checked in one round-trip. */
    @Modifying
    @Query("delete from JpaPushSubscription p where p.id = :id and p.userId = :userId")
    int deleteByIdAndUserIdReturningCount(@Param("id") UUID id, @Param("userId") String userId);
}
