package com.lilac.planner.persistence.jpa;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

@Profile({"postgres", "mariadb", "jpa-test"})
public interface JpaUserRepository extends JpaRepository<JpaUser, UUID> {
    Optional<JpaUser> findFirstByUsername(String username);
    Optional<JpaUser> findFirstByEmail(String email);
}
