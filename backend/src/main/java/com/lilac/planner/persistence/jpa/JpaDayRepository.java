package com.lilac.planner.persistence.jpa;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import java.util.Optional;

@Profile({"postgres", "mariadb", "jpa-test"})
public interface JpaDayRepository extends JpaRepository<JpaDay, UUID> {

    Optional<JpaDay> findFirstByUserIdAndDate(String userId, LocalDate date);

    List<JpaDay> findByUserIdAndDateBetweenOrderByDate(String userId, LocalDate from, LocalDate to);

    List<JpaDay> findByUserIdAndDateAfterOrderByDate(String userId, LocalDate after);

    List<JpaDay> findByUserId(String userId);
}
