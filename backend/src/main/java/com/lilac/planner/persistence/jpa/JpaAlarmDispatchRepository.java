package com.lilac.planner.persistence.jpa;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

@Profile({"postgres", "mariadb", "jpa-test"})
public interface JpaAlarmDispatchRepository extends JpaRepository<JpaAlarmDispatch, JpaAlarmDispatch.Key> {

    @Modifying
    @Query("delete from JpaAlarmDispatch a where a.date < :before")
    int deleteByDateBefore(@Param("before") LocalDate before);
}
