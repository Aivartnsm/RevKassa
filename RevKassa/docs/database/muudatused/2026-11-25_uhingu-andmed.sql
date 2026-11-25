-- Muudatus 2026-11-25 (B39): ühingu andmed andmebaasis.
-- - organisation: üks rida (id = 1) saaja nime ja IBAN-iga, mida admin muudab rakenduses. Varem tulid need
--   seadetest revkassa.invoice.org-name / .iban; pärast seda muudatust täida need admini vaates
--   (või allpool UPDATE-iga) ja eemalda keskkonnamuutujad REVKASSA_INVOICE_ORG_NAME / _IBAN.
-- Olemasolevaid andmeid ei muudeta.

BEGIN;

-- Table: organisation (üks rida: saaja nimi ja IBAN pangaülekannete ja arvete jaoks; admin muudab)
CREATE TABLE organisation (
    id         integer     NOT NULL,
    name       text,
    iban       text,
    updated_by bigint,
    updated_at timestamptz,
    version    bigint      NOT NULL DEFAULT 0,
    CONSTRAINT organisation_pk PRIMARY KEY (id),
    CONSTRAINT ck_organisation_single_row CHECK (id = 1)
);

ALTER TABLE organisation ADD CONSTRAINT fk_organisation_updated_by FOREIGN KEY (updated_by) REFERENCES member (id);

INSERT INTO organisation (id) VALUES (1);
-- UPDATE organisation SET name = '<saaja nimi>', iban = '<IBAN tühikuteta>' WHERE id = 1;

COMMIT;
