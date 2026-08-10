package com.lilac.planner.persistence.jpa;

import com.lilac.planner.domain.AuthTokenType;
import com.lilac.planner.domain.Platform;
import com.lilac.planner.model.AuthToken;
import com.lilac.planner.model.Day;
import com.lilac.planner.model.PushSubscription;
import com.lilac.planner.model.Task;
import com.lilac.planner.model.User;
import com.lilac.planner.persistence.PlannerStore;
import com.lilac.planner.service.ConcurrentUpdateException;
import com.lilac.planner.util.Timestamps;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One adapter that backs both Postgres and MariaDB - only the JDBC driver
 * differs at runtime, which is decided by the active Spring profile.
 */
@Profile({"postgres", "mariadb", "jpa-test"})
@Component
public class JpaPlannerStore implements PlannerStore {

    private final JpaUserRepository userRepo;
    private final JpaDayRepository dayRepo;
    private final JpaTaskRepository taskRepo;
    private final JpaAuthTokenRepository authTokenRepo;
    private final JpaPushSubscriptionRepository pushSubscriptionRepo;
    private final JpaAlarmDispatchRepository alarmDispatchRepo;
    private final EntityManager em;
    /** Day inserts run in their own transaction so a duplicate-key race cannot poison the caller's. */
    private final TransactionTemplate requiresNewTx;

    public JpaPlannerStore(JpaUserRepository userRepo, JpaDayRepository dayRepo,
                           JpaTaskRepository taskRepo,
                           JpaAuthTokenRepository authTokenRepo,
                           JpaPushSubscriptionRepository pushSubscriptionRepo,
                           JpaAlarmDispatchRepository alarmDispatchRepo, EntityManager em,
                           PlatformTransactionManager transactionManager) {
        this.userRepo = userRepo;
        this.dayRepo = dayRepo;
        this.taskRepo = taskRepo;
        this.authTokenRepo = authTokenRepo;
        this.pushSubscriptionRepo = pushSubscriptionRepo;
        this.alarmDispatchRepo = alarmDispatchRepo;
        this.em = em;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // --- Users ---

    @Override
    @Transactional
    public User createUser(String username, String displayName) {
        return userRepo.findFirstByUsername(username)
                .map(JpaPlannerStore::toDomainUser)
                .orElseGet(() -> toDomainUser(userRepo.save(new JpaUser(username, displayName))));
    }

    @Override
    @Transactional
    public User createNativeUser(String username, String email, String displayName,
                                 String passwordHash, List<String> roles) {
        return toDomainUser(userRepo.save(
                new JpaUser(username, email, displayName, passwordHash, joinRoles(roles))));
    }

    @Override
    public Optional<User> findUserById(String userId) {
        try {
            return userRepo.findById(UUID.fromString(userId)).map(JpaPlannerStore::toDomainUser);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<User> findUserByUsername(String username) {
        return userRepo.findFirstByUsername(username).map(JpaPlannerStore::toDomainUser);
    }

    @Override
    public Optional<User> findUserByEmail(String email) {
        return userRepo.findFirstByEmail(email).map(JpaPlannerStore::toDomainUser);
    }

    @Override
    public List<User> listUsers() {
        return userRepo.findAll().stream().map(JpaPlannerStore::toDomainUser).toList();
    }

    @Override
    @Transactional
    public void updateUserPassword(String userId, String passwordHash) {
        userRepo.findById(UUID.fromString(userId)).ifPresent(u -> {
            u.setPasswordHash(passwordHash);
            userRepo.save(u);
        });
    }

    @Override
    @Transactional
    public void updateUserRoles(String userId, List<String> roles) {
        userRepo.findById(UUID.fromString(userId)).ifPresent(u -> {
            u.setRoles(joinRoles(roles));
            userRepo.save(u);
        });
    }

    @Override
    @Transactional
    public void updateUserTimezone(String userId, String timezone) {
        userRepo.findById(UUID.fromString(userId)).ifPresent(u -> {
            u.setTimezone(timezone);
            userRepo.save(u);
        });
    }

    // --- Auth tokens ---

    @Override
    @Transactional
    public void saveAuthToken(AuthToken token) {
        authTokenRepo.save(new JpaAuthToken(token.getType().name(), token.getTokenHash(),
                token.getUserId(), token.getExpiresAt(), token.getCreatedAt()));
    }

    @Override
    public Optional<AuthToken> findAuthToken(AuthTokenType type, String tokenHash) {
        return authTokenRepo.findFirstByTypeAndTokenHash(type.name(), tokenHash)
                .map(JpaPlannerStore::toDomainAuthToken);
    }

    @Override
    @Transactional
    public boolean deleteAuthToken(String tokenHash) {
        return authTokenRepo.deleteByTokenHashReturningCount(tokenHash) > 0;
    }

    @Override
    @Transactional
    public void deleteAuthTokensForUser(AuthTokenType type, String userId) {
        authTokenRepo.deleteAll(authTokenRepo.findByTypeAndUserId(type.name(), userId));
    }

    @Override
    @Transactional
    public int deleteExpiredAuthTokens(Instant now) {
        return authTokenRepo.deleteByExpiresAtBefore(now);
    }

    // --- Days ---

    @Override
    public Day getOrCreateDay(String userId, LocalDate date) {
        return dayRepo.findFirstByUserIdAndDate(userId, date)
                .map(this::toDomainDay)
                .orElseGet(() -> createDayRecoveringFromRace(userId, date));
    }

    /**
     * Insert the new Day in its own transaction (REQUIRES_NEW). If a concurrent
     * request won the insert race, only that inner transaction is rolled back -
     * any caller transaction stays healthy - and the winner's row is re-read in
     * a fresh transaction (fresh snapshot, so it is visible even under
     * REPEATABLE READ).
     */
    private Day createDayRecoveringFromRace(String userId, LocalDate date) {
        try {
            return requiresNewTx.execute(tx ->
                    toDomainDay(dayRepo.saveAndFlush(new JpaDay(userId, date))));
        } catch (RuntimeException insertFailure) {
            Day winner = requiresNewTx.execute(tx ->
                    dayRepo.findFirstByUserIdAndDate(userId, date).map(this::toDomainDay).orElse(null));
            if (winner != null) return winner;
            throw insertFailure;
        }
    }

    @Override
    @Transactional
    public Day saveDay(Day day) {
        try {
            JpaDay entity;
            if (day.getId() != null) {
                // Lock by primary key - PK is the clustered index, so InnoDB acquires
                // only a record lock on this specific row, not a gap lock on the secondary
                // (userId, date) unique index.  That means concurrent REQUIRES_NEW day-inserts
                // (for adjacent dates in the recurring-task creation loop) are never blocked
                // by this lock.  PESSIMISTIC_FORCE_INCREMENT also atomically increments the
                // @Version and updates the Java field so toDomainDay always returns the
                // current version, preventing a false conflict on the very next save.
                entity = em.find(JpaDay.class, UUID.fromString(day.getId()),
                        LockModeType.PESSIMISTIC_FORCE_INCREMENT);
            } else {
                entity = null;
            }
            if (entity == null) {
                entity = new JpaDay(day.getUserId(), day.getDate());
            } else if (day.getVersion() != null && !day.getVersion().equals(entity.getVersion() - 1)) {
                // entity.getVersion() is already post-increment (PESSIMISTIC_FORCE_INCREMENT
                // bumped it); the caller's snapshot was at version - 1.  If the stored
                // pre-increment value doesn't match the caller's, another writer snuck in.
                throw new ConcurrentUpdateException(
                        "Day was modified concurrently - please retry: " + day.getDate());
            }
            entity.getEarnedStickers().clear();
            entity.getEarnedStickers().addAll(day.getEarnedStickers());
            applyTasks(entity, day.getTasks());
            JpaDay saved = dayRepo.saveAndFlush(entity);
            return toDomainDay(saved);
        } catch (OptimisticLockingFailureException | jakarta.persistence.OptimisticLockException ex) {
            throw new ConcurrentUpdateException(
                    "Day was modified concurrently - please retry: " + day.getDate(), ex);
        }
    }

    @Override
    public Optional<Day> findDay(String userId, LocalDate date) {
        return dayRepo.findFirstByUserIdAndDate(userId, date).map(this::toDomainDay);
    }

    @Override
    public List<Day> findDaysInRange(String userId, LocalDate from, LocalDate to) {
        return dayRepo.findByUserIdAndDateBetweenOrderByDate(userId, from, to)
                .stream().map(this::toDomainDay).toList();
    }

    @Override
    @Transactional
    public void deleteTask(String userId, LocalDate date, String taskId) {
        dayRepo.findFirstByUserIdAndDate(userId, date).ifPresent(d -> {
            UUID tid;
            try {
                tid = UUID.fromString(taskId);
            } catch (IllegalArgumentException ignored) {
                return; // not a UUID → no task could match
            }
            boolean removed = d.getTasks().removeIf(t -> tid.equals(t.getId()));
            if (removed) dayRepo.save(d);
        });
    }

    @Override
    @Transactional
    public void deleteTasksByRecurrenceGroup(String userId, LocalDate from, String recurrenceGroupId) {
        dayRepo.findByUserIdAndDateAfterOrderByDate(userId, from).forEach(day -> {
            boolean removed = day.getTasks().removeIf(t -> recurrenceGroupId.equals(t.getRecurrenceGroupId()));
            if (removed) dayRepo.save(day);
        });
    }

    @Override
    @Transactional
    public void updateRecurringTaskSeriesFields(String userId, String recurrenceGroupId, String title, Integer points) {
        // Targeted bulk JPQL update - O(matching tasks) instead of O(all user days).
        if (title != null) taskRepo.updateSeriesTitle(userId, recurrenceGroupId, title);
        if (points != null) taskRepo.updateSeriesPoints(userId, recurrenceGroupId, points);
    }

    // --- Push subscriptions ---

    @Override
    public PushSubscription savePushSubscription(PushSubscription subscription) {
        return pushSubscriptionRepo.findFirstByUserIdAndToken(subscription.getUserId(), subscription.getToken())
                .map(existing -> toDomainPushSubscription(applyAndSave(existing, subscription)))
                .orElseGet(() -> insertPushSubscriptionRecoveringFromRace(subscription));
    }

    /**
     * Mirrors {@link #createDayRecoveringFromRace}: the insert runs in its own REQUIRES_NEW
     * transaction so a concurrent registration of the same (userId, token) - e.g. two
     * browser tabs subscribing at once - only rolls back this insert, not the caller's. If
     * another request already won the race, its row is re-read and updated in place instead
     * of surfacing the unique-constraint violation to the caller.
     */
    private PushSubscription insertPushSubscriptionRecoveringFromRace(PushSubscription subscription) {
        JpaPushSubscription entity = new JpaPushSubscription(
                subscription.getUserId(), subscription.getPlatform().name(), subscription.getToken());
        applyFields(entity, subscription);
        try {
            return requiresNewTx.execute(tx -> toDomainPushSubscription(pushSubscriptionRepo.saveAndFlush(entity)));
        } catch (DataIntegrityViolationException raceLost) {
            JpaPushSubscription winner = requiresNewTx.execute(tx -> pushSubscriptionRepo
                    .findFirstByUserIdAndToken(subscription.getUserId(), subscription.getToken())
                    .orElse(null));
            if (winner == null) throw raceLost;
            return toDomainPushSubscription(applyAndSave(winner, subscription));
        }
    }

    private JpaPushSubscription applyAndSave(JpaPushSubscription entity, PushSubscription subscription) {
        applyFields(entity, subscription);
        return pushSubscriptionRepo.save(entity);
    }

    private static void applyFields(JpaPushSubscription entity, PushSubscription subscription) {
        entity.setPlatform(subscription.getPlatform().name());
        entity.setP256dh(subscription.getP256dh());
        entity.setAuth(subscription.getAuth());
        entity.setLastSeenAt(Timestamps.now());
    }

    @Override
    public List<PushSubscription> listPushSubscriptions(String userId) {
        return pushSubscriptionRepo.findByUserId(userId).stream()
                .map(JpaPlannerStore::toDomainPushSubscription).toList();
    }

    @Override
    @Transactional
    public boolean deletePushSubscription(String userId, String subscriptionId) {
        UUID id;
        try {
            id = UUID.fromString(subscriptionId);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return pushSubscriptionRepo.deleteByIdAndUserIdReturningCount(id, userId) > 0;
    }

    // --- Alarm dispatch dedup ---

    /**
     * Runs the insert in its own REQUIRES_NEW transaction (mirroring
     * {@link #createDayRecoveringFromRace}) so a PK-violation on a repeat call rolls back
     * only this dedup insert, never the caller's surrounding transaction.
     */
    @Override
    public boolean markAlarmDispatched(String userId, LocalDate date, String taskId) {
        try {
            requiresNewTx.executeWithoutResult(tx ->
                    alarmDispatchRepo.saveAndFlush(new JpaAlarmDispatch(userId, date, taskId)));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    @Override
    @Transactional
    public int deleteExpiredAlarmDispatches(LocalDate before) {
        return alarmDispatchRepo.deleteByDateBefore(before);
    }

    @Override
    @Transactional
    public void resetAllData() {
        alarmDispatchRepo.deleteAll();
        pushSubscriptionRepo.deleteAll();
        authTokenRepo.deleteAll();
        dayRepo.deleteAll();
        userRepo.deleteAll();
    }

    // --- Mapping ---

    private static User toDomainUser(JpaUser u) {
        User out = new User(u.getId().toString(), u.getUsername(), u.getDisplayName());
        out.setEmail(u.getEmail());
        out.setPasswordHash(u.getPasswordHash());
        out.setRoles(splitRoles(u.getRoles()));
        out.setCreatedAt(u.getCreatedAt());
        out.setTimezone(u.getTimezone());
        return out;
    }

    private static PushSubscription toDomainPushSubscription(JpaPushSubscription p) {
        PushSubscription out = new PushSubscription(p.getUserId(), Platform.valueOf(p.getPlatform()), p.getToken());
        out.setId(p.getId().toString());
        out.setP256dh(p.getP256dh());
        out.setAuth(p.getAuth());
        out.setCreatedAt(p.getCreatedAt());
        out.setLastSeenAt(p.getLastSeenAt());
        return out;
    }

    private static AuthToken toDomainAuthToken(JpaAuthToken t) {
        AuthToken out = new AuthToken(AuthTokenType.valueOf(t.getType()), t.getTokenHash(),
                t.getUserId(), t.getExpiresAt());
        out.setCreatedAt(t.getCreatedAt());
        return out;
    }

    private static String joinRoles(List<String> roles) {
        return (roles == null || roles.isEmpty()) ? null : String.join(",", roles);
    }

    private static List<String> splitRoles(String roles) {
        if (roles == null || roles.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(roles.split(",")));
    }

    private Day toDomainDay(JpaDay d) {
        Day out = new Day(d.getUserId(), d.getDate());
        out.setId(d.getId().toString());
        out.setVersion(d.getVersion());
        out.setEarnedStickers(new ArrayList<>(d.getEarnedStickers()));
        out.setTasks(d.getTasks().stream().map(JpaPlannerStore::toDomainTask)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
        return out;
    }

    private static Task toDomainTask(JpaTask t) {
        Task out = new Task(t.getTitle(), t.getPoints(), t.getPosition());
        out.setId(t.getId() == null ? null : t.getId().toString());
        out.setCompleted(t.isCompleted());
        out.setCreatedAt(t.getCreatedAt());
        out.setScheduledTime(t.getScheduledTime());
        out.setRecurrence(t.getRecurrence());
        out.setRecurrenceGroupId(t.getRecurrenceGroupId());
        return out;
    }

    private static void applyTasks(JpaDay entity, List<Task> domainTasks) {
        Map<UUID, JpaTask> byId = new HashMap<>();
        for (JpaTask t : entity.getTasks()) {
            if (t.getId() != null) byId.put(t.getId(), t);
        }

        List<JpaTask> updated = new ArrayList<>(domainTasks.size());
        for (Task d : domainTasks) {
            JpaTask t;
            if (d.getId() == null) {
                t = new JpaTask(entity, d.getTitle(), d.getPoints(), d.getPosition());
            } else {
                UUID tid = UUID.fromString(d.getId());
                t = byId.getOrDefault(tid, new JpaTask(entity, d.getTitle(), d.getPoints(), d.getPosition()));
                t.setDay(entity);
            }
            t.setTitle(d.getTitle());
            t.setPoints(d.getPoints());
            t.setPosition(d.getPosition());
            t.setCompleted(d.isCompleted());
            t.setScheduledTime(d.getScheduledTime());
            t.setRecurrence(d.getRecurrence());
            t.setRecurrenceGroupId(d.getRecurrenceGroupId());
            if (d.getCreatedAt() != null && t.getId() == null) t.setCreatedAt(d.getCreatedAt());
            updated.add(t);
        }
        entity.getTasks().clear();
        entity.getTasks().addAll(updated);
    }
}
