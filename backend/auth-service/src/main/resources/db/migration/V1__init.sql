-- Auth schema: students + refresh tokens
CREATE TABLE users (
    id                  UUID PRIMARY KEY,
    username            VARCHAR(20)  NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,
    college_id          VARCHAR(80)  NOT NULL,
    display_name        VARCHAR(40),
    bio                 VARCHAR(160),
    avatar_url          VARCHAR(512),
    birthday            DATE,
    role                VARCHAR(16)  NOT NULL DEFAULT 'USER',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    password_changed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_users_username ON users (lower(username));
CREATE INDEX ix_users_college ON users (college_id);

-- Username and college are permanent. Enforce it in the database too, not only in code.
CREATE FUNCTION forbid_identity_change() RETURNS trigger AS $$
BEGIN
    IF NEW.username <> OLD.username OR NEW.college_id <> OLD.college_id THEN
        RAISE EXCEPTION 'username and college_id cannot be changed';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_identity_locked
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION forbid_identity_change();

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id   UUID        NOT NULL,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    user_agent  VARCHAR(255)
);

CREATE INDEX ix_refresh_user ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_family ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_expires ON refresh_tokens (expires_at);
