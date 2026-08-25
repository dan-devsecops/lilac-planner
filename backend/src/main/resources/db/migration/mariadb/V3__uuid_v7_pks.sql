-- Migrate all surrogate BIGINT AUTO_INCREMENT PKs to native UUID type (MariaDB 10.7+).
-- Idempotent: safe to re-run from any partial state, including a schema where the
-- BIGINT→UUID swap was already applied by a prior (failed) run of this migration.

-- 1. Drop FK constraints (IF EXISTS: no-op when already absent).
-- Names vary depending on how the schema was bootstrapped:
-- · Hibernate-generated (ddl-auto:create on the original production DB):
--     FKg97mn5ut9pcghk257cmd8o34g / FKefqpxtdnx27vjmjvl6bpi9mnu
-- · Explicit names from V1.sql (fresh installs, E2E, new environments):
--     fk_stickers_day / fk_task_day
ALTER TABLE jpa_day_earned_stickers DROP FOREIGN KEY IF EXISTS FKg97mn5ut9pcghk257cmd8o34g;
ALTER TABLE jpa_day_earned_stickers DROP FOREIGN KEY IF EXISTS fk_stickers_day;
ALTER TABLE planner_task             DROP FOREIGN KEY IF EXISTS FKefqpxtdnx27vjmjvl6bpi9mnu;
ALTER TABLE planner_task             DROP FOREIGN KEY IF EXISTS fk_task_day;

-- 2. Add new UUID columns (IF NOT EXISTS: no-op when already added by a prior partial run).
ALTER TABLE planner_user        ADD COLUMN IF NOT EXISTS new_id         UUID AFTER id;
ALTER TABLE planner_day         ADD COLUMN IF NOT EXISTS new_id         UUID AFTER id;
ALTER TABLE planner_task        ADD COLUMN IF NOT EXISTS new_id         UUID AFTER id;
ALTER TABLE planner_task        ADD COLUMN IF NOT EXISTS new_day_id     UUID AFTER day_id;
ALTER TABLE jpa_day_earned_stickers ADD COLUMN IF NOT EXISTS new_jpa_day_id UUID AFTER jpa_day_id;
ALTER TABLE planner_auth_token  ADD COLUMN IF NOT EXISTS new_id         UUID AFTER id;

-- 3. Generate UUIDs for existing rows; WHERE IS NULL skips rows already populated.
UPDATE planner_user        SET new_id = UUID()  WHERE new_id IS NULL;
UPDATE planner_day         SET new_id = UUID()  WHERE new_id IS NULL;
UPDATE planner_task        SET new_id = UUID()  WHERE new_id IS NULL;
UPDATE planner_auth_token  SET new_id = UUID()  WHERE new_id IS NULL;
UPDATE planner_task t JOIN planner_day d ON t.day_id = d.id
    SET t.new_day_id = d.new_id WHERE t.new_day_id IS NULL;
UPDATE jpa_day_earned_stickers s JOIN planner_day d ON s.jpa_day_id = d.id
    SET s.new_jpa_day_id = d.new_id WHERE s.new_jpa_day_id IS NULL;

-- 4–8. For each table: if id is still BIGINT, do the full column swap; if id is already
--      UUID (from a prior partial run), just drop the leftover new_id / new_day_id column
--      that step 2 re-added above.  Flyway's MySQL/MariaDB parser handles BEGIN…END
--      compound statements without a DELIMITER change.
DROP PROCEDURE IF EXISTS _v3_migrate;

CREATE PROCEDURE _v3_migrate()
BEGIN
  -- planner_user
  IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'planner_user'
             AND COLUMN_NAME = 'id' AND DATA_TYPE = 'bigint') THEN
    ALTER TABLE planner_user MODIFY COLUMN id BIGINT NOT NULL;
    ALTER TABLE planner_user DROP PRIMARY KEY;
    ALTER TABLE planner_user DROP COLUMN id;
    ALTER TABLE planner_user CHANGE COLUMN new_id id UUID NOT NULL FIRST;
    ALTER TABLE planner_user ADD PRIMARY KEY (id);
  ELSE
    ALTER TABLE planner_user DROP COLUMN IF EXISTS new_id;
  END IF;

  -- planner_day
  IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'planner_day'
             AND COLUMN_NAME = 'id' AND DATA_TYPE = 'bigint') THEN
    ALTER TABLE planner_day MODIFY COLUMN id BIGINT NOT NULL;
    ALTER TABLE planner_day DROP PRIMARY KEY;
    ALTER TABLE planner_day DROP COLUMN id;
    ALTER TABLE planner_day CHANGE COLUMN new_id id UUID NOT NULL FIRST;
    ALTER TABLE planner_day ADD PRIMARY KEY (id);
  ELSE
    ALTER TABLE planner_day DROP COLUMN IF EXISTS new_id;
  END IF;

  -- planner_task: both id and day_id columns
  IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'planner_task'
             AND COLUMN_NAME = 'id' AND DATA_TYPE = 'bigint') THEN
    ALTER TABLE planner_task MODIFY COLUMN id BIGINT NOT NULL;
    ALTER TABLE planner_task DROP PRIMARY KEY;
    ALTER TABLE planner_task DROP COLUMN id;
    ALTER TABLE planner_task DROP COLUMN day_id;
    ALTER TABLE planner_task CHANGE COLUMN new_id     id     UUID NOT NULL FIRST;
    ALTER TABLE planner_task CHANGE COLUMN new_day_id day_id UUID NOT NULL AFTER id;
    ALTER TABLE planner_task ADD PRIMARY KEY (id);
  ELSE
    ALTER TABLE planner_task DROP COLUMN IF EXISTS new_id;
    ALTER TABLE planner_task DROP COLUMN IF EXISTS new_day_id;
  END IF;

  -- jpa_day_earned_stickers: FK column only, no surrogate PK
  IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jpa_day_earned_stickers'
             AND COLUMN_NAME = 'jpa_day_id' AND DATA_TYPE = 'bigint') THEN
    ALTER TABLE jpa_day_earned_stickers DROP PRIMARY KEY;
    ALTER TABLE jpa_day_earned_stickers DROP COLUMN jpa_day_id;
    ALTER TABLE jpa_day_earned_stickers CHANGE COLUMN new_jpa_day_id jpa_day_id UUID NOT NULL FIRST;
    ALTER TABLE jpa_day_earned_stickers ADD PRIMARY KEY (jpa_day_id, sticker_code);
  ELSE
    ALTER TABLE jpa_day_earned_stickers DROP COLUMN IF EXISTS new_jpa_day_id;
  END IF;

  -- planner_auth_token
  IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'planner_auth_token'
             AND COLUMN_NAME = 'id' AND DATA_TYPE = 'bigint') THEN
    ALTER TABLE planner_auth_token MODIFY COLUMN id BIGINT NOT NULL;
    ALTER TABLE planner_auth_token DROP PRIMARY KEY;
    ALTER TABLE planner_auth_token DROP COLUMN id;
    ALTER TABLE planner_auth_token CHANGE COLUMN new_id id UUID NOT NULL FIRST;
    ALTER TABLE planner_auth_token ADD PRIMARY KEY (id);
  ELSE
    ALTER TABLE planner_auth_token DROP COLUMN IF EXISTS new_id;
  END IF;
END;

CALL _v3_migrate();
DROP PROCEDURE IF EXISTS _v3_migrate;

-- 9. Re-add FK constraints.
-- MariaDB places IF NOT EXISTS *after* FOREIGN KEY, not after CONSTRAINT - the
-- form `ADD CONSTRAINT <name> FOREIGN KEY IF NOT EXISTS (...)` is the only one it
-- parses (and is idempotent: a prior run's constraint of the same name is kept).
-- `ADD CONSTRAINT IF NOT EXISTS <name> FOREIGN KEY (...)` is a syntax error (1064).
ALTER TABLE jpa_day_earned_stickers
    ADD CONSTRAINT fk_stickers_day FOREIGN KEY IF NOT EXISTS (jpa_day_id) REFERENCES planner_day (id);
ALTER TABLE planner_task
    ADD CONSTRAINT fk_task_day FOREIGN KEY IF NOT EXISTS (day_id) REFERENCES planner_day (id);
