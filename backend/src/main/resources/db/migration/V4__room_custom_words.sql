-- Custom words per room. Kept in their own table (NOT the global `words` pool) because
-- `words.text` is globally UNIQUE — two rooms must be able to use the same custom word.
CREATE TABLE room_custom_words (
    id         BIGSERIAL PRIMARY KEY,
    room_id    BIGINT      NOT NULL REFERENCES rooms (id) ON DELETE CASCADE,
    word       VARCHAR(64) NOT NULL,
    added_by   BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_room_custom_word UNIQUE (room_id, word)
);

-- When a room enables custom words, the game picks options from this table instead of
-- the global pool. At least 3 must exist before the host can start the game.
