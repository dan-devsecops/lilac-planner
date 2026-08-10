package com.lilac.planner.persistence.jpa;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Profile({"postgres", "mariadb", "jpa-test"})
public interface JpaTaskRepository extends JpaRepository<JpaTask, UUID> {

    /**
     * Bulk-update title for every task in the given recurrence group that belongs to
     * the given user. Only called when {@code title} is non-null, so the JPQL is a
     * simple SET rather than a conditional expression.
     */
    @Modifying
    @Query("UPDATE JpaTask t SET t.title = :title " +
           "WHERE t.recurrenceGroupId = :groupId AND t.day.userId = :userId")
    int updateSeriesTitle(@Param("userId") String userId,
                          @Param("groupId") String groupId,
                          @Param("title") String title);

    /**
     * Bulk-update points for every task in the given recurrence group that belongs to
     * the given user. Only called when {@code points} is non-null.
     */
    @Modifying
    @Query("UPDATE JpaTask t SET t.points = :points " +
           "WHERE t.recurrenceGroupId = :groupId AND t.day.userId = :userId")
    int updateSeriesPoints(@Param("userId") String userId,
                           @Param("groupId") String groupId,
                           @Param("points") int points);
}
