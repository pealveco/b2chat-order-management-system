-- Schema
-- TODO: Move schema evolution and seed data to Flyway or Liquibase when migrations are introduced.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500) NOT NULL
);

CREATE TABLE IF NOT EXISTS products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price NUMERIC(12, 2) NOT NULL CHECK (price > 0),
    stock INTEGER NOT NULL CHECK (stock >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

ALTER TABLE products ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

-- Seed data

INSERT INTO users (email, name, address)
VALUES ('demo.user@example.com', 'Demo User', 'Demo Address')
ON CONFLICT (email) DO NOTHING;

INSERT INTO products (id, name, description, price, stock)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'Mechanical Keyboard', 'Compact mechanical keyboard with tactile switches', 250000.00, 15),
    ('22222222-2222-2222-2222-222222222222', 'Wireless Mouse', 'Ergonomic wireless mouse with USB receiver', 85000.00, 30),
    ('33333333-3333-3333-3333-333333333333', 'USB-C Hub', 'Multiport USB-C hub with HDMI and ethernet', 145000.00, 8)
ON CONFLICT (id) DO NOTHING;
