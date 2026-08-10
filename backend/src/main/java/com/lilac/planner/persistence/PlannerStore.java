package com.lilac.planner.persistence;

import com.lilac.planner.domain.AuthTokenType;
import com.lilac.planner.model.AuthToken;
import com.lilac.planner.model.Day;
import com.lilac.planner.model.PushSubscription;
import com.lilac.planner.model.User;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port. Every backend (Neo4j, JPA-Postgres, JPA-MariaDB, DynamoDB) is
 * an adapter implementing this interface. Switching backends is a matter of
 * activating the right Spring profile - see {@code application.yml}.
 *
 * <p>All Day / Task operations are scoped by {@code userId} so multiple users can
 * use the same backend store without seeing each other's data.</p>
 */
public interface PlannerStore {

    // --- Users ---

    User createUser(String username, String displayName);

    /** Create a native (username+password) user with credentials and roles. */
    User createNativeUser(String username, String email, String displayName,
                          String passwordHash, List<String> roles);

    Optional<User> findUserById(String userId);

    Optional<User> findUserByUsername(String username);

    Optional<User> findUserByEmail(String email);

    List<User> listUsers();

    void updateUserPassword(String userId, String passwordHash);

    void updateUserRoles(String userId, List<String> roles);

    /** Update the user's IANA timezone identifier. */
    void updateUserTimezone(String userId, String timezone);

    // --- Auth tokens (native auth: refresh + password-reset) ---

    void saveAuthToken(AuthToken token);

    Optional<AuthToken> findAuthToken(AuthTokenType type, String tokenHash);

    /**
     * Delete the token with the given hash.
     *
     * @return {@code true} only if this call actually removed a token - callers that
     *         rotate single-use tokens (e.g. refresh) rely on this to reject a token
     *         that was already spent concurrently.
     */
    boolean deleteAuthToken(String tokenHash);

    void deleteAuthTokensForUser(AuthTokenType type, String userId);

    /**
     * Purge all tokens (of every type) whose {@code expiresAt} is strictly before {@code now}.
     *
     * @return the number of tokens removed
     */
    int deleteExpiredAuthTokens(Instant now);

    // --- Days ---

    /** Idempotent - returns the existing Day for (user, date) or creates an empty one. */
    Day getOrCreateDay(String userId, LocalDate date);

    /** @return updated Day after save (with ids assigned for any new tasks) */
    Day saveDay(Day day);

    Optional<Day> findDay(String userId, LocalDate date);

    List<Day> findDaysInRange(String userId, LocalDate from, LocalDate to);

    /** Remove the given task from its Day; no-op if absent. */
    void deleteTask(String userId, LocalDate date, String taskId);

    /** Remove all tasks with the given recurrenceGroupId from days strictly after {@code from}. */
    void deleteTasksByRecurrenceGroup(String userId, LocalDate from, String recurrenceGroupId);

    /**
     * Update series-level fields of every task in the recurrence group across all dates.
     * Null arguments are left unchanged.
     */
    void updateRecurringTaskSeriesFields(String userId, String recurrenceGroupId, String title, Integer points);

    // --- Push subscriptions ---

    /**
     * Upsert by (userId, token): a subscription already registered with the same token
     * has its {@code lastSeenAt} (and keys, in case they rotated) updated in place rather
     * than being duplicated.
     *
     * @return the saved subscription, with {@code id} assigned
     */
    PushSubscription savePushSubscription(PushSubscription subscription);

    List<PushSubscription> listPushSubscriptions(String userId);

    /**
     * Delete the subscription with the given id, scoped to {@code userId} so one user
     * cannot delete another user's registration.
     *
     * @return {@code true} only if a subscription with that id, owned by that user, was removed
     */
    boolean deletePushSubscription(String userId, String subscriptionId);

    // --- Alarm dispatch dedup ---

    /**
     * Insert-if-absent dedup marker recording that the alarm for (userId, date, taskId)
     * has been dispatched, mirroring the boolean-return contract of {@link #deleteAuthToken}.
     *
     * @return {@code true} only on the call that actually inserted the record - a repeat
     *         call for the same (userId, date, taskId) returns {@code false}, so callers
     *         can tell "already sent" from "sent just now".
     */
    boolean markAlarmDispatched(String userId, LocalDate date, String taskId);

    /**
     * Purge all alarm dispatch dedup records whose {@code date} is strictly before
     * {@code before}. Safe to run aggressively: {@link com.lilac.planner.service.PushAlarmDispatchService}
     * only ever looks up "today" and, near midnight, "yesterday" in a user's own timezone -
     * a dispatch record more than a couple of days old can never be looked up again (see
     * {@link com.lilac.planner.service.AlarmDispatchCleanupService} for the worst-case
     * timezone-skew reasoning behind the default retention window).
     *
     * @return the number of records removed
     */
    int deleteExpiredAlarmDispatches(LocalDate before);

    /** Delete all users, days, and tasks. Intended for test resets only. */
    void resetAllData();
}
