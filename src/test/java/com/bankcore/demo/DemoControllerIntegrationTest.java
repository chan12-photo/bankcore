package com.bankcore.demo;

import com.bankcore.domain.Account;
import com.bankcore.repository.AccountRepository;
import com.bankcore.service.TransferService;
import com.bankcore.support.MySqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@ImportTestcontainers(MySqlContainerSupport.class)
class DemoControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DemoDataInitializer demoDataInitializer;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferService transferService;

    @Test
    void findDemoAccounts_shouldReturnSeededAliceAndBobAccounts() throws Exception {
        mockMvc.perform(get("/api/v1/demo/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.customerName == 'Alice Demo' && @.balance == 100000)]").exists())
                .andExpect(jsonPath("$[?(@.customerName == 'Bob Demo' && @.balance == 30000)]").exists());
    }

    @Test
    void run_shouldRebalanceExistingDemoAccountsToStartingBalances() {
        Account alice = findDemoAccount(DemoDataInitializer.ALICE_ACCOUNT_NUMBER);
        Account bob = findDemoAccount(DemoDataInitializer.BOB_ACCOUNT_NUMBER);

        transferService.transferInternalIdempotent(
                "demo-test",
                "drift-" + UUID.randomUUID(),
                alice.getId(),
                bob.getId(),
                1_500L
        );

        assertThat(findDemoAccount(DemoDataInitializer.ALICE_ACCOUNT_NUMBER).getBalance()).isEqualTo(98_500L);
        assertThat(findDemoAccount(DemoDataInitializer.BOB_ACCOUNT_NUMBER).getBalance()).isEqualTo(31_500L);

        demoDataInitializer.run(new DefaultApplicationArguments());

        assertThat(findDemoAccount(DemoDataInitializer.ALICE_ACCOUNT_NUMBER).getBalance()).isEqualTo(100_000L);
        assertThat(findDemoAccount(DemoDataInitializer.BOB_ACCOUNT_NUMBER).getBalance()).isEqualTo(30_000L);
    }

    private Account findDemoAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).orElseThrow();
    }
}
