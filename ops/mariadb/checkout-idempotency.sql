-- Aplicar antes de desplegar la versión que contiene el checkout transaccional.
-- MariaDB ejecuta DDL con commit implícito: realizar respaldo y probar primero en staging.

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS checkout_request_id VARCHAR(100) NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_payments_tenant_order_checkout_request
    ON payments (tenant_id, order_id, checkout_request_id);
