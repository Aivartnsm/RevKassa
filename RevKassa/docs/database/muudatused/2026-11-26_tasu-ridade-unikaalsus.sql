-- Muudatus 2026-11-26 (B24): liikmemaksu pearaamatu ridade viimane kaitseliin.
-- - Ühel membership_charge-il on kõige rohkem üks MEMBERSHIP_FEE ja üks MEMBERSHIP_FEE_WAIVER rida, nii et sama tasu
--   ei saa kaks korda võtta ega kaks korda tühistada (rakendus kontrollib seda ka ise liikme rea lukuga).
-- Teiste tüüpide read (membership_charge_id NULL) ei sega: unikaalne indeks lubab mitut NULL-i. Andmeid ei kao.
-- Enne käivitamist kontrolli, et topeltridu pole (peab tagastama 0 rida):
--   SELECT membership_charge_id, type, count(*) FROM account_entry
--   WHERE membership_charge_id IS NOT NULL GROUP BY 1, 2 HAVING count(*) > 1;

BEGIN;

CREATE UNIQUE INDEX ux_account_entry_membership_charge_id_type ON account_entry (membership_charge_id, type);

COMMIT;
