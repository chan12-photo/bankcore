const API_BASE_URL = normalizeBaseUrl(import.meta.env.VITE_BANKCORE_API_BASE_URL)

export const apiDisplayOrigin =
  API_BASE_URL === '' ? 'Vite dev proxy: /api -> http://localhost:8080' : API_BASE_URL

export type DemoAccount = {
  accountId: number
  customerId: number
  customerName: string
  accountNumber: string
  balance: number
}

export type InternalTransferRequest = {
  sourceAccountId: number
  destinationAccountId: number
  amount: number
}

export type TransferOperation = {
  callerScope: string
  idempotencyKey: string
  request: InternalTransferRequest
}

export type TransferResponse = {
  transactionId: number
  transactionKey: string
  sourceAccountId: number
  destinationAccountId: number
  sourceBalanceAfter: number
  destinationBalanceAfter: number
  amount: number
}

export type JournalMovementType = 'BALANCE_DECREASE' | 'BALANCE_INCREASE'

export type AccountJournalEntry = {
  entryId: number
  transactionId: number
  entryNo: number
  movementType: JournalMovementType
  amount: number
  balanceAfter: number
  createdAt: string
}

export type AccountBalanceMismatch = {
  accountId: number
  storedBalance: number
  journalBalance: number
  difference: number
}

export type TransactionJournalMismatch = {
  transactionId: number
  transactionType: string
  transactionAmount: number
  issueCodes: string[]
  journalEntryCount: number
  decreaseEntryCount: number
  increaseEntryCount: number
  distinctAccountCount: number
  journalAmountMismatchCount: number
  signedJournalAmount: number
}

export type ApiErrorResponse = {
  code: string
  message: string
}

export class ApiClientError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'ApiClientError'
    this.status = status
    this.code = code
  }
}

export function isApiClientError(error: unknown): error is ApiClientError {
  return error instanceof ApiClientError
}

export async function fetchDemoAccounts(): Promise<DemoAccount[]> {
  return requestJson('/api/v1/demo/accounts')
}

export async function postInternalTransfer(
  operation: TransferOperation,
): Promise<TransferResponse> {
  return requestJson('/api/v1/transfers/internal', {
    method: 'POST',
    headers: {
      'Idempotency-Key': operation.idempotencyKey,
      'X-Caller-Scope': operation.callerScope,
    },
    body: operation.request,
  })
}

export async function fetchJournalEntries(
  accountId: number,
  limit = 10,
): Promise<AccountJournalEntry[]> {
  return requestJson(`/api/v1/accounts/${accountId}/journal-entries?limit=${limit}`)
}

export async function fetchReconciliationMismatches(): Promise<AccountBalanceMismatch[]> {
  return requestJson('/api/v1/reconciliation/account-balances/mismatches')
}

export async function fetchTransactionJournalMismatches(): Promise<TransactionJournalMismatch[]> {
  return requestJson('/api/v1/reconciliation/transaction-journals/mismatches')
}

type JsonRequestInit = Omit<RequestInit, 'body'> & {
  body?: unknown
}

async function requestJson<T>(path: string, init: JsonRequestInit = {}): Promise<T> {
  const { body, headers: initHeaders, ...rest } = init
  const headers = new Headers(initHeaders)

  if (body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...rest,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const bodyResult = await readResponseBody(response)

  if (!response.ok) {
    if (isApiErrorResponse(bodyResult.payload)) {
      throw new ApiClientError(response.status, bodyResult.payload.code, bodyResult.payload.message)
    }

    throw new ApiClientError(
      response.status,
      'HTTP_ERROR',
      response.statusText || summarizeRawBody(bodyResult.rawText) || `Request failed with HTTP ${response.status}`,
    )
  }

  if (!bodyResult.parsed) {
    throw new ApiClientError(response.status, 'INVALID_JSON_RESPONSE', 'Expected a JSON response from BankCore.')
  }

  return bodyResult.payload as T
}

async function readResponseBody(
  response: Response,
): Promise<{ parsed: boolean; payload: unknown; rawText: string }> {
  const text = await response.text()
  if (text === '') {
    return { parsed: true, payload: null, rawText: text }
  }

  try {
    return { parsed: true, payload: JSON.parse(text) as unknown, rawText: text }
  } catch {
    return { parsed: false, payload: null, rawText: text }
  }
}

function isApiErrorResponse(payload: unknown): payload is ApiErrorResponse {
  return (
    typeof payload === 'object' &&
    payload !== null &&
    'code' in payload &&
    'message' in payload &&
    typeof payload.code === 'string' &&
    typeof payload.message === 'string'
  )
}

function normalizeBaseUrl(value: string | undefined): string {
  return value?.trim().replace(/\/+$/, '') ?? ''
}

function summarizeRawBody(text: string): string {
  return text.trim().replace(/\s+/g, ' ').slice(0, 160)
}
