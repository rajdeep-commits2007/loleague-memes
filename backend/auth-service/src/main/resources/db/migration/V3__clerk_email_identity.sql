-- Sign-up moves from phone OTP to email OTP (Clerk's SMS delivery to Indian numbers was unreliable).
-- Every new account is tied to one verified email. The phone column stays for accounts created before
-- this change but is no longer filled in.

ALTER TABLE users ADD COLUMN email VARCHAR(320);

CREATE UNIQUE INDEX ux_users_email ON users (lower(email)) WHERE email IS NOT NULL;

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
    IF OLD.email IS NOT NULL AND NEW.email IS DISTINCT FROM OLD.email THEN
        RAISE EXCEPTION 'email cannot be changed once linked';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
