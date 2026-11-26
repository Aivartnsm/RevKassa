-- Üritused: mustand/avaldatud olek, hinnaperioodid ja tasu võtmise aeg (B28 ümbertegemine, B29 eeltöö).
-- Viib andmebaasi eelmisest skeemist uude andmeid kaotamata. Käivita ühe korra.
--  * event.status: PLANNED → PUBLISHED, uus DRAFT; registreerimine saab olla avatud ainult PUBLISHED üritusel
--  * event_level_fee: hinnaperioodid (valid_from, null = algusest), seepärast oma id võtmeks
--  * event_participant.charged_at: millal tasu saldole võeti (null = veel võtmata); olemasolevatel osalejatel,
--    kellel on EVENT_FEE rida, täidetakse selle rea ajaga

BEGIN;

-- event
ALTER TABLE event DROP CONSTRAINT ck_event_status;
UPDATE event SET status = 'PUBLISHED' WHERE status = 'PLANNED';
ALTER TABLE event ADD CONSTRAINT ck_event_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED', 'CANCELLED'));
UPDATE event SET signup_open = false WHERE status <> 'PUBLISHED';
ALTER TABLE event ADD CONSTRAINT ck_event_signup_open CHECK (NOT signup_open OR status = 'PUBLISHED');

-- event_level_fee: olemasolevad read on perioodi "algusest" read
ALTER TABLE event_level_fee DROP CONSTRAINT event_level_fee_pk;
ALTER TABLE event_level_fee ADD COLUMN id bigint GENERATED ALWAYS AS IDENTITY;
ALTER TABLE event_level_fee ADD COLUMN valid_from date;
ALTER TABLE event_level_fee ADD CONSTRAINT event_level_fee_pk PRIMARY KEY (id);
CREATE UNIQUE INDEX ux_event_level_fee_event_id_level_id_valid_from ON event_level_fee (event_id, level_id, valid_from) NULLS NOT DISTINCT;
-- 0 € read pole vajalikud (rea puudumine = 0 €)
DELETE FROM event_level_fee WHERE amount_cents = 0;

-- event_participant
ALTER TABLE event_participant ADD COLUMN charged_at timestamptz;
UPDATE event_participant p
SET charged_at = (SELECT min(e.created_at) FROM account_entry e
                  WHERE e.event_participant_id = p.id AND e.type = 'EVENT_FEE');

COMMIT;
