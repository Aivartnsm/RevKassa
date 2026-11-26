-- Muudatus 2026-11-26 (B19): toote ostuhind ja marginaal müügiaruandes.
-- - product.cost_cents: toote praegune ostuhind, admin määrab ja muudab toote vormis (valikuline).
-- - purchase_line.unit_cost_cents: ostuhind müügi hetkel (kopeeritakse nagu müügihind), et ostuhinna muutmine
--   ei kirjutaks varasemaid marginaale ümber. Olemasolevad read jäävad NULL-iks: aruanne kasutab neil toote
--   praegust ostuhinda. Andmeid ei kao; ostuhinnad sisestab admin pärast muudatust toodete vaates.

BEGIN;

ALTER TABLE product ADD COLUMN cost_cents bigint;
ALTER TABLE product ADD CONSTRAINT ck_product_cost_cents CHECK (cost_cents >= 0);

ALTER TABLE purchase_line ADD COLUMN unit_cost_cents bigint;
ALTER TABLE purchase_line ADD CONSTRAINT ck_purchase_line_unit_cost_cents CHECK (unit_cost_cents >= 0);

COMMIT;
