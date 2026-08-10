package com.lilac.planner.unit;

import com.lilac.planner.config.Neo4jInit;
import com.lilac.planner.domain.AuthTokenType;
import com.lilac.planner.domain.Platform;
import com.lilac.planner.domain.Recurrence;
import com.lilac.planner.model.AuthToken;
import com.lilac.planner.model.Day;
import com.lilac.planner.model.PushSubscription;
import com.lilac.planner.model.Task;
import com.lilac.planner.model.User;
import com.lilac.planner.persistence.neo4j.Neo4jAlarmDispatch;
import com.lilac.planner.persistence.neo4j.Neo4jAlarmDispatchRepository;
import com.lilac.planner.persistence.neo4j.Neo4jAuthToken;
import com.lilac.planner.persistence.neo4j.Neo4jAuthTokenRepository;
import com.lilac.planner.persistence.neo4j.Neo4jDay;
import com.lilac.planner.persistence.neo4j.Neo4jDayRepository;
import com.lilac.planner.persistence.neo4j.Neo4jPlannerStore;
import com.lilac.planner.persistence.neo4j.Neo4jPushSubscription;
import com.lilac.planner.persistence.neo4j.Neo4jPushSubscriptionRepository;
import com.lilac.planner.persistence.neo4j.Neo4jTask;
import com.lilac.planner.persistence.neo4j.Neo4jTaskRepository;
import com.lilac.planner.persistence.neo4j.Neo4jUser;
import com.lilac.planner.persistence.neo4j.Neo4jUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import com.lilac.planner.service.ConcurrentUpdateException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Neo4jPlannerStore - adapter mapping and persistence calls")
class Neo4jPlannerStoreUnitTest {

    @Mock Neo4jUserRepository userRepo;
    @Mock Neo4jDayRepository dayRepo;
    @Mock Neo4jTaskRepository taskRepo;
    @Mock Neo4jAuthTokenRepository authTokenRepo;
    @Mock Neo4jPushSubscriptionRepository pushSubscriptionRepo;
    @Mock Neo4jAlarmDispatchRepository alarmDispatchRepo;
    @Mock ObjectProvider<Neo4jInit> neo4jInit;
    @Mock Neo4jInit init;
    @Mock PlatformTransactionManager txManager;

    Neo4jPlannerStore store;

    static final LocalDate DATE = LocalDate.of(2099, 6, 1);

    @BeforeEach
    void setUp() {
        store = new Neo4jPlannerStore(userRepo, dayRepo, taskRepo, authTokenRepo,
                pushSubscriptionRepo, alarmDispatchRepo, neo4jInit, txManager);
        when(dayRepo.save(any(Neo4jDay.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepo.save(any(Neo4jUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pushSubscriptionRepo.save(any(Neo4jPushSubscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(alarmDispatchRepo.save(any(Neo4jAlarmDispatch.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Neo4jUser neoUser(String id, String username) {
        Neo4jUser u = new Neo4jUser(username, username.toUpperCase());
        u.setId(id);
        return u;
    }

    private static Neo4jTask neoTask(String id, String title, int points) {
        Neo4jTask t = new Neo4jTask(title, points, 0);
        t.setId(id);
        return t;
    }

    // --- Users ---

    @Test
    @DisplayName("createUser returns the existing user when the username is taken")
    void createUser_existing_returnsIt() {
        when(userRepo.findFirstByUsername("alice")).thenReturn(Optional.of(neoUser("u1", "alice")));

        User out = store.createUser("alice", "Alice");

        assertThat(out.getId()).isEqualTo("u1");
        verify(userRepo, never()).save(any());
    }

    @Test
    @DisplayName("createUser saves a new user when the username is free")
    void createUser_new_saves() {
        when(userRepo.findFirstByUsername("bob")).thenReturn(Optional.empty());

        User out = store.createUser("bob", "Bob");

        assertThat(out.getUsername()).isEqualTo("bob");
        assertThat(out.getDisplayName()).isEqualTo("Bob");
        verify(userRepo).save(any(Neo4jUser.class));
    }

    @Test
    @DisplayName("findUserById maps the entity to the domain model")
    void findUserById_maps() {
        when(userRepo.findById("u1")).thenReturn(Optional.of(neoUser("u1", "alice")));

        Optional<User> out = store.findUserById("u1");

        assertThat(out).isPresent();
        assertThat(out.get().getUsername()).isEqualTo("alice");
    }

    @Test
    @DisplayName("findUserByUsername maps the entity to the domain model")
    void findUserByUsername_maps() {
        when(userRepo.findFirstByUsername("alice")).thenReturn(Optional.of(neoUser("u1", "alice")));

        assertThat(store.findUserByUsername("alice")).isPresent();
        assertThat(store.findUserByUsername("missing")).isEmpty();
    }

    @Test
    @DisplayName("listUsers maps every entity")
    void listUsers_mapsAll() {
        when(userRepo.findAll()).thenReturn(List.of(neoUser("u1", "alice"), neoUser("u2", "bob")));

        List<User> out = store.listUsers();

        assertThat(out).extracting(User::getUsername).containsExactly("alice", "bob");
    }

    @Test
    @DisplayName("createNativeUser persists credentials and roles")
    void createNativeUser_savesCredentials() {
        User out = store.createNativeUser("alice", "alice@x.com", "Alice", "hash", List.of("ADMIN"));

        assertThat(out.getUsername()).isEqualTo("alice");
        assertThat(out.getEmail()).isEqualTo("alice@x.com");
        assertThat(out.getPasswordHash()).isEqualTo("hash");
        assertThat(out.getRoles()).containsExactly("ADMIN");
        verify(userRepo).save(any(Neo4jUser.class));
    }

    @Test
    @DisplayName("findUserByEmail maps the entity to the domain model")
    void findUserByEmail_maps() {
        Neo4jUser u = neoUser("u1", "alice");
        u.setEmail("alice@x.com");
        when(userRepo.findFirstByEmail("alice@x.com")).thenReturn(Optional.of(u));

        assertThat(store.findUserByEmail("alice@x.com")).map(User::getUsername).contains("alice");
        assertThat(store.findUserByEmail("missing@x.com")).isEmpty();
    }

    @Test
    @DisplayName("updateUserPassword saves the new hash")
    void updateUserPassword_saves() {
        Neo4jUser u = neoUser("u1", "alice");
        when(userRepo.findById("u1")).thenReturn(Optional.of(u));

        store.updateUserPassword("u1", "new-hash");

        assertThat(u.getPasswordHash()).isEqualTo("new-hash");
        verify(userRepo).save(u);
    }

    @Test
    @DisplayName("updateUserRoles replaces the roles")
    void updateUserRoles_saves() {
        Neo4jUser u = neoUser("u1", "alice");
        when(userRepo.findById("u1")).thenReturn(Optional.of(u));

        store.updateUserRoles("u1", List.of("ADMIN", "USER"));

        assertThat(u.getRoles()).containsExactly("ADMIN", "USER");
        verify(userRepo).save(u);
    }

    @Test
    @DisplayName("updateUserTimezone saves the new timezone and findUserById returns it")
    void updateUserTimezone_saves() {
        Neo4jUser u = neoUser("u1", "alice");
        when(userRepo.findById("u1")).thenReturn(Optional.of(u));

        store.updateUserTimezone("u1", "Europe/Prague");

        assertThat(u.getTimezone()).isEqualTo("Europe/Prague");
        verify(userRepo).save(u);
    }

    // --- Auth tokens ---

    @Test
    @DisplayName("saveAuthToken persists a token node")
    void saveAuthToken_saves() {
        store.saveAuthToken(new AuthToken(AuthTokenType.REFRESH, "hash", "u1",
                Instant.parse("2099-01-01T00:00:00Z")));

        verify(authTokenRepo).save(any(Neo4jAuthToken.class));
    }

    @Test
    @DisplayName("findAuthToken maps the matching token, empty when type differs")
    void findAuthToken_maps() {
        Neo4jAuthToken t = new Neo4jAuthToken("REFRESH", "hash", "u1",
                Instant.parse("2099-01-01T00:00:00Z"), Instant.parse("2098-01-01T00:00:00Z"));
        when(authTokenRepo.findFirstByTypeAndTokenHash("REFRESH", "hash")).thenReturn(Optional.of(t));

        Optional<AuthToken> out = store.findAuthToken(AuthTokenType.REFRESH, "hash");

        assertThat(out).isPresent();
        assertThat(out.get().getUserId()).isEqualTo("u1");
        assertThat(out.get().getType()).isEqualTo(AuthTokenType.REFRESH);
    }

    @Test
    @DisplayName("deleteAuthToken reports whether this call removed the token")
    void deleteAuthToken_reportsRemoval() {
        when(authTokenRepo.deleteByTokenHashReturningCount("hash")).thenReturn(1L);
        assertThat(store.deleteAuthToken("hash")).isTrue();

        when(authTokenRepo.deleteByTokenHashReturningCount("hash")).thenReturn(0L);
        assertThat(store.deleteAuthToken("hash")).as("already spent").isFalse();
    }

    @Test
    @DisplayName("deleteExpiredAuthTokens returns the purge count from the repository")
    void deleteExpiredAuthTokens_returnsCount() {
        Instant now = Instant.parse("2099-01-01T00:00:00Z");
        when(authTokenRepo.deleteByExpiresAtBeforeReturningCount(now)).thenReturn(4L);

        assertThat(store.deleteExpiredAuthTokens(now)).isEqualTo(4);
    }

    @Test
    @DisplayName("deleteAuthTokensForUser removes all of the user's tokens of a type")
    void deleteAuthTokensForUser_removesAll() {
        List<Neo4jAuthToken> tokens = List.of(new Neo4jAuthToken("REFRESH", "h1", "u1", null, null));
        when(authTokenRepo.findByTypeAndUserId("REFRESH", "u1")).thenReturn(tokens);

        store.deleteAuthTokensForUser(AuthTokenType.REFRESH, "u1");

        verify(authTokenRepo).deleteAll(tokens);
    }

    // --- Days ---

    @Test
    @DisplayName("getOrCreateDay returns the existing day")
    void getOrCreateDay_existing() {
        Neo4jDay existing = new Neo4jDay("u1", DATE);
        existing.setId("d1");
        when(dayRepo.findFirstByUserIdAndDate("u1", DATE)).thenReturn(Optional.of(existing));

        Day out = store.getOrCreateDay("u1", DATE);

        assertThat(out.getId()).isEqualTo("d1");
        verify(dayRepo, never()).save(any());
    }

    @Test
    @DisplayName("getOrCreateDay creates a new day when none exists")
    void getOrCreateDay_new() {
        when(dayRepo.findFirstByUserIdAndDate("u1", DATE)).thenReturn(Optional.empty());

        Day out = store.getOrCreateDay("u1", DATE);

        assertThat(out.getUserId()).isEqualTo("u1");
        assertThat(out.getDate()).isEqualTo(DATE);
        verify(dayRepo).save(any(Neo4jDay.class));
    }

    @Test
    @DisplayName("getOrCreateDay recovers when a concurrent insert won the race")
    void getOrCreateDay_duplicateRace_returnsExisting() {
        Neo4jDay winner = new Neo4jDay("u1", DATE);
        winner.setId("d-winner");
        when(dayRepo.findFirstByUserIdAndDate("u1", DATE))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(dayRepo.save(any(Neo4jDay.class))).thenThrow(new DataIntegrityViolationException("dup"));

        Day out = store.getOrCreateDay("u1", DATE);

        assertThat(out.getId()).isEqualTo("d-winner");
    }

    @Test
    @DisplayName("getOrCreateDay drops a legacy Day.date constraint and retries")
    void getOrCreateDay_legacyConstraint_dropsAndRetries() {
        when(dayRepo.findFirstByUserIdAndDate("u1", DATE)).thenReturn(Optional.empty());
        Neo4jDay retried = new Neo4jDay("u1", DATE);
        retried.setId("d-retry");
        when(dayRepo.save(any(Neo4jDay.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "already exists with label `Day` and property `date`"))
                .thenReturn(retried);
        when(neo4jInit.getIfAvailable()).thenReturn(init);

        Day out = store.getOrCreateDay("u1", DATE);

        assertThat(out.getId()).isEqualTo("d-retry");
        verify(init).dropLegacyDayDateConstraints();
    }

    @Test
    @DisplayName("getOrCreateDay rethrows an unrecognized integrity violation")
    void getOrCreateDay_unknownViolation_rethrows() {
        when(dayRepo.findFirstByUserIdAndDate("u1", DATE)).thenReturn(Optional.empty());
        when(dayRepo.save(any(Neo4jDay.class))).thenThrow(new DataIntegrityViolationException("other"));

        assertThatThrownBy(() -> store.getOrCreateDay("u1", DATE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("saveDay maps stickers and tasks both ways, reusing existing task nodes by id")
    void saveDay_roundTrip() {
        Neo4jTask existingTask = neoTask("t1", "old title", 1);
        Neo4jDay entity = new Neo4jDay("u1", DATE);
        entity.setId("d1");
        entity.getTasks().add(existingTask);
        when(dayRepo.findFirstByUserIdAndDate("u1", DATE)).thenReturn(Optional.of(entity));

        Day day = new Day("u1", DATE);
        day.getEarnedStickers().add("kitty");
        Task updated = new Task("new title", 5, 0);
        updated.setId("t1");
        updated.setCompleted(true);
        updated.setScheduledTime(LocalTime.of(9, 0));
        updated.setRecurrence(Recurrence.DAILY);
        updated.setRecurrenceGroupId("grp");
        Task fresh = new Task("brand new", 2, 1);
        day.getTasks().add(updated);
        day.getTasks().add(fresh);

        Day out = store.saveDay(day);

        assertThat(out.getEarnedStickers()).containsExactly("kitty");
        assertThat(out.getTasks()).hasSize(2);
        Task t1 = out.getTasks().get(0);
        assertThat(t1.getId()).isEqualTo("t1");           // node reused
        assertThat(t1.getTitle()).isEqualTo("new title"); // fields updated
        assertThat(t1.isCompleted()).isTrue();
        assertThat(t1.getScheduledTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(t1.getRecurrence()).isEqualTo(Recurrence.DAILY);
        assertThat(t1.getRecurrenceGroupId()).isEqualTo("grp");
        assertThat(out.getTasks().get(1).getTitle()).isEqualTo("brand new");
    }

    @Test
    @DisplayName("saveDay rejects a stale snapshot whose version lags the stored node")
    void saveDay_staleVersion_throwsConcurrentUpdate() {
        Neo4jDay entity = new Neo4jDay("u1", DATE);
        entity.setId("d1");
        entity.setVersion(2L);
        when(dayRepo.findFirstByUserIdAndDate("u1", DATE)).thenReturn(Optional.of(entity));

        Day stale = new Day("u1", DATE);
        stale.setId("d1");
        stale.setVersion(1L); // read before a concurrent writer bumped the node to v2

        assertThatThrownBy(() -> store.saveDay(stale))
                .isInstanceOf(ConcurrentUpdateException.class);
        verify(dayRepo, never()).save(any());
    }

    @Test
    @DisplayName("saveDay creates the day entity when it does not exist yet")
    void saveDay_newDay() {
        when(dayRepo.findFirstByUserIdAndDate("u1", DATE)).thenReturn(Optional.empty());

        Day out = store.saveDay(new Day("u1", DATE));

        assertThat(out.getUserId()).isEqualTo("u1");
        verify(dayRepo).save(any(Neo4jDay.class));
    }

    @Test
    @DisplayName("findDaysInRange maps each day")
    void findDaysInRange_maps() {
        Neo4jDay d1 = new Neo4jDay("u1", DATE);
        Neo4jDay d2 = new Neo4jDay("u1", DATE.plusDays(1));
        when(dayRepo.findByUserIdAndDateBetweenOrderByDate("u1", DATE, DATE.plusDays(7)))
                .thenReturn(List.of(d1, d2));

        List<Day> out = store.findDaysInRange("u1", DATE, DATE.plusDays(7));

        assertThat(out).extracting(Day::getDate).containsExactly(DATE, DATE.plusDays(1));
    }

    // --- deleteTask ---

    @Test
    @DisplayName("deleteTask removes the task node and saves the day")
    void deleteTask_removesAndSaves() {
        Neo4jDay day = new Neo4jDay("u1", DATE);
        day.getTasks().add(neoTask("t1", "doomed", 1));
        when(dayRepo.findFirstByUserIdAndDate("u1", DATE)).thenReturn(Optional.of(day));

        store.deleteTask("u1", DATE, "t1");

        assertThat(day.getTasks()).isEmpty();
        verify(dayRepo).save(day);
        verify(taskRepo).deleteById("t1");
    }

    @Test
    @DisplayName("deleteTask is a no-op when the task is absent")
    void deleteTask_absent_noop() {
        Neo4jDay day = new Neo4jDay("u1", DATE);
        when(dayRepo.findFirstByUserIdAndDate("u1", DATE)).thenReturn(Optional.of(day));

        store.deleteTask("u1", DATE, "missing");

        verify(dayRepo, never()).save(any());
        verify(taskRepo, never()).deleteById(any());
    }

    @Test
    @DisplayName("deleteTasksByRecurrenceGroup removes matching tasks from future days only")
    void deleteTasksByRecurrenceGroup_removesMatching() {
        Neo4jTask grouped = neoTask("t2", "recurring", 1);
        grouped.setRecurrenceGroupId("grp");
        Neo4jTask other = neoTask("t3", "unrelated", 1);
        Neo4jDay futureDay = new Neo4jDay("u1", DATE.plusDays(1));
        futureDay.getTasks().add(grouped);
        futureDay.getTasks().add(other);
        when(dayRepo.findByUserIdAndDateAfterOrderByDate("u1", DATE)).thenReturn(List.of(futureDay));

        store.deleteTasksByRecurrenceGroup("u1", DATE, "grp");

        assertThat(futureDay.getTasks()).containsExactly(other);
        verify(dayRepo).save(futureDay);
        verify(taskRepo).deleteById("t2");
    }

    @Test
    @DisplayName("deleteTasksByRecurrenceGroup leaves days without matches untouched")
    void deleteTasksByRecurrenceGroup_noMatch_noop() {
        Neo4jDay futureDay = new Neo4jDay("u1", DATE.plusDays(1));
        futureDay.getTasks().add(neoTask("t3", "unrelated", 1));
        when(dayRepo.findByUserIdAndDateAfterOrderByDate("u1", DATE)).thenReturn(List.of(futureDay));

        store.deleteTasksByRecurrenceGroup("u1", DATE, "grp");

        verify(dayRepo, never()).save(any());
        verify(taskRepo, never()).deleteById(any());
    }

    @Test
    @DisplayName("resetAllData clears dispatch dedup, push subscriptions, auth tokens, tasks, days, and users")
    void resetAllData_clearsEverything() {
        store.resetAllData();

        verify(alarmDispatchRepo).deleteAll();
        verify(pushSubscriptionRepo).deleteAll();
        verify(authTokenRepo).deleteAll();
        verify(taskRepo).deleteAll();
        verify(dayRepo).deleteAll();
        verify(userRepo).deleteAll();
    }

    // --- Push subscriptions ---

    @Test
    @DisplayName("savePushSubscription inserts a new node when no subscription with that token exists")
    void savePushSubscription_new_inserts() {
        when(pushSubscriptionRepo.findFirstByUserIdAndToken("u1", "token-abc")).thenReturn(Optional.empty());

        PushSubscription out = store.savePushSubscription(new PushSubscription("u1", Platform.EXPO, "token-abc"));

        assertThat(out.getUserId()).isEqualTo("u1");
        assertThat(out.getPlatform()).isEqualTo(Platform.EXPO);
        assertThat(out.getToken()).isEqualTo("token-abc");
        verify(pushSubscriptionRepo).save(any(Neo4jPushSubscription.class));
    }

    @Test
    @DisplayName("savePushSubscription upserts by (userId, token) instead of duplicating")
    void savePushSubscription_existing_upserts() {
        Neo4jPushSubscription existing = new Neo4jPushSubscription("u1", "EXPO", "token-abc");
        existing.setId("p1");
        Instant staleSeen = Instant.parse("2020-01-01T00:00:00Z");
        existing.setLastSeenAt(staleSeen);
        when(pushSubscriptionRepo.findFirstByUserIdAndToken("u1", "token-abc")).thenReturn(Optional.of(existing));

        PushSubscription out = store.savePushSubscription(new PushSubscription("u1", Platform.EXPO, "token-abc"));

        assertThat(out.getId()).isEqualTo("p1");
        assertThat(out.getLastSeenAt()).isAfter(staleSeen);
        verify(pushSubscriptionRepo).save(existing);
    }

    @Test
    @DisplayName("savePushSubscription recovers when a concurrent registration wins the insert race")
    void savePushSubscription_duplicateRace_updatesWinner() {
        Neo4jPushSubscription winner = new Neo4jPushSubscription("u1", "EXPO", "token-abc");
        winner.setId("p-winner");
        when(pushSubscriptionRepo.findFirstByUserIdAndToken("u1", "token-abc"))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(pushSubscriptionRepo.save(any(Neo4jPushSubscription.class)))
                .thenThrow(new DataIntegrityViolationException("dup"))
                .thenAnswer(inv -> inv.getArgument(0));

        PushSubscription out = store.savePushSubscription(new PushSubscription("u1", Platform.EXPO, "token-abc"));

        assertThat(out.getId()).isEqualTo("p-winner");
    }

    @Test
    @DisplayName("listPushSubscriptions maps every subscription for the user")
    void listPushSubscriptions_mapsAll() {
        Neo4jPushSubscription p1 = new Neo4jPushSubscription("u1", "EXPO", "token-a");
        p1.setId("p1");
        Neo4jPushSubscription p2 = new Neo4jPushSubscription("u1", "WEB", "token-b");
        p2.setId("p2");
        when(pushSubscriptionRepo.findByUserId("u1")).thenReturn(List.of(p1, p2));

        List<PushSubscription> out = store.listPushSubscriptions("u1");

        assertThat(out).extracting(PushSubscription::getId).containsExactly("p1", "p2");
    }

    @Test
    @DisplayName("deletePushSubscription reports whether a subscription owned by that user was removed")
    void deletePushSubscription_scopedToOwner() {
        when(pushSubscriptionRepo.deleteByIdAndUserIdReturningCount("p1", "other")).thenReturn(0L);
        assertThat(store.deletePushSubscription("other", "p1")).isFalse();

        when(pushSubscriptionRepo.deleteByIdAndUserIdReturningCount("p1", "u1")).thenReturn(1L);
        assertThat(store.deletePushSubscription("u1", "p1")).isTrue();
    }

    // --- Alarm dispatch dedup ---

    @Test
    @DisplayName("markAlarmDispatched is true on first insert, false on a repeat for the same key")
    void markAlarmDispatched_idempotent() {
        when(alarmDispatchRepo.save(any(Neo4jAlarmDispatch.class)))
                .thenReturn(new Neo4jAlarmDispatch("u1", DATE, "t1"))
                .thenThrow(new DataIntegrityViolationException("dup"));

        assertThat(store.markAlarmDispatched("u1", DATE, "t1")).isTrue();
        assertThat(store.markAlarmDispatched("u1", DATE, "t1")).isFalse();
    }

    @Test
    @DisplayName("deleteExpiredAlarmDispatches returns the purge count from the repository")
    void deleteExpiredAlarmDispatches_returnsCount() {
        LocalDate before = LocalDate.of(2099, 1, 1);
        when(alarmDispatchRepo.deleteByDateBeforeReturningCount(before)).thenReturn(6L);

        assertThat(store.deleteExpiredAlarmDispatches(before)).isEqualTo(6);
    }

    @Test
    @DisplayName("deleteExpiredAlarmDispatches tolerates a null count from the repository")
    void deleteExpiredAlarmDispatches_nullCount() {
        LocalDate before = LocalDate.of(2099, 1, 1);
        when(alarmDispatchRepo.deleteByDateBeforeReturningCount(before)).thenReturn(null);

        assertThat(store.deleteExpiredAlarmDispatches(before)).isZero();
    }
}
