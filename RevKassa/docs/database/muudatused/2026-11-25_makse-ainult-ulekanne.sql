-- Muudatus 2026-11-25 (B15): makse viis on ainult pangaülekanne.
-- - payment.method: kaardimakset (CARD) pole — makseplatvormi ega terminali lepingut pole. Veerg jääb
--   tulevase terminali jaoks, lubatud ainult BANK_TRANSFER. Olemasolevad CARD read muudetakse
--   BANK_TRANSFER-iks (summa, staatus ja pearaamatu read jäävad samaks).
-- - Uus indeks liikme maksete nimekirja jaoks (GET /api/me/payments).

BEGIN;

UPDATE payment SET method = 'BANK_TRANSFER' WHERE method = 'CARD';

ALTER TABLE payment DROP CONSTRAINT ck_payment_method;
ALTER TABLE payment ADD CONSTRAINT ck_payment_method CHECK (method IN ('BANK_TRANSFER'));

CREATE INDEX ix_payment_member_id_status_reported_at ON payment (member_id, status, reported_at DESC);

COMMIT;
