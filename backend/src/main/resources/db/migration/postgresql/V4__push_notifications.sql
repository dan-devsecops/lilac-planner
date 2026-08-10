ALTER TABLE planner_user ADD COLUMN timezone VARCHAR(64);

CREATE TABLE planner_push_subscription (
    id           UUID         PRIMARY KEY,
    user_id      VARCHAR(80)  NOT NULL,
    platform     VARCHAR(10)  NOT NULL,
    token        VARCHAR(512) NOT NULL,
    p256dh       VARCHAR(255),
    auth         VARCHAR(255),
    created_at   TIMESTAMP(6) NOT NULL,
    last_seen_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_push_subscription_user_token UNIQUE (user_id, token)
);

CREATE INDEX idx_push_subscription_user ON planner_push_subscription (user_id);

CREATE TABLE planner_alarm_dispatch (
    user_id       VARCHAR(80) NOT NULL,
    dispatch_date DATE        NOT NULL,
    task_id       VARCHAR(64) NOT NULL,
    CONSTRAINT pk_alarm_dispatch PRIMARY KEY (user_id, dispatch_date, task_id)
);
