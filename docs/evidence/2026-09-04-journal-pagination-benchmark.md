# Journal Pagination Benchmark Evidence - 2026-09-04

## Purpose

This benchmark captures why account journal lookup uses keyset pagination instead of offset pagination.

The benchmark is intentionally synthetic. It is not a production load test, but it gives a reproducible local SQL comparison with a larger journal table than the normal development database.

Re-run on 2026-09-07 after aligning the benchmark SQL with `AccountJournalQueryService`, which fetches `limit + 1` rows to derive `hasNext` and `nextCursor`.

## Setup

Script:

```bash
docker exec -i bankcore-mysql mysql -ubankcore -pbankcore_password bankcore \
  < scripts/sql/seed-journal-pagination-benchmark.sql
```

The seed script inserts synthetic data and runs `EXPLAIN ANALYZE` for the first-page keyset query, middle-page keyset query, and offset comparison query.

Cleanup script:

```bash
docker exec -i bankcore-mysql mysql -ubankcore -pbankcore_password bankcore \
  < scripts/sql/cleanup-journal-pagination-benchmark.sql
```

Inserted benchmark data:

```text
benchmark_account_id  inserted_journal_rows
5                     50000
```

Requested API page size and service query probe size:

```text
requested_page_size  service_query_limit
20                   21
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
LIMIT 21;
```

Observed `EXPLAIN ANALYZE`:

```text
-> Limit: 21 row(s) (actual time=0.28..0.282 rows=21 loops=1)
    -> Index lookup on account_journal_entry using idx_account_journal_account_id_id
       (reverse) (actual time=0.277..0.279 rows=21 loops=1)
```

## Keyset Middle Page

Query:

```sql
SELECT id, transaction_id, entry_no, movement_type, amount, balance_after, created_at
FROM account_journal_entry
WHERE account_id = ?
  AND id < ?
ORDER BY id DESC
LIMIT 21;
```

Observed `EXPLAIN ANALYZE`:

```text
-> Limit: 21 row(s) (actual time=0.0843..0.0873 rows=21 loops=1)
    -> Filter: (account_id = 5 and id < 75098)
        -> Index range scan on account_journal_entry using PRIMARY over (id < 75098)
           (reverse) (actual time=0.0727..0.0738 rows=21 loops=1)
```

For this synthetic data distribution, MySQL chose a reverse primary-key range scan for the middle-page query rather than the composite `(account_id, id)` index. The query shape still matches the service implementation and still reads only the probed page after the cursor condition.

## Offset Comparison

Query:

```sql
SELECT id, transaction_id, entry_no, movement_type, amount, balance_after, created_at
FROM account_journal_entry
WHERE account_id = ?
ORDER BY id DESC
LIMIT 21 OFFSET 25000;
```

Observed `EXPLAIN ANALYZE`:

```text
-> Limit/Offset: 21/25000 row(s) (actual time=9.47..9.47 rows=21 loops=1)
    -> Index lookup on account_journal_entry using idx_account_journal_account_id_id
       (reverse) (actual time=0.254..9.06 rows=25021 loops=1)
```

## Takeaway

The keyset query reads only the requested page after the cursor condition. The offset query must scan and discard 25,000 rows before returning 20 rows.

For this synthetic local run:

- Keyset middle page: about `0.087ms`
- Offset page at 25,000: about `9.47ms`
- Offset was roughly `109x` slower in this observed run.

The exact timing is machine-dependent, but the access pattern difference is the important evidence.
