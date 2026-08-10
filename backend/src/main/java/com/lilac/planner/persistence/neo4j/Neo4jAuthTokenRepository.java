package com.lilac.planner.persistence.neo4j;

import org.springframework.context.annotation.Profile;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Profile("neo4j")
public interface Neo4jAuthTokenRepository extends Neo4jRepository<Neo4jAuthToken, String> {
    Optional<Neo4jAuthToken> findFirstByTypeAndTokenHash(String type, String tokenHash);
    List<Neo4jAuthToken> findByTypeAndUserId(String type, String userId);

    /** Delete returning the removed-node count - lets callers detect an already-spent token. */
    @Query("MATCH (t:AuthToken {tokenHash: $tokenHash}) DETACH DELETE t RETURN count(t)")
    Long deleteByTokenHashReturningCount(@Param("tokenHash") String tokenHash);

    @Query("MATCH (t:AuthToken) WHERE t.expiresAt < $now DETACH DELETE t RETURN count(t)")
    Long deleteByExpiresAtBeforeReturningCount(@Param("now") Instant now);
}
