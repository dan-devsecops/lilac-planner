package com.lilac.planner.persistence.dynamodb;

import com.lilac.planner.domain.AuthTokenType;
import com.lilac.planner.domain.Platform;
import com.lilac.planner.domain.Recurrence;
import com.lilac.planner.model.AuthToken;
import com.lilac.planner.model.Day;
import com.lilac.planner.model.PushSubscription;
import com.lilac.planner.model.Task;
import com.lilac.planner.model.User;
import com.lilac.planner.persistence.PlannerStore;
import com.lilac.planner.service.ConcurrentUpdateException;
import com.lilac.planner.util.Uuids;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Profile("dynamodb")
@Component
public class DynamoDbPlannerStore implements PlannerStore {

    private static final int TRANSACT_BATCH_SIZE = 25;
    /** First version the enhanced client's VersionedRecordExtension assigns to a new item. */
    private static final long INITIAL_VERSION = 1L;

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<DynamoUser> users;
    private final DynamoDbTable<DynamoDay> days;
    private final DynamoDbTable<DynamoAuthToken> authTokens;
    private final DynamoDbTable<DynamoPushSubscription> pushSubscriptions;
    private final DynamoDbTable<DynamoAlarmDispatch> alarmDispatches;

    @Value("${planner.push.alarm-dispatch-retention-days:7}")
    private long alarmDispatchRetentionDays;

    public DynamoDbPlannerStore(DynamoDbEnhancedClient enhancedClient,
                                DynamoDbTable<DynamoUser> users, DynamoDbTable<DynamoDay> days,
                                DynamoDbTable<DynamoAuthToken> authTokens,
                                DynamoDbTable<DynamoPushSubscription> pushSubscriptions,
                                DynamoDbTable<DynamoAlarmDispatch> alarmDispatches) {
        this.enhancedClient = enhancedClient;
        this.users = users;
        this.days = days;
        this.authTokens = authTokens;
        this.pushSubscriptions = pushSubscriptions;
        this.alarmDispatches = alarmDispatches;
    }

    // --- Users ---

    @Override
    public User createUser(String username, String displayName) {
        return findUserByUsername(username).orElseGet(() -> {
            DynamoUser u = new DynamoUser();
            u.setId(Uuids.v7());
            u.setUsername(username);
            u.setDisplayName(displayName);
            u.setCreatedAt(Instant.now().toString());
            users.putItem(u);
            return toDomainUser(u);
        });
    }

    @Override
    public User createNativeUser(String username, String email, String displayName,
                                 String passwordHash, List<String> roles) {
        DynamoUser u = new DynamoUser();
        u.setId(Uuids.v7());
        u.setUsername(username);
        u.setEmail(email);
        u.setDisplayName(displayName);
        u.setPasswordHash(passwordHash);
        u.setRoles(roles == null || roles.isEmpty() ? null : new ArrayList<>(roles));
        u.setCreatedAt(Instant.now().toString());
        users.putItem(u);
        return toDomainUser(u);
    }

    @Override
    public Optional<User> findUserById(String userId) {
        DynamoUser u = users.getItem(Key.builder().partitionValue(userId).build());
        return Optional.ofNullable(u).map(DynamoDbPlannerStore::toDomainUser);
    }

    @Override
    public Optional<User> findUserByUsername(String username) {
        return users.index("by_username")
                .query(QueryConditional.keyEqualTo(Key.builder().partitionValue(username).build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .findFirst()
                .map(DynamoDbPlannerStore::toDomainUser);
    }

    @Override
    public Optional<User> findUserByEmail(String email) {
        if (email == null) return Optional.empty();
        return users.index("by_email")
                .query(QueryConditional.keyEqualTo(Key.builder().partitionValue(email).build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .findFirst()
                .map(DynamoDbPlannerStore::toDomainUser);
    }

    @Override
    public List<User> listUsers() {
        return users.scan().items().stream().map(DynamoDbPlannerStore::toDomainUser).toList();
    }

    @Override
    public void updateUserPassword(String userId, String passwordHash) {
        DynamoUser u = users.getItem(Key.builder().partitionValue(userId).build());
        if (u != null) {
            u.setPasswordHash(passwordHash);
            users.putItem(u);
        }
    }

    @Override
    public void updateUserRoles(String userId, List<String> roles) {
        DynamoUser u = users.getItem(Key.builder().partitionValue(userId).build());
        if (u != null) {
            u.setRoles(roles == null || roles.isEmpty() ? null : new ArrayList<>(roles));
            users.putItem(u);
        }
    }

    @Override
    public void updateUserTimezone(String userId, String timezone) {
        DynamoUser u = users.getItem(Key.builder().partitionValue(userId).build());
        if (u != null) {
            u.setTimezone(timezone);
            users.putItem(u);
        }
    }

    // --- Auth tokens ---

    @Override
    public void saveAuthToken(AuthToken token) {
        DynamoAuthToken t = new DynamoAuthToken();
        t.setTokenHash(token.getTokenHash());
        t.setType(token.getType().name());
        t.setUserId(token.getUserId());
        t.setExpiresAt(token.getExpiresAt() == null ? null : token.getExpiresAt().toString());
        t.setCreatedAt(token.getCreatedAt() == null ? Instant.now().toString() : token.getCreatedAt().toString());
        authTokens.putItem(t);
    }

    @Override
    public Optional<AuthToken> findAuthToken(AuthTokenType type, String tokenHash) {
        DynamoAuthToken t = authTokens.getItem(Key.builder().partitionValue(tokenHash).build());
        if (t == null || !type.name().equals(t.getType())) return Optional.empty();
        return Optional.of(toDomainAuthToken(t));
    }

    @Override
    public boolean deleteAuthToken(String tokenHash) {
        // The enhanced client issues DeleteItem with ReturnValues=ALL_OLD and maps the old
        // image back - null means the item was already gone (e.g. spent concurrently).
        return authTokens.deleteItem(Key.builder().partitionValue(tokenHash).build()) != null;
    }

    @Override
    public void deleteAuthTokensForUser(AuthTokenType type, String userId) {
        List<DynamoAuthToken> matches = authTokens.index("by_user")
                .query(QueryConditional.keyEqualTo(Key.builder().partitionValue(userId).build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .filter(t -> type.name().equals(t.getType()))
                .toList();
        matches.forEach(t -> authTokens.deleteItem(Key.builder().partitionValue(t.getTokenHash()).build()));
    }

    @Override
    public int deleteExpiredAuthTokens(Instant now) {
        // Full scan + per-item delete, mirroring deleteAuthTokensForUser; the token table
        // stays small (one row per live refresh/reset token) so a scan is fine at this scale.
        List<DynamoAuthToken> expired = authTokens.scan().items().stream()
                .filter(t -> isExpiredBefore(t.getExpiresAt(), now))
                .toList();
        expired.forEach(t -> authTokens.deleteItem(Key.builder().partitionValue(t.getTokenHash()).build()));
        return expired.size();
    }

    private static boolean isExpiredBefore(String expiresAt, Instant now) {
        if (expiresAt == null) return false;
        try {
            return Instant.parse(expiresAt).isBefore(now);
        } catch (Exception e) {
            return false; // unparseable expiry - keep the row rather than guess
        }
    }

    // --- Days ---

    @Override
    public Day getOrCreateDay(String userId, LocalDate date) {
        return findDay(userId, date).orElseGet(() -> {
            DynamoDay d = new DynamoDay();
            d.setId(Uuids.v7());
            d.setUserId(userId);
            d.setDate(date.toString());
            try {
                days.putItem(d); // version is null → conditioned on the item not existing yet
            } catch (ConditionalCheckFailedException race) {
                // A concurrent request created the same (user, date) first - return its item.
                return findDay(userId, date).orElseThrow(() -> race);
            }
            d.setVersion(INITIAL_VERSION);
            return toDomainDay(d);
        });
    }

    @Override
    public Day saveDay(Day day) {
        // Write conditionally on the version the caller's snapshot was read with
        // (@DynamoDbVersionAttribute). Re-reading the latest version here instead would
        // make the condition always pass and let a stale snapshot overwrite concurrent
        // updates. A null version means "new item" → conditioned on non-existence.
        DynamoDay entity = new DynamoDay();
        entity.setUserId(day.getUserId());
        entity.setDate(day.getDate().toString());
        entity.setId(day.getId() != null ? day.getId() : Uuids.v7());
        entity.setVersion(day.getVersion());
        entity.setEarnedStickers(new ArrayList<>(day.getEarnedStickers()));
        entity.setTasks(day.getTasks().stream().map(DynamoDbPlannerStore::toDynamoTask).toList());
        try {
            days.putItem(entity);
        } catch (ConditionalCheckFailedException ex) {
            throw new ConcurrentUpdateException(
                    "Day was modified concurrently - please retry: " + day.getDate(), ex);
        }
        // The enhanced client wrote version+1 (or INITIAL_VERSION for a new item) but does
        // not mutate the bean - mirror it so the returned Day can be saved again.
        entity.setVersion(day.getVersion() == null ? INITIAL_VERSION : day.getVersion() + 1);
        return toDomainDay(entity);
    }

    @Override
    public Optional<Day> findDay(String userId, LocalDate date) {
        DynamoDay d = days.getItem(Key.builder()
                .partitionValue(userId)
                .sortValue(date.toString())
                .build());
        return Optional.ofNullable(d).map(DynamoDbPlannerStore::toDomainDay);
    }

    @Override
    public List<Day> findDaysInRange(String userId, LocalDate from, LocalDate to) {
        return days.query(QueryConditional.sortBetween(
                Key.builder().partitionValue(userId).sortValue(from.toString()).build(),
                Key.builder().partitionValue(userId).sortValue(to.toString()).build()))
                .stream()
                .flatMap(p -> p.items().stream())
                .map(DynamoDbPlannerStore::toDomainDay)
                .toList();
    }

    @Override
    public void deleteTask(String userId, LocalDate date, String taskId) {
        findDay(userId, date).ifPresent(d -> {
            boolean removed = d.getTasks().removeIf(t -> taskId.equals(t.getId()));
            if (removed) saveDay(d);
        });
    }

    @Override
    public void deleteTasksByRecurrenceGroup(String userId, LocalDate from, String recurrenceGroupId) {
        // Collect DynamoDay entities that contain tasks from this recurrence group,
        // remove those tasks, then write them back in atomic batches of up to TRANSACT_BATCH_SIZE.
        // Each batch is atomic; across batches is best-effort - document this limitation.
        List<DynamoDay> updates = findDaysInRange(userId, from.plusDays(1), LocalDate.of(9999, 12, 31))
                .stream()
                .map(day -> {
                    DynamoDay entity = days.getItem(Key.builder()
                            .partitionValue(day.getUserId())
                            .sortValue(day.getDate().toString())
                            .build());
                    if (entity == null) return null;
                    boolean removed = entity.getTasks().removeIf(
                            t -> recurrenceGroupId.equals(t.getRecurrenceGroupId()));
                    return removed ? entity : null;
                })
                .filter(e -> e != null)
                .toList();

        writeInBatches(updates,
                "Concurrent modification detected during recurrence group delete - partial update applied");
    }

    @Override
    public void updateRecurringTaskSeriesFields(String userId, String recurrenceGroupId, String title, Integer points) {
        // Query the user's day partition once (primary key scan of one partition key),
        // filter in-memory for days that contain the recurrence group, and batch-write
        // back only the modified items. This avoids the prior double-read (findDaysInRange
        // + per-day getItem) and skips days that have no matching tasks.
        List<DynamoDay> updates = days
                .query(QueryConditional.keyEqualTo(Key.builder().partitionValue(userId).build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .filter(entity -> applySeriesUpdate(entity, recurrenceGroupId, title, points))
                .toList();

        writeInBatches(updates,
                "Concurrent modification detected during recurrence group title update - partial update applied");
    }

    private static boolean applySeriesUpdate(DynamoDay entity, String recurrenceGroupId, String title, Integer points) {
        boolean updated = false;
        for (DynamoTask t : entity.getTasks()) {
            if (recurrenceGroupId.equals(t.getRecurrenceGroupId())) {
                if (title != null) t.setTitle(title);
                if (points != null) t.setPoints(points);
                updated = true;
            }
        }
        return updated;
    }

    /** Writes days in atomic batches of up to {@link #TRANSACT_BATCH_SIZE}; across batches is best-effort. */
    private void writeInBatches(List<DynamoDay> updates, String conflictMessage) {
        for (int i = 0; i < updates.size(); i += TRANSACT_BATCH_SIZE) {
            List<DynamoDay> batch = updates.subList(i, Math.min(i + TRANSACT_BATCH_SIZE, updates.size()));
            try {
                enhancedClient.transactWriteItems(tx -> {
                    for (DynamoDay d : batch) {
                        tx.addPutItem(days, d);
                    }
                });
            } catch (TransactionCanceledException ex) {
                throw new ConcurrentUpdateException(conflictMessage, ex);
            }
        }
    }

    // --- Push subscriptions ---

    @Override
    public PushSubscription savePushSubscription(PushSubscription subscription) {
        DynamoPushSubscription existing = pushSubscriptions.getItem(Key.builder()
                .partitionValue(subscription.getUserId())
                .sortValue(subscription.getToken())
                .build());

        DynamoPushSubscription entity = new DynamoPushSubscription();
        entity.setUserId(subscription.getUserId());
        entity.setToken(subscription.getToken());
        entity.setPlatform(subscription.getPlatform().name());
        entity.setP256dh(subscription.getP256dh());
        entity.setAuth(subscription.getAuth());
        entity.setLastSeenAt(Instant.now().toString());
        if (existing != null) {
            entity.setId(existing.getId());
            entity.setCreatedAt(existing.getCreatedAt());
        } else {
            entity.setId(Uuids.v7());
            entity.setCreatedAt(entity.getLastSeenAt());
        }
        pushSubscriptions.putItem(entity);
        return toDomainPushSubscription(entity);
    }

    @Override
    public List<PushSubscription> listPushSubscriptions(String userId) {
        return pushSubscriptions.query(QueryConditional.keyEqualTo(
                        Key.builder().partitionValue(userId).build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .map(DynamoDbPlannerStore::toDomainPushSubscription)
                .toList();
    }

    @Override
    public boolean deletePushSubscription(String userId, String subscriptionId) {
        Optional<DynamoPushSubscription> match = pushSubscriptions.query(QueryConditional.keyEqualTo(
                        Key.builder().partitionValue(userId).build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .filter(s -> subscriptionId.equals(s.getId()))
                .findFirst();
        if (match.isEmpty()) return false;
        pushSubscriptions.deleteItem(Key.builder()
                .partitionValue(userId)
                .sortValue(match.get().getToken())
                .build());
        return true;
    }

    // --- Alarm dispatch dedup ---

    @Override
    public boolean markAlarmDispatched(String userId, LocalDate date, String taskId) {
        DynamoAlarmDispatch entity = new DynamoAlarmDispatch();
        entity.setUserId(userId);
        entity.setDate(date.toString());
        entity.setTaskId(taskId);
        entity.setDateTaskKey(date + "#" + taskId);
        // DynamoDB's own TTL sweeper reclaims the item once this passes; see DynamoAlarmDispatch.
        entity.setTtl(date.plusDays(alarmDispatchRetentionDays).atStartOfDay(ZoneOffset.UTC).toEpochSecond());
        try {
            alarmDispatches.putItem(entity);
            return true;
        } catch (ConditionalCheckFailedException alreadyDispatched) {
            return false;
        }
    }

    @Override
    public int deleteExpiredAlarmDispatches(LocalDate before) {
        // Belt-and-suspenders alongside the item's native TTL attribute (DynamoDB's TTL
        // reclamation is best-effort and can lag by hours); full scan is fine at this table's
        // scale, mirroring deleteExpiredAuthTokens.
        List<DynamoAlarmDispatch> expired = alarmDispatches.scan().items().stream()
                .filter(d -> LocalDate.parse(d.getDate()).isBefore(before))
                .toList();
        expired.forEach(d -> alarmDispatches.deleteItem(Key.builder()
                .partitionValue(d.getUserId())
                .sortValue(d.getDateTaskKey())
                .build()));
        return expired.size();
    }

    @Override
    public void resetAllData() {
        days.scan().items().forEach(days::deleteItem);
        users.scan().items().forEach(users::deleteItem);
        authTokens.scan().items().forEach(authTokens::deleteItem);
        pushSubscriptions.scan().items().forEach(pushSubscriptions::deleteItem);
        alarmDispatches.scan().items().forEach(alarmDispatches::deleteItem);
    }

    // --- Mapping ---

    private static User toDomainUser(DynamoUser u) {
        User out = new User(u.getId(), u.getUsername(), u.getDisplayName());
        out.setEmail(u.getEmail());
        out.setPasswordHash(u.getPasswordHash());
        out.setRoles(u.getRoles() == null ? new ArrayList<>() : new ArrayList<>(u.getRoles()));
        if (u.getCreatedAt() != null) {
            try { out.setCreatedAt(Instant.parse(u.getCreatedAt())); }
            catch (Exception ignored) { /* leave default */ }
        }
        out.setTimezone(u.getTimezone());
        return out;
    }

    private static PushSubscription toDomainPushSubscription(DynamoPushSubscription p) {
        PushSubscription out = new PushSubscription(p.getUserId(), Platform.valueOf(p.getPlatform()), p.getToken());
        out.setId(p.getId());
        out.setP256dh(p.getP256dh());
        out.setAuth(p.getAuth());
        if (p.getCreatedAt() != null) {
            try { out.setCreatedAt(Instant.parse(p.getCreatedAt())); } catch (Exception ignored) { /* leave default */ }
        }
        if (p.getLastSeenAt() != null) {
            try { out.setLastSeenAt(Instant.parse(p.getLastSeenAt())); } catch (Exception ignored) { /* leave default */ }
        }
        return out;
    }

    private static AuthToken toDomainAuthToken(DynamoAuthToken t) {
        Instant expiresAt = null;
        if (t.getExpiresAt() != null) {
            try { expiresAt = Instant.parse(t.getExpiresAt()); } catch (Exception ignored) { /* leave default */ }
        }
        AuthToken out = new AuthToken(AuthTokenType.valueOf(t.getType()), t.getTokenHash(),
                t.getUserId(), expiresAt);
        if (t.getCreatedAt() != null) {
            try { out.setCreatedAt(Instant.parse(t.getCreatedAt())); } catch (Exception ignored) { /* leave default */ }
        }
        return out;
    }

    private static Day toDomainDay(DynamoDay d) {
        Day out = new Day(d.getUserId(), LocalDate.parse(d.getDate()));
        out.setId(d.getId());
        out.setVersion(d.getVersion());
        out.setEarnedStickers(new ArrayList<>(d.getEarnedStickers()));
        out.setTasks(d.getTasks().stream().map(DynamoDbPlannerStore::toDomainTask)
                .collect(Collectors.toCollection(ArrayList::new)));
        return out;
    }

    private static Task toDomainTask(DynamoTask t) {
        Task out = new Task(t.getTitle(), t.getPoints(), t.getPosition());
        out.setId(t.getId());
        out.setCompleted(t.isCompleted());
        if (t.getCreatedAt() != null) {
            try { out.setCreatedAt(Instant.parse(t.getCreatedAt())); } catch (Exception ignored) { /* leave default */ }
        }
        if (t.getScheduledTime() != null) {
            try { out.setScheduledTime(LocalTime.parse(t.getScheduledTime())); } catch (Exception ignored) { /* leave default */ }
        }
        try {
            out.setRecurrence(Recurrence.valueOf(t.getRecurrence() == null ? "NONE" : t.getRecurrence()));
        } catch (IllegalArgumentException ignored) {
            out.setRecurrence(Recurrence.NONE);
        }
        out.setRecurrenceGroupId(t.getRecurrenceGroupId());
        return out;
    }

    private static DynamoTask toDynamoTask(Task t) {
        DynamoTask out = new DynamoTask();
        out.setId(t.getId() != null ? t.getId() : Uuids.v7());
        out.setTitle(t.getTitle());
        out.setPoints(t.getPoints());
        out.setCompleted(t.isCompleted());
        out.setPosition(t.getPosition());
        out.setCreatedAt(t.getCreatedAt() == null ? Instant.now().toString() : t.getCreatedAt().toString());
        out.setScheduledTime(t.getScheduledTime() == null ? null : t.getScheduledTime().toString());
        out.setRecurrence(t.getRecurrence() == null ? "NONE" : t.getRecurrence().name());
        out.setRecurrenceGroupId(t.getRecurrenceGroupId());
        return out;
    }
}
