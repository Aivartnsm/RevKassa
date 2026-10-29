-- AINULT ARENDUSES! Tootmises ei käivitata seda kunagi (muudatused: docs/database/muudatused/).
-- Kustutab public schema (mis põhimõtteliselt kustutab kõik tabelid ja andmed)
DROP SCHEMA IF EXISTS public CASCADE;
-- Loob uue public schema, omanikuks rakenduse kasutaja revkassa
CREATE SCHEMA public AUTHORIZATION revkassa;
-- taastab vajalikud andmebaasi õigused
GRANT ALL ON SCHEMA public TO revkassa;
GRANT USAGE ON SCHEMA public TO PUBLIC;
