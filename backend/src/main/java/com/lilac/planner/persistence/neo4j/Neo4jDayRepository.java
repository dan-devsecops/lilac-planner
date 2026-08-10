package com.lilac.planner.persistence.neo4j;

import org.springframework.context.annotation.Profile;
import org.springframework.data.neo4j.repository.Neo4jRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Profile("neo4j")
public interface Neo4jDayRepository extends Neo4jRepository<Neo4jDay, String> {

    Optional<Neo4jDay> findFirstByUserIdAndDate(String userId, LocalDate date);

    List<Neo4jDay> findByUserIdAndDateBetweenOrderByDate(String userId, LocalDate from, LocalDate to);

    List<Neo4jDay> findByUserIdAndDateAfterOrderByDate(String userId, LocalDate after);

    List<Neo4jDay> findByUserId(String userId);
}
