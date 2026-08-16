-- Schema
-- TODO: Move schema evolution and seed data to Flyway or Liquibase when migrations are introduced.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500) NOT NULL
);

-- Seed data

INSERT INTO users (email, name, address)
VALUES ('demo.user@example.com', 'Demo User', 'Demo Address')
ON CONFLICT (email) DO NOTHING;
