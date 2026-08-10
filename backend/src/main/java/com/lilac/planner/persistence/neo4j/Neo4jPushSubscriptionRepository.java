package com.lilac.planner.persistence.neo4j;

import org.springframework.context.annotation.Profile;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Profile("neo4j")
public interface Neo4jPushSubscriptionRepository extends Neo4jRepository<Neo4jPushSubscription, String> {
    Optional<Neo4jPushSubscription> findFirstByUserIdAndToken(String userId, String token);

    List<Neo4jPushSubscription> findByUserId(String userId);

    /** Delete returning the removed-node count - lets callers verify the (id, userId) pair matched. */
    @Query("MATCH (p:PushSubscription {id: $id, userId: $userId}) DETACH DELETE p RETURN count(p)")
    Long deleteByIdAndUserIdReturningCount(@Param("id") String id, @Param("userId") String userId);
}
