package com.bankcore.repository;

import com.bankcore.domain.Account;
import com.bankcore.domain.AccountStatus;
import com.bankcore.domain.Customer;
import com.bankcore.support.MySqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AccountRepositoryTest extends MySqlContainerSupport {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createAccount_shouldStartWithZeroBalance() {
        Customer customer = customerRepository.save(new Customer("Chanil Park"));

        Account account = accountRepository.saveAndFlush(new Account(customer, "100-000-000001"));

        assertThat(account.getId()).isNotNull();
        assertThat(account.getBalance()).isZero();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getVersion()).isZero();
    }

    @Test
    void findByAccountNumber_shouldReturnAccount() {
        Customer customer = customerRepository.save(new Customer("Chanil Park"));
        accountRepository.saveAndFlush(new Account(customer, "100-000-000002"));

        assertThat(accountRepository.findByAccountNumber("100-000-000002"))
                .isPresent()
                .get()
                .extracting(Account::getBalance)
                .isEqualTo(0L);
    }

    @Test
    void database_shouldRejectNegativeBalance() {
        Long customerId = customerRepository.saveAndFlush(new Customer("Chanil Park")).getId();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO account (customer_id, account_number, balance, status, version)
                VALUES (?, ?, ?, ?, ?)
                """, customerId, "100-000-000003", -1L, "ACTIVE", 0L))
                .hasMessageContaining("chk_account_balance_non_negative");
    }
}
