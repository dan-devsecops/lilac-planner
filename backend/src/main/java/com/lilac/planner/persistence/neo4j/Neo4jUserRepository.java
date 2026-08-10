package com.lilac.planner.persistence.neo4j;

import org.springframework.context.annotation.Profile;
import org.springframework.data.neo4j.repository.Neo4jRepository;

import java.util.Optional;

@Profile("neo4j")
public interface Neo4jUserRepository extends Neo4jRepository<Neo4jUser, String> {

    Optional<Neo4jUser> findFirstByUsername(String username);
    Optional<Neo4jUser> findFirstByEmail(String email);
}
