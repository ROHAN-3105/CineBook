-- Idempotent sample catalog for CineBook. Existing records and schemas are preserved.
START TRANSACTION;
INSERT INTO movie (title,description,duration,rating)
SELECT seed.title,seed.description,seed.duration,seed.rating FROM (
 SELECT 'Inception' title,'A skilled thief enters layered dreams to plant a transformative idea.' description,148 duration,8.8 rating UNION ALL
 SELECT 'Interstellar','Explorers cross a wormhole in search of a new home for humanity.',169,8.7 UNION ALL
 SELECT 'The Grand Budapest Hotel','A concierge and his loyal apprentice navigate a caper at a storied hotel.',99,8.1 UNION ALL
 SELECT 'Dune: Part Two','Paul Atreides joins the Fremen and faces a choice that will shape Arrakis.',166,8.5 UNION ALL
 SELECT 'The Dark Knight','Batman faces a chaotic criminal who pushes Gotham to its limits.',152,9.0 UNION ALL
 SELECT 'La La Land','A musician and an actor chase their dreams and find each other in Los Angeles.',128,8.0 UNION ALL
 SELECT 'Oppenheimer','A physicist leads a wartime project with lasting moral consequences.',180,8.6 UNION ALL
 SELECT 'Spider-Man: Across the Spider-Verse','Miles Morales travels across worlds to challenge the Spider-Society.',140,8.6 UNION ALL
 SELECT 'Everything Everywhere All at Once','A laundromat owner discovers unexpected paths through the multiverse.',139,7.8 UNION ALL
 SELECT 'The Martian','An astronaut stranded on Mars works to survive and return home.',144,8.0
) seed WHERE NOT EXISTS (SELECT 1 FROM movie m WHERE m.title=seed.title);
SET @movies_inserted = ROW_COUNT();

-- Fixed future dates make reruns idempotent even on a different day.
INSERT INTO showtime (movie_id,show_time,available_seats)
SELECT m.id,slots.show_time,slots.available_seats FROM movie m JOIN (
 SELECT '2027-01-15 18:30:00' show_time,80 available_seats UNION ALL
 SELECT '2027-01-16 15:00:00',100 UNION ALL
 SELECT '2027-01-17 20:15:00',70
) slots WHERE m.title IN (
 'Inception','Interstellar','The Grand Budapest Hotel','Dune: Part Two','The Dark Knight',
 'La La Land','Oppenheimer','Spider-Man: Across the Spider-Verse',
 'Everything Everywhere All at Once','The Martian'
) AND NOT EXISTS (
 SELECT 1 FROM showtime s WHERE s.movie_id=m.id AND s.show_time=slots.show_time
);
SET @showtimes_inserted = ROW_COUNT();
COMMIT;
SELECT @movies_inserted AS movies_inserted, 10 - @movies_inserted AS movies_already_present,
       @showtimes_inserted AS showtimes_inserted, 30 - @showtimes_inserted AS showtimes_already_present;
