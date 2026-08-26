-- Migrate all surrogate BIGSERIAL PKs to native UUID type.
-- For empty tables (fresh install) the UPDATEs are no-ops.
-- For non-empty tables, existing rows receive gen_random_uuid() values (PostgreSQL 13+);
-- FK relationships are re-mapped before the old columns are dropped.

-- 1. Drop FK constraints (IF EXISTS: idempotent against partial re-runs).
ALTER TABLE jpa_day_earned_stickers DROP CONSTRAINT IF EXISTS fk_stickers_day;
ALTER TABLE planner_task             DROP CONSTRAINT IF EXISTS fk_task_day;

-- 2. Drop BIGSERIAL auto-increment defaults.
ALTER TABLE planner_user      ALTER COLUMN id DROP DEFAULT;
ALTER TABLE planner_day       ALTER COLUMN id DROP DEFAULT;
ALTER TABLE planner_task      ALTER COLUMN id DROP DEFAULT;
ALTER TABLE planner_auth_token ALTER COLUMN id DROP DEFAULT;

-- 3. Add new UUID columns (nullable until populated).
ALTER TABLE planner_user        ADD COLUMN IF NOT EXISTS new_id         UUID DEFAULT gen_random_uuid();
ALTER TABLE planner_day         ADD COLUMN IF NOT EXISTS new_id         UUID DEFAULT gen_random_uuid();
ALTER TABLE planner_task        ADD COLUMN IF NOT EXISTS new_id         UUID DEFAULT gen_random_uuid();
ALTER TABLE planner_task        ADD COLUMN IF NOT EXISTS new_day_id     UUID;
ALTER TABLE jpa_day_earned_stickers ADD COLUMN IF NOT EXISTS new_jpa_day_id UUID;
ALTER TABLE planner_auth_token  ADD COLUMN IF NOT EXISTS new_id         UUID DEFAULT gen_random_uuid();

-- 4. Map FK columns via JOIN for non-empty tables.
UPDATE planner_task t
    SET new_day_id = d.new_id
    FROM planner_day d WHERE t.day_id = d.id;

UPDATE jpa_day_earned_stickers s
    SET new_jpa_day_id = d.new_id
    FROM planner_day d WHERE s.jpa_day_id = d.id;

-- 5. planner_user: drop BIGSERIAL PK, promote UUID column.
ALTER TABLE planner_user DROP CONSTRAINT planner_user_pkey;
ALTER TABLE planner_user DROP COLUMN id;
ALTER TABLE planner_user RENAME COLUMN new_id TO id;
ALTER TABLE planner_user ALTER COLUMN id SET NOT NULL;
ALTER TABLE planner_user ADD PRIMARY KEY (id);

-- 6. planner_day: same.
ALTER TABLE planner_day DROP CONSTRAINT planner_day_pkey;
ALTER TABLE planner_day DROP COLUMN id;
ALTER TABLE planner_day RENAME COLUMN new_id TO id;
ALTER TABLE planner_day ALTER COLUMN id SET NOT NULL;
ALTER TABLE planner_day ADD PRIMARY KEY (id);

-- 7. planner_task: id + day_id.
ALTER TABLE planner_task DROP CONSTRAINT planner_task_pkey;
ALTER TABLE planner_task DROP COLUMN id;
ALTER TABLE planner_task DROP COLUMN day_id;
ALTER TABLE planner_task RENAME COLUMN new_id TO id;
ALTER TABLE planner_task RENAME COLUMN new_day_id TO day_id;
ALTER TABLE planner_task ALTER COLUMN id     SET NOT NULL;
ALTER TABLE planner_task ALTER COLUMN day_id SET NOT NULL;
ALTER TABLE planner_task ADD PRIMARY KEY (id);

-- 8. jpa_day_earned_stickers:
ALTER TABLE jpa_day_earned_stickers DROP CONSTRAINT jpa_day_earned_stickers_pkey;
ALTER TABLE jpa_day_earned_stickers DROP COLUMN jpa_day_id;
ALTER TABLE jpa_day_earned_stickers RENAME COLUMN new_jpa_day_id TO jpa_day_id;
ALTER TABLE jpa_day_earned_stickers ALTER COLUMN jpa_day_id SET NOT NULL;
ALTER TABLE jpa_day_earned_stickers ALTER COLUMN sticker_code SET NOT NULL;
ALTER TABLE jpa_day_earned_stickers ADD PRIMARY KEY (jpa_day_id, sticker_code);

-- 9. planner_auth_token.
ALTER TABLE planner_auth_token DROP CONSTRAINT planner_auth_token_pkey;
ALTER TABLE planner_auth_token DROP COLUMN id;
ALTER TABLE planner_auth_token RENAME COLUMN new_id TO id;
ALTER TABLE planner_auth_token ALTER COLUMN id SET NOT NULL;
ALTER TABLE planner_auth_token ADD PRIMARY KEY (id);

-- 10. Drop now-unused BIGSERIAL sequences.
DROP SEQUENCE IF EXISTS planner_user_id_seq;
DROP SEQUENCE IF EXISTS planner_day_id_seq;
DROP SEQUENCE IF EXISTS planner_task_id_seq;
DROP SEQUENCE IF EXISTS planner_auth_token_id_seq;

-- 11. Re-add FK constraints.
ALTER TABLE jpa_day_earned_stickers
    ADD CONSTRAINT fk_stickers_day FOREIGN KEY (jpa_day_id) REFERENCES planner_day (id);
ALTER TABLE planner_task
    ADD CONSTRAINT fk_task_day FOREIGN KEY (day_id) REFERENCES planner_day (id);
