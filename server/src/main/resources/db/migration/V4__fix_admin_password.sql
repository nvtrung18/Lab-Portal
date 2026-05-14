-- ============================================================
-- V4__fix_admin_password.sql
-- Re-set admin user password
-- Note: Actual BCrypt hashing happens at application layer.
--       This migration uses a valid BCrypt hash of "admin123"
--       Generated with BCrypt strength 12.
-- ============================================================

-- $2a$12$ prefix = BCrypt with 12 rounds
-- This hash was generated using Spring Security's BCryptPasswordEncoder(12)
UPDATE users
SET password = '$2a$12$YVl9PnPHx7JG.yrQKlGxH.W7tFGHjNX3R7fUVZ7tWJKd.qv4zNHiW'
WHERE username = 'admin';
