-- ============================================================================
-- SketchTrench · V1 · Initial schema
-- ----------------------------------------------------------------------------
-- Design principles:
--   1. NORMALIZED: data is stored once, related by foreign keys. No denormalized
--      "score" copy living in two tables unless it is an intentional, explained cache.
--   2. ENUMS AS VARCHAR + CHECK instead of native PG enums: adding a value later is a
--      one-line migration instead of an ALTER TYPE. Portable and diff-friendly.
--   3. BIGSERIAL PKs (surrogate keys): stable identifiers never reused, simpler joins.
--   4. TIMESTAMPTZ everywhere + UTC in the app: wall-clock math never depends on a server TZ.
--   5. INDEXES match the exact lookup patterns each feature uses (joins + status filters).
-- ============================================================================

-- ============================================================================
-- Identity & authentication
-- ============================================================================

CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    username       VARCHAR(32)  NOT NULL,                 -- login handle, unique
    email          VARCHAR(255) NOT NULL,
    password       VARCHAR(100) NOT NULL,                 -- BCrypt hash (60 chars), room for argon2
    display_name   VARCHAR(50)  NOT NULL,                 -- what other players see
    avatar_url     VARCHAR(500),
    bio            VARCHAR(500),
    xp             INTEGER      NOT NULL DEFAULT 0,
    level          INTEGER      NOT NULL DEFAULT 1,
    elo_rating     INTEGER      NOT NULL DEFAULT 1200,    -- ranking baseline (Module 5)
    league         VARCHAR(16)  NOT NULL DEFAULT 'BRONZE',
    online_status  VARCHAR(16)  NOT NULL DEFAULT 'OFFLINE',
    last_seen_at   TIMESTAMPTZ,
    email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    is_banned      BOOLEAN      NOT NULL DEFAULT FALSE,
    banned_until   TIMESTAMPTZ,                           -- NULL = permanent ban
    muted_until    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_username        UNIQUE (username),
    CONSTRAINT uq_users_email           UNIQUE (email),
    CONSTRAINT ck_users_league          CHECK (league IN ('BRONZE','SILVER','GOLD','PLATINUM','DIAMOND','MASTER')),
    CONSTRAINT ck_users_online_status   CHECK (online_status IN ('ONLINE','OFFLINE','IN_GAME','AFK')),
    CONSTRAINT ck_users_xp_non_negative CHECK (xp >= 0),
    CONSTRAINT ck_users_level_min       CHECK (level >= 1)
);

-- Leaderboard reads filter by league, sorted by elo.
CREATE INDEX idx_users_league_elo ON users (league, elo_rating DESC);
-- Partial index: only rows matching the WHERE clause are indexed (tiny, fast online-status lookups).
CREATE INDEX idx_users_online ON users (online_status) WHERE online_status = 'ONLINE';

CREATE TABLE roles (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(32) NOT NULL,

    CONSTRAINT uq_roles_name UNIQUE (name)
);

INSERT INTO roles (name) VALUES ('PLAYER'), ('ADMIN');

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles (id) ON DELETE CASCADE,

    PRIMARY KEY (user_id, role_id)
);

-- Refresh tokens are stored HASHED (sha-256). A DB leak yields useless tokens,
-- and `revoked` lets us invalidate a device/session server-side.
CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL,
    device     VARCHAR(255),
    ip_address VARCHAR(45),
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id, revoked);

CREATE TABLE password_reset_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    used       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_password_reset_tokens_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens (user_id);

-- ============================================================================
-- Game content: word bank
-- ============================================================================

CREATE TABLE word_categories (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(64) NOT NULL,
    emoji      VARCHAR(16) NOT NULL DEFAULT 'box',
    is_active  BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_word_categories_name UNIQUE (name)
);

CREATE TABLE words (
    id          BIGSERIAL PRIMARY KEY,
    text        VARCHAR(64) NOT NULL,
    difficulty  VARCHAR(8)  NOT NULL DEFAULT 'MEDIUM',
    category_id BIGINT      NOT NULL REFERENCES word_categories (id),
    is_custom   BOOLEAN     NOT NULL DEFAULT FALSE,     -- player-submitted words
    created_by  BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    times_used  INTEGER     NOT NULL DEFAULT 0,         -- for freshness-based word picking
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,      -- soft delete (admin moderation)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_words_text            UNIQUE (text),
    CONSTRAINT ck_words_difficulty      CHECK (difficulty IN ('EASY','MEDIUM','HARD'))
);

-- Word selection: pick from an active category, favouring least-used words.
CREATE INDEX idx_words_category_active ON words (category_id, is_active);

-- ============================================================================
-- Rooms / lobby
-- ============================================================================

CREATE TABLE rooms (
    id                  BIGSERIAL PRIMARY KEY,
    host_id             BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name                VARCHAR(64)  NOT NULL,
    mode                VARCHAR(16)  NOT NULL DEFAULT 'CLASSIC',
    visibility          VARCHAR(16)  NOT NULL DEFAULT 'PUBLIC',
    status              VARCHAR(16)  NOT NULL DEFAULT 'WAITING',
    invite_code         VARCHAR(12),                     -- short code to share/join a private room
    is_password_protected BOOLEAN     NOT NULL DEFAULT FALSE,
    password_hash       VARCHAR(100),                    -- BCrypt of the room password
    max_players         INTEGER      NOT NULL DEFAULT 8,
    current_round       INTEGER      NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_rooms_mode          CHECK (mode IN ('CLASSIC','RANKED','TEAMS','RAPID','CUSTOM')),
    CONSTRAINT ck_rooms_visibility    CHECK (visibility IN ('PUBLIC','PRIVATE')),
    CONSTRAINT ck_rooms_status        CHECK (status IN ('WAITING','PLAYING','FINISHED')),
    CONSTRAINT ck_rooms_max_players   CHECK (max_players BETWEEN 2 AND 16),
    CONSTRAINT ck_rooms_password_pair CHECK ((is_password_protected = FALSE) OR password_hash IS NOT NULL)
);

-- Lobby list = "open public rooms"; invites = lookup by code.
CREATE INDEX idx_rooms_status_visibility ON rooms (status, visibility);
CREATE INDEX idx_rooms_invite_code ON rooms (invite_code) WHERE invite_code IS NOT NULL;

-- 1:1 with rooms. Keeping game-tuning in its own table avoids bloating rooms with
-- optional config and matches "Room Settings" as a distinct concept.
CREATE TABLE room_settings (
    room_id          BIGINT PRIMARY KEY REFERENCES rooms (id) ON DELETE CASCADE,
    drawing_time_sec INTEGER NOT NULL DEFAULT 80,
    rounds           INTEGER NOT NULL DEFAULT 3,
    hints_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    allow_spectators BOOLEAN NOT NULL DEFAULT TRUE,
    custom_words     BOOLEAN NOT NULL DEFAULT FALSE,
    word_count       INTEGER NOT NULL DEFAULT 3,         -- how many words the drawer picks from
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_room_settings_drawing_time CHECK (drawing_time_sec BETWEEN 15 AND 300),
    CONSTRAINT ck_room_settings_rounds       CHECK (rounds BETWEEN 1 AND 10),
    CONSTRAINT ck_room_settings_word_count   CHECK (word_count BETWEEN 1 AND 6)
);

-- Players = the membership table between users and rooms (a room has many players,
-- a player sits in many rooms over time). Holds per-room state: ready flag, score.
CREATE TABLE room_members (
    id           BIGSERIAL PRIMARY KEY,
    room_id      BIGINT      NOT NULL REFERENCES rooms (id) ON DELETE CASCADE,
    user_id      BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role         VARCHAR(16) NOT NULL DEFAULT 'PLAYER',  -- HOST / PLAYER / SPECTATOR
    is_ready     BOOLEAN     NOT NULL DEFAULT FALSE,
    score        INTEGER     NOT NULL DEFAULT 0,
    is_connected BOOLEAN     NOT NULL DEFAULT TRUE,
    joined_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_room_members_room_user UNIQUE (room_id, user_id),
    CONSTRAINT ck_room_members_role      CHECK (role IN ('HOST','PLAYER','SPECTATOR'))
);

CREATE INDEX idx_room_members_user ON room_members (user_id);

-- ============================================================================
-- Game play
-- ============================================================================

CREATE TABLE rounds (
    id         BIGSERIAL PRIMARY KEY,
    room_id    BIGINT      NOT NULL REFERENCES rooms (id) ON DELETE CASCADE,
    word_id    BIGINT      REFERENCES words (id) ON DELETE SET NULL,
    drawer_id  BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    round_num  INTEGER     NOT NULL,
    status     VARCHAR(16) NOT NULL DEFAULT 'DRAWING',
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at   TIMESTAMPTZ,

    CONSTRAINT uq_rounds_room_round UNIQUE (room_id, round_num),
    CONSTRAINT ck_rounds_status     CHECK (status IN ('DRAWING','GUESSING','ENDED'))
);

-- Drawings persist the FINAL canvas snapshot per round (JSON of vector strokes)
-- so replay + "drawing of the day" features can show art without re-watching the
-- live WebSocket stream. Live strokes never touch the DB (Module 4).
CREATE TABLE drawings (
    id            BIGSERIAL PRIMARY KEY,
    round_id      BIGINT      NOT NULL REFERENCES rounds (id) ON DELETE CASCADE,
    drawer_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    snapshot_type VARCHAR(8)  NOT NULL DEFAULT 'JSON',   -- JSON vector strokes / PNG render
    data          JSONB       NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_drawings_type CHECK (snapshot_type IN ('JSON','PNG'))
);

CREATE INDEX idx_drawings_round ON drawings (round_id);

CREATE TABLE guesses (
    id           BIGSERIAL PRIMARY KEY,
    round_id     BIGINT       NOT NULL REFERENCES rounds (id) ON DELETE CASCADE,
    user_id      BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    guessed_word VARCHAR(64)  NOT NULL,
    is_correct   BOOLEAN      NOT NULL DEFAULT FALSE,
    distance     SMALLINT,                               -- Levenshtein distance vs the answer (teaching)
    points       INTEGER      NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_guesses_round ON guesses (round_id);
CREATE INDEX idx_guesses_user  ON guesses (user_id);

-- Score ledger: one row per scoring event. `reason` explains WHERE the points came from
-- (auditable), and the running total lives on room_members.score for fast display.
CREATE TABLE scores (
    id         BIGSERIAL PRIMARY KEY,
    room_id    BIGINT      NOT NULL REFERENCES rooms (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    round_id   BIGINT      REFERENCES rounds (id) ON DELETE CASCADE,
    points     INTEGER     NOT NULL DEFAULT 0,
    reason     VARCHAR(32) NOT NULL DEFAULT 'GUESS',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_scores_reason CHECK (reason IN ('GUESS','WORD_BONUS','SPECTATOR_BONUS','RANKED_ADJUSTMENT','DAILY_BONUS'))
);

CREATE INDEX idx_scores_room ON scores (room_id, user_id);

-- ============================================================================
-- Progression: achievements, missions, seasons
-- ============================================================================

CREATE TABLE achievements (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(64)  NOT NULL,                   -- stable key, e.g. "FIRST_GAME"
    name        VARCHAR(64)  NOT NULL,
    description VARCHAR(255) NOT NULL,
    xp_reward   INTEGER      NOT NULL DEFAULT 0,
    icon_url    VARCHAR(500),
    is_secret   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_achievements_code UNIQUE (code)
);

CREATE TABLE user_achievements (
    user_id        BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    achievement_id BIGINT      NOT NULL REFERENCES achievements (id) ON DELETE CASCADE,
    progress       INTEGER     NOT NULL DEFAULT 0,       -- e.g. 7 / 10 drawings
    unlocked_at    TIMESTAMPTZ,

    PRIMARY KEY (user_id, achievement_id)
);

CREATE TABLE seasons (
    id        BIGSERIAL PRIMARY KEY,
    name      VARCHAR(64) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at   TIMESTAMPTZ NOT NULL,
    is_active BOOLEAN     NOT NULL DEFAULT TRUE,

    CONSTRAINT ck_seasons_dates CHECK (ends_at > starts_at)
);

CREATE TABLE missions (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(64)  NOT NULL,
    name         VARCHAR(64)  NOT NULL,
    description  VARCHAR(255) NOT NULL,
    period       VARCHAR(16)  NOT NULL,                  -- DAILY / WEEKLY / SEASON
    target_value INTEGER      NOT NULL,
    xp_reward    INTEGER      NOT NULL,
    season_id    BIGINT       REFERENCES seasons (id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_missions_code      UNIQUE (code),
    CONSTRAINT ck_missions_period    CHECK (period IN ('DAILY','WEEKLY','SEASON'))
);

CREATE TABLE user_missions (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    mission_id  BIGINT      NOT NULL REFERENCES missions (id) ON DELETE CASCADE,
    progress    INTEGER     NOT NULL DEFAULT 0,
    completed   BOOLEAN     NOT NULL DEFAULT FALSE,
    claimed     BOOLEAN     NOT NULL DEFAULT FALSE,      -- xp already paid out?
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_user_missions UNIQUE (user_id, mission_id)
);

-- Seasonal ranked snapshot. Primary key (season_id, user_id) makes upserting the
-- ranking per season a single statement.
CREATE TABLE leaderboards (
    season_id    BIGINT      NOT NULL REFERENCES seasons (id) ON DELETE CASCADE,
    user_id      BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    elo_rating   INTEGER     NOT NULL,
    position     INTEGER     NOT NULL,
    games_played INTEGER     NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (season_id, user_id),
    CONSTRAINT ck_leaderboards_position CHECK (position >= 1)
);

CREATE INDEX idx_leaderboards_season_position ON leaderboards (season_id, position);

-- ============================================================================
-- Match history
-- ============================================================================

CREATE TABLE game_history (
    id           BIGSERIAL PRIMARY KEY,
    room_id      BIGINT      REFERENCES rooms (id) ON DELETE SET NULL,
    winner_id    BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    mode         VARCHAR(16) NOT NULL,
    duration_sec INTEGER     NOT NULL DEFAULT 0,
    played_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One row per player per finished game: final placement + elo delta.
CREATE TABLE game_history_players (
    game_id   BIGINT  NOT NULL REFERENCES game_history (id) ON DELETE CASCADE,
    user_id   BIGINT  NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    position  INTEGER NOT NULL,
    points    INTEGER NOT NULL DEFAULT 0,
    elo_delta INTEGER NOT NULL DEFAULT 0,

    PRIMARY KEY (game_id, user_id)
);

CREATE INDEX idx_game_history_players_user ON game_history_players (user_id);

-- ============================================================================
-- Social & moderation
-- ============================================================================

-- Friend requests carry their lifecycle (PENDING -> ACCEPTED/DECLINED/CANCELLED).
CREATE TABLE friend_requests (
    id           BIGSERIAL PRIMARY KEY,
    sender_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    receiver_id  BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status       VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ,

    CONSTRAINT uq_friend_requests_pair   UNIQUE (sender_id, receiver_id),
    CONSTRAINT ck_friend_requests_status CHECK (status IN ('PENDING','ACCEPTED','DECLINED','CANCELLED')),
    CONSTRAINT ck_friend_requests_self   CHECK (sender_id <> receiver_id)
);

CREATE INDEX idx_friend_requests_receiver ON friend_requests (receiver_id, status);

-- Confirmed friendships. Each edge stored ONCE with (user_a_id < user_b_id):
-- no mirrored rows, no duplicate pairs, and "are X and Y friends?" is one lookup.
CREATE TABLE friends (
    user_a_id  BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    user_b_id  BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (user_a_id, user_b_id),
    CONSTRAINT ck_friends_ordered CHECK (user_a_id < user_b_id)
);

CREATE INDEX idx_friends_user_b ON friends (user_b_id);

CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       VARCHAR(32)  NOT NULL,                    -- FRIEND_REQUEST, GAME_INVITE, REPORT_UPDATE...
    title      VARCHAR(100) NOT NULL,
    body       VARCHAR(500),
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user ON notifications (user_id, is_read);

CREATE TABLE reports (
    id          BIGSERIAL PRIMARY KEY,
    reporter_id BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    reported_id BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    room_id     BIGINT       REFERENCES rooms (id) ON DELETE SET NULL,
    reason      VARCHAR(64)  NOT NULL,                   -- CHEATING, ABUSE, SPAM, INAPPROPRIATE_DRAWING...
    details     VARCHAR(1000),
    status      VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    admin_id    BIGINT       REFERENCES users (id) ON DELETE SET NULL,  -- who handled it
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,

    CONSTRAINT ck_reports_status CHECK (status IN ('OPEN','REVIEWING','RESOLVED','DISMISSED'))
);

CREATE INDEX idx_reports_reported ON reports (reported_id, status);
