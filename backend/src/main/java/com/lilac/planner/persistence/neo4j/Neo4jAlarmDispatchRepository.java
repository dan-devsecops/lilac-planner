package com.lilac.planner.persistence.neo4j;

import org.springframework.context.annotation.Profile;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

@Profile("neo4j")
public interface Neo4jAlarmDispatchRepository extends Neo4jRepository<Neo4jAlarmDispatch, String> {

    @Query("MATCH (a:AlarmDispatch) WHERE a.date < $before DETACH DELETE a RETURN count(a)")
    Long deleteByDateBeforeReturningCount(@Param("before") LocalDate before);
}
