-- Keep only a hash of the unpredictable family secret. Legacy sessions migrate on rotation.
-- Family identity authorizes revocation only; refresh still requires the current full token hash.
ALTER TABLE auth_sessions ADD COLUMN refresh_family_hash VARCHAR(44);
CREATE UNIQUE INDEX uq_auth_sessions_refresh_family
    ON auth_sessions(refresh_family_hash) WHERE refresh_family_hash IS NOT NULL;
