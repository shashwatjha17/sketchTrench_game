-- Seed the default word bank. Categories get stable IDs via a WHERE-based lookup so
-- this migration is idempotent-by-order and readable, not dependent on generated ids.

INSERT INTO word_categories (name, emoji, is_active, created_at) VALUES
    ('Animals', 'paw', TRUE, now()),
    ('Food', 'pizza', TRUE, now()),
    ('Objects', 'basket', TRUE, now()),
    ('Places', 'world', TRUE, now()),
    ('People', 'man', TRUE, now()),
    ('Nature', 'herb', TRUE, now());

WITH c AS (SELECT id, name FROM word_categories)
INSERT INTO words (text, difficulty, category_id, is_custom, is_active, created_at) VALUES
    -- Animals
    ('dog', 'EASY', (SELECT id FROM c WHERE name = 'Animals'), FALSE, TRUE, now()),
    ('cat', 'EASY', (SELECT id FROM c WHERE name = 'Animals'), FALSE, TRUE, now()),
    ('snake', 'EASY', (SELECT id FROM c WHERE name = 'Animals'), FALSE, TRUE, now()),
    ('rabbit', 'EASY', (SELECT id FROM c WHERE name = 'Animals'), FALSE, TRUE, now()),
    ('penguin', 'MEDIUM', (SELECT id FROM c WHERE name = 'Animals'), FALSE, TRUE, now()),
    ('butterfly', 'MEDIUM', (SELECT id FROM c WHERE name = 'Animals'), FALSE, TRUE, now()),
    ('dolphin', 'MEDIUM', (SELECT id FROM c WHERE name = 'Animals'), FALSE, TRUE, now()),
    ('octopus', 'HARD', (SELECT id FROM c WHERE name = 'Animals'), FALSE, TRUE, now()),
    ('elephant', 'HARD', (SELECT id FROM c WHERE name = 'Animals'), FALSE, TRUE, now()),
    ('giraffe', 'HARD', (SELECT id FROM c WHERE name = 'Animals'), FALSE, TRUE, now()),
    -- Food
    ('apple', 'EASY', (SELECT id FROM c WHERE name = 'Food'), FALSE, TRUE, now()),
    ('banana', 'EASY', (SELECT id FROM c WHERE name = 'Food'), FALSE, TRUE, now()),
    ('pizza', 'MEDIUM', (SELECT id FROM c WHERE name = 'Food'), FALSE, TRUE, now()),
    ('donut', 'MEDIUM', (SELECT id FROM c WHERE name = 'Food'), FALSE, TRUE, now()),
    ('hamburger', 'MEDIUM', (SELECT id FROM c WHERE name = 'Food'), FALSE, TRUE, now()),
    ('ice cream', 'MEDIUM', (SELECT id FROM c WHERE name = 'Food'), FALSE, TRUE, now()),
    ('watermelon', 'MEDIUM', (SELECT id FROM c WHERE name = 'Food'), FALSE, TRUE, now()),
    ('spaghetti', 'HARD', (SELECT id FROM c WHERE name = 'Food'), FALSE, TRUE, now()),
    -- Objects
    ('book', 'EASY', (SELECT id FROM c WHERE name = 'Objects'), FALSE, TRUE, now()),
    ('lamp', 'EASY', (SELECT id FROM c WHERE name = 'Objects'), FALSE, TRUE, now()),
    ('telephone', 'EASY', (SELECT id FROM c WHERE name = 'Objects'), FALSE, TRUE, now()),
    ('clock', 'EASY', (SELECT id FROM c WHERE name = 'Objects'), FALSE, TRUE, now()),
    ('umbrella', 'MEDIUM', (SELECT id FROM c WHERE name = 'Objects'), FALSE, TRUE, now()),
    ('bicycle', 'MEDIUM', (SELECT id FROM c WHERE name = 'Objects'), FALSE, TRUE, now()),
    ('camera', 'MEDIUM', (SELECT id FROM c WHERE name = 'Objects'), FALSE, TRUE, now()),
    ('guitar', 'HARD', (SELECT id FROM c WHERE name = 'Objects'), FALSE, TRUE, now()),
    ('rocket', 'HARD', (SELECT id FROM c WHERE name = 'Objects'), FALSE, TRUE, now()),
    -- Places
    ('beach', 'EASY', (SELECT id FROM c WHERE name = 'Places'), FALSE, TRUE, now()),
    ('mountain', 'EASY', (SELECT id FROM c WHERE name = 'Places'), FALSE, TRUE, now()),
    ('jungle', 'EASY', (SELECT id FROM c WHERE name = 'Places'), FALSE, TRUE, now()),
    ('bridge', 'MEDIUM', (SELECT id FROM c WHERE name = 'Places'), FALSE, TRUE, now()),
    ('airport', 'MEDIUM', (SELECT id FROM c WHERE name = 'Places'), FALSE, TRUE, now()),
    ('castle', 'HARD', (SELECT id FROM c WHERE name = 'Places'), FALSE, TRUE, now()),
    ('church', 'HARD', (SELECT id FROM c WHERE name = 'Places'), FALSE, TRUE, now()),
    ('space station', 'HARD', (SELECT id FROM c WHERE name = 'Places'), FALSE, TRUE, now()),
    -- People
    ('doctor', 'EASY', (SELECT id FROM c WHERE name = 'People'), FALSE, TRUE, now()),
    ('clown', 'EASY', (SELECT id FROM c WHERE name = 'People'), FALSE, TRUE, now()),
    ('robot', 'MEDIUM', (SELECT id FROM c WHERE name = 'People'), FALSE, TRUE, now()),
    ('cowboy', 'MEDIUM', (SELECT id FROM c WHERE name = 'People'), FALSE, TRUE, now()),
    ('ninja', 'MEDIUM', (SELECT id FROM c WHERE name = 'People'), FALSE, TRUE, now()),
    ('astronaut', 'HARD', (SELECT id FROM c WHERE name = 'People'), FALSE, TRUE, now()),
    ('pirate', 'HARD', (SELECT id FROM c WHERE name = 'People'), FALSE, TRUE, now()),
    ('superhero', 'HARD', (SELECT id FROM c WHERE name = 'People'), FALSE, TRUE, now()),
    -- Nature
    ('sun', 'EASY', (SELECT id FROM c WHERE name = 'Nature'), FALSE, TRUE, now()),
    ('tree', 'EASY', (SELECT id FROM c WHERE name = 'Nature'), FALSE, TRUE, now()),
    ('flower', 'EASY', (SELECT id FROM c WHERE name = 'Nature'), FALSE, TRUE, now()),
    ('cloud', 'EASY', (SELECT id FROM c WHERE name = 'Nature'), FALSE, TRUE, now()),
    ('ocean', 'EASY', (SELECT id FROM c WHERE name = 'Nature'), FALSE, TRUE, now()),
    ('rainbow', 'MEDIUM', (SELECT id FROM c WHERE name = 'Nature'), FALSE, TRUE, now()),
    ('volcano', 'MEDIUM', (SELECT id FROM c WHERE name = 'Nature'), FALSE, TRUE, now()),
    ('snowman', 'MEDIUM', (SELECT id FROM c WHERE name = 'Nature'), FALSE, TRUE, now()),
    ('waterfall', 'HARD', (SELECT id FROM c WHERE name = 'Nature'), FALSE, TRUE, now());
