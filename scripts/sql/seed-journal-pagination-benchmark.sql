SET SESSION cte_max_recursion_depth = 60000;

SET @row_count = 50000;
SET @account_number = CONCAT('BENCH-', LEFT(REPLACE(UUID(), '-', ''), 20));

INSERT INTO customer (name)
VALUES ('Journal Pagination Benchmark');

SET @customer_id = LAST_INSERT_ID();

INSERT INTO account (customer_id, account_number, balance, status, version)
VALUES (@customer_id, @account_number, @row_count, 'ACTIVE', 0);

SET @account_id = LAST_INSERT_ID();

INSERT INTO financial_transaction (transaction_key, type, amount)
WITH RECURSIVE seq(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < @row_count
)
SELECT UUID(), 'CONTROLLED_SEED', 1
FROM seq;

SET @first_transaction_id = LAST_INSERT_ID();

INSERT INTO account_journal_entry
    (transaction_id, entry_no, account_id, movement_type, amount, balance_after)
WITH RECURSIVE seq(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < @row_count
)
SELECT
    @first_transaction_id + n - 1,
    1,
    @account_id,
    'BALANCE_INCREASE',
    1,
    n
FROM seq;

SELECT @account_id AS benchmark_account_id, @row_count AS inserted_journal_rows;

EXPLAIN ANALYZE
SELECT id, transaction_id, entry_no, movement_type, amount, balance_after, created_at
FROM account_journal_entry
WHERE account_id = @account_id
ORDER BY id DESC
LIMIT 20;

SET @before_entry_id = (
    SELECT MAX(id) - 25000
    FROM account_journal_entry
    WHERE account_id = @account_id
);

EXPLAIN ANALYZE
SELECT id, transaction_id, entry_no, movement_type, amount, balance_after, created_at
FROM account_journal_entry
WHERE account_id = @account_id
  AND id < @before_entry_id
ORDER BY id DESC
LIMIT 20;
