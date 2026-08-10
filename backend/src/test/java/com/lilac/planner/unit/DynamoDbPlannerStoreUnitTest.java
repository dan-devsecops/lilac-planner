package com.lilac.planner.unit;

import com.lilac.planner.domain.AuthTokenType;
import com.lilac.planner.domain.Platform;
import com.lilac.planner.domain.Recurrence;
import com.lilac.planner.model.AuthToken;
import com.lilac.planner.model.Day;
import com.lilac.planner.model.PushSubscription;
import com.lilac.planner.model.Task;
import com.lilac.planner.model.User;
import com.lilac.planner.persistence.dynamodb.DynamoAlarmDispatch;
import com.lilac.planner.persistence.dynamodb.DynamoAuthToken;
import com.lilac.planner.persistence.dynamodb.DynamoDay;
import com.lilac.planner.persistence.dynamodb.DynamoDbPlannerStore;
import com.lilac.planner.persistence.dynamodb.DynamoPushSubscription;
import com.lilac.planner.persistence.dynamodb.DynamoTask;
import com.lilac.planner.persistence.dynamodb.DynamoUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import com.lilac.planner.service.ConcurrentUpdateException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DynamoDbPlannerStore - adapter mapping and persistence calls")
class DynamoDbPlannerStoreUnitTest {

    @Mock DynamoDbEnhancedClient enhancedClient;
    @Mock DynamoDbTable<DynamoUser> users;
    @Mock DynamoDbTable<DynamoDay> days;
    @Mock DynamoDbTable<DynamoAuthToken> authTokens;
    @Mock DynamoDbTable<DynamoPushSubscription> pushSubscriptions;
    @Mock DynamoDbTable<DynamoAlarmDispatch> alarmDispatches;
    @Mock DynamoDbIndex<DynamoUser> usernameIndex;
    @Mock DynamoDbIndex<DynamoUser> emailIndex;
    @Mock DynamoDbIndex<DynamoAuthToken> userTokenIndex;

    DynamoDbPlannerStore store;

    static final LocalDate DATE = LocalDate.of(2099, 6, 1);

    @BeforeEach
    void setUp() {
        store = new DynamoDbPlannerStore(enhancedClient, users, days, authTokens, pushSubscriptions, alarmDispatches);
        ReflectionTestUtils.setField(store, "alarmDispatchRetentionDays", 7L);
        when(users.index("by_username")).thenReturn(usernameIndex);
    }

    private static <T> PageIterable<T> pages(List<T> items) {
        SdkIterable<Page<T>> iterable = () -> List.of(Page.create(items)).iterator();
        return PageIterable.create(iterable);
    }

    private static <T> SdkIterable<Page<T>> indexPages(List<T> items) {
        return () -> List.of(Page.create(items)).iterator();
    }

    private static DynamoUser dynUser(String id, String username) {
        DynamoUser u = new DynamoUser();
        u.setId(id);
        u.setUsername(username);
        u.setDisplayName(username.toUpperCase());
        u.setCreatedAt(Instant.parse("2099-01-01T00:00:00Z").toString());
        return u;
    }

    private static DynamoDay dynDay(String userId, LocalDate date) {
        DynamoDay d = new DynamoDay();
        d.setId("d-" + date);
        d.setUserId(userId);
        d.setDate(date.toString());
        return d;
    }

    private static DynamoTask dynTask(String id, String title) {
        DynamoTask t = new DynamoTask();
        t.setId(id);
        t.setTitle(title);
        t.setPoints(1);
        t.setPosition(0);
        return t;
    }

    // --- Users ---

    @Test
    @DisplayName("createUser returns the existing user when the username is taken")
    void createUser_existing_returnsIt() {
        when(usernameIndex.query(any(QueryConditional.class)))
                .thenReturn(indexPages(List.of(dynUser("u1", "alice"))));

        User out = store.createUser("alice", "Alice");

        assertThat(out.getId()).isEqualTo("u1");
        verify(users, never()).putItem(any(DynamoUser.class));
    }

    @Test
    @DisplayName("createUser stores a new user with a generated id")
    void createUser_new_putsItem() {
        when(usernameIndex.query(any(QueryConditional.class))).thenReturn(indexPages(List.of()));

        User out = store.createUser("bob", "Bob");

        assertThat(out.getId()).isNotBlank();
        assertThat(out.getUsername()).isEqualTo("bob");
        verify(users).putItem(any(DynamoUser.class));
    }

    @Test
    @DisplayName("findUserById maps the item, empty when absent")
    void findUserById_maps() {
        when(users.getItem(any(Key.class))).thenReturn(dynUser("u1", "alice"));
        assertThat(store.findUserById("u1")).map(User::getUsername).contains("alice");

        when(users.getItem(any(Key.class))).thenReturn(null);
        assertThat(store.findUserById("missing")).isEmpty();
    }

    @Test
    @DisplayName("findUserById tolerates an unparseable createdAt")
    void findUserById_badCreatedAt_ignored() {
        DynamoUser u = dynUser("u1", "alice");
        u.setCreatedAt("not-a-timestamp");
        when(users.getItem(any(Key.class))).thenReturn(u);

        Optional<User> out = store.findUserById("u1");

        assertThat(out).isPresent();
        assertThat(out.get().getCreatedAt()).isNotNull(); // default kept
    }

    @Test
    @DisplayName("listUsers maps every scanned item")
    void listUsers_mapsAll() {
        when(users.scan()).thenReturn(pages(List.of(dynUser("u1", "alice"), dynUser("u2", "bob"))));

        List<User> out = store.listUsers();

        assertThat(out).extracting(User::getUsername).containsExactly("alice", "bob");
    }

    @Test
    @DisplayName("createNativeUser stores credentials and roles")
    void createNativeUser_putsItem() {
        User out = store.createNativeUser("alice", "alice@x.com", "Alice", "hash", List.of("ADMIN"));

        assertThat(out.getEmail()).isEqualTo("alice@x.com");
        assertThat(out.getRoles()).containsExactly("ADMIN");
        assertThat(out.getPasswordHash()).isEqualTo("hash");
        verify(users).putItem(any(DynamoUser.class));
    }

    @Test
    @DisplayName("findUserByEmail queries the by_email index; null email short-circuits")
    void findUserByEmail_queriesIndex() {
        DynamoUser u = dynUser("u1", "alice");
        u.setEmail("alice@x.com");
        when(users.index("by_email")).thenReturn(emailIndex);
        when(emailIndex.query(any(QueryConditional.class))).thenReturn(indexPages(List.of(u)));

        assertThat(store.findUserByEmail("alice@x.com")).map(User::getUsername).contains("alice");
        assertThat(store.findUserByEmail(null)).isEmpty();
    }

    @Test
    @DisplayName("updateUserPassword rewrites the item with the new hash")
    void updateUserPassword_putsItem() {
        DynamoUser u = dynUser("u1", "alice");
        when(users.getItem(any(Key.class))).thenReturn(u);

        store.updateUserPassword("u1", "new-hash");

        assertThat(u.getPasswordHash()).isEqualTo("new-hash");
        verify(users).putItem(u);
    }

    @Test
    @DisplayName("updateUserRoles rewrites the item with the new roles")
    void updateUserRoles_putsItem() {
        DynamoUser u = dynUser("u1", "alice");
        when(users.getItem(any(Key.class))).thenReturn(u);

        store.updateUserRoles("u1", List.of("ADMIN", "USER"));

        assertThat(u.getRoles()).containsExactly("ADMIN", "USER");
        verify(users).putItem(u);
    }

    // --- Auth tokens ---

    @Test
    @DisplayName("saveAuthToken stores the hashed token")
    void saveAuthToken_putsItem() {
        store.saveAuthToken(new AuthToken(AuthTokenType.REFRESH, "hash", "u1",
                Instant.parse("2099-01-01T00:00:00Z")));

        ArgumentCaptor<DynamoAuthToken> captor = ArgumentCaptor.forClass(DynamoAuthToken.class);
        verify(authTokens).putItem(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isEqualTo("hash");
        assertThat(captor.getValue().getType()).isEqualTo("REFRESH");
        assertThat(captor.getValue().getUserId()).isEqualTo("u1");
    }

    @Test
    @DisplayName("findAuthToken returns the item only when the type matches")
    void findAuthToken_typeMustMatch() {
        DynamoAuthToken t = new DynamoAuthToken();
        t.setTokenHash("hash");
        t.setType("REFRESH");
        t.setUserId("u1");
        t.setExpiresAt(Instant.parse("2099-01-01T00:00:00Z").toString());
        when(authTokens.getItem(any(Key.class))).thenReturn(t);

        assertThat(store.findAuthToken(AuthTokenType.REFRESH, "hash")).isPresent();
        assertThat(store.findAuthToken(AuthTokenType.PASSWORD_RESET, "hash")).isEmpty();

        when(authTokens.getItem(any(Key.class))).thenReturn(null);
        assertThat(store.findAuthToken(AuthTokenType.REFRESH, "missing")).isEmpty();
    }

    @Test
    @DisplayName("deleteAuthToken reports whether this call removed the token")
    void deleteAuthToken_reportsRemoval() {
        DynamoAuthToken old = new DynamoAuthToken();
        old.setTokenHash("hash");
        when(authTokens.deleteItem(any(Key.class))).thenReturn(old);
        assertThat(store.deleteAuthToken("hash")).isTrue();

        when(authTokens.deleteItem(any(Key.class))).thenReturn(null);
        assertThat(store.deleteAuthToken("hash")).as("already spent").isFalse();
    }

    @Test
    @DisplayName("deleteExpiredAuthTokens purges only tokens whose expiry parses and is in the past")
    void deleteExpiredAuthTokens_purgesOnlyExpired() {
        Instant now = Instant.parse("2099-06-01T00:00:00Z");
        DynamoAuthToken expired = new DynamoAuthToken();
        expired.setTokenHash("expired");
        expired.setExpiresAt(now.minusSeconds(60).toString());
        DynamoAuthToken live = new DynamoAuthToken();
        live.setTokenHash("live");
        live.setExpiresAt(now.plusSeconds(60).toString());
        DynamoAuthToken noExpiry = new DynamoAuthToken();
        noExpiry.setTokenHash("no-expiry");
        when(authTokens.scan()).thenReturn(pages(List.of(expired, live, noExpiry)));

        int purged = store.deleteExpiredAuthTokens(now);

        assertThat(purged).isEqualTo(1);
        verify(authTokens, times(1)).deleteItem(any(Key.class));
    }

    @Test
    @DisplayName("deleteAuthTokensForUser deletes only the matching type")
    void deleteAuthTokensForUser_filtersByType() {
        DynamoAuthToken refresh = new DynamoAuthToken();
        refresh.setTokenHash("h1");
        refresh.setType("REFRESH");
        refresh.setUserId("u1");
        DynamoAuthToken reset = new DynamoAuthToken();
        reset.setTokenHash("h2");
        reset.setType("PASSWORD_RESET");
        reset.setUserId("u1");
        when(authTokens.index("by_user")).thenReturn(userTokenIndex);
        when(userTokenIndex.query(any(QueryConditional.class)))
                .thenReturn(indexPages(List.of(refresh, reset)));

        store.deleteAuthTokensForUser(AuthTokenType.REFRESH, "u1");

        verify(authTokens, times(1)).deleteItem(any(Key.class));
    }

    // --- Days ---

    @Test
    @DisplayName("getOrCreateDay returns the existing day")
    void getOrCreateDay_existing() {
        when(days.getItem(any(Key.class))).thenReturn(dynDay("u1", DATE));

        Day out = store.getOrCreateDay("u1", DATE);

        assertThat(out.getDate()).isEqualTo(DATE);
        verify(days, never()).putItem(any(DynamoDay.class));
    }

    @Test
    @DisplayName("getOrCreateDay stores a new day when none exists")
    void getOrCreateDay_new_putsItem() {
        when(days.getItem(any(Key.class))).thenReturn(null);

        Day out = store.getOrCreateDay("u1", DATE);

        assertThat(out.getUserId()).isEqualTo("u1");
        assertThat(out.getDate()).isEqualTo(DATE);
        verify(days).putItem(any(DynamoDay.class));
    }

    @Test
    @DisplayName("getOrCreateDay recovers when a concurrent insert won the race")
    void getOrCreateDay_conditionalCheckRace_returnsExisting() {
        DynamoDay winner = dynDay("u1", DATE);
        winner.setVersion(1L);
        when(days.getItem(any(Key.class))).thenReturn(null, winner);
        doThrow(ConditionalCheckFailedException.builder().message("exists").build())
                .when(days).putItem(any(DynamoDay.class));

        Day out = store.getOrCreateDay("u1", DATE);

        assertThat(out.getId()).isEqualTo(winner.getId());
        assertThat(out.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("saveDay writes conditionally on the snapshot's version and maps a stale write to ConcurrentUpdateException")
    void saveDay_staleVersion_throwsConcurrentUpdate() {
        doThrow(ConditionalCheckFailedException.builder().message("version mismatch").build())
                .when(days).putItem(any(DynamoDay.class));

        Day stale = new Day("u1", DATE);
        stale.setId("d1");
        stale.setVersion(1L);

        assertThatThrownBy(() -> store.saveDay(stale))
                .isInstanceOf(ConcurrentUpdateException.class);
    }

    @Test
    @DisplayName("saveDay returns a Day carrying the incremented version so it can be saved again")
    void saveDay_returnsIncrementedVersion() {
        Day day = new Day("u1", DATE);
        day.setId("d1");
        day.setVersion(3L);

        Day out = store.saveDay(day);

        assertThat(out.getVersion()).isEqualTo(4L);
        verify(days).putItem(any(DynamoDay.class));
    }

    @Test
    @DisplayName("saveDay maps tasks both ways, keeping existing ids and generating new ones")
    void saveDay_roundTrip() {
        Day day = new Day("u1", DATE);
        day.setId("d1");
        day.getEarnedStickers().add("kitty");
        Task withId = new Task("keep id", 5, 0);
        withId.setId("t1");
        withId.setCompleted(true);
        withId.setScheduledTime(LocalTime.of(9, 30));
        withId.setRecurrence(Recurrence.WEEKLY);
        withId.setRecurrenceGroupId("grp");
        Task fresh = new Task("new task", 2, 1);
        day.getTasks().add(withId);
        day.getTasks().add(fresh);

        Day out = store.saveDay(day);

        ArgumentCaptor<DynamoDay> captor = ArgumentCaptor.forClass(DynamoDay.class);
        verify(days).putItem(captor.capture());
        DynamoDay stored = captor.getValue();
        assertThat(stored.getDate()).isEqualTo(DATE.toString());
        assertThat(stored.getEarnedStickers()).containsExactly("kitty");
        assertThat(stored.getTasks().get(0).getId()).isEqualTo("t1");
        assertThat(stored.getTasks().get(0).getScheduledTime()).isEqualTo("09:30");
        assertThat(stored.getTasks().get(0).getRecurrence()).isEqualTo("WEEKLY");
        assertThat(stored.getTasks().get(1).getId()).isNotBlank(); // generated

        assertThat(out.getTasks().get(0).getScheduledTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(out.getTasks().get(0).getRecurrence()).isEqualTo(Recurrence.WEEKLY);
        assertThat(out.getTasks().get(0).getRecurrenceGroupId()).isEqualTo("grp");
    }

    @Test
    @DisplayName("saveDay generates a day id when missing")
    void saveDay_missingId_generated() {
        Day out = store.saveDay(new Day("u1", DATE));

        assertThat(out.getId()).isNotBlank();
    }

    @Test
    @DisplayName("findDay maps the stored item, empty when absent")
    void findDay_maps() {
        DynamoDay d = dynDay("u1", DATE);
        d.getTasks().add(dynTask("t1", "stored"));
        when(days.getItem(any(Key.class))).thenReturn(d);

        Optional<Day> out = store.findDay("u1", DATE);

        assertThat(out).isPresent();
        assertThat(out.get().getTasks()).extracting(Task::getTitle).containsExactly("stored");

        when(days.getItem(any(Key.class))).thenReturn(null);
        assertThat(store.findDay("u1", DATE)).isEmpty();
    }

    @Test
    @DisplayName("findDay tolerates bad recurrence and timestamps in stored tasks")
    void findDay_badStoredValues_defaulted() {
        DynamoTask t = dynTask("t1", "weird");
        t.setRecurrence("NOT_A_RECURRENCE");
        t.setCreatedAt("garbage");
        t.setScheduledTime("25:99");
        DynamoDay d = dynDay("u1", DATE);
        d.getTasks().add(t);
        when(days.getItem(any(Key.class))).thenReturn(d);

        Task out = store.findDay("u1", DATE).orElseThrow().getTasks().get(0);

        assertThat(out.getRecurrence()).isEqualTo(Recurrence.NONE);
        assertThat(out.getScheduledTime()).isNull();
    }

    @Test
    @DisplayName("findDaysInRange maps each day in the query result")
    void findDaysInRange_maps() {
        when(days.query(any(QueryConditional.class)))
                .thenReturn(pages(List.of(dynDay("u1", DATE), dynDay("u1", DATE.plusDays(1)))));

        List<Day> out = store.findDaysInRange("u1", DATE, DATE.plusDays(7));

        assertThat(out).extracting(Day::getDate).containsExactly(DATE, DATE.plusDays(1));
    }

    // --- deleteTask ---

    @Test
    @DisplayName("deleteTask removes the task and persists the day")
    void deleteTask_removesAndSaves() {
        DynamoDay d = dynDay("u1", DATE);
        d.getTasks().add(dynTask("t1", "doomed"));
        when(days.getItem(any(Key.class))).thenReturn(d);

        store.deleteTask("u1", DATE, "t1");

        ArgumentCaptor<DynamoDay> captor = ArgumentCaptor.forClass(DynamoDay.class);
        verify(days).putItem(captor.capture());
        assertThat(captor.getValue().getTasks()).isEmpty();
    }

    @Test
    @DisplayName("deleteTask is a no-op when the task is absent")
    void deleteTask_absent_noop() {
        when(days.getItem(any(Key.class))).thenReturn(dynDay("u1", DATE));

        store.deleteTask("u1", DATE, "missing");

        verify(days, never()).putItem(any(DynamoDay.class));
    }

    @Test
    @DisplayName("deleteTasksByRecurrenceGroup strips matching tasks from future days")
    void deleteTasksByRecurrenceGroup_removesMatching() {
        DynamoTask grouped = dynTask("t2", "recurring");
        grouped.setRecurrenceGroupId("grp");
        DynamoTask other = dynTask("t3", "unrelated");
        DynamoDay future = dynDay("u1", DATE.plusDays(1));
        future.getTasks().add(grouped);
        future.getTasks().add(other);
        when(days.query(any(QueryConditional.class))).thenReturn(pages(List.of(future)));
        when(days.getItem(any(Key.class))).thenReturn(future);

        store.deleteTasksByRecurrenceGroup("u1", DATE, "grp");

        verify(enhancedClient).transactWriteItems(any(Consumer.class));
        assertThat(future.getTasks())
                .extracting(DynamoTask::getTitle).containsExactly("unrelated");
    }

    @Test
    @DisplayName("deleteTasksByRecurrenceGroup leaves days without matches untouched")
    void deleteTasksByRecurrenceGroup_noMatch_noop() {
        DynamoDay future = dynDay("u1", DATE.plusDays(1));
        future.getTasks().add(dynTask("t3", "unrelated"));
        when(days.query(any(QueryConditional.class))).thenReturn(pages(List.of(future)));

        store.deleteTasksByRecurrenceGroup("u1", DATE, "grp");

        verify(enhancedClient, never()).transactWriteItems(any(Consumer.class));
    }

    @Test
    @DisplayName("resetAllData deletes every scanned day, user and auth token")
    void resetAllData_deletesEverything() {
        DynamoDay d = dynDay("u1", DATE);
        DynamoUser u = dynUser("u1", "alice");
        DynamoAuthToken t = new DynamoAuthToken();
        t.setTokenHash("h1");
        when(days.scan()).thenReturn(pages(List.of(d)));
        when(users.scan()).thenReturn(pages(List.of(u)));
        when(authTokens.scan()).thenReturn(pages(List.of(t)));
        when(pushSubscriptions.scan()).thenReturn(pages(List.of()));
        when(alarmDispatches.scan()).thenReturn(pages(List.of()));

        store.resetAllData();

        verify(days).deleteItem(d);
        verify(users).deleteItem(u);
        verify(authTokens).deleteItem(t);
    }

    // --- Push subscriptions ---

    @Test
    @DisplayName("savePushSubscription creates a new item with a generated id when none exists for (userId, token)")
    void savePushSubscription_new_generatesId() {
        when(pushSubscriptions.getItem(any(Key.class))).thenReturn(null);

        var saved = store.savePushSubscription(
                new PushSubscription("u1", Platform.EXPO, "tok-1"));

        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getUserId()).isEqualTo("u1");
        assertThat(saved.getToken()).isEqualTo("tok-1");
        verify(pushSubscriptions).putItem(any(DynamoPushSubscription.class));
    }

    @Test
    @DisplayName("savePushSubscription overwrites in place, keeping the original id and createdAt")
    void savePushSubscription_existing_upserts() {
        DynamoPushSubscription existing = new DynamoPushSubscription();
        existing.setUserId("u1");
        existing.setToken("tok-1");
        existing.setId("sub-1");
        existing.setCreatedAt(Instant.parse("2099-01-01T00:00:00Z").toString());
        when(pushSubscriptions.getItem(any(Key.class))).thenReturn(existing);

        var saved = store.savePushSubscription(
                new PushSubscription("u1", Platform.WEB, "tok-1"));

        assertThat(saved.getId()).isEqualTo("sub-1");
        assertThat(saved.getCreatedAt()).isEqualTo(Instant.parse("2099-01-01T00:00:00Z"));
        verify(pushSubscriptions).putItem(any(DynamoPushSubscription.class));
    }

    @Test
    @DisplayName("listPushSubscriptions maps every item in the user's partition")
    void listPushSubscriptions_maps() {
        DynamoPushSubscription sub = new DynamoPushSubscription();
        sub.setUserId("u1");
        sub.setToken("tok-1");
        sub.setId("sub-1");
        sub.setPlatform("EXPO");
        when(pushSubscriptions.query(any(QueryConditional.class))).thenReturn(pages(List.of(sub)));

        assertThat(store.listPushSubscriptions("u1"))
                .extracting(PushSubscription::getToken)
                .containsExactly("tok-1");
    }

    @Test
    @DisplayName("deletePushSubscription removes only the matching id scoped to the user")
    void deletePushSubscription_scopedToUser() {
        DynamoPushSubscription sub = new DynamoPushSubscription();
        sub.setUserId("u1");
        sub.setToken("tok-1");
        sub.setId("sub-1");
        when(pushSubscriptions.query(any(QueryConditional.class))).thenReturn(pages(List.of(sub)));

        assertThat(store.deletePushSubscription("u1", "sub-1")).isTrue();
        verify(pushSubscriptions).deleteItem(any(Key.class));

        when(pushSubscriptions.query(any(QueryConditional.class))).thenReturn(pages(List.of()));
        assertThat(store.deletePushSubscription("u1", "missing")).isFalse();
    }

    // --- Alarm dispatch dedup ---

    @Test
    @DisplayName("markAlarmDispatched returns true on first insert and false when already dispatched")
    void markAlarmDispatched_dedups() {
        assertThat(store.markAlarmDispatched("u1", DATE, "t1")).isTrue();

        doThrow(ConditionalCheckFailedException.builder().message("exists").build())
                .when(alarmDispatches).putItem(any(DynamoAlarmDispatch.class));
        assertThat(store.markAlarmDispatched("u1", DATE, "t1")).isFalse();
    }

    @Test
    @DisplayName("markAlarmDispatched sets a native DynamoDB TTL attribute retentionDays past the dispatch date")
    void markAlarmDispatched_setsTtl() {
        store.markAlarmDispatched("u1", DATE, "t1");

        ArgumentCaptor<DynamoAlarmDispatch> captor = ArgumentCaptor.forClass(DynamoAlarmDispatch.class);
        verify(alarmDispatches).putItem(captor.capture());
        long expectedTtl = DATE.plusDays(7).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        assertThat(captor.getValue().getTtl()).isEqualTo(expectedTtl);
    }

    @Test
    @DisplayName("deleteExpiredAlarmDispatches removes only records dated before the cutoff")
    void deleteExpiredAlarmDispatches_purgesOnlyExpired() {
        LocalDate cutoff = LocalDate.of(2099, 6, 10);
        DynamoAlarmDispatch expired = new DynamoAlarmDispatch();
        expired.setUserId("u1");
        expired.setDate(cutoff.minusDays(1).toString());
        expired.setDateTaskKey(cutoff.minusDays(1) + "#t1");
        DynamoAlarmDispatch live = new DynamoAlarmDispatch();
        live.setUserId("u2");
        live.setDate(cutoff.toString());
        live.setDateTaskKey(cutoff + "#t2");
        when(alarmDispatches.scan()).thenReturn(pages(List.of(expired, live)));

        int purged = store.deleteExpiredAlarmDispatches(cutoff);

        assertThat(purged).isEqualTo(1);
        ArgumentCaptor<Key> keyCaptor = ArgumentCaptor.forClass(Key.class);
        verify(alarmDispatches, times(1)).deleteItem(keyCaptor.capture());
    }

    // --- Timezone ---

    @Test
    @DisplayName("updateUserTimezone rewrites the item with the new timezone")
    void updateUserTimezone_putsItem() {
        DynamoUser u = dynUser("u1", "alice");
        when(users.getItem(any(Key.class))).thenReturn(u);

        store.updateUserTimezone("u1", "Europe/Prague");

        assertThat(u.getTimezone()).isEqualTo("Europe/Prague");
        verify(users).putItem(u);
    }
}
