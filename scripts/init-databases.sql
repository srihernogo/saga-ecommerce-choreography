-- Buat database terpisah untuk setiap service
-- Script ini dijalankan otomatis saat postgres container pertama kali start

CREATE DATABASE order_db;
CREATE DATABASE payment_db;
CREATE DATABASE inventory_db;
CREATE DATABASE shipping_db;

-- Buat user terpisah per service (best practice untuk production)
CREATE USER order_user     WITH PASSWORD 'order_pass';
CREATE USER payment_user   WITH PASSWORD 'payment_pass';
CREATE USER inventory_user WITH PASSWORD 'inventory_pass';
CREATE USER shipping_user  WITH PASSWORD 'shipping_pass';

GRANT ALL PRIVILEGES ON DATABASE order_db     TO order_user;
GRANT ALL PRIVILEGES ON DATABASE payment_db   TO payment_user;
GRANT ALL PRIVILEGES ON DATABASE inventory_db TO inventory_user;
GRANT ALL PRIVILEGES ON DATABASE shipping_db  TO shipping_user;

\connect order_db
GRANT ALL ON SCHEMA public TO order_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO order_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO order_user;

\connect payment_db
GRANT ALL ON SCHEMA public TO payment_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO payment_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO payment_user;

\connect inventory_db
GRANT ALL ON SCHEMA public TO inventory_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO inventory_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO inventory_user;

\connect shipping_db
GRANT ALL ON SCHEMA public TO shipping_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO shipping_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO shipping_user;
