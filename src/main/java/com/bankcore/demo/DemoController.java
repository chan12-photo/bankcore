package com.bankcore.demo;

import com.bankcore.repository.AccountRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Profile("demo")
@RequestMapping("/api/v1/demo")
public class DemoController {

    private final AccountRepository accountRepository;

    public DemoController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @GetMapping("/accounts")
    public List<DemoAccountResponse> findDemoAccounts() {
        return List.of(
                findDemoAccount(DemoDataInitializer.ALICE_ACCOUNT_NUMBER),
                findDemoAccount(DemoDataInitializer.BOB_ACCOUNT_NUMBER)
        );
    }

    private DemoAccountResponse findDemoAccount(String accountNumber) {
        return accountRepository.findByAccountNumberWithCustomer(accountNumber)
                .map(account -> new DemoAccountResponse(
                        account.getId(),
                        account.getCustomer().getId(),
                        account.getCustomer().getName(),
                        account.getAccountNumber(),
                        account.getBalance()
                ))
                .orElseThrow();
    }
}
