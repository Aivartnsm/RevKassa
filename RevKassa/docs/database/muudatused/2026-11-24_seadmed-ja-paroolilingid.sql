-- Muudatus 2026-11-24: PIN-iga sisselogimine ainult registreeritud seadmetes, mujal e-post + parool.
-- - member.email kohustuslik kõigile (parooliga sisselogimine ja parooli lingid käivad e-posti kaudu)
-- - ck_member_admin_credentials eemaldatud (parool määratakse kutselingi kaudu pärast loomist)
-- - uued tabelid trusted_device ja password_token
-- Enne käivitamist: igal liikmel peab olema e-post (vt kontrollpäringut allpool).

BEGIN;

-- kontroll: kui see tagastab ridu, lisa neile liikmetele enne e-post
-- SELECT id, display_name FROM member WHERE email IS NULL;

ALTER TABLE member DROP CONSTRAINT ck_member_admin_credentials;
ALTER TABLE member ALTER COLUMN email SET NOT NULL;
DROP INDEX ux_member_email;
CREATE UNIQUE INDEX ux_member_email ON member (lower(email));

CREATE TABLE trusted_device (
    id           bigint      GENERATED ALWAYS AS IDENTITY,
    name         text        NOT NULL,
    token_hash   text        NOT NULL,
    created_by   bigint      NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    last_used_at timestamptz,
    revoked_by   bigint,
    revoked_at   timestamptz,
    CONSTRAINT trusted_device_pk PRIMARY KEY (id)
);

CREATE TABLE password_token (
    id         bigint      GENERATED ALWAYS AS IDENTITY,
    member_id  bigint      NOT NULL,
    token_hash text        NOT NULL,
    purpose    text        NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT password_token_pk PRIMARY KEY (id),
    CONSTRAINT ck_password_token_purpose CHECK (purpose IN ('INVITE', 'RESET'))
);

ALTER TABLE trusted_device ADD CONSTRAINT fk_trusted_device_created_by FOREIGN KEY (created_by) REFERENCES member (id);
ALTER TABLE trusted_device ADD CONSTRAINT fk_trusted_device_revoked_by FOREIGN KEY (revoked_by) REFERENCES member (id);
ALTER TABLE password_token ADD CONSTRAINT fk_password_token_member_id FOREIGN KEY (member_id) REFERENCES member (id);
ALTER TABLE password_token ADD CONSTRAINT fk_password_token_created_by FOREIGN KEY (created_by) REFERENCES member (id);

CREATE UNIQUE INDEX ux_trusted_device_token_hash ON trusted_device (token_hash);
CREATE UNIQUE INDEX ux_password_token_token_hash ON password_token (token_hash);
CREATE INDEX ix_password_token_member_id ON password_token (member_id);

COMMIT;
