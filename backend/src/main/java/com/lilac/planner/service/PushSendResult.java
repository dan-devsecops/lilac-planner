package com.lilac.planner.service;

/**
 * Outcome of a single push send attempt, returned by {@link WebPushSender} and
 * {@link ExpoPushSender}. Callers (e.g. the alarm dispatch job) use {@link Status#INVALID_SUBSCRIPTION}
 * as the signal to prune the subscription via {@code PlannerStore.deletePushSubscription} - the
 * senders never touch the store themselves.
 */
public record PushSendResult(Status status, String detail) {

    public enum Status {
        SUCCESS,
        TRANSIENT_FAILURE,
        INVALID_SUBSCRIPTION,
        UNAVAILABLE
    }

    public static PushSendResult success() {
        return new PushSendResult(Status.SUCCESS, null);
    }

    public static PushSendResult invalidSubscription() {
        return new PushSendResult(Status.INVALID_SUBSCRIPTION, null);
    }

    public static PushSendResult transientFailure(String detail) {
        return new PushSendResult(Status.TRANSIENT_FAILURE, detail);
    }

    public static PushSendResult unavailable() {
        return new PushSendResult(Status.UNAVAILABLE, "Sender not configured");
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isInvalidSubscription() {
        return status == Status.INVALID_SUBSCRIPTION;
    }
}
