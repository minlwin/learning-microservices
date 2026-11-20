-- ==========================================
-- 1. Database
-- ==========================================
-- wallet_demo is automatically created by
-- POSTGRES_DB in docker-compose.yml.

-- ==========================================
-- 2. Create service roles
-- ==========================================

CREATE ROLE authuser
    WITH LOGIN PASSWORD 'authpass';

CREATE ROLE mnguser
    WITH LOGIN PASSWORD 'mngpass';

CREATE ROLE csmuser
    WITH LOGIN PASSWORD 'csmpass';

-- ==========================================
-- 3. Create schemas
-- ==========================================

CREATE SCHEMA auth AUTHORIZATION authuser;

CREATE SCHEMA management
    AUTHORIZATION mnguser;

CREATE SCHEMA consumer
    AUTHORIZATION csmuser;

-- ==========================================
-- 4. Database permissions
-- ==========================================

REVOKE ALL ON DATABASE wallet_demo FROM PUBLIC;

GRANT CONNECT ON DATABASE wallet_demo
    TO authuser, mnguser, csmuser;

REVOKE ALL ON SCHEMA public FROM PUBLIC;

-- ==========================================
-- 5. Default search paths
-- ==========================================

ALTER ROLE authuser IN DATABASE wallet_demo
    SET search_path = auth;

ALTER ROLE mnguser IN DATABASE wallet_demo
    SET search_path = management;

ALTER ROLE csmuser IN DATABASE wallet_demo
    SET search_path = consumer;