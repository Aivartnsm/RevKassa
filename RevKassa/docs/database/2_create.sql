-- RevKassa skeem (PostgreSQL 16). Kavand: docs/DB-MODEL.md
-- Loob kogu praeguse skeemi nullist + viis algset liikmetaset.
-- Konventsioonid: id bigint identity, raha *_cents bigint, ajatemplid timestamptz,
-- enumid text + check, FK fk_<tabel>_<veerg>, indeksid ix_/ux_<tabel>_<veerud>.

-- tables
-- Table: membership_level
CREATE TABLE membership_level (
    id         bigint      GENERATED ALWAYS AS IDENTITY,
    name       text        NOT NULL,
    sort_order integer     NOT NULL,
    active     boolean     NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT membership_level_pk PRIMARY KEY (id)
);

-- Table: member
CREATE TABLE member (
    id             bigint      GENERATED ALWAYS AS IDENTITY,
    email          text,
    display_name   text        NOT NULL,
    pin_hmac       text        NOT NULL,
    password_hash  text,
    role           text        NOT NULL,
    balance_cents  bigint      NOT NULL DEFAULT 0,
    active         boolean     NOT NULL DEFAULT true,
    fee_exempt     boolean     NOT NULL DEFAULT false,
    level_id       bigint      NOT NULL,
    pin_changed_at timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now(),
    version        bigint      NOT NULL DEFAULT 0,
    CONSTRAINT member_pk PRIMARY KEY (id),
    CONSTRAINT ck_member_role CHECK (role IN ('MEMBER', 'ADMIN')),
    CONSTRAINT ck_member_admin_credentials CHECK (role <> 'ADMIN' OR (email IS NOT NULL AND password_hash IS NOT NULL))
);

-- Table: product
CREATE TABLE product (
    id                  bigint      GENERATED ALWAYS AS IDENTITY,
    name                text        NOT NULL,
    style               text,
    volume_ml           integer,
    price_cents         bigint      NOT NULL,
    stock_qty           integer     NOT NULL DEFAULT 0,
    low_stock_threshold integer     NOT NULL DEFAULT 6,
    sort_order          integer     NOT NULL DEFAULT 0,
    active              boolean     NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    version             bigint      NOT NULL DEFAULT 0,
    CONSTRAINT product_pk PRIMARY KEY (id),
    CONSTRAINT ck_product_price_cents CHECK (price_cents > 0)
);

-- Table: purchase
CREATE TABLE purchase (
    id          bigint      GENERATED ALWAYS AS IDENTITY,
    member_id   bigint      NOT NULL,
    total_cents bigint      NOT NULL,
    status      text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    reversed_at timestamptz,
    reversed_by bigint,
    CONSTRAINT purchase_pk PRIMARY KEY (id),
    CONSTRAINT ck_purchase_total_cents CHECK (total_cents > 0),
    CONSTRAINT ck_purchase_status CHECK (status IN ('COMPLETED', 'REVERSED'))
);

-- Table: purchase_line
CREATE TABLE purchase_line (
    id               bigint  GENERATED ALWAYS AS IDENTITY,
    purchase_id      bigint  NOT NULL,
    product_id       bigint  NOT NULL,
    quantity         integer NOT NULL,
    unit_price_cents bigint  NOT NULL,
    CONSTRAINT purchase_line_pk PRIMARY KEY (id),
    CONSTRAINT ck_purchase_line_quantity CHECK (quantity > 0)
);

-- Table: payment
CREATE TABLE payment (
    id            bigint      GENERATED ALWAYS AS IDENTITY,
    member_id     bigint      NOT NULL,
    amount_cents  bigint      NOT NULL,
    method        text        NOT NULL,
    reference     text,
    status        text        NOT NULL,
    reported_at   timestamptz NOT NULL DEFAULT now(),
    decided_by    bigint,
    decided_at    timestamptz,
    decision_note text,
    CONSTRAINT payment_pk PRIMARY KEY (id),
    CONSTRAINT ck_payment_amount_cents CHECK (amount_cents > 0),
    CONSTRAINT ck_payment_method CHECK (method IN ('CARD', 'BANK_TRANSFER')),
    CONSTRAINT ck_payment_status CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED'))
);

-- Table: member_favourite
CREATE TABLE member_favourite (
    member_id  bigint      NOT NULL,
    product_id bigint      NOT NULL,
    position   integer     NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT member_favourite_pk PRIMARY KEY (member_id, product_id)
);

-- Table: membership_fee
CREATE TABLE membership_fee (
    id           bigint      GENERATED ALWAYS AS IDENTITY,
    level_id     bigint      NOT NULL,
    amount_cents bigint      NOT NULL,
    valid_from   date        NOT NULL,
    created_by   bigint      NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT membership_fee_pk PRIMARY KEY (id),
    CONSTRAINT ck_membership_fee_amount_cents CHECK (amount_cents >= 0),
    CONSTRAINT ck_membership_fee_valid_from CHECK (extract(day FROM valid_from) = 1)
);

-- Table: membership_charge
CREATE TABLE membership_charge (
    id           bigint      GENERATED ALWAYS AS IDENTITY,
    member_id    bigint      NOT NULL,
    month        date        NOT NULL,
    level_id     bigint      NOT NULL,
    amount_cents bigint      NOT NULL,
    status       text        NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    waived_by    bigint,
    waived_at    timestamptz,
    waive_note   text,
    CONSTRAINT membership_charge_pk PRIMARY KEY (id),
    CONSTRAINT ck_membership_charge_amount_cents CHECK (amount_cents > 0),
    CONSTRAINT ck_membership_charge_month CHECK (extract(day FROM month) = 1),
    CONSTRAINT ck_membership_charge_status CHECK (status IN ('CHARGED', 'WAIVED')),
    CONSTRAINT ck_membership_charge_waive_note CHECK (status <> 'WAIVED' OR waive_note IS NOT NULL)
);

-- Table: invoice
CREATE TABLE invoice (
    id           bigint      GENERATED ALWAYS AS IDENTITY,
    number       text        NOT NULL,
    member_id    bigint      NOT NULL,
    email_to     text        NOT NULL,
    amount_cents bigint      NOT NULL,
    due_date     date        NOT NULL,
    status       text        NOT NULL,
    error        text,
    sent_by      bigint      NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    sent_at      timestamptz,
    CONSTRAINT invoice_pk PRIMARY KEY (id),
    CONSTRAINT ck_invoice_amount_cents CHECK (amount_cents > 0),
    CONSTRAINT ck_invoice_status CHECK (status IN ('SENT', 'FAILED'))
);

-- Table: event
CREATE TABLE event (
    id          bigint      GENERATED ALWAYS AS IDENTITY,
    name        text        NOT NULL,
    starts_at   timestamptz NOT NULL,
    location    text,
    description text,
    status      text        NOT NULL,
    signup_open boolean     NOT NULL DEFAULT false,
    created_by  bigint      NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    version     bigint      NOT NULL DEFAULT 0,
    CONSTRAINT event_pk PRIMARY KEY (id),
    CONSTRAINT ck_event_status CHECK (status IN ('PLANNED', 'CLOSED', 'CANCELLED'))
);

-- Table: event_level_fee
CREATE TABLE event_level_fee (
    event_id     bigint NOT NULL,
    level_id     bigint NOT NULL,
    amount_cents bigint NOT NULL,
    CONSTRAINT event_level_fee_pk PRIMARY KEY (event_id, level_id),
    CONSTRAINT ck_event_level_fee_amount_cents CHECK (amount_cents >= 0)
);

-- Table: event_participant
CREATE TABLE event_participant (
    id         bigint      GENERATED ALWAYS AS IDENTITY,
    event_id   bigint      NOT NULL,
    member_id  bigint      NOT NULL,
    level_id   bigint      NOT NULL,
    fee_cents  bigint      NOT NULL,
    status     text        NOT NULL,
    added_by   bigint      NOT NULL,
    added_at   timestamptz NOT NULL DEFAULT now(),
    removed_at timestamptz,
    CONSTRAINT event_participant_pk PRIMARY KEY (id),
    CONSTRAINT ck_event_participant_fee_cents CHECK (fee_cents >= 0),
    CONSTRAINT ck_event_participant_status CHECK (status IN ('ACTIVE', 'REMOVED'))
);

-- Table: event_budget_line
CREATE TABLE event_budget_line (
    id            bigint      GENERATED ALWAYS AS IDENTITY,
    event_id      bigint      NOT NULL,
    category      text        NOT NULL,
    description   text        NOT NULL,
    planned_cents bigint      NOT NULL,
    actual_cents  bigint,
    note          text,
    source        text        NOT NULL DEFAULT 'MANUAL',
    sort_order    integer     NOT NULL DEFAULT 0,
    created_by    bigint      NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT event_budget_line_pk PRIMARY KEY (id),
    CONSTRAINT ck_event_budget_line_category CHECK (category IN ('DRINKS', 'SNACKS', 'FOOD', 'ENTERTAINMENT', 'VENUE', 'DECORATION', 'OTHER')),
    CONSTRAINT ck_event_budget_line_planned_cents CHECK (planned_cents >= 0),
    CONSTRAINT ck_event_budget_line_actual_cents CHECK (actual_cents >= 0),
    CONSTRAINT ck_event_budget_line_source CHECK (source IN ('MANUAL', 'AI'))
);

-- Table: account_entry (rahapearaamat, ainult lisatav)
CREATE TABLE account_entry (
    id                   bigint      GENERATED ALWAYS AS IDENTITY,
    member_id            bigint      NOT NULL,
    type                 text        NOT NULL,
    amount_cents         bigint      NOT NULL,
    purchase_id          bigint,
    payment_id           bigint,
    membership_charge_id bigint,
    event_participant_id bigint,
    note                 text,
    created_by           bigint,
    created_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT account_entry_pk PRIMARY KEY (id),
    CONSTRAINT ck_account_entry_type CHECK (type IN ('PURCHASE', 'PURCHASE_REVERSAL', 'PAYMENT', 'ADJUSTMENT', 'MEMBERSHIP_FEE', 'MEMBERSHIP_FEE_WAIVER', 'EVENT_FEE', 'EVENT_FEE_REVERSAL')),
    CONSTRAINT ck_account_entry_amount_cents CHECK (amount_cents <> 0),
    CONSTRAINT ck_account_entry_note CHECK (type NOT IN ('ADJUSTMENT', 'MEMBERSHIP_FEE_WAIVER') OR note IS NOT NULL)
);

-- Table: stock_movement (laopearaamat, ainult lisatav)
CREATE TABLE stock_movement (
    id              bigint      GENERATED ALWAYS AS IDENTITY,
    product_id      bigint      NOT NULL,
    type            text        NOT NULL,
    quantity_delta  integer     NOT NULL,
    unit_cost_cents bigint,
    purchase_id     bigint,
    note            text,
    created_by      bigint      NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT stock_movement_pk PRIMARY KEY (id),
    CONSTRAINT ck_stock_movement_type CHECK (type IN ('DELIVERY', 'SALE', 'SALE_REVERSAL', 'STOCKTAKE', 'WRITE_OFF')),
    CONSTRAINT ck_stock_movement_quantity_delta CHECK (quantity_delta <> 0)
);

-- foreign keys
ALTER TABLE member ADD CONSTRAINT fk_member_level_id FOREIGN KEY (level_id) REFERENCES membership_level (id);

ALTER TABLE purchase ADD CONSTRAINT fk_purchase_member_id FOREIGN KEY (member_id) REFERENCES member (id);
ALTER TABLE purchase ADD CONSTRAINT fk_purchase_reversed_by FOREIGN KEY (reversed_by) REFERENCES member (id);

ALTER TABLE purchase_line ADD CONSTRAINT fk_purchase_line_purchase_id FOREIGN KEY (purchase_id) REFERENCES purchase (id);
ALTER TABLE purchase_line ADD CONSTRAINT fk_purchase_line_product_id FOREIGN KEY (product_id) REFERENCES product (id);

ALTER TABLE payment ADD CONSTRAINT fk_payment_member_id FOREIGN KEY (member_id) REFERENCES member (id);
ALTER TABLE payment ADD CONSTRAINT fk_payment_decided_by FOREIGN KEY (decided_by) REFERENCES member (id);

-- lemmikud on eelistus, mitte pearaamat: liikme kustutamine kustutab ka need
ALTER TABLE member_favourite ADD CONSTRAINT fk_member_favourite_member_id FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE;
ALTER TABLE member_favourite ADD CONSTRAINT fk_member_favourite_product_id FOREIGN KEY (product_id) REFERENCES product (id);

ALTER TABLE membership_fee ADD CONSTRAINT fk_membership_fee_level_id FOREIGN KEY (level_id) REFERENCES membership_level (id);
ALTER TABLE membership_fee ADD CONSTRAINT fk_membership_fee_created_by FOREIGN KEY (created_by) REFERENCES member (id);

ALTER TABLE membership_charge ADD CONSTRAINT fk_membership_charge_member_id FOREIGN KEY (member_id) REFERENCES member (id);
ALTER TABLE membership_charge ADD CONSTRAINT fk_membership_charge_level_id FOREIGN KEY (level_id) REFERENCES membership_level (id);
ALTER TABLE membership_charge ADD CONSTRAINT fk_membership_charge_waived_by FOREIGN KEY (waived_by) REFERENCES member (id);

ALTER TABLE invoice ADD CONSTRAINT fk_invoice_member_id FOREIGN KEY (member_id) REFERENCES member (id);
ALTER TABLE invoice ADD CONSTRAINT fk_invoice_sent_by FOREIGN KEY (sent_by) REFERENCES member (id);

ALTER TABLE event ADD CONSTRAINT fk_event_created_by FOREIGN KEY (created_by) REFERENCES member (id);

ALTER TABLE event_level_fee ADD CONSTRAINT fk_event_level_fee_event_id FOREIGN KEY (event_id) REFERENCES event (id) ON DELETE CASCADE;
ALTER TABLE event_level_fee ADD CONSTRAINT fk_event_level_fee_level_id FOREIGN KEY (level_id) REFERENCES membership_level (id);

ALTER TABLE event_participant ADD CONSTRAINT fk_event_participant_event_id FOREIGN KEY (event_id) REFERENCES event (id);
ALTER TABLE event_participant ADD CONSTRAINT fk_event_participant_member_id FOREIGN KEY (member_id) REFERENCES member (id);
ALTER TABLE event_participant ADD CONSTRAINT fk_event_participant_level_id FOREIGN KEY (level_id) REFERENCES membership_level (id);
ALTER TABLE event_participant ADD CONSTRAINT fk_event_participant_added_by FOREIGN KEY (added_by) REFERENCES member (id);

-- eelarveread on planeerimisandmed, mitte pearaamat
ALTER TABLE event_budget_line ADD CONSTRAINT fk_event_budget_line_event_id FOREIGN KEY (event_id) REFERENCES event (id) ON DELETE CASCADE;
ALTER TABLE event_budget_line ADD CONSTRAINT fk_event_budget_line_created_by FOREIGN KEY (created_by) REFERENCES member (id);

ALTER TABLE account_entry ADD CONSTRAINT fk_account_entry_member_id FOREIGN KEY (member_id) REFERENCES member (id);
ALTER TABLE account_entry ADD CONSTRAINT fk_account_entry_purchase_id FOREIGN KEY (purchase_id) REFERENCES purchase (id);
ALTER TABLE account_entry ADD CONSTRAINT fk_account_entry_payment_id FOREIGN KEY (payment_id) REFERENCES payment (id);
ALTER TABLE account_entry ADD CONSTRAINT fk_account_entry_membership_charge_id FOREIGN KEY (membership_charge_id) REFERENCES membership_charge (id);
ALTER TABLE account_entry ADD CONSTRAINT fk_account_entry_event_participant_id FOREIGN KEY (event_participant_id) REFERENCES event_participant (id);
ALTER TABLE account_entry ADD CONSTRAINT fk_account_entry_created_by FOREIGN KEY (created_by) REFERENCES member (id);

ALTER TABLE stock_movement ADD CONSTRAINT fk_stock_movement_product_id FOREIGN KEY (product_id) REFERENCES product (id);
ALTER TABLE stock_movement ADD CONSTRAINT fk_stock_movement_purchase_id FOREIGN KEY (purchase_id) REFERENCES purchase (id);
ALTER TABLE stock_movement ADD CONSTRAINT fk_stock_movement_created_by FOREIGN KEY (created_by) REFERENCES member (id);

-- indexes
CREATE UNIQUE INDEX ux_member_email ON member (lower(email)) WHERE email IS NOT NULL;
CREATE UNIQUE INDEX ux_member_pin_hmac ON member (pin_hmac);
CREATE INDEX ix_purchase_member_id_created_at ON purchase (member_id, created_at DESC);
CREATE INDEX ix_payment_status_reported_at ON payment (status, reported_at);
CREATE UNIQUE INDEX ux_account_entry_payment_id ON account_entry (payment_id);
CREATE INDEX ix_account_entry_member_id_created_at ON account_entry (member_id, created_at DESC);
CREATE INDEX ix_stock_movement_product_id_created_at ON stock_movement (product_id, created_at DESC);
CREATE UNIQUE INDEX ux_member_favourite_member_id_position ON member_favourite (member_id, position);
CREATE UNIQUE INDEX ux_membership_fee_level_id_valid_from ON membership_fee (level_id, valid_from);
CREATE UNIQUE INDEX ux_membership_charge_member_id_month ON membership_charge (member_id, month);
CREATE INDEX ix_membership_charge_month ON membership_charge (month);
CREATE UNIQUE INDEX ux_invoice_number ON invoice (number);
CREATE INDEX ix_invoice_member_id_created_at ON invoice (member_id, created_at DESC);
CREATE UNIQUE INDEX ux_membership_level_name ON membership_level (lower(name));
CREATE INDEX ix_event_starts_at ON event (starts_at DESC);
CREATE UNIQUE INDEX ux_event_participant_event_id_member_id ON event_participant (event_id, member_id);
CREATE INDEX ix_event_participant_member_id ON event_participant (member_id);
CREATE INDEX ix_event_budget_line_event_id_category ON event_budget_line (event_id, category);

-- initial data: viis liikmetaset (adminid saavad ümber nimetada, lisada, järjestada, maha võtta)
INSERT INTO membership_level (name, sort_order) VALUES ('Täisliige', 1);
INSERT INTO membership_level (name, sort_order) VALUES ('Noorliige', 2);
INSERT INTO membership_level (name, sort_order) VALUES ('Vilistlane', 3);
INSERT INTO membership_level (name, sort_order) VALUES ('Toetajaliige', 4);
INSERT INTO membership_level (name, sort_order) VALUES ('Auliige', 5);

-- End of file.
