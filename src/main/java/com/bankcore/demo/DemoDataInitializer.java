package com.bankcore.demo;

import com.bankcore.domain.Account;
import com.bankcore.domain.Customer;
import com.bankcore.repository.AccountRepository;
import com.bankcore.repository.CustomerRepository;
import com.bankcore.service.ControlledFundingService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
class DemoDataInitializer implements ApplicationRunner {

    static final String ALICE_ACCOUNT_NUMBER = "DEMO-ALICE-001";
    static final String BOB_ACCOUNT_NUMBER = "DEMO-BOB-001";

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final ControlledFundingService controlledFundingService;

    DemoDataInitializer(
            CustomerRepository customerRepository,
            AccountRepository accountRepository,
            ControlledFundingService controlledFundingService
    ) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.controlledFundingService = controlledFundingService;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureDemoAccount("Alice Demo", ALICE_ACCOUNT_NUMBER, 100_000L);
        ensureDemoAccount("Bob Demo", BOB_ACCOUNT_NUMBER, 30_000L);
    }

    private void ensureDemoAccount(String customerName, String accountNumber, long initialBalance) {
        if (accountRepository.findByAccountNumber(accountNumber).isPresent()) {
            return;
        }
        Customer customer = customerRepository.saveAndFlush(new Customer(customerName));
        Account account = accountRepository.saveAndFlush(new Account(customer, accountNumber));
        controlledFundingService.seedFunds(account.getId(), initialBalance);
    }
}
