package com.bankcore.controller;

import com.bankcore.domain.Account;
import com.bankcore.domain.Customer;
import com.bankcore.repository.AccountRepository;
import com.bankcore.repository.CustomerRepository;
import com.bankcore.service.ControlledFundingService;
import com.bankcore.support.MySqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ImportTestcontainers(MySqlContainerSupport.class)
class AccountJournalControllerIntegrationTest {

    private static final AtomicLong ACCOUNT_SEQUENCE = new AtomicLong(110_000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ControlledFundingService controlledFundingService;

    @Test
    void findRecentEntries_shouldReturnNewestEntriesWithKeysetPagination() throws Exception {
        Account account = createZeroBalanceAccount();
        controlledFundingService.seedFunds(account.getId(), 1_000L);
        controlledFundingService.seedFunds(account.getId(), 2_000L);
        controlledFundingService.seedFunds(account.getId(), 3_000L);

        String firstPage = mockMvc.perform(get("/api/v1/accounts/{accountId}/journal-entries", account.getId())
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(3_000))
                .andExpect(jsonPath("$[0].balanceAfter").value(6_000))
                .andExpect(jsonPath("$[1].amount").value(2_000))
                .andExpect(jsonPath("$[1].balanceAfter").value(3_000))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long beforeEntryId = extractSecondEntryId(firstPage);

        mockMvc.perform(get("/api/v1/accounts/{accountId}/journal-entries", account.getId())
                        .param("beforeEntryId", beforeEntryId.toString())
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(1_000))
                .andExpect(jsonPath("$[0].balanceAfter").value(1_000))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    void findRecentEntries_shouldRejectInvalidLimit() throws Exception {
        Account account = createZeroBalanceAccount();

        mockMvc.perform(get("/api/v1/accounts/{accountId}/journal-entries", account.getId())
                        .param("limit", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGE_REQUEST"));
    }

    @Test
    void findRecentEntries_shouldReturnNotFoundForMissingAccount() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/journal-entries", -1L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    private Account createZeroBalanceAccount() {
        long sequence = ACCOUNT_SEQUENCE.incrementAndGet();
        Customer customer = customerRepository.saveAndFlush(new Customer("Journal API Customer " + sequence));
        return accountRepository.saveAndFlush(new Account(customer, "110-000-" + sequence));
    }

    private static Long extractSecondEntryId(String json) {
        int markerIndex = json.indexOf("\"entryId\":", json.indexOf("\"entryId\":") + 1);
        assertThat(markerIndex).isNotNegative();
        int valueStart = markerIndex + "\"entryId\":".length();
        int valueEnd = json.indexOf(',', valueStart);
        return Long.valueOf(json.substring(valueStart, valueEnd));
    }
}
