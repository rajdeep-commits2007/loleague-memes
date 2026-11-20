-- Sign-in moves to Clerk (phone OTP, Google, GitHub). Every account is tied to exactly one Clerk user
-- and one verified phone number, so the same phone can never own two accounts.
-- password_hash is only kept so pre-Clerk accounts can be claimed once; it is cleared on claim.

ALTER TABLE users
    ALTER COLUMN password_hash DROP NOT NULL,
    ADD COLUMN clerk_user_id       VARCHAR(64),
    ADD COLUMN phone               VARCHAR(20),
    ADD COLUMN username_changed_at TIMESTAMPTZ;

CREATE UNIQUE INDEX ux_users_clerk ON users (clerk_user_id) WHERE clerk_user_id IS NOT NULL;
CREATE UNIQUE INDEX ux_users_phone ON users (phone) WHERE phone IS NOT NULL;

-- Usernames can now change (still unique, case-insensitive: ux_users_username). College stays permanent,
-- and a linked phone / Clerk account can't be swapped for another one.
CREATE OR REPLACE FUNCTION forbid_identity_change() RETURNS trigger AS $$
BEGIN
    IF NEW.college_id <> OLD.college_id THEN
        RAISE EXCEPTION 'college_id cannot be changed';
    END IF;
    IF OLD.clerk_user_id IS NOT NULL AND NEW.clerk_user_id IS DISTINCT FROM OLD.clerk_user_id THEN
        RAISE EXCEPTION 'clerk_user_id cannot be changed once linked';
    END IF;
    IF OLD.phone IS NOT NULL AND NEW.phone IS DISTINCT FROM OLD.phone THEN
        RAISE EXCEPTION 'phone cannot be changed once linked';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
