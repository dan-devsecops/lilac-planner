CREATE TABLE planner_user (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(80)  NOT NULL,
    display_name  VARCHAR(120),
    email         VARCHAR(160),
    password_hash VARCHAR(100),
    roles         VARCHAR(255),
    created_at    TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_user_username UNIQUE (username),
    CONSTRAINT uq_user_email    UNIQUE (email)
);

CREATE TABLE planner_day (
    id       BIGSERIAL   PRIMARY KEY,
    user_id  VARCHAR(64) NOT NULL,
    day_date DATE        NOT NULL,
    CONSTRAINT uk_day_user_date UNIQUE (user_id, day_date)
);

CREATE TABLE jpa_day_earned_stickers (
    jpa_day_id   BIGINT       NOT NULL,
    sticker_code VARCHAR(255) NOT NULL,
    PRIMARY KEY (jpa_day_id, sticker_code),
    CONSTRAINT fk_stickers_day FOREIGN KEY (jpa_day_id) REFERENCES planner_day (id)
);

CREATE TABLE planner_task (
    id                  BIGSERIAL    PRIMARY KEY,
    day_id              BIGINT       NOT NULL,
    title               VARCHAR(240) NOT NULL,
    points              INT          NOT NULL DEFAULT 1,
    completed           BOOLEAN      NOT NULL DEFAULT FALSE,
    position            INT          NOT NULL,
    created_at          TIMESTAMP(6) NOT NULL,
    scheduled_time      TIME,
    recurrence          VARCHAR(16)  NOT NULL DEFAULT 'NONE',
    recurrence_group_id VARCHAR(64),
    CONSTRAINT fk_task_day FOREIGN KEY (day_id) REFERENCES planner_day (id)
);

CREATE TABLE planner_auth_token (
    id         BIGSERIAL    PRIMARY KEY,
    type       VARCHAR(20)  NOT NULL,
    token_hash VARCHAR(100) NOT NULL,
    user_id    VARCHAR(80)  NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_auth_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_auth_token_hash ON planner_auth_token (token_hash);
CREATE INDEX idx_auth_token_user ON planner_auth_token (type, user_id);
