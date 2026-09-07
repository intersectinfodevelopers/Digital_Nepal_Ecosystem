-- V32__widen_refresh_token_column.sql
--
-- BUG FIX: refresh_tokens.token was VARCHAR(512), but the actual RS256 JWT
-- refresh token JwtService generates (header + claims: sub, jti, iat, exp +
-- RS256 signature, base64url-encoded) routinely exceeds 512 characters.
-- Every single login attempt failed with:
--   ERROR: value too long for type character varying(512)
-- inside the batch insert of the new RefreshToken row — meaning login has
-- never worked end-to-end since V25 introduced this table, in any
-- environment using the real JwtService (not just a mocked one in tests).
-- Found by running a real authenticated login against a freshly migrated
-- database.
--
-- Switched to TEXT (unbounded, still indexed/unique — Postgres has no
-- length cap on TEXT and the unique btree index has no practical size
-- issue at real JWT lengths) rather than picking another fixed VARCHAR
-- bound that token length could grow past again later (e.g. if more
-- claims are added to the refresh token).

ALTER TABLE refresh_tokens ALTER COLUMN token TYPE TEXT;
