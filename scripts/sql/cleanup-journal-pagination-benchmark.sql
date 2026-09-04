CREATE TEMPORARY TABLE benchmark_transaction_ids (
    transaction_id BIGINT NOT NULL PRIMARY KEY
);

INSERT INTO benchmark_transaction_ids (transaction_id)
SELECT DISTINCT aje.transaction_id
FROM account_journal_entry aje
JOIN account a ON a.id = aje.account_id
JOIN customer c ON c.id = a.customer_id
WHERE c.name = 'Journal Pagination Benchmark';

DELETE aje
FROM account_journal_entry aje
JOIN account a ON a.id = aje.account_id
JOIN customer c ON c.id = a.customer_id
WHERE c.name = 'Journal Pagination Benchmark';

DELETE ft
FROM financial_transaction ft
JOIN benchmark_transaction_ids benchmark
    ON benchmark.transaction_id = ft.id;

DELETE a
FROM account a
JOIN customer c ON c.id = a.customer_id
WHERE c.name = 'Journal Pagination Benchmark';

DELETE FROM customer
WHERE name = 'Journal Pagination Benchmark';

SELECT COUNT(*) AS remaining_benchmark_customers
FROM customer
WHERE name = 'Journal Pagination Benchmark';
