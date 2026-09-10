-- ============================================================================
-- V7 - Password reset tokens (self-service "forgot password" flow).
--
-- One new table, fully additive (no existing column/row is touched).
--
-- A row is created when a user asks to reset their password.
--   * token_hash holds the SHA-256 (hex) of the random token that gets
--     emailed to the user - the plaintext token itself is never stored,
--     so a leak of this table can't be used to reset anyone's password.
--   * expires_at makes the link time-limited (30 minutes - set by
--     PasswordResetService.TOKEN_TTL).
--   * used_at makes the link single-use: it's stamped the moment a reset
--     succeeds, and a token with used_at set is rejected on any later try.
--
-- Old rows are harmless once expired/used and are left in place (there's
-- no scheduled cleanup job - out of scope for this project).
--
-- Canonical source: db/01_schema_v3.sql carries this table too, per
-- db/README.md's process - edit the canonical file first, then copy the
-- delta into a new V<n+1> migration rather than editing an applied one.
-- ============================================================================

CREATE TABLE password_reset_tokens (
    token_id    BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens(user_id);
