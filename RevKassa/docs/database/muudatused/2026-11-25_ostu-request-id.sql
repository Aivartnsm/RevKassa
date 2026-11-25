-- Muudatus 2026-11-25 (B11): ostu kordamise kaitse.
-- - purchase.request_id: kliendi genereeritud UUID ostukorvi kohta. Kui sama ost saadetakse uuesti
--   (katkenud ühendus, topeltvajutus), tagastab server juba salvestatud ostu ega võta raha teist korda.
-- Olemasolevad ostud jäävad NULL-iks (unikaalne indeks lubab mitut NULL-i). Andmeid ei kao.

BEGIN;

ALTER TABLE purchase ADD COLUMN request_id uuid;
CREATE UNIQUE INDEX ux_purchase_request_id ON purchase (request_id);

COMMIT;
