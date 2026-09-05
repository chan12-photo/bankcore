package com.bankcore.demo;

import com.bankcore.domain.Account;
import com.bankcore.domain.Customer;
import com.bankcore.repository.AccountRepository;
import com.bankcore.repository.CustomerRepository;
import com.bankcore.service.ControlledFundingService;
import com.bankcore.service.TransferService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile("demo")
class DemoDataInitializer implements ApplicationRunner {

    static final String ALICE_ACCOUNT_NUMBER = "DEMO-ALICE-001";
    static final String BOB_ACCOUNT_NUMBER = "DEMO-BOB-001";
    private static final long ALICE_STARTING_BALANCE = 100_000L;
    private static final long BOB_STARTING_BALANCE = 30_000L;
    private static final String DEMO_REBALANCE_SCOPE = "demo-data-initializer";

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final ControlledFundingService controlledFundingService;
    private final TransferService transferService;

    DemoDataInitializer(
            CustomerRepository customerRepository,
            AccountRepository accountRepository,
            ControlledFundingService controlledFundingService,
            TransferService transferService
    ) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.controlledFundingService = controlledFundingService;
        this.transferService = transferService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Account alice = ensureDemoAccount("Alice Demo", ALICE_ACCOUNT_NUMBER, ALICE_STARTING_BALANCE);
        Account bob = ensureDemoAccount("Bob Demo", BOB_ACCOUNT_NUMBER, BOB_STARTING_BALANCE);
        rebalanceDemoAccounts(alice, bob);
    }

    private Account ensureDemoAccount(String customerName, String accountNumber, long initialBalance) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseGet(() -> createDemoAccount(customerName, accountNumber, initialBalance));
    }

    private Account createDemoAccount(String customerName, String accountNumber, long initialBalance) {
        Customer customer = customerRepository.saveAndFlush(new Customer(customerName));
        Account account = accountRepository.saveAndFlush(new Account(customer, accountNumber));
        controlledFundingService.seedFunds(account.getId(), initialBalance);
        return accountRepository.findByAccountNumber(accountNumber).orElseThrow();
    }

    private void rebalanceDemoAccounts(Account alice, Account bob) {
        long aliceDelta = ALICE_STARTING_BALANCE - alice.getBalance();
        long bobDelta = BOB_STARTING_BALANCE - bob.getBalance();

        if (aliceDelta == 0L && bobDelta == 0L) {
            return;
        }

        if (aliceDelta > 0L && bobDelta < 0L) {
            long transferAmount = Math.min(aliceDelta, -bobDelta);
            transferDemoFunds(bob.getId(), alice.getId(), transferAmount);
            seedShortfall(alice.getId(), aliceDelta - transferAmount);
            return;
        }

        if (aliceDelta < 0L && bobDelta > 0L) {
            long transferAmount = Math.min(-aliceDelta, bobDelta);
            transferDemoFunds(alice.getId(), bob.getId(), transferAmount);
            seedShortfall(bob.getId(), bobDelta - transferAmount);
            return;
        }

        seedShortfall(alice.getId(), aliceDelta);
        seedShortfall(bob.getId(), bobDelta);
    }

    private void transferDemoFunds(Long sourceAccountId, Long destinationAccountId, long amount) {
        if (amount <= 0L) {
            return;
        }
        transferService.transferInternalIdempotent(
                DEMO_REBALANCE_SCOPE,
                "demo-rebalance-" + UUID.randomUUID(),
                sourceAccountId,
                destinationAccountId,
                amount
        );
    }

    private void seedShortfall(Long accountId, long amount) {
        if (amount > 0L) {
            controlledFundingService.seedFunds(accountId, amount);
        }
    }
}
