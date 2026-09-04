package com.bankcore.domain;

import com.bankcore.exception.AccountNotActiveException;
import com.bankcore.exception.AmountLimitExceededException;
import com.bankcore.exception.InsufficientBalanceException;
import com.bankcore.exception.InvalidAmountException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    @Test
    void newAccount_shouldStartWithZeroBalance() {
        Account account = new Account(new Customer("Chanil Park"), "100-000-000001");

        assertThat(account.getBalance()).isZero();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void deposit_shouldIncreaseBalance() {
        Account account = new Account(new Customer("Chanil Park"), "100-000-000002");

        account.deposit(15_000L);

        assertThat(account.getBalance()).isEqualTo(15_000L);
    }

    @Test
    void withdraw_shouldDecreaseBalance_whenBalanceIsEnough() {
        Account account = new Account(new Customer("Chanil Park"), "100-000-000003");
        account.deposit(10_000L);

        account.withdraw(4_000L);

        assertThat(account.getBalance()).isEqualTo(6_000L);
    }

    @Test
    void withdraw_shouldFail_whenBalanceIsInsufficient() {
        Account account = new Account(new Customer("Chanil Park"), "100-000-000004");
        account.deposit(5_000L);

        assertThatThrownBy(() -> account.withdraw(7_000L))
                .isInstanceOf(InsufficientBalanceException.class);
        assertThat(account.getBalance()).isEqualTo(5_000L);
    }

    @Test
    void moneyMovement_shouldFail_whenAmountIsNotPositive() {
        Account account = new Account(new Customer("Chanil Park"), "100-000-000005");

        assertThatThrownBy(() -> account.deposit(0L))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void moneyMovement_shouldFail_whenAmountExceedsLimit() {
        Account account = new Account(new Customer("Chanil Park"), "100-000-000006");

        assertThatThrownBy(() -> account.deposit(MoneyPolicy.MAX_AMOUNT + 1L))
                .isInstanceOf(AmountLimitExceededException.class);
    }

    @Test
    void frozenAccount_shouldRejectMoneyMovement() {
        Account account = new Account(new Customer("Chanil Park"), "100-000-000007");
        account.freeze();

        assertThatThrownBy(() -> account.deposit(1_000L))
                .isInstanceOf(AccountNotActiveException.class)
                .hasMessage("Account is frozen.");
    }

    @Test
    void closedAccount_shouldRejectMoneyMovement() {
        Account account = new Account(new Customer("Chanil Park"), "100-000-000008");
        account.close();

        assertThatThrownBy(() -> account.withdraw(1_000L))
                .isInstanceOf(AccountNotActiveException.class)
                .hasMessage("Account is closed.");
    }
}
