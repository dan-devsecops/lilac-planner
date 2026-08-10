package com.lilac.planner.persistence.neo4j;

import com.lilac.planner.config.Neo4jInit;
import com.lilac.planner.domain.AuthTokenType;
import com.lilac.planner.domain.Platform;
import com.lilac.planner.model.AuthToken;
import com.lilac.planner.model.Day;
import com.lilac.planner.model.PushSubscription;
import com.lilac.planner.model.Task;
import com.lilac.planner.model.User;
import com.lilac.planner.persistence.PlannerStore;
import com.lilac.planner.service.ConcurrentUpdateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Profile("neo4j")
@Component
public class Neo4jPlannerStore implements PlannerStore {

    private static final Logger log = LoggerFactory.getLogger(Neo4jPlannerStore.class);

    private final Neo4jUserRepository userRepo;
    private final Neo4jDayRepository dayRepo;
    private final Neo4jTaskRepository taskRepo;
    private final Neo4jAuthTokenRepository authTokenRepo;
    private final Neo4jPushSubscriptionRepository pushSubscriptionRepo;
    private final Neo4jAlarmDispatchRepository alarmDispatchRepo;
    /** Use ObjectProvider to avoid a circular bean dependency with Neo4jInit. */
    private final ObjectProvider<Neo4jInit> neo4jInit;
    /** Day inserts run in their own transaction so a duplicate-key race cannot poison the caller's. */
    private final TransactionTemplate requiresNewTx;

    public Neo4jPlannerStore(Neo4jUserRepository userRepo,
                             Neo4jDayRepository dayRepo,
                             Neo4jTaskRepository taskRepo,
                             Neo4jAuthTokenRepository authTokenRepo,
                             Neo4jPushSubscriptionRepository pushSubscriptionRepo,
                             Neo4jAlarmDispatchRepository alarmDispatchRepo,
                             ObjectProvider<Neo4jInit> neo4jInit,
                             PlatformTransactionManager transactionManager) {
        this.userRepo = userRepo;
        this.dayRepo = dayRepo;
        this.taskRepo = taskRepo;
        this.authTokenRepo = authTokenRepo;
        this.pushSubscriptionRepo = pushSubscriptionRepo;
        this.alarmDispatchRepo = alarmDispatchRepo;
        this.neo4jInit = neo4jInit;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // --- Users ---

    @Override
    @Transactional
    public User createUser(String username, String displayName) {
        return userRepo.findFirstByUsername(username)
                .map(Neo4jPlannerStore::toDomainUser)
                .orElseGet(() -> toDomainUser(userRepo.save(new Neo4jUser(username, displayName))));
    }

    @Override
    @Transactional
    public User createNativeUser(String username, String email, String displayName,
                                 String passwordHash, List<String> roles) {
        return toDomainUser(userRepo.save(
                new Neo4jUser(username, email, displayName, passwordHash, new ArrayList<>(roles))));
    }

    @Override
    public Optional<User> findUserById(String userId) {
        return userRepo.findById(userId).map(Neo4jPlannerStore::toDomainUser);
    }

    @Override
    public Optional<User> findUserByUsername(String username) {
        return userRepo.findFirstByUsername(username).map(Neo4jPlannerStore::toDomainUser);
    }

    @Override
    public Optional<User> findUserByEmail(String email) {
        return userRepo.findFirstByEmail(email).map(Neo4jPlannerStore::toDomainUser);
    }

    @Override
    public List<User> listUsers() {
        List<User> out = new ArrayList<>();
        userRepo.findAll().forEach(u -> out.add(toDomainUser(u)));
        return out;
    }

    @Override
    @Transactional
    public void updateUserPassword(String userId, String passwordHash) {
        userRepo.findById(userId).ifPresent(u -> {
            u.setPasswordHash(passwordHash);
            userRepo.save(u);
        });
    }

    @Override
    @Transactional
    public void updateUserRoles(String userId, List<String> roles) {
        userRepo.findById(userId).ifPresent(u -> {
            u.setRoles(new ArrayList<>(roles));
            userRepo.save(u);
        });
    }

    @Override
    @Transactional
    public void updateUserTimezone(String userId, String timezone) {
        userRepo.findById(userId).ifPresent(u -> {
            u.setTimezone(timezone);
            userRepo.save(u);
        });
    }

    // --- Auth tokens ---

    @Override
    @Transactional
    public void saveAuthToken(AuthToken token) {
        authTokenRepo.save(new Neo4jAuthToken(token.getType().name(), token.getTokenHash(),
                token.getUserId(), token.getExpiresAt(), token.getCreatedAt()));
    }

    @Override
    public Optional<AuthToken> findAuthToken(AuthTokenType type, String tokenHash) {
        return authTokenRepo.findFirstByTypeAndTokenHash(type.name(), tokenHash)
                .map(Neo4jPlannerStore::toDomainAuthToken);
    }

    @Override
    @Transactional
    public boolean deleteAuthToken(String tokenHash) {
        Long deleted = authTokenRepo.deleteByTokenHashReturningCount(tokenHash);
        return deleted != null && deleted > 0;
    }

    @Override
    @Transactional
    public void deleteAuthTokensForUser(AuthTokenType type, String userId) {
        authTokenRepo.deleteAll(authTokenRepo.findByTypeAndUserId(type.name(), userId));
    }

    @Override
    @Transactional
    public int deleteExpiredAuthTokens(Instant now) {
        Long purged = authTokenRepo.deleteByExpiresAtBeforeReturningCount(now);
        return purged == null ? 0 : purged.intValue();
    }

    // --- Days ---

    @Override
    public Day getOrCreateDay(String userId, LocalDate date) {
        return dayRepo.findFirstByUserIdAndDate(userId, date)
                .map(this::toDomainDay)
                .orElseGet(() -> toDomainDay(saveNewDayWithLegacyRetry(userId, date)));
    }

    /**
     * Insert the new Day in its own transaction (REQUIRES_NEW) so a unique-constraint
     * clash with a concurrent creator only rolls back that inner transaction - any
     * caller transaction stays healthy and the recovery fetch actually works.
     */
    private Neo4jDay saveNewDayWithLegacyRetry(String userId, LocalDate date) {
        try {
            return requiresNewTx.execute(tx -> dayRepo.save(new Neo4jDay(userId, date)));
        } catch (RuntimeException insertFailure) {
            Optional<Neo4jDay> sameUser = dayRepo.findFirstByUserIdAndDate(userId, date);
            if (sameUser.isPresent()) return sameUser.get();

            // The user-scoped fetch found nothing yet the save failed - almost certainly a
            // legacy single-column Day.date unique constraint. Best-effort drop + retry.
            if (looksLikeLegacyDayDateClash(insertFailure)) {
                log.warn("Detected legacy Day.date uniqueness clash - dropping and retrying ({})",
                        insertFailure.getMessage());
                Neo4jInit init = neo4jInit.getIfAvailable();
                if (init != null) init.dropLegacyDayDateConstraints();
                return requiresNewTx.execute(tx -> dayRepo.save(new Neo4jDay(userId, date)));
            }
            throw insertFailure;
        }
    }

    private static boolean looksLikeLegacyDayDateClash(Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            String msg = cur.getMessage();
            if (msg != null && msg.contains("label `Day`") && msg.contains("property `date`")) return true;
        }
        return false;
    }

    @Override
    @Transactional
    public Day saveDay(Day day) {
        try {
            Neo4jDay entity = dayRepo.findFirstByUserIdAndDate(day.getUserId(), day.getDate())
                    .orElseGet(() -> new Neo4jDay(day.getUserId(), day.getDate()));
            // The caller's snapshot must still match the stored node; otherwise overwriting
            // the freshly-read entity would silently revert a concurrent update (the
            // re-fetched version always matches itself, so SDN's own check never trips).
            if (day.getVersion() != null && !day.getVersion().equals(entity.getVersion())) {
                throw new ConcurrentUpdateException(
                        "Day was modified concurrently - please retry: " + day.getDate());
            }
            entity.setEarnedStickers(new ArrayList<>(day.getEarnedStickers()));
            entity.setTasks(mapTasksToNeo4j(entity.getTasks(), day.getTasks()));
            Neo4jDay saved = dayRepo.save(entity);
            return toDomainDay(saved);
        } catch (OptimisticLockingFailureException ex) {
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
            boolean removed = d.getTasks().removeIf(t -> Objects.equals(taskId, t.getId()));
            if (removed) {
                dayRepo.save(d);
                taskRepo.deleteById(taskId);
            }
        });
    }

    @Override
    @Transactional
    public void updateRecurringTaskSeriesFields(String userId, String recurrenceGroupId, String title, Integer points) {
        // Targeted Cypher MATCH+SET - O(matching tasks) instead of O(all user days).
        if (title != null) taskRepo.updateSeriesTitle(userId, recurrenceGroupId, title);
        if (points != null) taskRepo.updateSeriesPoints(userId, recurrenceGroupId, points);
    }

    @Override
    @Transactional
    public void deleteTasksByRecurrenceGroup(String userId, LocalDate from, String recurrenceGroupId) {
        dayRepo.findByUserIdAndDateAfterOrderByDate(userId, from).forEach(day -> {
            List<String> toDelete = day.getTasks().stream()
                    .filter(t -> recurrenceGroupId.equals(t.getRecurrenceGroupId()))
                    .map(Neo4jTask::getId)
                    .toList();
            if (!toDelete.isEmpty()) {
                day.getTasks().removeIf(t -> recurrenceGroupId.equals(t.getRecurrenceGroupId()));
                dayRepo.save(day);
                toDelete.forEach(taskRepo::deleteById);
            }
        });
    }

    // --- Push subscriptions ---

    @Override
    public PushSubscription savePushSubscription(PushSubscription subscription) {
        return pushSubscriptionRepo.findFirstByUserIdAndToken(subscription.getUserId(), subscription.getToken())
                .map(existing -> toDomainPushSubscription(applyAndSave(existing, subscription)))
                .orElseGet(() -> insertPushSubscriptionRecoveringFromRace(subscription));
    }

    /**
     * Mirrors {@link #saveNewDayWithLegacyRetry}: the insert runs in its own REQUIRES_NEW
     * transaction so a concurrent registration of the same (userId, token) - e.g. two
     * browser tabs subscribing at once - only rolls back this insert, not the caller's. If
     * another request already won the race, its node is re-read and updated in place instead
     * of surfacing the constraint violation to the caller.
     */
    private PushSubscription insertPushSubscriptionRecoveringFromRace(PushSubscription subscription) {
        Neo4jPushSubscription entity = new Neo4jPushSubscription(
                subscription.getUserId(), subscription.getPlatform().name(), subscription.getToken());
        applyFields(entity, subscription);
        try {
            return requiresNewTx.execute(tx -> toDomainPushSubscription(pushSubscriptionRepo.save(entity)));
        } catch (DataIntegrityViolationException raceLost) {
            Neo4jPushSubscription winner = requiresNewTx.execute(tx -> pushSubscriptionRepo
                    .findFirstByUserIdAndToken(subscription.getUserId(), subscription.getToken())
                    .orElse(null));
            if (winner == null) throw raceLost;
            return toDomainPushSubscription(applyAndSave(winner, subscription));
        }
    }

    private Neo4jPushSubscription applyAndSave(Neo4jPushSubscription entity, PushSubscription subscription) {
        applyFields(entity, subscription);
        return pushSubscriptionRepo.save(entity);
    }

    private static void applyFields(Neo4jPushSubscription entity, PushSubscription subscription) {
        entity.setPlatform(subscription.getPlatform().name());
        entity.setP256dh(subscription.getP256dh());
        entity.setAuth(subscription.getAuth());
        entity.setLastSeenAt(Instant.now());
    }

    @Override
    public List<PushSubscription> listPushSubscriptions(String userId) {
        return pushSubscriptionRepo.findByUserId(userId).stream()
                .map(Neo4jPlannerStore::toDomainPushSubscription).toList();
    }

    @Override
    @Transactional
    public boolean deletePushSubscription(String userId, String subscriptionId) {
        Long deleted = pushSubscriptionRepo.deleteByIdAndUserIdReturningCount(subscriptionId, userId);
        return deleted != null && deleted > 0;
    }

    // --- Alarm dispatch dedup ---

    /**
     * Runs the insert in its own REQUIRES_NEW transaction (mirroring
     * {@link #saveNewDayWithLegacyRetry}) so a constraint violation on a repeat call rolls
     * back only this dedup insert, never the caller's surrounding transaction.
     */
    @Override
    public boolean markAlarmDispatched(String userId, LocalDate date, String taskId) {
        try {
            requiresNewTx.executeWithoutResult(tx ->
                    alarmDispatchRepo.save(new Neo4jAlarmDispatch(userId, date, taskId)));
            return true;
        } catch (DataIntegrityViolationException alreadyDispatched) {
            return false;
        }
    }

    @Override
    @Transactional
    public int deleteExpiredAlarmDispatches(LocalDate before) {
        Long purged = alarmDispatchRepo.deleteByDateBeforeReturningCount(before);
        return purged == null ? 0 : purged.intValue();
    }

    @Override
    @Transactional
    public void resetAllData() {
        alarmDispatchRepo.deleteAll();
        pushSubscriptionRepo.deleteAll();
        authTokenRepo.deleteAll();
        taskRepo.deleteAll();
        dayRepo.deleteAll();
        userRepo.deleteAll();
    }

    // --- Mapping ---

    private static User toDomainUser(Neo4jUser u) {
        User out = new User(u.getId(), u.getUsername(), u.getDisplayName());
        out.setEmail(u.getEmail());
        out.setPasswordHash(u.getPasswordHash());
        out.setRoles(u.getRoles() == null ? new ArrayList<>() : new ArrayList<>(u.getRoles()));
        out.setCreatedAt(u.getCreatedAt());
        out.setTimezone(u.getTimezone());
        return out;
    }

    private static PushSubscription toDomainPushSubscription(Neo4jPushSubscription p) {
        PushSubscription out = new PushSubscription(p.getUserId(), Platform.valueOf(p.getPlatform()), p.getToken());
        out.setId(p.getId());
        out.setP256dh(p.getP256dh());
        out.setAuth(p.getAuth());
        out.setCreatedAt(p.getCreatedAt());
        out.setLastSeenAt(p.getLastSeenAt());
        return out;
    }

    private static AuthToken toDomainAuthToken(Neo4jAuthToken t) {
        AuthToken out = new AuthToken(AuthTokenType.valueOf(t.getType()), t.getTokenHash(),
                t.getUserId(), t.getExpiresAt());
        out.setCreatedAt(t.getCreatedAt());
        return out;
    }

    private Day toDomainDay(Neo4jDay d) {
        Day out = new Day(d.getUserId(), d.getDate());
        out.setId(d.getId());
        out.setVersion(d.getVersion());
        out.setEarnedStickers(new ArrayList<>(d.getEarnedStickers()));
        out.setTasks(new ArrayList<>(d.getTasks().stream().map(Neo4jPlannerStore::toDomainTask).toList()));
        return out;
    }

    private static Task toDomainTask(Neo4jTask t) {
        Task out = new Task(t.getTitle(), t.getPoints(), t.getPosition());
        out.setId(t.getId());
        out.setCompleted(t.isCompleted());
        out.setCreatedAt(t.getCreatedAt());
        out.setScheduledTime(t.getScheduledTime());
        out.setRecurrence(t.getRecurrence());
        out.setRecurrenceGroupId(t.getRecurrenceGroupId());
        return out;
    }

    private static List<Neo4jTask> mapTasksToNeo4j(List<Neo4jTask> existing, List<Task> domain) {
        List<Neo4jTask> out = new ArrayList<>(domain.size());
        for (Task d : domain) {
            Neo4jTask t = (d.getId() == null)
                    ? new Neo4jTask(d.getTitle(), d.getPoints(), d.getPosition())
                    : existing.stream()
                        .filter(e -> Objects.equals(d.getId(), e.getId()))
                        .findFirst()
                        .orElseGet(() -> new Neo4jTask(d.getTitle(), d.getPoints(), d.getPosition()));
            t.setTitle(d.getTitle());
            t.setPoints(d.getPoints());
            t.setPosition(d.getPosition());
            t.setCompleted(d.isCompleted());
            t.setScheduledTime(d.getScheduledTime());
            t.setRecurrence(d.getRecurrence());
            t.setRecurrenceGroupId(d.getRecurrenceGroupId());
            if (t.getCreatedAt() == null && d.getCreatedAt() != null) t.setCreatedAt(d.getCreatedAt());
            out.add(t);
        }
        return out;
    }
}
