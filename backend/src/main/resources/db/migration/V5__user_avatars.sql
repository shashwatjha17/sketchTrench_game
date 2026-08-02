-- Customizable avatars: base color, facial expression, sunglasses, wig.
-- ddl-auto=validate mirrors these in User entity — keep both in sync.
ALTER TABLE users
    ADD COLUMN avatar_color      VARCHAR(7)  NOT NULL DEFAULT '#6d5dfc',
    ADD COLUMN avatar_expression VARCHAR(16) NOT NULL DEFAULT 'happy',
    ADD COLUMN avatar_sunglasses BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN avatar_wig        VARCHAR(16) NOT NULL DEFAULT 'none';
