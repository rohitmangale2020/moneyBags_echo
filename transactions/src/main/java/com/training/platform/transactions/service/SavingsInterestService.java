package com.training.platform.transactions.service;

import com.training.platform.transactions.client.AccountsClient;
import com.training.platform.transactions.client.InterestBearingAccountResponse;
import com.training.platform.transactions.entity.BankTransaction;
import com.training.platform.transactions.entity.TransactionStatus;
import com.training.platform.transactions.entity.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates monthly bank transactions for savings/salary interest. */
@Service
public class SavingsInterestService {
    private static final Logger log = LoggerFactory.getLogger(SavingsInterestService.class);
    private static final BigDecimal DAYS_IN_YEAR = new BigDecimal("365");
    private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("yyMM");

    private final AccountsClient accountsClient;
    private final BankTransactionService transactionService;

    public SavingsInterestService(AccountsClient accountsClient,
                                  BankTransactionService transactionService) {
        this.accountsClient = accountsClient;
        this.transactionService = transactionService;
    }

    public int processDue(LocalDate asOf) {
        List<InterestBearingAccountResponse> due = accountsClient.interestDue(asOf);
        int processed = 0;
        for (InterestBearingAccountResponse account : due) {
            try {
                LocalDate periodEnd = account.payoutDueDate();
                String reference = reference(account.accountId(), periodEnd);
                BigDecimal interest = interest(account.balance(), account.annualInterestRate(),
                        account.accruedThrough(), periodEnd);
                if (interest.signum() == 0) {
                    accountsClient.markInterestProcessed(account.accountId(), periodEnd, reference);
                    processed++;
                    continue;
                }
                BankTransaction transaction = new BankTransaction();
                transaction.setTransactionRef(reference);
                transaction.setTransactionType(TransactionType.INTEREST_CREDIT);
                transaction.setTransactionStatus(TransactionStatus.INITIATED);
                transaction.setCreditAccountId(account.accountId());
                transaction.setAmount(interest);
                transaction.setCurrencyCode(account.currencyCode());
                transaction.setFeeAmount(BigDecimal.ZERO);
                transaction.setInitiatedByCustomerId(account.customerId());
                transaction.setInitiatedByUserId("SYSTEM");
                transaction.setInterestPeriodEnd(periodEnd);
                if (transactionService.initiate(transaction).getTransactionStatus() == TransactionStatus.COMPLETED) {
                    processed++;
                }
            } catch (RuntimeException exception) {
                log.error("Failed to process savings interest for account {}", account.accountId(), exception);
            }
        }
        return processed;
    }

    static BigDecimal interest(BigDecimal balance, BigDecimal annualRate,
                               LocalDate accruedThrough, LocalDate periodEnd) {
        if (balance == null || annualRate == null || accruedThrough == null || periodEnd == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        long days = Math.max(0, ChronoUnit.DAYS.between(accruedThrough, periodEnd));
        return balance.multiply(annualRate)
                .multiply(BigDecimal.valueOf(days))
                .divide(new BigDecimal("100"), 12, RoundingMode.HALF_EVEN)
                .divide(DAYS_IN_YEAR, 2, RoundingMode.HALF_EVEN);
    }

    private String reference(String accountId, LocalDate periodEnd) {
        String compactId = accountId.replace("-", "");
        compactId = compactId.substring(0, Math.min(32, compactId.length()));
        return "SI" + PERIOD.format(periodEnd) + "-" + compactId;
    }
}
