import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'

const demoAccounts = [
  {
    accountId: 1,
    customerId: 1,
    customerName: 'Alice Demo',
    accountNumber: 'DEMO-ALICE-001',
    balance: 100_000,
  },
  {
    accountId: 2,
    customerId: 2,
    customerName: 'Bob Demo',
    accountNumber: 'DEMO-BOB-001',
    balance: 30_000,
  },
]

const transferResponse = {
  transactionId: 9001,
  transactionKey: 'tx-9001',
  sourceAccountId: 1,
  destinationAccountId: 2,
  sourceBalanceAfter: 90_000,
  destinationBalanceAfter: 40_000,
  amount: 10_000,
}

describe('BankCore Lab Console', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows the transfer, replay, conflict, journal, and reconciliation evidence flow', async () => {
    vi.stubGlobal('fetch', vi.fn(handleFetch))

    renderApp()

    expect(await screen.findByText('Alice Demo')).toBeTruthy()
    expect(await screen.findByText('Bob Demo')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: 'Run transfer' }))

    expect(await screen.findByText('#9001')).toBeTruthy()
    expect(await screen.findByText('Debit and credit journal rows match the captured transfer invariant.'))
      .toBeTruthy()
    expect(screen.getByText('Account balances and transaction journals both reconcile.')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: 'Replay same request' }))

    expect(await screen.findByText('Replay response matches the original transfer response.')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: 'Same key, changed amount' }))

    expect(await screen.findByText(/returned the expected 409 conflict/)).toBeTruthy()
    await waitFor(() => {
      expect(fetch).toHaveBeenCalledWith(
        '/api/v1/reconciliation/transaction-journals/mismatches',
        expect.any(Object),
      )
    })
  })
})

function renderApp() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  render(
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>,
  )
}

async function handleFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const url = String(input)

  if (url === '/api/v1/demo/accounts') {
    return jsonResponse(demoAccounts)
  }

  if (url === '/api/v1/reconciliation/account-balances/mismatches') {
    return jsonResponse([])
  }

  if (url === '/api/v1/reconciliation/transaction-journals/mismatches') {
    return jsonResponse([])
  }

  if (url === '/api/v1/accounts/1/journal-entries?limit=10') {
    return jsonResponse({
      items: [
        {
          entryId: 101,
          transactionId: transferResponse.transactionId,
          entryNo: 1,
          movementType: 'BALANCE_DECREASE',
          amount: transferResponse.amount,
          balanceAfter: transferResponse.sourceBalanceAfter,
          createdAt: '2026-09-06T00:00:00Z',
        },
      ],
      nextCursor: null,
      hasNext: false,
    })
  }

  if (url === '/api/v1/accounts/2/journal-entries?limit=10') {
    return jsonResponse({
      items: [
        {
          entryId: 102,
          transactionId: transferResponse.transactionId,
          entryNo: 2,
          movementType: 'BALANCE_INCREASE',
          amount: transferResponse.amount,
          balanceAfter: transferResponse.destinationBalanceAfter,
          createdAt: '2026-09-06T00:00:01Z',
        },
      ],
      nextCursor: null,
      hasNext: false,
    })
  }

  if (url === '/api/v1/transfers/internal' && init?.method === 'POST') {
    const body = JSON.parse(String(init.body)) as { amount: number }
    if (body.amount !== transferResponse.amount) {
      return jsonResponse(
        {
          code: 'IDEMPOTENCY_KEY_CONFLICT',
          message: 'Idempotency key was already used for a different request.',
        },
        { status: 409 },
      )
    }
    return jsonResponse(transferResponse)
  }

  return jsonResponse({ code: 'NOT_MOCKED', message: url }, { status: 500 })
}

function jsonResponse(body: unknown, init: ResponseInit = {}): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
    ...init,
  })
}
