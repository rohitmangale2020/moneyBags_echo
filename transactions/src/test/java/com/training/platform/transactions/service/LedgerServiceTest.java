package com.training.platform.transactions.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.training.platform.transactions.dto.LedgerPostingRequest;
import com.training.platform.transactions.entity.BankTransaction;
import com.training.platform.transactions.entity.LedgerAccount;
import com.training.platform.transactions.entity.LedgerAccountType;
import com.training.platform.transactions.entity.LedgerEntry;
import com.training.platform.transactions.entity.LedgerEntryType;
import com.training.platform.transactions.entity.TransactionType;
import com.training.platform.transactions.repository.LedgerAccountRepository;
import com.training.platform.transactions.repository.LedgerEntryRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {
    @Mock private LedgerAccountRepository accountRepository;
    @Mock private LedgerEntryRepository entryRepository;
    private LedgerService ledgerService;

    @BeforeEach
    void setUp() {
        ledgerService = new LedgerService(accountRepository, entryRepository);
        org.mockito.Mockito.lenient().when(entryRepository.saveAll(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void postsBalancedEntriesAndUpdatesTheAffectedBalances() {
        LedgerAccount cash = account(LedgerService.CASH_ON_HAND, LedgerAccountType.ASSET);
        LedgerAccount deposits = account(LedgerService.CUSTOMER_DEPOSITS, LedgerAccountType.LIABILITY);
        accounts(cash, deposits);
        LedgerPostingRequest request = new LedgerPostingRequest("TXN-1", null, "inr", "Cash deposit", List.of(
                new LedgerPostingRequest.Item(LedgerService.CASH_ON_HAND, null, LedgerEntryType.DEBIT,
                        new BigDecimal("100.00"), null),
                new LedgerPostingRequest.Item(LedgerService.CUSTOMER_DEPOSITS, "account-1", LedgerEntryType.CREDIT,
                        new BigDecimal("100.00"), null)));

        List<LedgerEntry> entries = ledgerService.post(request);

        assertEquals(2, entries.size());
        assertEquals(new BigDecimal("100.00"), cash.getCurrentBalance());
        assertEquals(new BigDecimal("100.00"), deposits.getCurrentBalance());
    }

    @Test
    void rejectsAnUnbalancedManualPosting() {
        LedgerPostingRequest request = new LedgerPostingRequest("TXN-2", null, "INR", null, List.of(
                new LedgerPostingRequest.Item("CASH_ON_HAND", null, LedgerEntryType.DEBIT, new BigDecimal("100"), null),
                new LedgerPostingRequest.Item("CUSTOMER_DEPOSITS", null, LedgerEntryType.CREDIT, new BigDecimal("90"), null)));

        assertThrows(IllegalArgumentException.class, () -> ledgerService.post(request));
    }

    @Test
    void createsFourNetZeroInternalClearingEntriesForTransfers() {
        LedgerAccount deposits = account(LedgerService.CUSTOMER_DEPOSITS, LedgerAccountType.LIABILITY);
        LedgerAccount clearing = account(LedgerService.INTERNAL_CLEARING, LedgerAccountType.LIABILITY);
        accounts(deposits, clearing);
        BankTransaction transaction = new BankTransaction();
        transaction.setTransactionRef("TXN-3");
        transaction.setTransactionType(TransactionType.TRANSFER);
        transaction.setDebitAccountId("account-a");
        transaction.setCreditAccountId("account-b");
        transaction.setAmount(new BigDecimal("50.00"));
        transaction.setCurrencyCode("INR");

        List<LedgerEntry> entries = ledgerService.postCompletedTransaction(transaction);

        assertEquals(4, entries.size());
        assertEquals(0, clearing.getCurrentBalance().compareTo(BigDecimal.ZERO));
        assertEquals(0, deposits.getCurrentBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    void openingDepositUsesTheSameCashAndCustomerLiabilityPostingAsADeposit() {
        LedgerAccount cash = account(LedgerService.CASH_ON_HAND, LedgerAccountType.ASSET);
        LedgerAccount deposits = account(LedgerService.CUSTOMER_DEPOSITS, LedgerAccountType.LIABILITY);
        accounts(cash, deposits);
        BankTransaction transaction = new BankTransaction();
        transaction.setTransactionRef("OPEN-account1");
        transaction.setTransactionType(TransactionType.OPENING_DEPOSIT);
        transaction.setCreditAccountId("account-1");
        transaction.setAmount(new BigDecimal("10000.00"));
        transaction.setCurrencyCode("INR");

        List<LedgerEntry> entries = ledgerService.postCompletedTransaction(transaction);

        assertEquals(2, entries.size());
        assertEquals(LedgerEntryType.DEBIT, entries.get(0).getEntryType());
        assertEquals(LedgerEntryType.CREDIT, entries.get(1).getEntryType());
        assertEquals("account-1", entries.get(1).getCustomerAccountId());
        assertEquals(0, cash.getCurrentBalance().compareTo(new BigDecimal("10000.00")));
        assertEquals(0, deposits.getCurrentBalance().compareTo(new BigDecimal("10000.00")));
    }

    @Test
    void postsAnnualFeeFromCustomerDepositLiabilityToFeeIncome() {
        LedgerAccount deposits = account(LedgerService.CUSTOMER_DEPOSITS, LedgerAccountType.LIABILITY);
        LedgerAccount feeIncome = account(LedgerService.FEE_INCOME, LedgerAccountType.INCOME);
        accounts(deposits, feeIncome);
        BankTransaction transaction = new BankTransaction();
        transaction.setTransactionRef("AF2026-account1");
        transaction.setTransactionType(TransactionType.ANNUAL_MAINTENANCE_FEE);
        transaction.setDebitAccountId("account-1");
        transaction.setAmount(new BigDecimal("250.00"));
        transaction.setCurrencyCode("INR");
        transaction.setDescription("Annual maintenance fee charged to TEST CUSTOMER for the 2026 account anniversary");

        List<LedgerEntry> entries = ledgerService.postCompletedTransaction(transaction);

        assertEquals(2, entries.size());
        assertEquals(LedgerEntryType.DEBIT, entries.get(0).getEntryType());
        assertEquals("account-1", entries.get(0).getCustomerAccountId());
        assertEquals(LedgerEntryType.CREDIT, entries.get(1).getEntryType());
        assertEquals("Customer maintenance fee debited: Annual maintenance fee charged to TEST CUSTOMER"
                        + " for the 2026 account anniversary",
                entries.get(0).getDescription());
        assertEquals("Bank maintenance fee income credited: Annual maintenance fee charged to TEST CUSTOMER"
                        + " for the 2026 account anniversary",
                entries.get(1).getDescription());
        assertEquals(0, deposits.getCurrentBalance().compareTo(new BigDecimal("-250.00")));
        assertEquals(0, feeIncome.getCurrentBalance().compareTo(new BigDecimal("250.00")));
    }

    @Test
    void interestLedgerLinesExplainTheBankExpenseAndCustomerCredit() {
        LedgerAccount expense = account(LedgerService.INTEREST_EXPENSE, LedgerAccountType.EXPENSE);
        LedgerAccount deposits = account(LedgerService.CUSTOMER_DEPOSITS, LedgerAccountType.LIABILITY);
        accounts(expense, deposits);
        BankTransaction transaction = new BankTransaction();
        transaction.setTransactionRef("SI2608-account1");
        transaction.setTransactionType(TransactionType.INTEREST_CREDIT);
        transaction.setCreditAccountId("account-1");
        transaction.setAmount(new BigDecimal("40.00"));
        transaction.setCurrencyCode("INR");
        transaction.setDescription("Savings interest credited to TEST CUSTOMER for period ending 2026-08-31");

        List<LedgerEntry> entries = ledgerService.postCompletedTransaction(transaction);

        assertEquals("Bank deposit interest expense debited: Savings interest credited to TEST CUSTOMER"
                        + " for period ending 2026-08-31",
                entries.get(0).getDescription());
        assertEquals("Customer deposit liability credited with interest: Savings interest credited to TEST CUSTOMER"
                        + " for period ending 2026-08-31",
                entries.get(1).getDescription());
    }

    private void accounts(LedgerAccount... accounts) {
        for (LedgerAccount account : accounts) {
            when(accountRepository.findByCode(account.getCode())).thenReturn(Optional.of(account));
        }
    }

    private static LedgerAccount account(String code, LedgerAccountType type) {
        LedgerAccount account = new LedgerAccount();
        account.setCode(code);
        account.setName(code);
        account.setAccountType(type);
        return account;
    }
}
