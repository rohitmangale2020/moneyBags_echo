package com.training.platform.accounts.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.training.platform.accounts.client.ProductsClient;
import com.training.platform.accounts.client.FixedDepositsClient;
import com.training.platform.accounts.dto.AccountTransferRequest;
import com.training.platform.accounts.entity.Account;
import com.training.platform.accounts.entity.AccountStatus;
import com.training.platform.accounts.entity.OwnershipType;
import com.training.platform.accounts.repository.AccountBalanceOperationRepository;
import com.training.platform.accounts.repository.AccountHolderRepository;
import com.training.platform.accounts.repository.AccountRepository;
import com.training.platform.accounts.repository.AccountStatusHistoryRepository;
import com.training.platform.accounts.repository.AccountTransferOperationRepository;
import com.training.platform.auditclient.AuditClient;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AccountProductRulesTest {
    @Mock AccountRepository accounts;
    @Mock AccountHolderRepository holders;
    @Mock AccountStatusHistoryRepository histories;
    @Mock AccountTransferOperationRepository transfers;
    @Mock AccountBalanceOperationRepository adjustments;
    @Mock AuditClient audit;
    @Mock ProductsClient products;
    @Mock FixedDepositsClient fixedDeposits;
    AccountService service;

    @BeforeEach void setUp() {
        service = new AccountService(accounts, holders, histories, transfers, adjustments, audit, products,
                fixedDeposits);
    }

    @Test void currentAccountCannotBeDebitedBelowConfiguredMinimum() {
        Account debit = account("debit", "CURRENT", "6000.00", "5000.00");
        Account credit = account("credit", "SAVINGS", "100.00", "0.00");
        when(accounts.findAllByIdForUpdate(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(debit, credit));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.transfer(new AccountTransferRequest("MIN-BAL", "debit", "credit",
                        new BigDecimal("1500.00"), "INR", null)));

        assertEquals("6000.00", debit.getAvailableBalance().toPlainString());
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("minimum balance"));
    }

    @Test void fixedDepositCannotBeUsedInAnOrdinaryTransfer() {
        Account fd = account("fd", "FD", "10000.00", "5000.00");
        Account savings = account("savings", "SAVINGS", "0.00", "0.00");
        when(accounts.findAllByIdForUpdate(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(fd, savings));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.transfer(new AccountTransferRequest("FD-STANDARD", "fd", "savings",
                        new BigDecimal("10000.00"), "INR", null)));

        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("fixed-deposit workflow"));
    }

    private static Account account(String id, String productType, String balance,
                                   String minimum) {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "accountId", id);
        account.setAccountNumber("ACC-" + id);
        account.setCustomerId("customer-1");
        account.setProductId("1");
        account.setProductTypeCode(productType);
        account.setOwnershipType(OwnershipType.INDIVIDUAL);
        account.setStatus(AccountStatus.ACTIVE);
        account.setCurrencyCode("INR");
        account.setAvailableBalance(new BigDecimal(balance));
        account.setMinimumBalance(new BigDecimal(minimum));
        account.setMaximumBalance(null);
        account.setAnnualInterestRate(new BigDecimal("4.00"));
        account.setTenureMonths("FD".equals(productType) ? 12 : null);
        account.setMaturityInstruction("FD".equals(productType) ? "CREDIT_TO_ACCOUNT" : null);
        return account;
    }
}
