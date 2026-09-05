import type { FormEvent, ReactNode } from 'react'
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  apiDisplayOrigin,
  fetchDemoAccounts,
  fetchJournalEntries,
  fetchReconciliationMismatches,
  isApiClientError,
  postInternalTransfer,
  type AccountJournalEntry,
  type DemoAccount,
  type TransferOperation,
  type TransferResponse,
} from './api'
import './App.css'

type TransferForm = {
  callerScope: string
  idempotencyKey: string
  sourceAccountId: string
  destinationAccountId: string
  amount: string
}

type ReplayCheck = {
  response: TransferResponse
  identical: boolean
}

type ConflictCheck = {
  kind: 'expected-conflict' | 'unexpected-success' | 'unexpected-error'
  testedAmount: number
  response?: TransferResponse
  error?: Error
}

type JournalRow = AccountJournalEntry & {
  accountId: number
  accountLabel: string
}

function App() {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<TransferForm>({
    callerScope: 'bankcore-lab-console',
    idempotencyKey: createIdempotencyKey(),
    sourceAccountId: '',
    destinationAccountId: '',
    amount: '10000',
  })
  const [firstOperation, setFirstOperation] = useState<TransferOperation | null>(null)
  const [firstResponse, setFirstResponse] = useState<TransferResponse | null>(null)
  const [replayCheck, setReplayCheck] = useState<ReplayCheck | null>(null)
  const [conflictCheck, setConflictCheck] = useState<ConflictCheck | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [lastError, setLastError] = useState<Error | null>(null)

  const demoAccountsQuery = useQuery({
    queryKey: ['demoAccounts'],
    queryFn: fetchDemoAccounts,
  })
  const reconciliationQuery = useQuery({
    queryKey: ['reconciliationMismatches'],
    queryFn: fetchReconciliationMismatches,
  })

  const accounts = demoAccountsQuery.data ?? []
  const sourceAccountId = form.sourceAccountId || String(accounts[0]?.accountId ?? '')
  const destinationAccountId =
    form.destinationAccountId ||
    String(accounts.find((account) => String(account.accountId) !== sourceAccountId)?.accountId ?? '')
  const numericSourceAccountId = Number(sourceAccountId)
  const numericDestinationAccountId = Number(destinationAccountId)
  const journalSourceAccountId = firstOperation?.request.sourceAccountId ?? numericSourceAccountId
  const journalDestinationAccountId =
    firstOperation?.request.destinationAccountId ?? numericDestinationAccountId

  const sourceJournalQuery = useQuery({
    queryKey: ['journalEntries', journalSourceAccountId],
    queryFn: () => fetchJournalEntries(journalSourceAccountId, 10),
    enabled: journalSourceAccountId > 0,
  })
  const destinationJournalQuery = useQuery({
    queryKey: ['journalEntries', journalDestinationAccountId],
    queryFn: () => fetchJournalEntries(journalDestinationAccountId, 10),
    enabled: journalDestinationAccountId > 0,
  })

  async function invalidateTransferEvidence(operation: TransferOperation) {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['demoAccounts'] }),
      queryClient.invalidateQueries({ queryKey: ['journalEntries', operation.request.sourceAccountId] }),
      queryClient.invalidateQueries({ queryKey: ['journalEntries', operation.request.destinationAccountId] }),
      queryClient.invalidateQueries({ queryKey: ['reconciliationMismatches'] }),
    ])
  }

  const transferMutation = useMutation<TransferResponse, Error, TransferOperation>({
    mutationFn: postInternalTransfer,
    onSuccess: async (_response, operation) => {
      await invalidateTransferEvidence(operation)
    },
  })
  const replayMutation = useMutation<TransferResponse, Error, TransferOperation>({
    mutationFn: postInternalTransfer,
    onSuccess: async (_response, operation) => {
      await invalidateTransferEvidence(operation)
    },
  })
  const conflictMutation = useMutation<TransferResponse, Error, TransferOperation>({
    mutationFn: postInternalTransfer,
    onSuccess: async (_response, operation) => {
      await invalidateTransferEvidence(operation)
    },
  })

  const combinedJournalRows = buildJournalRows(
    accounts,
    journalSourceAccountId,
    sourceJournalQuery.data ?? [],
    journalDestinationAccountId,
    destinationJournalQuery.data ?? [],
  )
  const focusedJournalRows = firstResponse
    ? combinedJournalRows.filter((row) => row.transactionId === firstResponse.transactionId)
    : combinedJournalRows.slice(0, 6)
  const journalProofReady = firstResponse !== null && focusedJournalRows.length === 2
  const sourceAccount = findAccount(
    accounts,
    firstResponse?.sourceAccountId ?? Number(sourceAccountId),
  )
  const destinationAccount = findAccount(
    accounts,
    firstResponse?.destinationAccountId ?? Number(destinationAccountId),
  )
  const mismatches = reconciliationQuery.data ?? []
  const canRunTransfer = accounts.length >= 2 && !transferMutation.isPending
  const canReplay = firstOperation !== null && firstResponse !== null && !replayMutation.isPending
  const canProbeConflict = firstOperation !== null && !conflictMutation.isPending

  async function handleTransferSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const operation = buildOperation()
    if (operation === null) {
      return
    }

    setFormError(null)
    setLastError(null)
    setReplayCheck(null)
    setConflictCheck(null)

    try {
      const response = await transferMutation.mutateAsync(operation)
      setFirstOperation(operation)
      setFirstResponse(response)
    } catch (error) {
      setLastError(toError(error))
    }
  }

  async function handleReplay() {
    if (firstOperation === null || firstResponse === null) {
      return
    }

    setLastError(null)

    try {
      const response = await replayMutation.mutateAsync(firstOperation)
      setReplayCheck({
        response,
        identical: transferResponsesMatch(firstResponse, response),
      })
    } catch (error) {
      setLastError(toError(error))
    }
  }

  async function handleConflictProbe() {
    if (firstOperation === null) {
      return
    }

    const testedAmount = firstOperation.request.amount + 1
    const conflictOperation: TransferOperation = {
      ...firstOperation,
      request: {
        ...firstOperation.request,
        amount: testedAmount,
      },
    }

    setLastError(null)

    try {
      const response = await conflictMutation.mutateAsync(conflictOperation)
      setConflictCheck({ kind: 'unexpected-success', testedAmount, response })
    } catch (error) {
      const normalizedError = toError(error)
      const expected =
        isApiClientError(normalizedError) &&
        normalizedError.status === 409 &&
        normalizedError.code === 'IDEMPOTENCY_KEY_CONFLICT'

      setConflictCheck({
        kind: expected ? 'expected-conflict' : 'unexpected-error',
        testedAmount,
        error: normalizedError,
      })
    }
  }

  function handleNewScenario() {
    setForm((current) => ({
      ...current,
      idempotencyKey: createIdempotencyKey(),
    }))
    setFirstOperation(null)
    setFirstResponse(null)
    setReplayCheck(null)
    setConflictCheck(null)
    setFormError(null)
    setLastError(null)
    transferMutation.reset()
    replayMutation.reset()
    conflictMutation.reset()
  }

  function handleSourceChange(nextSourceId: string) {
    const fallbackDestinationId = String(
      accounts.find((account) => String(account.accountId) !== nextSourceId)?.accountId ?? '',
    )
    setForm((current) => ({
      ...current,
      sourceAccountId: nextSourceId,
      destinationAccountId:
        current.destinationAccountId === nextSourceId ? fallbackDestinationId : current.destinationAccountId,
    }))
  }

  function buildOperation(): TransferOperation | null {
    const sourceId = Number(sourceAccountId)
    const destinationId = Number(destinationAccountId)
    const amount = Number(form.amount)
    const callerScope = form.callerScope.trim()
    const idempotencyKey = form.idempotencyKey.trim()

    if (!Number.isInteger(sourceId) || sourceId <= 0) {
      setFormError('Select a source demo account.')
      return null
    }

    if (!Number.isInteger(destinationId) || destinationId <= 0) {
      setFormError('Select a destination demo account.')
      return null
    }

    if (sourceId === destinationId) {
      setFormError('Source and destination accounts must be different.')
      return null
    }

    if (!Number.isInteger(amount) || amount <= 0) {
      setFormError('Amount must be a positive integer.')
      return null
    }

    if (callerScope === '' || idempotencyKey === '') {
      setFormError('Caller scope and idempotency key are required headers.')
      return null
    }

    return {
      callerScope,
      idempotencyKey,
      request: {
        sourceAccountId: sourceId,
        destinationAccountId: destinationId,
        amount,
      },
    }
  }

  return (
    <main className="app-shell">
      <header className="hero-panel">
        <div>
          <p className="eyebrow">Backend verification console</p>
          <h1>BankCore Lab Console</h1>
          <p className="hero-copy">
            Run a real internal transfer, replay the same idempotency key, probe the intentional
            conflict path, and confirm the journal plus reconciliation evidence without leaving
            one screen.
          </p>
        </div>
        <div className="hero-status" aria-label="API base URL">
          <span>API route</span>
          <strong>{apiDisplayOrigin}</strong>
        </div>
      </header>

      <section className="lab-grid" aria-label="Transfer lab">
        <section className="panel accounts-panel">
          <PanelHeader eyebrow="Step 1" title="Demo accounts" endpoint="GET /api/v1/demo/accounts" />
          <QueryState
            isLoading={demoAccountsQuery.isLoading}
            error={demoAccountsQuery.error}
            empty={accounts.length === 0}
            emptyText="Start the backend with the demo profile to load Alice and Bob."
          >
            <div className="account-list">
              {accounts.map((account) => (
                <button
                  className={`account-card ${String(account.accountId) === sourceAccountId ? 'is-source' : ''} ${
                    String(account.accountId) === destinationAccountId ? 'is-destination' : ''
                  }`}
                  key={account.accountId}
                  type="button"
                  onClick={() => handleSourceChange(String(account.accountId))}
                >
                  <span className="account-name">{account.customerName}</span>
                  <span className="account-number">{account.accountNumber}</span>
                  <span className="account-balance">{formatMoney(account.balance)}</span>
                  <span className="account-role">
                    {String(account.accountId) === sourceAccountId ? 'Source' : ''}
                    {String(account.accountId) === destinationAccountId ? 'Destination' : ''}
                  </span>
                </button>
              ))}
            </div>
          </QueryState>
        </section>

        <section className="panel transfer-panel">
          <PanelHeader eyebrow="Step 2" title="Internal transfer" endpoint="POST /api/v1/transfers/internal" />
          <form className="transfer-form" onSubmit={handleTransferSubmit}>
            <label>
              <span>Source account</span>
              <select value={sourceAccountId} onChange={(event) => handleSourceChange(event.target.value)}>
                {accounts.map((account) => (
                  <option key={account.accountId} value={account.accountId}>
                    {account.customerName} / {account.accountNumber}
                  </option>
                ))}
              </select>
            </label>

            <label>
              <span>Destination account</span>
              <select
                value={destinationAccountId}
                onChange={(event) =>
                  setForm((current) => ({ ...current, destinationAccountId: event.target.value }))
                }
              >
                {accounts
                  .filter((account) => String(account.accountId) !== sourceAccountId)
                  .map((account) => (
                    <option key={account.accountId} value={account.accountId}>
                      {account.customerName} / {account.accountNumber}
                    </option>
                  ))}
              </select>
            </label>

            <label>
              <span>Amount</span>
              <input
                min="1"
                step="1"
                type="number"
                value={form.amount}
                onChange={(event) => setForm((current) => ({ ...current, amount: event.target.value }))}
              />
            </label>

            <label>
              <span>X-Caller-Scope</span>
              <input
                value={form.callerScope}
                onChange={(event) => setForm((current) => ({ ...current, callerScope: event.target.value }))}
              />
            </label>

            <label>
              <span>Idempotency-Key</span>
              <input
                value={form.idempotencyKey}
                onChange={(event) =>
                  setForm((current) => ({ ...current, idempotencyKey: event.target.value }))
                }
              />
            </label>

            <div className="button-row">
              <button className="primary-action" type="submit" disabled={!canRunTransfer}>
                {transferMutation.isPending ? 'Running transfer...' : 'Run transfer'}
              </button>
              <button className="secondary-action" type="button" onClick={handleNewScenario}>
                New key
              </button>
            </div>
          </form>
          <Notice error={lastError} message={formError} />
        </section>

        <section className="panel result-panel" aria-live="polite">
          <PanelHeader eyebrow="Step 3" title="Idempotency proof" endpoint="Replay and conflict checks" />
          {firstResponse === null ? (
            <div className="empty-state">Run a transfer to capture the first transaction response.</div>
          ) : (
            <div className="result-stack">
              <TransferResultCard
                response={firstResponse}
                sourceAccount={sourceAccount}
                destinationAccount={destinationAccount}
              />
              <div className="button-row">
                <button className="secondary-action" type="button" onClick={handleReplay} disabled={!canReplay}>
                  {replayMutation.isPending ? 'Replaying...' : 'Replay same request'}
                </button>
                <button
                  className="secondary-action"
                  type="button"
                  onClick={handleConflictProbe}
                  disabled={!canProbeConflict}
                >
                  {conflictMutation.isPending ? 'Probing...' : 'Same key, changed amount'}
                </button>
              </div>
              <ReplayStatus replayCheck={replayCheck} />
              <ConflictStatus conflictCheck={conflictCheck} />
            </div>
          )}
        </section>
      </section>

      <section className="evidence-grid" aria-label="Evidence">
        <section className="panel journal-panel">
          <PanelHeader
            eyebrow="Step 4"
            title="Journal entries"
            endpoint="GET /api/v1/accounts/{accountId}/journal-entries"
          />
          <div className={`proof-strip ${journalProofReady ? 'is-good' : ''}`}>
            {journalProofReady
              ? 'Two journal rows found for the captured transfer.'
              : 'After a transfer, this panel should show one debit-side row and one credit-side row.'}
          </div>
          <QueryState
            isLoading={sourceJournalQuery.isLoading || destinationJournalQuery.isLoading}
            error={sourceJournalQuery.error ?? destinationJournalQuery.error}
            empty={focusedJournalRows.length === 0}
            emptyText="No journal rows are loaded for the selected transfer yet."
          >
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Account</th>
                    <th>Movement</th>
                    <th>Amount</th>
                    <th>Balance after</th>
                    <th>Txn</th>
                    <th>Created</th>
                  </tr>
                </thead>
                <tbody>
                  {focusedJournalRows.map((row) => (
                    <tr key={`${row.accountId}-${row.entryId}`}>
                      <td>{row.accountLabel}</td>
                      <td>{row.movementType}</td>
                      <td className={row.movementType === 'BALANCE_INCREASE' ? 'positive' : 'negative'}>
                        {formatSignedMoney(row.movementType === 'BALANCE_INCREASE' ? row.amount : -row.amount)}
                      </td>
                      <td>{formatMoney(row.balanceAfter)}</td>
                      <td>#{row.transactionId} / {row.entryNo}</td>
                      <td>{formatDateTime(row.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </QueryState>
        </section>

        <section className="panel reconciliation-panel">
          <PanelHeader
            eyebrow="Step 5"
            title="Reconciliation"
            endpoint="GET /api/v1/reconciliation/account-balances/mismatches"
          />
          <QueryState
            isLoading={reconciliationQuery.isLoading}
            error={reconciliationQuery.error}
            empty={false}
            emptyText=""
          >
            {mismatches.length === 0 ? (
              <div className="reconciliation-ok">
                <span>No mismatches</span>
                <strong>Stored balances match journal-derived balances.</strong>
              </div>
            ) : (
              <div className="mismatch-list">
                {mismatches.map((mismatch) => (
                  <div className="mismatch-card" key={mismatch.accountId}>
                    <strong>Account #{mismatch.accountId}</strong>
                    <span>Stored {formatMoney(mismatch.storedBalance)}</span>
                    <span>Journal {formatMoney(mismatch.journalBalance)}</span>
                    <span>Diff {formatSignedMoney(mismatch.difference)}</span>
                  </div>
                ))}
              </div>
            )}
          </QueryState>
        </section>
      </section>

      <details className="panel evidence-summary" open>
        <summary>Evidence summary</summary>
        <div className="summary-grid">
          <EvidenceItem title="Atomic transfer" body="One API call updates both account balances and writes journal rows." />
          <EvidenceItem title="Idempotent replay" body="The same key and same body return the original transaction response." />
          <EvidenceItem title="Conflict guard" body="The same key with a changed amount should return HTTP 409." />
          <EvidenceItem title="Reconciliation" body="The mismatch endpoint should stay empty after normal traffic." />
        </div>
      </details>
    </main>
  )
}

function PanelHeader({
  eyebrow,
  title,
  endpoint,
}: {
  eyebrow: string
  title: string
  endpoint: string
}) {
  return (
    <div className="panel-header">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h2>{title}</h2>
      </div>
      <code>{endpoint}</code>
    </div>
  )
}

function QueryState({
  isLoading,
  error,
  empty,
  emptyText,
  children,
}: {
  isLoading: boolean
  error: Error | null
  empty: boolean
  emptyText: string
  children: ReactNode
}) {
  if (isLoading) {
    return <div className="loading-state">Loading live backend data...</div>
  }

  if (error !== null) {
    return <Notice error={error} />
  }

  if (empty) {
    return <div className="empty-state">{emptyText}</div>
  }

  return children
}

function Notice({ error, message }: { error?: Error | null; message?: string | null }) {
  if (message !== undefined && message !== null) {
    return <div className="notice is-warning">{message}</div>
  }

  if (error === undefined || error === null) {
    return null
  }

  return (
    <div className="notice is-danger">
      {isApiClientError(error)
        ? `${error.status} ${error.code}: ${error.message}`
        : `Request failed: ${error.message}`}
    </div>
  )
}

function TransferResultCard({
  response,
  sourceAccount,
  destinationAccount,
}: {
  response: TransferResponse
  sourceAccount?: DemoAccount
  destinationAccount?: DemoAccount
}) {
  return (
    <div className="transfer-result-card">
      <div>
        <span>Transaction</span>
        <strong>#{response.transactionId}</strong>
      </div>
      <div>
        <span>Transaction key</span>
        <strong>{response.transactionKey}</strong>
      </div>
      <div>
        <span>{sourceAccount?.customerName ?? `Account #${response.sourceAccountId}`} after</span>
        <strong>{formatMoney(response.sourceBalanceAfter)}</strong>
        <small>{formatSignedMoney(-response.amount)}</small>
      </div>
      <div>
        <span>{destinationAccount?.customerName ?? `Account #${response.destinationAccountId}`} after</span>
        <strong>{formatMoney(response.destinationBalanceAfter)}</strong>
        <small>{formatSignedMoney(response.amount)}</small>
      </div>
    </div>
  )
}

function ReplayStatus({ replayCheck }: { replayCheck: ReplayCheck | null }) {
  if (replayCheck === null) {
    return <div className="proof-strip">Replay has not run yet.</div>
  }

  return (
    <div className={`proof-strip ${replayCheck.identical ? 'is-good' : 'is-bad'}`}>
      {replayCheck.identical
        ? 'Replay response matches the original transfer response.'
        : 'Replay response differed from the original response.'}
    </div>
  )
}

function ConflictStatus({ conflictCheck }: { conflictCheck: ConflictCheck | null }) {
  if (conflictCheck === null) {
    return <div className="proof-strip">Conflict probe has not run yet.</div>
  }

  if (conflictCheck.kind === 'expected-conflict') {
    return (
      <div className="proof-strip is-good">
        Changed amount {formatMoney(conflictCheck.testedAmount)} returned the expected 409 conflict.
      </div>
    )
  }

  if (conflictCheck.kind === 'unexpected-success') {
    return (
      <div className="proof-strip is-bad">
        Changed amount {formatMoney(conflictCheck.testedAmount)} unexpectedly succeeded as transaction #
        {conflictCheck.response?.transactionId}.
      </div>
    )
  }

  return (
    <div className="proof-strip is-bad">
      Changed amount {formatMoney(conflictCheck.testedAmount)} failed unexpectedly:{' '}
      {conflictCheck.error?.message ?? 'Unknown error'}
    </div>
  )
}

function EvidenceItem({ title, body }: { title: string; body: string }) {
  return (
    <div className="evidence-item">
      <strong>{title}</strong>
      <span>{body}</span>
    </div>
  )
}

function buildJournalRows(
  accounts: DemoAccount[],
  sourceAccountId: number,
  sourceRows: AccountJournalEntry[],
  destinationAccountId: number,
  destinationRows: AccountJournalEntry[],
): JournalRow[] {
  return [
    ...sourceRows.map((row) => ({
      ...row,
      accountId: sourceAccountId,
      accountLabel: getAccountLabel(accounts, sourceAccountId),
    })),
    ...destinationRows.map((row) => ({
      ...row,
      accountId: destinationAccountId,
      accountLabel: getAccountLabel(accounts, destinationAccountId),
    })),
  ].sort((left, right) => {
    if (left.transactionId !== right.transactionId) {
      return right.transactionId - left.transactionId
    }

    return left.entryNo - right.entryNo
  })
}

function findAccount(accounts: DemoAccount[], accountId: number): DemoAccount | undefined {
  return accounts.find((account) => account.accountId === accountId)
}

function getAccountLabel(accounts: DemoAccount[], accountId: number): string {
  const account = findAccount(accounts, accountId)
  return account === undefined ? `Account #${accountId}` : `${account.customerName} / ${account.accountNumber}`
}

function createIdempotencyKey(): string {
  return `lab-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`
}

function transferResponsesMatch(left: TransferResponse, right: TransferResponse): boolean {
  return (
    left.transactionId === right.transactionId &&
    left.transactionKey === right.transactionKey &&
    left.sourceAccountId === right.sourceAccountId &&
    left.destinationAccountId === right.destinationAccountId &&
    left.sourceBalanceAfter === right.sourceBalanceAfter &&
    left.destinationBalanceAfter === right.destinationBalanceAfter &&
    left.amount === right.amount
  )
}

function toError(error: unknown): Error {
  return error instanceof Error ? error : new Error('Unknown error')
}

function formatMoney(value: number): string {
  return new Intl.NumberFormat('en-US').format(value)
}

function formatSignedMoney(value: number): string {
  const sign = value >= 0 ? '+' : '-'
  return `${sign}${formatMoney(Math.abs(value))}`
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat('en-US', {
    dateStyle: 'short',
    timeStyle: 'medium',
  }).format(new Date(value))
}

export default App
