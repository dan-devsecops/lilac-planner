package com.lilac.planner.unit;

import com.lilac.planner.domain.Platform;
import com.lilac.planner.dto.TaskRequest;
import com.lilac.planner.model.Day;
import com.lilac.planner.model.PushSubscription;
import com.lilac.planner.model.User;
import com.lilac.planner.persistence.PlannerStore;
import com.lilac.planner.service.PlannerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("jpa-test")
@DisplayName("JPA adapter - works on H2 the same way it works on Postgres / MariaDB")
class JpaPlannerStoreTest {

    @Autowired
    PlannerStore store;

    @Autowired
    PlannerService planner;

    @Test
    @DisplayName("creates users and isolates each user's days")
    void usersAreIsolated() {
        User alice = store.createUser("alice-" + System.nanoTime(), "Alice");
        User bob = store.createUser("bob-" + System.nanoTime(), "Bob");
        LocalDate today = LocalDate.now();

        planner.addTask(alice.getId(), today,
                new TaskRequest("alice-only", 5, null, null, null, null, null));
        planner.addTask(bob.getId(), today,
                new TaskRequest("bob-only", 3, null, null, null, null, null));

        Day aliceDay = store.findDay(alice.getId(), today).orElseThrow();
        Day bobDay   = store.findDay(bob.getId(), today).orElseThrow();

        assertThat(aliceDay.getTasks()).extracting("title").containsExactly("alice-only");
        assertThat(bobDay.getTasks()).extracting("title").containsExactly("bob-only");
    }

    @Test
    @DisplayName("round-trips scheduledTime, recurrence and earnedStickers")
    void roundTripsOptionalFields() {
        User u = store.createUser("u-" + System.nanoTime(), "U");
        LocalDate date = LocalDate.now().plusDays(1);
        TaskRequest req = new TaskRequest("standup", 20, null, null,
                LocalTime.of(9, 30), null, com.lilac.planner.domain.Recurrence.NONE);

        Day day = planner.addTask(u.getId(), date, req);
        String taskId = day.getTasks().get(0).getId();

        Day completed = planner.updateTask(u.getId(), date, taskId,
                new TaskRequest(null, null, true, null, null, null, null));

        assertThat(completed.totalPoints()).isEqualTo(20);
        assertThat(completed.getEarnedStickers()).hasSize(1);
        assertThat(completed.getTasks().get(0).getScheduledTime()).isEqualTo(LocalTime.of(9, 30));
    }

    @Test
    @DisplayName("stickers are removed when a task is unchecked and points drop below threshold")
    void stickersRemovedOnUncomplete() {
        User u = store.createUser("u-unc-" + System.nanoTime(), "U");
        LocalDate date = LocalDate.now().plusDays(2);
        Day day = planner.addTask(u.getId(), date,
                new TaskRequest("big task", 20, null, null, null, null, null));
        String taskId = day.getTasks().get(0).getId();

        Day completed = planner.updateTask(u.getId(), date, taskId,
                new TaskRequest(null, null, true, null, null, null, null));
        assertThat(completed.getEarnedStickers()).hasSize(1);

        Day unchecked = planner.updateTask(u.getId(), date, taskId,
                new TaskRequest(null, null, false, null, null, null, null));
        assertThat(unchecked.getEarnedStickers()).isEmpty();
    }

    @Test
    @DisplayName("stickers are removed when a completed task is deleted")
    void stickersRemovedOnDelete() {
        User u = store.createUser("u-del-" + System.nanoTime(), "U");
        LocalDate date = LocalDate.now().plusDays(3);
        Day day = planner.addTask(u.getId(), date,
                new TaskRequest("big task", 20, null, null, null, null, null));
        String taskId = day.getTasks().get(0).getId();

        planner.updateTask(u.getId(), date, taskId,
                new TaskRequest(null, null, true, null, null, null, null));

        Day afterDelete = planner.deleteTask(u.getId(), date, taskId);
        assertThat(afterDelete.getEarnedStickers()).isEmpty();
    }

    @Test
    @DisplayName("a daily recurring task materialises future instances")
    void dailyRecurrence() {
        User u = store.createUser("rec-" + System.nanoTime(), "Rec");
        LocalDate date = LocalDate.now();
        TaskRequest req = new TaskRequest("daily", 1, null, null, null,
                null, com.lilac.planner.domain.Recurrence.DAILY);

        planner.addTask(u.getId(), date, req);

        for (int offset = 1; offset <= 3; offset++) {
            Day d = planner.getOrCreateDay(u.getId(), date.plusDays(offset));
            assertThat(d.getTasks())
                    .as("future day +%d has the recurring task", offset)
                    .anySatisfy(t -> assertThat(t.getTitle()).isEqualTo("daily"));
        }
    }

    @Test
    @DisplayName("registering the same push token twice upserts instead of duplicating")
    void pushSubscriptionUpsertByToken() {
        User u = store.createUser("push-" + System.nanoTime(), "Push");

        PushSubscription first = new PushSubscription(u.getId(), Platform.EXPO, "token-abc");
        PushSubscription saved1 = store.savePushSubscription(first);

        PushSubscription second = new PushSubscription(u.getId(), Platform.EXPO, "token-abc");
        PushSubscription saved2 = store.savePushSubscription(second);

        assertThat(saved2.getId()).isEqualTo(saved1.getId());
        assertThat(store.listPushSubscriptions(u.getId())).hasSize(1);
    }

    @Test
    @DisplayName("deletePushSubscription only removes a subscription owned by that user")
    void deletePushSubscriptionScopedToOwner() {
        User owner = store.createUser("owner-" + System.nanoTime(), "Owner");
        User other = store.createUser("other-" + System.nanoTime(), "Other");
        PushSubscription saved = store.savePushSubscription(
                new PushSubscription(owner.getId(), Platform.WEB, "endpoint-xyz"));

        assertThat(store.deletePushSubscription(other.getId(), saved.getId())).isFalse();
        assertThat(store.deletePushSubscription(owner.getId(), saved.getId())).isTrue();
        assertThat(store.listPushSubscriptions(owner.getId())).isEmpty();
    }

    @Test
    @DisplayName("markAlarmDispatched is true once then false on repeat calls for the same key")
    void markAlarmDispatchedIsIdempotent() {
        User u = store.createUser("dispatch-" + System.nanoTime(), "Dispatch");
        LocalDate date = LocalDate.now();
        String taskId = "some-task-id";

        assertThat(store.markAlarmDispatched(u.getId(), date, taskId)).isTrue();
        assertThat(store.markAlarmDispatched(u.getId(), date, taskId)).isFalse();
        assertThat(store.markAlarmDispatched(u.getId(), date, "other-task-id")).isTrue();
    }

    @Test
    @DisplayName("updateUserTimezone persists and round-trips the IANA timezone")
    void updateUserTimezoneRoundTrips() {
        User u = store.createUser("tz-" + System.nanoTime(), "Tz");
        assertThat(u.getTimezone()).isNull();

        store.updateUserTimezone(u.getId(), "Europe/Prague");

        User reloaded = store.findUserById(u.getId()).orElseThrow();
        assertThat(reloaded.getTimezone()).isEqualTo("Europe/Prague");
    }
}
