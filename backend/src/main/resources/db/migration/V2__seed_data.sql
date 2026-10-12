-- Seed Venue
INSERT INTO venues (id, name, city) VALUES 
(1, 'Starlight Amphitheater', 'San Francisco'),
(2, 'The Grand Arena', 'New York');

-- Seed Events
INSERT INTO events (id, name, description, created_at) VALUES 
(1, 'Coldplay: Music of the Spheres', 'Immersive stadium world tour featuring kinetic floors and synchronized lightbands.', CURRENT_TIMESTAMP),
(2, 'Hans Zimmer Live in Concert', 'Legendary symphonic film scores including Inception, Interstellar, and Dune.', CURRENT_TIMESTAMP);

-- Seed Showtimes
INSERT INTO showtimes (id, event_id, venue_id, starts_at) VALUES 
(1, 1, 1, CURRENT_TIMESTAMP + INTERVAL '3 days'),
(2, 2, 1, CURRENT_TIMESTAMP + INTERVAL '5 days');

-- Seed Seats for Starlight Amphitheater (Venue 1): Rows A to F, Seats 1 to 10
-- Rows A and B: VIP ($150.00)
-- Rows C, D, E, F: STANDARD ($75.00)
DO $$
DECLARE
    row_chars text[] := ARRAY['A', 'B', 'C', 'D', 'E', 'F'];
    r text;
    s int;
    cat text;
    seat_price numeric;
BEGIN
    FOREACH r IN ARRAY row_chars LOOP
        IF r IN ('A', 'B') THEN
            cat := 'VIP';
            seat_price := 150.00;
        ELSE
            cat := 'STANDARD';
            seat_price := 75.00;
        END IF;

        FOR s IN 1..10 LOOP
            INSERT INTO seats (venue_id, row_label, seat_number, category, price)
            VALUES (1, r, s, cat, seat_price);
        END LOOP;
    END LOOP;
END $$;

SELECT setval('venues_id_seq', (SELECT MAX(id) FROM venues));
SELECT setval('events_id_seq', (SELECT MAX(id) FROM events));
SELECT setval('showtimes_id_seq', (SELECT MAX(id) FROM showtimes));
