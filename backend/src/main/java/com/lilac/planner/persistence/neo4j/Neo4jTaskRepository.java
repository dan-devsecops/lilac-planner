package com.lilac.planner.persistence.neo4j;

import org.springframework.context.annotation.Profile;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

@Profile("neo4j")
public interface Neo4jTaskRepository extends Neo4jRepository<Neo4jTask, String> {

    /**
     * Bulk-update title for all Task nodes in the given recurrence group that are
     * reachable via a HAS_TASK relationship from a Day node owned by userId.
     * Only called when a non-null title is provided.
     */
    @Query("MATCH (d:Day {userId: $userId})-[:HAS_TASK]->(t:Task {recurrenceGroupId: $groupId}) " +
           "SET t.title = $title")
    void updateSeriesTitle(@Param("userId") String userId,
                           @Param("groupId") String groupId,
                           @Param("title") String title);

    /**
     * Bulk-update points for all Task nodes in the given recurrence group that are
     * reachable via a HAS_TASK relationship from a Day node owned by userId.
     * Only called when a non-null points value is provided.
     */
    @Query("MATCH (d:Day {userId: $userId})-[:HAS_TASK]->(t:Task {recurrenceGroupId: $groupId}) " +
           "SET t.points = $points")
    void updateSeriesPoints(@Param("userId") String userId,
                            @Param("groupId") String groupId,
                            @Param("points") int points);
}
