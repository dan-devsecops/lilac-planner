package com.lilac.planner.contract;

import com.lilac.planner.domain.AuthTokenType;
import com.lilac.planner.domain.Platform;
import com.lilac.planner.domain.Recurrence;
import com.lilac.planner.domain.Role;
import com.lilac.planner.model.AuthToken;
import com.lilac.planner.model.Day;
import com.lilac.planner.model.PushSubscription;
import com.lilac.planner.model.Task;
import com.lilac.planner.model.User;
import com.lilac.planner.persistence.PlannerStore;
import com.lilac.planner.service.ConcurrentUpdateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Behavioral contract every {@link PlannerStore} adapter must satisfy.
 * <p>
 * The whole architecture hinges on the three adapters (JPA, Neo4j, DynamoDB)
 * being interchangeable behind this port. Mock-based unit tests verify each
 * adapter's <em>calls</em>; this suite verifies <em>semantics</em> against a
 * real backend, so an adapter cannot silently drift (e.g. one adapter deleting
 * recurring instances while another keeps them).
 * <p>
 * Subclass per adapter and provide the store under test. Tests use unique
 * usernames and far-future dates so they are safe on shared databases.
 */
public abstract class PlannerStoreContractTest {

    protected abstract PlannerStore store();

    private User newUser() {
        return store().createUser("contract-" + System.nanoTime(), "Contract User");
    }

    // --- Users ---

    @Test
    @DisplayName("createUser is idempotent per username")
    void createUser_idempotent() {
        String username = "contract-idem-" + System.nanoTime();

        User first = store().createUser(username, "First");
        User second = store().createUser(username, "Second");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getDisplayName()).isEqualTo("First"); // original wins
    }

    @Test
    @DisplayName("a created user can be found by id and by username")
    void user_roundTrip() {
        User created = newUser();

        assertThat(store().findUserById(created.getId()))
                .map(User::getUsername).contains(created.getUsername());
        assertThat(store().findUserByUsername(created.getUsername()))
                .map(User::getId).contains(created.getId());
        assertThat(store().listUsers())
                .extracting(User::getId).contains(created.getId());
    }

    @Test
    @DisplayName("lookups for unknown users are empty")
    void user_unknown_isEmpty() {
        assertThat(store().findUserById("999999")).isEmpty();
        assertThat(store().findUserByUsername("no-such-user-" + System.nanoTime())).isEmpty();
    }

    @Test
    @DisplayName("createNativeUser round-trips credentials and roles, and is findable by email")
    void nativeUser_roundTrip() {
        long n = System.nanoTime();
        String username = "native-" + n;
        String email = "native-" + n + "@example.com";

        User created = store().createNativeUser(username, email, "Native User",
                "hashed-secret", List.of(Role.ADMIN));

        User byId = store().findUserById(created.getId()).orElseThrow();
        assertThat(byId.getUsername()).isEqualTo(username);
        assertThat(byId.getEmail()).isEqualTo(email);
        assertThat(byId.getPasswordHash()).isEqualTo("hashed-secret");
        assertThat(byId.getRoles()).containsExactly(Role.ADMIN);
        assertThat(store().findUserByEmail(email)).map(User::getId).contains(created.getId());
        assertThat(store().findUserByEmail("no-such-" + n + "@example.com")).isEmpty();
    }

    @Test
    @DisplayName("updateUserPassword and updateUserRoles persist")
    void updateUserCredentials() {
        long n = System.nanoTime();
        User u = store().createNativeUser("upd-" + n, "upd-" + n + "@example.com", "Upd",
                "old-hash", List.of(Role.USER));

        store().updateUserPassword(u.getId(), "new-hash");
        store().updateUserRoles(u.getId(), List.of(Role.ADMIN, Role.USER));

        User reloaded = store().findUserById(u.getId()).orElseThrow();
        assertThat(reloaded.getPasswordHash()).isEqualTo("new-hash");
        assertThat(reloaded.getRoles()).containsExactlyInAnyOrder(Role.ADMIN, Role.USER);
    }

    // --- Auth tokens ---

    @Test
    @DisplayName("auth tokens save, find by (type, hash), and delete by hash")
    void authToken_saveFindDelete() {
        User u = newUser();
        String hash = "hash-" + System.nanoTime();
        store().saveAuthToken(new AuthToken(AuthTokenType.REFRESH, hash, u.getId(),
                Instant.now().plusSeconds(3600)));

        AuthToken found = store().findAuthToken(AuthTokenType.REFRESH, hash).orElseThrow();
        assertThat(found.getUserId()).isEqualTo(u.getId());
        assertThat(found.getType()).isEqualTo(AuthTokenType.REFRESH);
        // a hash is only matched for its own type
        assertThat(store().findAuthToken(AuthTokenType.PASSWORD_RESET, hash)).isEmpty();

        store().deleteAuthToken(hash);
        assertThat(store().findAuthToken(AuthTokenType.REFRESH, hash)).isEmpty();
    }

    @Test
    @DisplayName("deleteAuthToken reports true only for the call that actually removed the row")
    void deleteAuthToken_singleUse() {
        User u = newUser();
        String hash = "single-use-" + System.nanoTime();
        store().saveAuthToken(new AuthToken(AuthTokenType.REFRESH, hash, u.getId(),
                Instant.now().plusSeconds(3600)));

        assertThat(store().deleteAuthToken(hash)).as("first delete wins").isTrue();
        assertThat(store().deleteAuthToken(hash)).as("second delete sees nothing").isFalse();
        assertThat(store().deleteAuthToken("never-existed-" + System.nanoTime())).isFalse();
    }

    @Test
    @DisplayName("deleteExpiredAuthTokens purges only expired tokens of both types and returns the count")
    void deleteExpiredAuthTokens_purgesOnlyExpired() {
        User u = newUser();
        long n = System.nanoTime();
        Instant now = Instant.now();
        String expiredRefresh = "exp-r-" + n, expiredReset = "exp-p-" + n;
        String liveRefresh = "live-r-" + n, liveReset = "live-p-" + n;
        store().saveAuthToken(new AuthToken(AuthTokenType.REFRESH, expiredRefresh, u.getId(), now.minusSeconds(60)));
        store().saveAuthToken(new AuthToken(AuthTokenType.PASSWORD_RESET, expiredReset, u.getId(), now.minusSeconds(60)));
        store().saveAuthToken(new AuthToken(AuthTokenType.REFRESH, liveRefresh, u.getId(), now.plusSeconds(3600)));
        store().saveAuthToken(new AuthToken(AuthTokenType.PASSWORD_RESET, liveReset, u.getId(), now.plusSeconds(3600)));

        int purged = store().deleteExpiredAuthTokens(now);

        assertThat(purged).isGreaterThanOrEqualTo(2); // at least our two; shared DBs may hold more
        assertThat(store().deleteExpiredAuthTokens(now)).as("second purge finds nothing").isZero();
        assertThat(store().findAuthToken(AuthTokenType.REFRESH, expiredRefresh)).isEmpty();
        assertThat(store().findAuthToken(AuthTokenType.PASSWORD_RESET, expiredReset)).isEmpty();
        assertThat(store().findAuthToken(AuthTokenType.REFRESH, liveRefresh))
                .as("live refresh token survives").isPresent();
        assertThat(store().findAuthToken(AuthTokenType.PASSWORD_RESET, liveReset))
                .as("live reset token survives").isPresent();
    }

    @Test
    @DisplayName("deleteAuthTokensForUser removes only that user's tokens of the given type")
    void deleteAuthTokensForUser_scoped() {
        User u = newUser();
        long n = System.nanoTime();
        String r1 = "r1-" + n, r2 = "r2-" + n, p1 = "p1-" + n;
        store().saveAuthToken(new AuthToken(AuthTokenType.REFRESH, r1, u.getId(), Instant.now().plusSeconds(3600)));
        store().saveAuthToken(new AuthToken(AuthTokenType.REFRESH, r2, u.getId(), Instant.now().plusSeconds(3600)));
        store().saveAuthToken(new AuthToken(AuthTokenType.PASSWORD_RESET, p1, u.getId(), Instant.now().plusSeconds(3600)));

        store().deleteAuthTokensForUser(AuthTokenType.REFRESH, u.getId());

        assertThat(store().findAuthToken(AuthTokenType.REFRESH, r1)).isEmpty();
        assertThat(store().findAuthToken(AuthTokenType.REFRESH, r2)).isEmpty();
        assertThat(store().findAuthToken(AuthTokenType.PASSWORD_RESET, p1))
                .as("password-reset token of the same user survives").isPresent();
    }

    // --- Days ---

    @Test
    @DisplayName("getOrCreateDay is idempotent for the same (user, date)")
    void getOrCreateDay_idempotent() {
        User u = newUser();
        LocalDate date = LocalDate.of(2099, 11, 1);

        Day first = store().getOrCreateDay(u.getId(), date);
        Day second = store().getOrCreateDay(u.getId(), date);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getDate()).isEqualTo(date);
        assertThat(second.getUserId()).isEqualTo(u.getId());
    }

    @Test
    @DisplayName("two concurrent getOrCreateDay calls for the same (user, date) both get the same day")
    void getOrCreateDay_concurrent_bothGetSameDay() throws Exception {
        User u = newUser();
        LocalDate date = LocalDate.of(2099, 11, 28);
        int callers = 2;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            List<Future<Day>> results = new java.util.ArrayList<>();
            for (int i = 0; i < callers; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(30, TimeUnit.SECONDS);
                    return store().getOrCreateDay(u.getId(), date);
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            Day first = results.get(0).get(60, TimeUnit.SECONDS);  // must not throw
            Day second = results.get(1).get(60, TimeUnit.SECONDS); // must not throw

            assertThat(first.getId()).isNotNull();
            assertThat(second.getId()).isEqualTo(first.getId());
            assertThat(second.getDate()).isEqualTo(date);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("saving a stale snapshot conflicts instead of silently reverting a concurrent update")
    void saveDay_staleSnapshot_conflictsInsteadOfLosingUpdate() {
        User u = newUser();
        LocalDate date = LocalDate.of(2099, 11, 29);
        Day day = store().getOrCreateDay(u.getId(), date);
        day.getTasks().add(new Task("contended", 3, 0));
        store().saveDay(day);

        // Two clients load the same day…
        Day copy1 = store().findDay(u.getId(), date).orElseThrow();
        Day copy2 = store().findDay(u.getId(), date).orElseThrow();

        // …the first completes the task and saves…
        copy1.getTasks().get(0).setCompleted(true);
        store().saveDay(copy1);

        // …so the second's snapshot is now stale and its save must conflict, not win.
        copy2.getTasks().get(0).setTitle("stale rename");
        PlannerStore store = store();
        assertThatThrownBy(() -> store.saveDay(copy2))
                .isInstanceOfAny(ConcurrentUpdateException.class, OptimisticLockingFailureException.class);

        Day reloaded = store().findDay(u.getId(), date).orElseThrow();
        assertThat(reloaded.getTasks()).hasSize(1);
        assertThat(reloaded.getTasks().get(0).isCompleted())
                .as("first writer's update survives").isTrue();
        assertThat(reloaded.getTasks().get(0).getTitle())
                .as("stale writer's change is not applied").isEqualTo("contended");
    }

    @Test
    @DisplayName("findDay is empty for a day that was never created")
    void findDay_unknown_isEmpty() {
        User u = newUser();

        assertThat(store().findDay(u.getId(), LocalDate.of(2099, 11, 2))).isEmpty();
    }

    @Test
    @DisplayName("saveDay round-trips every task field and assigns task ids")
    void saveDay_roundTripsTaskFields() {
        User u = newUser();
        LocalDate date = LocalDate.of(2099, 11, 3);
        Day day = store().getOrCreateDay(u.getId(), date);

        Task task = new Task("full task", 7, 0);
        task.setCompleted(true);
        task.setScheduledTime(LocalTime.of(9, 30));
        task.setRecurrence(Recurrence.WEEKLY);
        task.setRecurrenceGroupId("contract-grp");
        day.getTasks().add(task);
        day.getEarnedStickers().add("kitty");

        store().saveDay(day);
        Day reloaded = store().findDay(u.getId(), date).orElseThrow();

        assertThat(reloaded.getEarnedStickers()).containsExactly("kitty");
        assertThat(reloaded.getTasks()).hasSize(1);
        Task t = reloaded.getTasks().get(0);
        assertThat(t.getId()).isNotBlank();
        assertThat(t.getTitle()).isEqualTo("full task");
        assertThat(t.getPoints()).isEqualTo(7);
        assertThat(t.isCompleted()).isTrue();
        assertThat(t.getScheduledTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(t.getRecurrence()).isEqualTo(Recurrence.WEEKLY);
        assertThat(t.getRecurrenceGroupId()).isEqualTo("contract-grp");
    }

    @Test
    @DisplayName("saveDay updates an existing task in place instead of duplicating it")
    void saveDay_updatesExistingTask() {
        User u = newUser();
        LocalDate date = LocalDate.of(2099, 11, 4);
        Day day = store().getOrCreateDay(u.getId(), date);
        day.getTasks().add(new Task("original", 1, 0));
        Day saved = store().saveDay(day);
        String taskId = saved.getTasks().get(0).getId();

        saved.getTasks().get(0).setTitle("renamed");
        saved.getTasks().get(0).setPoints(9);
        store().saveDay(saved);

        Day reloaded = store().findDay(u.getId(), date).orElseThrow();
        assertThat(reloaded.getTasks()).hasSize(1);
        assertThat(reloaded.getTasks().get(0).getId()).isEqualTo(taskId);
        assertThat(reloaded.getTasks().get(0).getTitle()).isEqualTo("renamed");
        assertThat(reloaded.getTasks().get(0).getPoints()).isEqualTo(9);
    }

    @Test
    @DisplayName("findDaysInRange returns only days inside the bounds, ordered by date")
    void findDaysInRange_boundsAndOrder() {
        User u = newUser();
        LocalDate base = LocalDate.of(2099, 11, 10);
        store().getOrCreateDay(u.getId(), base.minusDays(1)); // outside
        store().getOrCreateDay(u.getId(), base);
        store().getOrCreateDay(u.getId(), base.plusDays(2));
        store().getOrCreateDay(u.getId(), base.plusDays(5)); // outside

        List<Day> out = store().findDaysInRange(u.getId(), base, base.plusDays(4));

        assertThat(out).extracting(Day::getDate)
                .containsExactly(base, base.plusDays(2));
    }

    @Test
    @DisplayName("days are scoped per user")
    void days_scopedPerUser() {
        User a = newUser();
        User b = newUser();
        LocalDate date = LocalDate.of(2099, 11, 12);
        Day dayA = store().getOrCreateDay(a.getId(), date);
        dayA.getTasks().add(new Task("a's task", 1, 0));
        store().saveDay(dayA);

        store().getOrCreateDay(b.getId(), date);

        assertThat(store().findDay(b.getId(), date).orElseThrow().getTasks()).isEmpty();
        assertThat(store().findDay(a.getId(), date).orElseThrow().getTasks()).hasSize(1);
    }

    // --- deleteTask ---

    @Test
    @DisplayName("deleteTask removes exactly the targeted task")
    void deleteTask_removesOnlyTarget() {
        User u = newUser();
        LocalDate date = LocalDate.of(2099, 11, 14);
        Day day = store().getOrCreateDay(u.getId(), date);
        day.getTasks().add(new Task("keep", 1, 0));
        day.getTasks().add(new Task("remove", 1, 1));
        Day saved = store().saveDay(day);
        String removeId = saved.getTasks().stream()
                .filter(t -> t.getTitle().equals("remove")).findFirst().orElseThrow().getId();

        store().deleteTask(u.getId(), date, removeId);

        Day reloaded = store().findDay(u.getId(), date).orElseThrow();
        assertThat(reloaded.getTasks()).extracting(Task::getTitle).containsExactly("keep");
    }

    @Test
    @DisplayName("deleteTask with an unknown id is a no-op")
    void deleteTask_unknownId_noop() {
        User u = newUser();
        LocalDate date = LocalDate.of(2099, 11, 15);
        Day day = store().getOrCreateDay(u.getId(), date);
        day.getTasks().add(new Task("survivor", 1, 0));
        store().saveDay(day);

        store().deleteTask(u.getId(), date, "999999");

        assertThat(store().findDay(u.getId(), date).orElseThrow().getTasks()).hasSize(1);
    }

    // --- deleteTasksByRecurrenceGroup ---

    @Test
    @DisplayName("deleteTasksByRecurrenceGroup removes matches strictly after 'from', keeps the past and unrelated tasks")
    void deleteTasksByRecurrenceGroup_futureMatchesOnly() {
        User u = newUser();
        LocalDate from = LocalDate.of(2099, 11, 20);
        String group = "contract-grp-" + System.nanoTime();

        // group instance in the past, on 'from' itself, and on two future days
        for (LocalDate d : List.of(from.minusDays(1), from, from.plusDays(1), from.plusDays(2))) {
            Day day = store().getOrCreateDay(u.getId(), d);
            Task t = new Task("recurring", 1, 0);
            t.setRecurrence(Recurrence.DAILY);
            t.setRecurrenceGroupId(group);
            day.getTasks().add(t);
            store().saveDay(day);
        }
        // unrelated task on a future day must survive
        Day futureDay = store().findDay(u.getId(), from.plusDays(1)).orElseThrow();
        futureDay.getTasks().add(new Task("unrelated", 1, 1));
        store().saveDay(futureDay);

        store().deleteTasksByRecurrenceGroup(u.getId(), from, group);

        assertThat(store().findDay(u.getId(), from.minusDays(1)).orElseThrow().getTasks())
                .as("past instance kept").hasSize(1);
        assertThat(store().findDay(u.getId(), from).orElseThrow().getTasks())
                .as("instance on 'from' itself kept").hasSize(1);
        assertThat(store().findDay(u.getId(), from.plusDays(1)).orElseThrow().getTasks())
                .as("future day keeps only the unrelated task")
                .extracting(Task::getTitle).containsExactly("unrelated");
        assertThat(store().findDay(u.getId(), from.plusDays(2)).orElseThrow().getTasks())
                .as("future instance removed").isEmpty();
    }

    // --- updateRecurringTaskTitle ---

    @Test
    @DisplayName("updateRecurringTaskSeriesFields updates title and points across all instances")
    void updateRecurringTaskSeriesFields_allInstances() {
        User u = newUser();
        String group = "series-grp-" + System.nanoTime();
        LocalDate base = LocalDate.of(2099, 12, 1);

        for (LocalDate d : List.of(base, base.plusDays(1), base.plusDays(2))) {
            Day day = store().getOrCreateDay(u.getId(), d);
            Task t = new Task("old title", 1, 0);
            t.setRecurrence(Recurrence.DAILY);
            t.setRecurrenceGroupId(group);
            day.getTasks().add(t);
            store().saveDay(day);
        }
        // unrelated task on the first day must be untouched
        Day firstDay = store().findDay(u.getId(), base).orElseThrow();
        firstDay.getTasks().add(new Task("unrelated", 5, 1));
        store().saveDay(firstDay);

        store().updateRecurringTaskSeriesFields(u.getId(), group, "new title", 7);

        for (LocalDate d : List.of(base, base.plusDays(1), base.plusDays(2))) {
            List<Task> series = store().findDay(u.getId(), d).orElseThrow().getTasks()
                    .stream().filter(t -> group.equals(t.getRecurrenceGroupId())).toList();
            assertThat(series).as("instance on " + d).hasSize(1);
            assertThat(series.get(0).getTitle()).isEqualTo("new title");
            assertThat(series.get(0).getPoints()).isEqualTo(7);
        }
        // unrelated task unchanged
        assertThat(store().findDay(u.getId(), base).orElseThrow().getTasks())
                .filteredOn(t -> t.getRecurrenceGroupId() == null)
                .satisfies(tasks -> {
                    assertThat(tasks.get(0).getTitle()).isEqualTo("unrelated");
                    assertThat(tasks.get(0).getPoints()).isEqualTo(5);
                });
    }

    @Test
    @DisplayName("updateRecurringTaskSeriesFields with null title leaves title unchanged")
    void updateRecurringTaskSeriesFields_nullTitlePreserved() {
        User u = newUser();
        String group = "series-grp2-" + System.nanoTime();
        LocalDate base = LocalDate.of(2099, 12, 5);

        Day day = store().getOrCreateDay(u.getId(), base);
        Task t = new Task("keep this title", 1, 0);
        t.setRecurrenceGroupId(group);
        day.getTasks().add(t);
        store().saveDay(day);

        store().updateRecurringTaskSeriesFields(u.getId(), group, null, 10);

        Task reloaded = store().findDay(u.getId(), base).orElseThrow().getTasks().get(0);
        assertThat(reloaded.getTitle()).isEqualTo("keep this title");
        assertThat(reloaded.getPoints()).isEqualTo(10);
    }

    @Test
    @DisplayName("updateRecurringTaskSeriesFields with null points leaves points unchanged")
    void updateRecurringTaskSeriesFields_nullPointsPreserved() {
        User u = newUser();
        String group = "series-grp3-" + System.nanoTime();
        LocalDate base = LocalDate.of(2099, 12, 6);

        Day day = store().getOrCreateDay(u.getId(), base);
        Task t = new Task("series task", 42, 0);
        t.setRecurrenceGroupId(group);
        day.getTasks().add(t);
        store().saveDay(day);

        store().updateRecurringTaskSeriesFields(u.getId(), group, "updated title", null);

        Task reloaded = store().findDay(u.getId(), base).orElseThrow().getTasks().get(0);
        assertThat(reloaded.getTitle()).isEqualTo("updated title");
        assertThat(reloaded.getPoints()).isEqualTo(42);
    }

    @Test
    @DisplayName("updateRecurringTaskSeriesFields does not affect another user's tasks with the same group id")
    void updateRecurringTaskSeriesFields_isolatedToRequestingUser() {
        User owner = newUser();
        User other = newUser();
        // Both users happen to have a task sharing the same recurrenceGroupId string
        // (an unlikely but possible collision if group IDs were not user-scoped).
        String group = "shared-grp-" + System.nanoTime();
        LocalDate base = LocalDate.of(2099, 12, 8);

        for (User u : List.of(owner, other)) {
            Day day = store().getOrCreateDay(u.getId(), base);
            Task t = new Task("original", 3, 0);
            t.setRecurrence(Recurrence.DAILY);
            t.setRecurrenceGroupId(group);
            day.getTasks().add(t);
            store().saveDay(day);
        }

        // Update only for the owner
        store().updateRecurringTaskSeriesFields(owner.getId(), group, "owner updated", 9);

        Task ownerTask = store().findDay(owner.getId(), base).orElseThrow().getTasks().get(0);
        assertThat(ownerTask.getTitle()).as("owner's task is updated").isEqualTo("owner updated");
        assertThat(ownerTask.getPoints()).as("owner's points are updated").isEqualTo(9);

        Task otherTask = store().findDay(other.getId(), base).orElseThrow().getTasks().get(0);
        assertThat(otherTask.getTitle()).as("other user's task is untouched").isEqualTo("original");
        assertThat(otherTask.getPoints()).as("other user's points are untouched").isEqualTo(3);
    }

    // --- Push subscriptions ---

    @Test
    @DisplayName("push subscriptions save and list, scoped per user")
    void pushSubscription_saveAndList() {
        User u = newUser();
        User other = newUser();

        PushSubscription saved = store().savePushSubscription(
                new PushSubscription(u.getId(), Platform.EXPO, "expo-token-" + System.nanoTime()));
        store().savePushSubscription(
                new PushSubscription(u.getId(), Platform.WEB, "web-token-" + System.nanoTime()));
        store().savePushSubscription(
                new PushSubscription(other.getId(), Platform.EXPO, "expo-token-other-" + System.nanoTime()));

        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getPlatform()).isEqualTo(Platform.EXPO);
        assertThat(store().listPushSubscriptions(u.getId())).hasSize(2);
        assertThat(store().listPushSubscriptions(other.getId())).hasSize(1);
    }

    @Test
    @DisplayName("savePushSubscription upserts by (userId, token) instead of duplicating")
    void pushSubscription_upsertByToken() {
        User u = newUser();
        String token = "upsert-token-" + System.nanoTime();

        PushSubscription first = store().savePushSubscription(new PushSubscription(u.getId(), Platform.WEB, token));

        PushSubscription second = new PushSubscription(u.getId(), Platform.WEB, token);
        second.setP256dh("new-p256dh");
        second.setAuth("new-auth");
        PushSubscription updated = store().savePushSubscription(second);

        assertThat(updated.getId()).as("same row, not a new one").isEqualTo(first.getId());
        assertThat(updated.getCreatedAt()).as("createdAt is preserved").isEqualTo(first.getCreatedAt());
        assertThat(updated.getP256dh()).isEqualTo("new-p256dh");
        assertThat(updated.getAuth()).isEqualTo("new-auth");
        assertThat(updated.getLastSeenAt()).isAfterOrEqualTo(first.getLastSeenAt());

        assertThat(store().listPushSubscriptions(u.getId())).as("no duplicate row").hasSize(1);
    }

    @Test
    @DisplayName("deletePushSubscription removes the subscription and enforces ownership")
    void pushSubscription_delete() {
        User owner = newUser();
        User other = newUser();
        PushSubscription saved = store().savePushSubscription(
                new PushSubscription(owner.getId(), Platform.EXPO, "delete-token-" + System.nanoTime()));

        assertThat(store().deletePushSubscription(other.getId(), saved.getId()))
                .as("another user cannot delete it").isFalse();
        assertThat(store().listPushSubscriptions(owner.getId())).hasSize(1);

        assertThat(store().deletePushSubscription(owner.getId(), saved.getId()))
                .as("owner can delete it").isTrue();
        assertThat(store().listPushSubscriptions(owner.getId())).isEmpty();

        assertThat(store().deletePushSubscription(owner.getId(), saved.getId()))
                .as("second delete finds nothing").isFalse();
    }

    // --- Timezone ---

    @Test
    @DisplayName("updateUserTimezone persists and is read back via findUserById")
    void updateUserTimezone_roundTrip() {
        User u = newUser();
        assertThat(u.getTimezone()).isNull();

        store().updateUserTimezone(u.getId(), "Europe/Prague");

        assertThat(store().findUserById(u.getId())).map(User::getTimezone).contains("Europe/Prague");
    }

    @Test
    @DisplayName("updateUserTimezone for an unknown user is a silent no-op")
    void updateUserTimezone_unknownUser_noop() {
        // a well-formed but non-existent id: some adapters (e.g. JPA) parse userId as a UUID
        // and would throw on a non-UUID string like "999999" before ever reaching a lookup
        String unknownUserId = UUID.randomUUID().toString();
        assertThatCode(() -> store().updateUserTimezone(unknownUserId, "Europe/Prague"))
                .doesNotThrowAnyException();
    }

    // --- Alarm dispatch dedup ---

    @Test
    @DisplayName("markAlarmDispatched inserts-if-absent per (userId, date, taskId)")
    void markAlarmDispatched_dedup() {
        User u = newUser();
        LocalDate date = LocalDate.of(2099, 12, 20);
        String taskId = "task-" + System.nanoTime();

        assertThat(store().markAlarmDispatched(u.getId(), date, taskId)).as("first call wins").isTrue();
        assertThat(store().markAlarmDispatched(u.getId(), date, taskId)).as("repeat call is a no-op").isFalse();

        assertThat(store().markAlarmDispatched(u.getId(), date.plusDays(1), taskId))
                .as("different date is a distinct triple").isTrue();
        assertThat(store().markAlarmDispatched(u.getId(), date, taskId + "-other"))
                .as("different task is a distinct triple").isTrue();

        User other = newUser();
        assertThat(store().markAlarmDispatched(other.getId(), date, taskId))
                .as("different user is a distinct triple even for the same date/task").isTrue();
    }

    @Test
    @DisplayName("deleteExpiredAlarmDispatches purges only records dated before the cutoff and returns the count")
    void deleteExpiredAlarmDispatches_purgesOnlyExpired() {
        User u = newUser();
        LocalDate cutoff = LocalDate.of(2099, 3, 1);
        String oldTaskId = "old-" + System.nanoTime();
        String liveTaskId = "live-" + System.nanoTime();
        store().markAlarmDispatched(u.getId(), cutoff.minusDays(1), oldTaskId);
        store().markAlarmDispatched(u.getId(), cutoff, liveTaskId);

        int purged = store().deleteExpiredAlarmDispatches(cutoff);

        assertThat(purged).isPositive(); // at least ours; shared DBs may hold more
        assertThat(store().deleteExpiredAlarmDispatches(cutoff)).as("second purge finds nothing new").isZero();
        // The purged record's key is free again; the surviving one is still reserved.
        assertThat(store().markAlarmDispatched(u.getId(), cutoff.minusDays(1), oldTaskId))
                .as("purged record no longer dedups").isTrue();
        assertThat(store().markAlarmDispatched(u.getId(), cutoff, liveTaskId))
                .as("live record still dedups").isFalse();
    }

    // --- resetAllData ---

    @Test
    @DisplayName("resetAllData wipes users, days and auth tokens")
    void resetAllData_wipes() {
        User u = newUser();
        store().getOrCreateDay(u.getId(), LocalDate.of(2099, 11, 25));
        String hash = "reset-hash-" + System.nanoTime();
        store().saveAuthToken(new AuthToken(AuthTokenType.REFRESH, hash, u.getId(), Instant.now().plusSeconds(3600)));

        store().resetAllData();

        assertThat(store().listUsers()).isEmpty();
        assertThat(store().findDay(u.getId(), LocalDate.of(2099, 11, 25))).isEmpty();
        assertThat((Optional<User>) store().findUserById(u.getId())).isEmpty();
        assertThat(store().findAuthToken(AuthTokenType.REFRESH, hash)).isEmpty();
    }
}
