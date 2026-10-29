-- RevKassa arenduse näidisandmed (ainult arenduses, tootmisse ei lähe).
-- Käivita pärast 1_reset_database.sql + 2_create.sql. Andmed klapivad "RevKassa Mockups" lõuendiga.
--
-- Sisselogimine arenduses:
--   PIN-räsi on HMAC-SHA256(võti, PIN) hex-ina; võti arenduses: REVKASSA_PIN_SECRET=dev-pin-secret
--   Admini parool (Mari ja Jaan): parool123 (bcrypt)
--
--   id | nimi        | PIN    | roll   | tase         | saldo lõpuks | märkus
--    1 | Mari Tamm   | 482915 | ADMIN  | Täisliige    |   -12,50 €   | mari@example.org
--    2 | Kaspar Kask | 111111 | MEMBER | Täisliige    |   -27,40 €   | ootel kaardimakse 10 €
--    3 | Liis Lepp   | 222222 | MEMBER | Noorliige    |   -29,80 €   | e-posti kirjaviga (arve FAILED)
--    4 | Rasmus Pärn | 333333 | MEMBER | Noorliige    |    -8,20 €   | e-post puudub
--    5 | Jaan Saar   | 555555 | ADMIN  | Vilistlane   |    +4,50 €   | tasust vabastatud, jaan@example.org
--    6 | Kertu Mets  | 666666 | MEMBER | Toetajaliige |     0,00 €   | mitteaktiivne
--
-- Saldod ja laoseisud arvutatakse faili lõpus pearaamatutest (invariandid).

-- member
INSERT INTO member (id, email, display_name, pin_hmac, password_hash, role, active, fee_exempt, level_id, created_at) VALUES (default, 'mari@example.org', 'Mari Tamm', 'ad95f7d83aa4ca2b9739ba000c83cc398f655aa068bed3b22e5632eec411f0ce', '$2a$10$dlOgBYZ9MvgeOjamzay64OZYURBQUPNhRlv8TYAkeaP6dUTbQs6Cq', 'ADMIN', true, false, 1, '2026-08-01 10:00+03');
INSERT INTO member (id, email, display_name, pin_hmac, password_hash, role, active, fee_exempt, level_id, created_at) VALUES (default, 'kaspar@example.org', 'Kaspar Kask', '022a92e11a89a2f073dbbe4578920272870cb113a551e1457781545bc05030b7', null, 'MEMBER', true, false, 1, '2026-08-01 10:00+03');
INSERT INTO member (id, email, display_name, pin_hmac, password_hash, role, active, fee_exempt, level_id, created_at) VALUES (default, 'liis@exmaple.org', 'Liis Lepp', '9b3c5f9d125357687af14cd00913895318a790f3fda196afb95853ed50277632', null, 'MEMBER', true, false, 2, '2026-08-01 10:00+03');
INSERT INTO member (id, email, display_name, pin_hmac, password_hash, role, active, fee_exempt, level_id, created_at) VALUES (default, null, 'Rasmus Pärn', '1e1abb5386b5056d92bba774333a13bad36b18eff9735596da5facff18551109', null, 'MEMBER', true, false, 2, '2026-08-01 10:00+03');
INSERT INTO member (id, email, display_name, pin_hmac, password_hash, role, active, fee_exempt, level_id, created_at) VALUES (default, 'jaan@example.org', 'Jaan Saar', '4a6a484ff1bf6cedf85fe3f515b76c24cd35cceb670babc6f2f0b3018c0c7ff2', '$2a$10$dlOgBYZ9MvgeOjamzay64OZYURBQUPNhRlv8TYAkeaP6dUTbQs6Cq', 'ADMIN', true, true, 3, '2026-07-01 10:00+03');
INSERT INTO member (id, email, display_name, pin_hmac, password_hash, role, active, fee_exempt, level_id, created_at) VALUES (default, 'kertu@example.org', 'Kertu Mets', '02912bc7502f1ef511c112220dd994f0da321635127b3a85e61ea0807f78c97e', null, 'MEMBER', false, false, 4, '2026-08-01 10:00+03');


-- product (laoseis lõpuks: 22, 15, 18, 12, 5, 3, 9, 10, 0)
INSERT INTO product (id, name, style, volume_ml, price_cents, low_stock_threshold, sort_order, active, created_at, updated_at) VALUES (default, 'Saku Originaal', 'Lager', 500, 250, 6, 1, true, '2026-08-01 10:00+03', '2026-08-01 10:00+03');
INSERT INTO product (id, name, style, volume_ml, price_cents, low_stock_threshold, sort_order, active, created_at, updated_at) VALUES (default, 'A. Le Coq Premium', 'Lager', 500, 250, 6, 2, true, '2026-08-01 10:00+03', '2026-08-01 10:00+03');
INSERT INTO product (id, name, style, volume_ml, price_cents, low_stock_threshold, sort_order, active, created_at, updated_at) VALUES (default, 'Saku Kuld', 'Lager', 500, 280, 6, 3, true, '2026-08-01 10:00+03', '2026-08-01 10:00+03');
INSERT INTO product (id, name, style, volume_ml, price_cents, low_stock_threshold, sort_order, active, created_at, updated_at) VALUES (default, 'Õllenaut IPA', 'IPA', 330, 450, 6, 4, true, '2026-08-01 10:00+03', '2026-08-01 10:00+03');
INSERT INTO product (id, name, style, volume_ml, price_cents, low_stock_threshold, sort_order, active, created_at, updated_at) VALUES (default, 'Pühaste Must', 'Stout', 330, 450, 6, 5, true, '2026-08-01 10:00+03', '2026-08-01 10:00+03');
INSERT INTO product (id, name, style, volume_ml, price_cents, low_stock_threshold, sort_order, active, created_at, updated_at) VALUES (default, 'Põhjala Öö', 'Imperial porter', 330, 550, 6, 6, true, '2026-08-01 10:00+03', '2026-08-01 10:00+03');
INSERT INTO product (id, name, style, volume_ml, price_cents, low_stock_threshold, sort_order, active, created_at, updated_at) VALUES (default, 'Sauna Session', 'Pale ale', 330, 400, 6, 7, true, '2026-08-01 10:00+03', '2026-08-01 10:00+03');
INSERT INTO product (id, name, style, volume_ml, price_cents, low_stock_threshold, sort_order, active, created_at, updated_at) VALUES (default, 'Saku 0,0', 'Alkoholivaba', 500, 200, 6, 8, true, '2026-08-01 10:00+03', '2026-08-01 10:00+03');
INSERT INTO product (id, name, style, volume_ml, price_cents, low_stock_threshold, sort_order, active, created_at, updated_at) VALUES (default, 'Alexander', 'Lager', 500, 300, 6, 9, false, '2026-08-01 10:00+03', '2026-09-30 18:00+03');


-- member_favourite (Mari: Õllenaut IPA, Saku Originaal — nagu lõuendil)
INSERT INTO member_favourite (member_id, product_id, position, created_at) VALUES (1, 4, 0, '2026-08-02 19:00+03');
INSERT INTO member_favourite (member_id, product_id, position, created_at) VALUES (1, 1, 1, '2026-08-02 19:00+03');
INSERT INTO member_favourite (member_id, product_id, position, created_at) VALUES (2, 1, 0, '2026-08-05 20:00+03');
INSERT INTO member_favourite (member_id, product_id, position, created_at) VALUES (2, 7, 1, '2026-08-05 20:00+03');
INSERT INTO member_favourite (member_id, product_id, position, created_at) VALUES (2, 5, 2, '2026-08-05 20:00+03');
INSERT INTO member_favourite (member_id, product_id, position, created_at) VALUES (5, 6, 0, '2026-08-01 12:00+03');


-- purchase (id 5 on tagasi võetud)
-- Mari (1)
INSERT INTO purchase (id, member_id, total_cents, status, created_at, reversed_at, reversed_by) VALUES (default, 1, 900, 'COMPLETED', '2026-08-29 19:40+03', null, null);
INSERT INTO purchase (id, member_id, total_cents, status, created_at, reversed_at, reversed_by) VALUES (default, 1, 840, 'COMPLETED', '2026-09-26 20:15+03', null, null);
INSERT INTO purchase (id, member_id, total_cents, status, created_at, reversed_at, reversed_by) VALUES (default, 1, 650, 'COMPLETED', '2026-10-01 19:30+03', null, null);
INSERT INTO purchase (id, member_id, total_cents, status, created_at, reversed_at, reversed_by) VALUES (default, 1, 840, 'COMPLETED', '2026-10-04 20:15+03', null, null);
INSERT INTO purchase (id, member_id, total_cents, status, created_at, reversed_at, reversed_by) VALUES (default, 1, 450, 'REVERSED', '2026-10-12 21:05+03', '2026-10-12 21:07+03', 1);
INSERT INTO purchase (id, member_id, total_cents, status, created_at, reversed_at, reversed_by) VALUES (default, 1, 1050, 'COMPLETED', '2026-10-12 21:10+03', null, null);
INSERT INTO purchase (id, member_id, total_cents, status, created_at, reversed_at, reversed_by) VALUES (default, 1, 500, 'COMPLETED', '2026-10-28 18:42+02', null, null);
-- Kaspar (2)
INSERT INTO purchase (id, member_id, total_cents, status, created_at, reversed_at, reversed_by) VALUES (default, 2, 1000, 'COMPLETED', '2026-09-12 21:30+03', null, null);
INSERT INTO purchase (id, member_id, total_cents, status, created_at, reversed_at, reversed_by) VALUES (default, 2, 1250, 'COMPLETED', '2026-10-03 22:10+03', null, null);
INSERT INTO purchase (id, member_id, total_cents, status, created_at, reversed_at, reversed_by) VALUES (default, 2, 550, 'COMPLETED', '2026-10-20 20:45+03', null, null);
-- Liis (3)
INSERT INTO purchase (id, member_id, total_cents, status, created_at, reversed_at, reversed_by) VALUES (default, 3, 800, 'COMPLETED', '2026-09-20 19:15+03', null, null);
INSERT INTO purchase (id, member_id, total_cents, status, created_at, reversed_at, reversed_by) VALUES (default, 3, 750, 'COMPLETED', '2026-10-10 20:00+03', null, null);
INSERT INTO purchase (id, member_id, total_cents, status, created_at, reversed_at, reversed_by) VALUES (default, 3, 1100, 'COMPLETED', '2026-10-24 21:20+03', null, null);
-- Rasmus (4)
INSERT INTO purchase (id, member_id, total_cents, status, created_at, reversed_at, reversed_by) VALUES (default, 4, 400, 'COMPLETED', '2026-09-05 18:30+03', null, null);
INSERT INTO purchase (id, member_id, total_cents, status, created_at, reversed_at, reversed_by) VALUES (default, 4, 530, 'COMPLETED', '2026-10-18 19:50+03', null, null);
-- Jaan (5)
INSERT INTO purchase (id, member_id, total_cents, status, created_at, reversed_at, reversed_by) VALUES (default, 5, 900, 'COMPLETED', '2026-09-10 20:00+03', null, null);
INSERT INTO purchase (id, member_id, total_cents, status, created_at, reversed_at, reversed_by) VALUES (default, 5, 1000, 'COMPLETED', '2026-10-02 21:00+03', null, null);
INSERT INTO purchase (id, member_id, total_cents, status, created_at, reversed_at, reversed_by) VALUES (default, 5, 650, 'COMPLETED', '2026-10-22 19:10+03', null, null);


-- purchase_line (hind kopeeritud müügi hetkel)
INSERT INTO purchase_line (id, purchase_id, product_id, quantity, unit_price_cents) VALUES (default, 1, 4, 2, 450);
INSERT INTO purchase_line (id, purchase_id, product_id, quantity, unit_price_cents) VALUES (default, 2, 3, 3, 280);
INSERT INTO purchase_line (id, purchase_id, product_id, quantity, unit_price_cents) VALUES (default, 3, 4, 1, 450);
INSERT INTO purchase_line (id, purchase_id, product_id, quantity, unit_price_cents) VALUES (default, 3, 8, 1, 200);
INSERT INTO purchase_line (id, purchase_id, product_id, quantity, unit_price_cents) VALUES (default, 4, 3, 3, 280);
INSERT INTO purchase_line (id, purchase_id, product_id, quantity, unit_price_cents) VALUES (default, 5, 5, 1, 450);
INSERT INTO purchase_line (id, purchase_id, product_id, quantity, unit_price_cents) VALUES (default, 6, 6, 1, 550);
INSERT INTO purchase_line (id, purchase_id, product_id, quantity, unit_price_cents) VALUES (default, 6, 2, 2, 250);
INSERT INTO purchase_line (id, purchase_id, product_id, quantity, unit_price_cents) VALUES (default, 7, 1, 2, 250);
INSERT INTO purchase_line (id, purchase_id, product_id, quantity, unit_price_cents) VALUES (default, 8, 1, 4, 250);
INSERT INTO purchase_line (id, purchase_id, product_id, quantity, unit_price_cents) VALUES (default, 9, 7, 2, 400);
INSERT INTO purchase_line (id, purchase_id, product_id, quantity, unit_price_cents) VALUES (default, 9, 5, 1, 450);
INSERT INTO purchase_line (id, purchase_id, product_id, quantity, unit_price_cents) VALUES (default, 10, 6, 1, 550);
INSERT INTO purchase_line (id, purchase_id, product_id, quantity, unit_price_cents) VALUES (default, 11, 7, 2, 400);
INSERT INTO purchase_line (id, purchase_id, product_id, quantity, unit_price_cents) VALUES (default, 12, 2, 3, 250);
INSERT INTO purchase_line (id, purchase_id, product_id, quantity, unit_price_cents) VALUES (default, 13, 4, 2, 450);
INSERT INTO purchase_line (id, purchase_id, product_id, quantity, unit_price_cents) VALUES (default, 13, 8, 1, 200);
INSERT INTO purchase_line (id, purchase_id, product_id, quantity, unit_price_cents) VALUES (default, 14, 8, 2, 200);
INSERT INTO purchase_line (id, purchase_id, product_id, quantity, unit_price_cents) VALUES (default, 15, 3, 1, 280);
INSERT INTO purchase_line (id, purchase_id, product_id, quantity, unit_price_cents) VALUES (default, 15, 1, 1, 250);
INSERT INTO purchase_line (id, purchase_id, product_id, quantity, unit_price_cents) VALUES (default, 16, 5, 2, 450);
INSERT INTO purchase_line (id, purchase_id, product_id, quantity, unit_price_cents) VALUES (default, 17, 6, 1, 550);
INSERT INTO purchase_line (id, purchase_id, product_id, quantity, unit_price_cents) VALUES (default, 17, 4, 1, 450);
INSERT INTO purchase_line (id, purchase_id, product_id, quantity, unit_price_cents) VALUES (default, 18, 4, 1, 450);
INSERT INTO purchase_line (id, purchase_id, product_id, quantity, unit_price_cents) VALUES (default, 18, 8, 1, 200);


-- payment (2 ootel adminile, 1 tagasi lükatud)
INSERT INTO payment (id, member_id, amount_cents, method, reference, status, reported_at, decided_by, decided_at, decision_note) VALUES (default, 5, 5000, 'CARD', 'Ettemaks', 'CONFIRMED', '2026-09-01 12:00+03', 5, '2026-09-01 12:00+03', null);
INSERT INTO payment (id, member_id, amount_cents, method, reference, status, reported_at, decided_by, decided_at, decision_note) VALUES (default, 3, 970, 'BANK_TRANSFER', 'Liis Lepp september', 'CONFIRMED', '2026-09-24 21:00+03', 1, '2026-09-25 09:30+03', null);
INSERT INTO payment (id, member_id, amount_cents, method, reference, status, reported_at, decided_by, decided_at, decision_note) VALUES (default, 1, 2000, 'BANK_TRANSFER', 'Mari Tamm september', 'CONFIRMED', '2026-09-28 18:00+03', 5, '2026-09-29 10:00+03', null);
INSERT INTO payment (id, member_id, amount_cents, method, reference, status, reported_at, decided_by, decided_at, decision_note) VALUES (default, 2, 2560, 'CARD', null, 'CONFIRMED', '2026-09-30 20:00+03', 5, '2026-09-30 22:00+03', null);
INSERT INTO payment (id, member_id, amount_cents, method, reference, status, reported_at, decided_by, decided_at, decision_note) VALUES (default, 1, 2000, 'BANK_TRANSFER', 'Mari Tamm oktoober', 'CONFIRMED', '2026-10-09 17:00+03', 5, '2026-10-10 11:00+03', null);
INSERT INTO payment (id, member_id, amount_cents, method, reference, status, reported_at, decided_by, decided_at, decision_note) VALUES (default, 3, 1500, 'BANK_TRANSFER', 'Arve 2026-0002', 'REJECTED', '2026-10-20 12:00+03', 1, '2026-10-22 09:00+03', 'Laekumist pangaväljavõttel ei leitud');
INSERT INTO payment (id, member_id, amount_cents, method, reference, status, reported_at, decided_by, decided_at, decision_note) VALUES (default, 2, 1000, 'CARD', null, 'PENDING', '2026-10-27 20:30+02', null, null, null);
INSERT INTO payment (id, member_id, amount_cents, method, reference, status, reported_at, decided_by, decided_at, decision_note) VALUES (default, 4, 820, 'BANK_TRANSFER', 'Rasmus Pärn', 'PENDING', '2026-10-28 09:15+02', null, null, null);


-- membership_fee (taseme tasu alates septembrist; Auliige 0 €)
INSERT INTO membership_fee (id, level_id, amount_cents, valid_from, created_by, created_at) VALUES (default, 1, 500, '2026-09-01', 5, '2026-08-15 12:00+03');
INSERT INTO membership_fee (id, level_id, amount_cents, valid_from, created_by, created_at) VALUES (default, 2, 250, '2026-09-01', 5, '2026-08-15 12:00+03');
INSERT INTO membership_fee (id, level_id, amount_cents, valid_from, created_by, created_at) VALUES (default, 3, 300, '2026-09-01', 5, '2026-08-15 12:00+03');
INSERT INTO membership_fee (id, level_id, amount_cents, valid_from, created_by, created_at) VALUES (default, 4, 1000, '2026-09-01', 5, '2026-08-15 12:00+03');
INSERT INTO membership_fee (id, level_id, amount_cents, valid_from, created_by, created_at) VALUES (default, 5, 0, '2026-09-01', 5, '2026-08-15 12:00+03');


-- membership_charge (september ja oktoober; Jaan vabastatud, Kertu mitteaktiivne; Rasmuse oktoober tühistatud)
INSERT INTO membership_charge (id, member_id, month, level_id, amount_cents, status, created_at, waived_by, waived_at, waive_note) VALUES (default, 1, '2026-09-01', 1, 500, 'CHARGED', '2026-09-01 06:00+03', null, null, null);
INSERT INTO membership_charge (id, member_id, month, level_id, amount_cents, status, created_at, waived_by, waived_at, waive_note) VALUES (default, 2, '2026-09-01', 1, 500, 'CHARGED', '2026-09-01 06:00+03', null, null, null);
INSERT INTO membership_charge (id, member_id, month, level_id, amount_cents, status, created_at, waived_by, waived_at, waive_note) VALUES (default, 3, '2026-09-01', 2, 250, 'CHARGED', '2026-09-01 06:00+03', null, null, null);
INSERT INTO membership_charge (id, member_id, month, level_id, amount_cents, status, created_at, waived_by, waived_at, waive_note) VALUES (default, 4, '2026-09-01', 2, 250, 'CHARGED', '2026-09-01 06:00+03', null, null, null);
INSERT INTO membership_charge (id, member_id, month, level_id, amount_cents, status, created_at, waived_by, waived_at, waive_note) VALUES (default, 1, '2026-10-01', 1, 500, 'CHARGED', '2026-10-01 06:00+03', null, null, null);
INSERT INTO membership_charge (id, member_id, month, level_id, amount_cents, status, created_at, waived_by, waived_at, waive_note) VALUES (default, 2, '2026-10-01', 1, 500, 'CHARGED', '2026-10-01 06:00+03', null, null, null);
INSERT INTO membership_charge (id, member_id, month, level_id, amount_cents, status, created_at, waived_by, waived_at, waive_note) VALUES (default, 3, '2026-10-01', 2, 250, 'CHARGED', '2026-10-01 06:00+03', null, null, null);
INSERT INTO membership_charge (id, member_id, month, level_id, amount_cents, status, created_at, waived_by, waived_at, waive_note) VALUES (default, 4, '2026-10-01', 2, 250, 'WAIVED', '2026-10-01 06:00+03', 5, '2026-10-06 12:00+03', 'Juhatuse otsus, oktoobris tasuta');


-- invoice (Kaspar sai arve alla 7 päeva tagasi → vaikimisi valimata; Liisi aadressil kirjaviga → FAILED)
INSERT INTO invoice (id, number, member_id, email_to, amount_cents, due_date, status, error, sent_by, created_at, sent_at) VALUES (default, '2026-0001', 2, 'kaspar@example.org', 1500, '2026-09-29', 'SENT', null, 5, '2026-09-15 12:00+03', '2026-09-15 12:00+03');
INSERT INTO invoice (id, number, member_id, email_to, amount_cents, due_date, status, error, sent_by, created_at, sent_at) VALUES (default, '2026-0002', 3, 'liis@exmaple.org', 1080, '2026-10-26', 'FAILED', '550 5.1.1 postkasti ei leitud – kontrolli aadressi', 1, '2026-10-12 12:00+03', null);
INSERT INTO invoice (id, number, member_id, email_to, amount_cents, due_date, status, error, sent_by, created_at, sent_at) VALUES (default, '2026-0003', 2, 'kaspar@example.org', 2740, '2026-11-04', 'SENT', null, 5, '2026-10-21 12:00+03', '2026-10-21 12:00+03');


-- event
INSERT INTO event (id, name, starts_at, location, description, status, signup_open, created_by, created_at, updated_at) VALUES (default, 'Jõulupidu 2026', '2026-12-12 19:00+02', 'Klubiruum', 'Aasta lõpu pidu õhtusöögi ja muusikaga.', 'PLANNED', true, 5, '2026-10-01 12:00+03', '2026-10-01 12:00+03');
INSERT INTO event (id, name, starts_at, location, description, status, signup_open, created_by, created_at, updated_at) VALUES (default, 'Õllematk Tartusse', '2026-11-14 10:00+02', 'Tartu', 'Bussiga kahte väikepruulikotta.', 'PLANNED', true, 5, '2026-10-15 12:00+03', '2026-10-15 12:00+03');
INSERT INTO event (id, name, starts_at, location, description, status, signup_open, created_by, created_at, updated_at) VALUES (default, 'Sügisene mälumäng', '2026-10-09 19:00+03', 'Klubiruum', 'Jäi osalejate puudusel ära.', 'CANCELLED', false, 1, '2026-09-20 12:00+03', '2026-10-05 12:00+03');


-- event_level_fee (rea puudumine = 0 €)
INSERT INTO event_level_fee (event_id, level_id, amount_cents) VALUES (1, 1, 1500);
INSERT INTO event_level_fee (event_id, level_id, amount_cents) VALUES (1, 2, 800);
INSERT INTO event_level_fee (event_id, level_id, amount_cents) VALUES (1, 3, 2000);
INSERT INTO event_level_fee (event_id, level_id, amount_cents) VALUES (1, 4, 1500);
INSERT INTO event_level_fee (event_id, level_id, amount_cents) VALUES (1, 5, 0);
INSERT INTO event_level_fee (event_id, level_id, amount_cents) VALUES (2, 1, 2500);
INSERT INTO event_level_fee (event_id, level_id, amount_cents) VALUES (2, 2, 2000);
INSERT INTO event_level_fee (event_id, level_id, amount_cents) VALUES (2, 3, 2500);
INSERT INTO event_level_fee (event_id, level_id, amount_cents) VALUES (2, 4, 2500);


-- event_participant (Jõulupidu: 4 aktiivset; Rasmus lisati ja eemaldati)
INSERT INTO event_participant (id, event_id, member_id, level_id, fee_cents, status, added_by, added_at, removed_at) VALUES (default, 1, 5, 3, 2000, 'ACTIVE', 5, '2026-10-02 12:00+03', null);
INSERT INTO event_participant (id, event_id, member_id, level_id, fee_cents, status, added_by, added_at, removed_at) VALUES (default, 1, 1, 1, 1500, 'ACTIVE', 5, '2026-10-12 18:00+03', null);
INSERT INTO event_participant (id, event_id, member_id, level_id, fee_cents, status, added_by, added_at, removed_at) VALUES (default, 1, 3, 2, 800, 'ACTIVE', 1, '2026-10-14 15:00+03', null);
INSERT INTO event_participant (id, event_id, member_id, level_id, fee_cents, status, added_by, added_at, removed_at) VALUES (default, 1, 2, 1, 1500, 'ACTIVE', 2, '2026-10-15 20:00+03', null);
INSERT INTO event_participant (id, event_id, member_id, level_id, fee_cents, status, added_by, added_at, removed_at) VALUES (default, 1, 4, 2, 800, 'REMOVED', 1, '2026-10-13 16:00+03', '2026-10-16 10:00+03');


-- event_budget_line
INSERT INTO event_budget_line (id, event_id, category, description, planned_cents, actual_cents, note, source, sort_order, created_by, created_at, updated_at) VALUES (default, 1, 'DRINKS', 'Saku Originaal, 3 kasti', 9000, null, null, 'MANUAL', 0, 5, '2026-10-02 12:00+03', '2026-10-02 12:00+03');
INSERT INTO event_budget_line (id, event_id, category, description, planned_cents, actual_cents, note, source, sort_order, created_by, created_at, updated_at) VALUES (default, 1, 'FOOD', 'Õhtusöök, toitlustaja', 30000, null, 'Pakkumine küsitud', 'MANUAL', 1, 5, '2026-10-02 12:00+03', '2026-10-02 12:00+03');
INSERT INTO event_budget_line (id, event_id, category, description, planned_cents, actual_cents, note, source, sort_order, created_by, created_at, updated_at) VALUES (default, 1, 'SNACKS', 'Piparkoogid ja suupisted', 2500, null, null, 'AI', 2, 5, '2026-10-03 12:00+03', '2026-10-03 12:00+03');
INSERT INTO event_budget_line (id, event_id, category, description, planned_cents, actual_cents, note, source, sort_order, created_by, created_at, updated_at) VALUES (default, 1, 'ENTERTAINMENT', 'DJ', 15000, null, null, 'MANUAL', 3, 5, '2026-10-02 12:00+03', '2026-10-02 12:00+03');
INSERT INTO event_budget_line (id, event_id, category, description, planned_cents, actual_cents, note, source, sort_order, created_by, created_at, updated_at) VALUES (default, 1, 'VENUE', 'Klubiruum', 0, 0, 'Oma ruum, tasuta', 'MANUAL', 4, 5, '2026-10-02 12:00+03', '2026-10-02 12:00+03');
INSERT INTO event_budget_line (id, event_id, category, description, planned_cents, actual_cents, note, source, sort_order, created_by, created_at, updated_at) VALUES (default, 1, 'DECORATION', 'Kuuseehted ja küünlad', 2500, 2180, 'Ostetud 20.10', 'MANUAL', 5, 1, '2026-10-02 12:00+03', '2026-10-20 18:00+03');
INSERT INTO event_budget_line (id, event_id, category, description, planned_cents, actual_cents, note, source, sort_order, created_by, created_at, updated_at) VALUES (default, 2, 'OTHER', 'Bussi rent', 35000, null, null, 'MANUAL', 0, 5, '2026-10-15 12:00+03', '2026-10-15 12:00+03');
INSERT INTO event_budget_line (id, event_id, category, description, planned_cents, actual_cents, note, source, sort_order, created_by, created_at, updated_at) VALUES (default, 2, 'ENTERTAINMENT', 'Pruulikodade tuurid', 20000, null, '2 × 10 € inimese kohta', 'MANUAL', 1, 5, '2026-10-15 12:00+03', '2026-10-15 12:00+03');


-- account_entry (rahapearaamat; summa märgiga)
-- Mari (1) → -1250
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 1, 'ADJUSTMENT', 2030, null, null, null, null, 'Algsaldo vanast vihikust', 5, '2026-08-01 10:00+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 1, 'PURCHASE', -900, 1, null, null, null, null, 1, '2026-08-29 19:40+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 1, 'MEMBERSHIP_FEE', -500, null, null, 1, null, null, null, '2026-09-01 06:00+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 1, 'PURCHASE', -840, 2, null, null, null, null, 1, '2026-09-26 20:15+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 1, 'PAYMENT', 2000, null, 3, null, null, null, 5, '2026-09-29 10:00+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 1, 'MEMBERSHIP_FEE', -500, null, null, 5, null, null, null, '2026-10-01 06:00+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 1, 'PURCHASE', -650, 3, null, null, null, null, 1, '2026-10-01 19:30+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 1, 'PURCHASE', -840, 4, null, null, null, null, 1, '2026-10-04 20:15+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 1, 'PAYMENT', 2000, null, 5, null, null, null, 5, '2026-10-10 11:00+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 1, 'EVENT_FEE', -1500, null, null, null, 2, null, 5, '2026-10-12 18:00+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 1, 'PURCHASE', -450, 5, null, null, null, null, 1, '2026-10-12 21:05+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 1, 'PURCHASE_REVERSAL', 450, 5, null, null, null, null, 1, '2026-10-12 21:07+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 1, 'PURCHASE', -1050, 6, null, null, null, null, 1, '2026-10-12 21:10+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 1, 'PURCHASE', -500, 7, null, null, null, null, 1, '2026-10-28 18:42+02');
-- Kaspar (2) → -2740
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 2, 'MEMBERSHIP_FEE', -500, null, null, 2, null, null, null, '2026-09-01 06:00+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 2, 'PURCHASE', -1000, 8, null, null, null, null, 2, '2026-09-12 21:30+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 2, 'PAYMENT', 2560, null, 4, null, null, null, 5, '2026-09-30 22:00+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 2, 'MEMBERSHIP_FEE', -500, null, null, 6, null, null, null, '2026-10-01 06:00+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 2, 'PURCHASE', -1250, 9, null, null, null, null, 2, '2026-10-03 22:10+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 2, 'EVENT_FEE', -1500, null, null, null, 4, null, 2, '2026-10-15 20:00+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 2, 'PURCHASE', -550, 10, null, null, null, null, 2, '2026-10-20 20:45+03');
-- Liis (3) → -2980
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 3, 'MEMBERSHIP_FEE', -250, null, null, 3, null, null, null, '2026-09-01 06:00+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 3, 'PURCHASE', -800, 11, null, null, null, null, 3, '2026-09-20 19:15+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 3, 'PAYMENT', 970, null, 2, null, null, null, 1, '2026-09-25 09:30+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 3, 'MEMBERSHIP_FEE', -250, null, null, 7, null, null, null, '2026-10-01 06:00+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 3, 'PURCHASE', -750, 12, null, null, null, null, 3, '2026-10-10 20:00+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 3, 'EVENT_FEE', -800, null, null, null, 3, null, 1, '2026-10-14 15:00+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 3, 'PURCHASE', -1100, 13, null, null, null, null, 3, '2026-10-24 21:20+03');
-- Rasmus (4) → -820
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 4, 'MEMBERSHIP_FEE', -250, null, null, 4, null, null, null, '2026-09-01 06:00+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 4, 'PURCHASE', -400, 14, null, null, null, null, 4, '2026-09-05 18:30+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 4, 'MEMBERSHIP_FEE', -250, null, null, 8, null, null, null, '2026-10-01 06:00+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 4, 'ADJUSTMENT', 360, null, null, null, null, 'Sularaha üle antud Jaanile 05.10', 5, '2026-10-05 21:00+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 4, 'MEMBERSHIP_FEE_WAIVER', 250, null, null, 8, null, 'Juhatuse otsus, oktoobris tasuta', 5, '2026-10-06 12:00+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 4, 'EVENT_FEE', -800, null, null, null, 5, null, 1, '2026-10-13 16:00+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 4, 'EVENT_FEE_REVERSAL', 800, null, null, null, 5, null, 5, '2026-10-16 10:00+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 4, 'PURCHASE', -530, 15, null, null, null, null, 4, '2026-10-18 19:50+03');
-- Jaan (5) → +450
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 5, 'PAYMENT', 5000, null, 1, null, null, null, 5, '2026-09-01 12:00+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 5, 'PURCHASE', -900, 16, null, null, null, null, 5, '2026-09-10 20:00+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 5, 'EVENT_FEE', -2000, null, null, null, 1, null, 5, '2026-10-02 12:00+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 5, 'PURCHASE', -1000, 17, null, null, null, null, 5, '2026-10-02 21:00+03');
INSERT INTO account_entry (id, member_id, type, amount_cents, purchase_id, payment_id, membership_charge_id, event_participant_id, note, created_by, created_at) VALUES (default, 5, 'PURCHASE', -650, 18, null, null, null, null, 5, '2026-10-22 19:10+03');


-- stock_movement (laopearaamat)
-- tarned
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 1, 'DELIVERY', 24, 150, null, null, 5, '2026-08-01 11:00+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 2, 'DELIVERY', 20, 145, null, null, 5, '2026-08-01 11:00+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 3, 'DELIVERY', 25, 170, null, null, 5, '2026-08-01 11:00+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 4, 'DELIVERY', 12, 280, null, null, 5, '2026-08-01 11:00+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 5, 'DELIVERY', 8, 290, null, null, 5, '2026-08-01 11:00+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 6, 'DELIVERY', 6, 360, null, null, 5, '2026-08-01 11:00+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 7, 'DELIVERY', 12, 250, null, null, 5, '2026-08-01 11:00+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 8, 'DELIVERY', 15, 90, null, null, 5, '2026-08-01 11:00+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 9, 'DELIVERY', 6, 160, null, null, 5, '2026-08-01 11:00+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 1, 'DELIVERY', 6, 150, null, null, 1, '2026-10-15 17:00+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 4, 'DELIVERY', 7, 280, null, null, 1, '2026-10-15 17:00+03');
-- müügid (created_by = ostja)
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 4, 'SALE', -2, null, 1, null, 1, '2026-08-29 19:40+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 3, 'SALE', -3, null, 2, null, 1, '2026-09-26 20:15+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 4, 'SALE', -1, null, 3, null, 1, '2026-10-01 19:30+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 8, 'SALE', -1, null, 3, null, 1, '2026-10-01 19:30+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 3, 'SALE', -3, null, 4, null, 1, '2026-10-04 20:15+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 5, 'SALE', -1, null, 5, null, 1, '2026-10-12 21:05+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 5, 'SALE_REVERSAL', 1, null, 5, null, 1, '2026-10-12 21:07+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 6, 'SALE', -1, null, 6, null, 1, '2026-10-12 21:10+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 2, 'SALE', -2, null, 6, null, 1, '2026-10-12 21:10+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 1, 'SALE', -2, null, 7, null, 1, '2026-10-28 18:42+02');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 1, 'SALE', -4, null, 8, null, 2, '2026-09-12 21:30+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 7, 'SALE', -2, null, 9, null, 2, '2026-10-03 22:10+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 5, 'SALE', -1, null, 9, null, 2, '2026-10-03 22:10+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 6, 'SALE', -1, null, 10, null, 2, '2026-10-20 20:45+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 7, 'SALE', -2, null, 11, null, 3, '2026-09-20 19:15+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 2, 'SALE', -3, null, 12, null, 3, '2026-10-10 20:00+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 4, 'SALE', -2, null, 13, null, 3, '2026-10-24 21:20+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 8, 'SALE', -1, null, 13, null, 3, '2026-10-24 21:20+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 8, 'SALE', -2, null, 14, null, 4, '2026-09-05 18:30+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 3, 'SALE', -1, null, 15, null, 4, '2026-10-18 19:50+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 1, 'SALE', -1, null, 15, null, 4, '2026-10-18 19:50+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 5, 'SALE', -2, null, 16, null, 5, '2026-09-10 20:00+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 6, 'SALE', -1, null, 17, null, 5, '2026-10-02 21:00+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 4, 'SALE', -1, null, 17, null, 5, '2026-10-02 21:00+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 4, 'SALE', -1, null, 18, null, 5, '2026-10-22 19:10+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 8, 'SALE', -1, null, 18, null, 5, '2026-10-22 19:10+03');
-- inventuur ja mahakandmised
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 9, 'WRITE_OFF', -6, null, null, 'Aegunud, toode müügist maas', 5, '2026-09-30 18:00+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 1, 'WRITE_OFF', -1, null, null, 'Pudel katki', 1, '2026-10-20 21:00+03');
INSERT INTO stock_movement (id, product_id, type, quantity_delta, unit_cost_cents, purchase_id, note, created_by, created_at) VALUES (default, 7, 'STOCKTAKE', 1, null, null, 'Inventuur: loeti 9', 5, '2026-10-25 12:00+03');


-- vahemälud pearaamatutest (invariandid: balance_cents = sum(amount_cents), stock_qty = sum(quantity_delta))
UPDATE member m SET balance_cents = coalesce((SELECT sum(e.amount_cents) FROM account_entry e WHERE e.member_id = m.id), 0);
UPDATE product p SET stock_qty = coalesce((SELECT sum(s.quantity_delta) FROM stock_movement s WHERE s.product_id = p.id), 0);
