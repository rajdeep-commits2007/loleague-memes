-- Starter catalogue. Edit prices / stock here or via the admin API.
INSERT INTO goodies (id, name, description, coin_cost, min_comments_received, stock, once_per_user, sort_order) VALUES
    ('7f1c2a10-0000-4000-8000-000000000001', 'Sticker Bomb Pack', '25 waterproof meme stickers for your laptop and bottle.', 2000, 0, 500, FALSE, 1),
    ('7f1c2a10-0000-4000-8000-000000000002', 'LOLeague Tee', 'Oversized tee: "I peaked in the comment section".', 5000, 0, 200, FALSE, 2),
    ('7f1c2a10-0000-4000-8000-000000000003', 'Meme Lord Hoodie', 'Heavy hoodie with your rank printed on the sleeve.', 10000, 0, 100, FALSE, 3),
    ('7f1c2a10-0000-4000-8000-000000000004', '20K Comments Legend Box', 'Free mystery box for creators whose memes crossed 20,000 comments.', 0, 20000, NULL, TRUE, 0);

INSERT INTO ads (id, title, body, cta_text, target_url, weight) VALUES
    ('a0a0a0a0-0000-4000-8000-000000000001', 'Advertise on LOLeague', 'Reach every campus in West Bengal between two memes.', 'Book a slot', 'mailto:ads@loleague.app', 3),
    ('a0a0a0a0-0000-4000-8000-000000000002', 'Canteen Combo Deals', 'Show your LOLeague profile at partner canteens for 10% off.', 'See partners', 'https://example.com/partners', 2),
    ('a0a0a0a0-0000-4000-8000-000000000003', 'Fest Season Is Here', 'List your college fest for free and sell passes in-app.', 'List my fest', 'https://example.com/fests', 1);
