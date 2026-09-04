# Journal Pagination Benchmark Evidence - 2026-09-04

## Purpose

This benchmark captures why account journal lookup uses keyset pagination instead of offset pagination.

The benchmark is intentionally synthetic. It is not a production load test, but it gives a reproducible local SQL comparison with a larger journal table than the normal development database.

## Setup

Script:

```bash
docker exec -i bankcore-mysql mysql -ubankcore -pbankcore_password bankcore \
  < scripts/sql/seed-journal-pagination-benchmark.sql
```

Cleanup script:

```bash
docker exec -i bankcore-mysql mysql -ubankcore -pbankcore_password bankcore \
  < scripts/sql/cleanup-journal-pagination-benchmark.sql
```

Inserted benchmark data:

```text
benchmark_account_id  inserted_journal_rows
2                     50000
```

Supporting index:

```sql
CREATE INDEX idx_account_journal_account_id_id
    ON account_journal_entry (account_id, id);
```

## Keyset First Page

Query:

```sql
SELECT id, transaction_id, entry_no, movement_type, amount, balance_after, created_at
FROM account_journal_entry
WHERE account_id = ?
ORDER BY id DESC
LIMIT 20;
```

Observed `EXPLAIN ANALYZE`:

```text
-> Limit: 20 row(s) (actual time=0.283..0.285 rows=20 loops=1)
    -> Index lookup on account_journal_entry using idx_account_journal_account_id_id
       (reverse) (actual time=0.282..0.284 rows=20 loops=1)
```

## Keyset Middle Page

Query:

```sql
SELECT id, transaction_id, entry_no, movement_type, amount, balance_after, created_at
FROM account_journal_entry USE INDEX (idx_account_journal_account_id_id)
WHERE account_id = ?
  AND id < ?
ORDER BY id DESC
LIMIT 20;
```

Observed `EXPLAIN ANALYZE`:

```text
-> Limit: 20 row(s) (actual time=0.368..0.370 rows=20 loops=1)
    -> Index range scan on account_journal_entry using idx_account_journal_account_id_id
       over (account_id = 2 AND id < 25000) (reverse)
       (actual time=0.367..0.369 rows=20 loops=1)
```

## Offset Comparison

Query:

```sql
SELECT id, transaction_id, entry_no, movement_type, amount, balance_after, created_at
FROM account_journal_entry
WHERE account_id = ?
ORDER BY id DESC
LIMIT 20 OFFSET 25000;
```

Observed `EXPLAIN ANALYZE`:

```text
-> Limit/Offset: 20/25000 row(s) (actual time=11.6..11.6 rows=20 loops=1)
    -> Index lookup on account_journal_entry using idx_account_journal_account_id_id
       (reverse) (actual time=0.297..11.1 rows=25020 loops=1)
```

## Takeaway

The keyset query reads only the requested page after the cursor condition. The offset query must scan and discard 25,000 rows before returning 20 rows.

For this synthetic local run:

- Keyset middle page: about `0.37ms`
- Offset page at 25,000: about `11.6ms`
- Offset was roughly `31x` slower in this observed run.

The exact timing is machine-dependent, but the access pattern difference is the important evidence.
