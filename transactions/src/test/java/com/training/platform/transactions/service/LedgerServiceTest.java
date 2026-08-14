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
        when(entryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
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
