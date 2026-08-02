-- Seed the achievement catalog. `code` is the stable key the AchievementService matches
-- against events; `xp_reward` is paid out when a player reaches the target.

INSERT INTO achievements (code, name, description, xp_reward, icon_url, is_secret, created_at) VALUES
    ('FIRST_GAME',    'First Steps',    'Play your first game',            50,  NULL, FALSE, now()),
    ('FIRST_GUESS',   'Sharp Eye',      'Make your first correct guess',   50,  NULL, FALSE, now()),
    ('QUICK_GUESS',   'Lightning Mind', 'Guess correctly within 10 seconds', 75, NULL, FALSE, now()),
    ('WINNER',        'Champion',       'Win a game',                      100, NULL, FALSE, now()),
    ('CORRECT_10',    'On a Roll',      'Make 10 correct guesses',         150, NULL, FALSE, now()),
    ('ARTIST_10',     'The Artist',     'Draw in 10 rounds',               150, NULL, FALSE, now()),
    ('FRIEND_1',      'Social',         'Add your first friend',           50,  NULL, FALSE, now()),
    ('LEVEL_5',       'Rising Star',    'Reach level 5',                   200, NULL, FALSE, now());
