package com.training.platform.accounts.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.training.platform.accounts.client.ProductsClient;
import com.training.platform.accounts.client.FixedDepositsClient;
import com.training.platform.accounts.dto.AccountAdjustmentRequest;
import com.training.platform.accounts.dto.AccountTransferRequest;
import com.training.platform.accounts.dto.AccountTransferResponse;
import com.training.platform.accounts.dto.TransferPurpose;
import com.training.platform.accounts.entity.Account;
import com.training.platform.accounts.entity.AccountStatus;
import com.training.platform.accounts.entity.AccountTransferOperation;
import com.training.platform.accounts.entity.OwnershipType;
import com.training.platform.accounts.repository.AccountBalanceOperationRepository;
import com.training.platform.accounts.repository.AccountHolderRepository;
import com.training.platform.accounts.repository.AccountRepository;
import com.training.platform.accounts.repository.AccountStatusHistoryRepository;
import com.training.platform.accounts.repository.AccountTransferOperationRepository;
import com.training.platform.auditclient.AuditClient;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Verifies fixed-deposit balance movements against an in-memory H2 database.
 * Every test is rolled back by {@link DataJpaTest}; Oracle is never contacted.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:fixed-deposit-funding;MODE=Oracle;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false"
})
class FixedDepositFundingH2IntegrationTest {

    @Autowired private AccountRepository accountRepository;
    @Autowired private AccountHolderRepository accountHolderRepository;
    @Autowired private AccountStatusHistoryRepository accountStatusHistoryRepository;
    @Autowired private AccountTransferOperationRepository transferOperationRepository;
    @Autowired private AccountBalanceOperationRepository balanceOperationRepository;
    @Autowired private EntityManager entityManager;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, accountHolderRepository,
                accountStatusHistoryRepository, transferOperationRepository, balanceOperationRepository,
                mock(AuditClient.class), mock(ProductsClient.class), mock(FixedDepositsClient.class));
    }

    @Test
    void fundingMovesPrincipalFromCustomerAccountIntoFixedDeposit() {
        accountRepository.saveAndFlush(transactionalAccount("source-1", "150000.00", "5000.00"));
        accountRepository.saveAndFlush(fixedDepositAccount("fd-1"));

        AccountTransferResponse response = accountService.transfer(fundingRequest("FD-FUND-H2-001"));
        entityManager.flush();
        entityManager.clear();

        assertMoney("50000.00", accountRepository.findById("source-1").orElseThrow().getAvailableBalance());
        assertMoney("100000.00", accountRepository.findById("fd-1").orElseThrow().getAvailableBalance());
        assertMoney("50000.00", response.debitBalanceAfter());
        assertMoney("100000.00", response.creditBalanceAfter());

        AccountTransferOperation operation = transferOperationRepository
                .findByTransactionRef("FD-FUND-H2-001").orElseThrow();
        assertEquals(TransferPurpose.FIXED_DEPOSIT_FUNDING, operation.getTransferPurpose());
    }

    @Test
    void openingDepositMovesAZeroBalanceAccountThroughAnIdempotentBalanceOperation() {
        accountRepository.saveAndFlush(transactionalAccount("source-1", "0.00", "5000.00"));

        accountService.adjust("source-1", new AccountAdjustmentRequest("OPEN-source1",
                AccountAdjustmentRequest.AdjustmentType.OPENING_DEPOSIT,
                new BigDecimal("10000.00"), "INR"));
        accountService.adjust("source-1", new AccountAdjustmentRequest("OPEN-source1",
                AccountAdjustmentRequest.AdjustmentType.OPENING_DEPOSIT,
                new BigDecimal("10000.00"), "INR"));
        entityManager.flush();
        entityManager.clear();

        assertMoney("10000.00", accountRepository.findById("source-1").orElseThrow().getAvailableBalance());
        assertEquals(1L, balanceOperationRepository.count());
    }

    @Test
    void savingsInterestCreditsOnlyTheSelectedSavingsAccount() {
        Account interestAccount = transactionalAccount("savings-interest-1", "100000.00", "5000.00");
        Account unrelatedAccount = transactionalAccount("unrelated-1", "25000.00", "5000.00");
        accountRepository.saveAndFlush(interestAccount);
        accountRepository.saveAndFlush(unrelatedAccount);

        LocalDate periodEnd = LocalDate.of(2026, 8, 31);
        accountService.adjust("savings-interest-1", new AccountAdjustmentRequest(
                "SI2608-savings-interest-1",
                AccountAdjustmentRequest.AdjustmentType.INTEREST_CREDIT,
                new BigDecimal("339.73"), "INR", periodEnd));
        entityManager.flush();
        entityManager.clear();

        Account credited = accountRepository.findById("savings-interest-1").orElseThrow();
        Account untouched = accountRepository.findById("unrelated-1").orElseThrow();
        assertMoney("100339.73", credited.getAvailableBalance());
        assertMoney("25000.00", untouched.getAvailableBalance());
        assertEquals(periodEnd, credited.getInterestAccruedThrough());
        assertEquals(LocalDate.of(2026, 9, 30), credited.getNextInterestPayoutDate());
        assertEquals("savings-interest-1", balanceOperationRepository
                .findByTransactionRef("SI2608-savings-interest-1").orElseThrow().getAccountId());
    }

    @Test
    void retryWithSameReferenceDoesNotMovePrincipalTwice() {
        accountRepository.saveAndFlush(transactionalAccount("source-1", "150000.00", "5000.00"));
        accountRepository.saveAndFlush(fixedDepositAccount("fd-1"));

        accountService.transfer(fundingRequest("FD-FUND-H2-RETRY"));
        AccountTransferResponse replay = accountService.transfer(fundingRequest("FD-FUND-H2-RETRY"));
        entityManager.flush();
        entityManager.clear();

        assertMoney("50000.00", accountRepository.findById("source-1").orElseThrow().getAvailableBalance());
        assertMoney("100000.00", accountRepository.findById("fd-1").orElseThrow().getAvailableBalance());
        assertMoney("50000.00", replay.debitBalanceAfter());
        assertEquals(1L, transferOperationRepository.count());
    }

    @Test
    void rejectedFundingLeavesBothPersistedBalancesUnchanged() {
        accountRepository.saveAndFlush(transactionalAccount("source-1", "102000.00", "5000.00"));
        accountRepository.saveAndFlush(fixedDepositAccount("fd-1"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> accountService.transfer(fundingRequest("FD-FUND-H2-REJECTED")));
        entityManager.clear();

        assertTrue(error.getMessage().contains("below its minimum balance"));
        assertMoney("102000.00", accountRepository.findById("source-1").orElseThrow().getAvailableBalance());
        assertMoney("0.00", accountRepository.findById("fd-1").orElseThrow().getAvailableBalance());
        assertEquals(0L, transferOperationRepository.count());
    }

    @Test
    void maturityCreditsPrincipalAndInterestIntoCustomerPayoutAccount() {
        Account payout = transactionalAccount("payout-1", "10000.00", "5000.00");
        Account fixedDeposit = fixedDepositAccount("fd-1");
        fixedDeposit.setAvailableBalance(new BigDecimal("100000.00"));
        accountRepository.saveAndFlush(payout);
        accountRepository.saveAndFlush(fixedDeposit);

        accountService.transfer(new AccountTransferRequest("FD-MATURITY-H2-001", "fd-1", "payout-1",
                new BigDecimal("100000.00"), "INR", "customer-1",
                TransferPurpose.FIXED_DEPOSIT_MATURITY));
        accountService.adjust("payout-1", new AccountAdjustmentRequest("FD-INTEREST-H2-001",
                AccountAdjustmentRequest.AdjustmentType.FIXED_DEPOSIT_INTEREST_CREDIT,
                new BigDecimal("6500.00"), "INR"));
        entityManager.flush();
        entityManager.clear();

        Account maturedDeposit = accountRepository.findById("fd-1").orElseThrow();
        Account customerPayout = accountRepository.findById("payout-1").orElseThrow();
        assertMoney("0.00", maturedDeposit.getAvailableBalance());
        assertEquals(AccountStatus.CLOSED, maturedDeposit.getStatus());
        // Existing 10,000 + principal 100,000 + interest 6,500.
        assertMoney("116500.00", customerPayout.getAvailableBalance());
        assertMoney("6500.00", balanceOperationRepository
                .findByTransactionRef("FD-INTEREST-H2-001").orElseThrow().getAmount());
    }

    @Test
    void contractualFdPayoutHasNoMaximumBalanceLimit() {
        Account payout = transactionalAccount("payout-1", "990000.00", "5000.00");
        Account fixedDeposit = fixedDepositAccount("fd-1");
        fixedDeposit.setAvailableBalance(new BigDecimal("100000.00"));
        accountRepository.saveAndFlush(payout);
        accountRepository.saveAndFlush(fixedDeposit);

        accountService.adjust("payout-1", new AccountAdjustmentRequest("FD-INTEREST-H2-NO-MAX",
                AccountAdjustmentRequest.AdjustmentType.FIXED_DEPOSIT_INTEREST_CREDIT,
                new BigDecimal("6500.00"), "INR"));
        accountService.transfer(new AccountTransferRequest("FD-MATURITY-H2-NO-MAX", "fd-1", "payout-1",
                new BigDecimal("100000.00"), "INR", "customer-1",
                TransferPurpose.FIXED_DEPOSIT_MATURITY));
        entityManager.flush();
        entityManager.clear();

        Account maturedDeposit = accountRepository.findById("fd-1").orElseThrow();
        Account customerPayout = accountRepository.findById("payout-1").orElseThrow();
        assertMoney("0.00", maturedDeposit.getAvailableBalance());
        assertEquals(AccountStatus.CLOSED, maturedDeposit.getStatus());
        assertMoney("1096500.00", customerPayout.getAvailableBalance());
    }

    @Test
    void annualMaintenanceFeeIsDebitedEvenWhenItMovesBalanceBelowProductMinimum() {
        accountRepository.saveAndFlush(transactionalAccount("source-1", "6000.00", "5000.00"));

        accountService.adjust("source-1", new AccountAdjustmentRequest("AF2026-source1",
                AccountAdjustmentRequest.AdjustmentType.ANNUAL_MAINTENANCE_FEE,
                new BigDecimal("1500.00"), "INR"));
        entityManager.flush();
        entityManager.clear();

        assertMoney("4500.00", accountRepository.findById("source-1").orElseThrow().getAvailableBalance());
        assertEquals(AccountAdjustmentRequest.AdjustmentType.ANNUAL_MAINTENANCE_FEE,
                balanceOperationRepository.findByTransactionRef("AF2026-source1")
                        .orElseThrow().getAdjustmentType());
    }

    private static AccountTransferRequest fundingRequest(String reference) {
        return new AccountTransferRequest(reference, "source-1", "fd-1",
                new BigDecimal("100000.00"), "INR", "customer-1",
                TransferPurpose.FIXED_DEPOSIT_FUNDING);
    }

    private static Account transactionalAccount(String id, String balance, String minimum) {
        Account account = baseAccount(id, "SAVINGS", balance);
        account.setMinimumBalance(new BigDecimal(minimum));
        account.setMaximumBalance(null);
        account.setAnnualInterestRate(new BigDecimal("4.00"));
        return account;
    }

    private static Account fixedDepositAccount(String id) {
        Account account = baseAccount(id, "FD", "0.00");
        account.setMinimumBalance(new BigDecimal("100000.00"));
        account.setMaximumBalance(null);
        account.setAnnualInterestRate(new BigDecimal("6.50"));
        account.setTenureMonths(12);
        account.setLockInPeriodMonths(3);
        account.setMaturityInstruction("CREDIT_TO_ACCOUNT");
        account.setPrematureWithdrawalAllowed(true);
        return account;
    }

    private static Account baseAccount(String id, String productType, String balance) {
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
        return account;
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
