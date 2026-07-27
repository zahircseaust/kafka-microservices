INSERT INTO products (id, name, stock_quantity)
SELECT 1, 'SIM Card - 4G', 100
WHERE NOT EXISTS (SELECT 1 FROM products WHERE id = 1);

INSERT INTO products (id, name, stock_quantity)
SELECT 2, '4G Router', 50
WHERE NOT EXISTS (SELECT 1 FROM products WHERE id = 2);

INSERT INTO products (id, name, stock_quantity)
SELECT 3, 'Fiber Modem', 5
WHERE NOT EXISTS (SELECT 1 FROM products WHERE id = 3);
