INSERT INTO products
    (name, stock_quantity, version, created_at, updated_at)
SELECT
    'SIM Card - 4G',
    100,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM products
    WHERE name = 'SIM Card - 4G'
);
