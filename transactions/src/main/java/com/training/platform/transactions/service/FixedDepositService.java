package com.training.platform.transactions.service;

import com.training.platform.auditclient.AuditClient;
import com.training.platform.transactions.client.AccountDetailsResponse;
import com.training.platform.transactions.client.AccountsClient;
import com.training.platform.transactions.dto.FixedDepositOpenRequest;
import com.training.platform.transactions.entity.BankTransaction;
import com.training.platform.transactions.entity.FixedDepositContract;
import com.training.platform.transactions.entity.FixedDepositStatus;
import com.training.platform.transactions.entity.TransactionStatus;
import com.training.platform.transactions.entity.TransactionType;
import com.training.platform.transactions.repository.FixedDepositContractRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FixedDepositService {
    private static final BigDecimal DAYS_IN_YEAR = new BigDecimal("365");

    private final FixedDepositContractRepository contractRepository;
    private final AccountsClient accountsClient;
    private final BankTransactionService transactionService;
    private final AuditClient auditClient;
    private final BigDecimal prematurePenaltyRate;

    public FixedDepositService(FixedDepositContractRepository contractRepository,
                               AccountsClient accountsClient,
                               BankTransactionService transactionService,
                               AuditClient auditClient,
                               @Value("${banking.fixed-deposit.premature-penalty-rate:1.00}")
                               BigDecimal prematurePenaltyRate) {
        this.contractRepository = contractRepository;
        this.accountsClient = accountsClient;
        this.transactionService = transactionService;
        this.auditClient = auditClient;
        this.prematurePenaltyRate = prematurePenaltyRate;
    }

    public FixedDepositContract get(String contractId) {
        return contractRepository.findById(contractId)
                .orElseThrow(() -> new EntityNotFoundException("Fixed-deposit contract not found: " + contractId));
    }

    public List<FixedDepositContract> all() { return contractRepository.findAll(); }

    @Transactional
    public FixedDepositContract open(FixedDepositOpenRequest request) {
        if (contractRepository.findByFdAccountId(request.fdAccountId()).isPresent()) {
            throw new IllegalArgumentException("A fixed-deposit contract already exists for this account");
        }
        AccountDetailsResponse fd = accountsClient.getAccount(request.fdAccountId());
        AccountDetailsResponse funding = accountsClient.getAccount(request.fundingAccountId());
        AccountDetailsResponse payout = accountsClient.getAccount(request.payoutAccountId());
        validateOpening(request, fd, funding, payout);

        int tenure = fd.tenureMonths();
        int lockIn = 0;
        FixedDepositContract contract = FixedDepositContract.open(fd.accountId(), funding.accountId(),
                payout.accountId(), fd.customerId(), fd.productId(), request.principal(),
                fd.annualInterestRate(), tenure, lockIn, fd.maturityInstruction(),
                true, LocalDate.now());
        contractRepository.save(contract);
        return fund(contract);
    }

    @Transactional
    public FixedDepositContract retryFunding(String contractId) {
        FixedDepositContract contract = get(contractId);
        if (contract.getStatus() != FixedDepositStatus.FUNDING_FAILED
                && contract.getStatus() != FixedDepositStatus.PENDING_FUNDING) {
            throw new IllegalArgumentException("Only a pending or failed funding operation can be retried");
        }
        return fund(contract);
    }

    @Transactional
    public FixedDepositContract mature(String contractId, LocalDate asOf) {
        FixedDepositContract contract = get(contractId);
        if (contract.getStatus() != FixedDepositStatus.ACTIVE) return contract;
        if (asOf.isBefore(contract.getMaturityDate())) {
            throw new IllegalArgumentException("Fixed deposit does not mature until " + contract.getMaturityDate());
        }
        return close(contract, contract.getMaturityDate(), FixedDepositStatus.MATURED,
                TransactionType.FIXED_DEPOSIT_MATURITY, contract.getAnnualInterestRate());
    }

    @Transactional
    public FixedDepositContract closePrematurely(String contractId, LocalDate asOf) {
        FixedDepositContract contract = get(contractId);
        if (contract.getStatus() != FixedDepositStatus.ACTIVE) {
            throw new IllegalArgumentException("Fixed deposit is not active");
        }
        if (!asOf.isBefore(contract.getMaturityDate())) return mature(contractId, asOf);
        BigDecimal penalizedRate = contract.getAnnualInterestRate().subtract(prematurePenaltyRate)
                .max(BigDecimal.ZERO);
        return close(contract, asOf, FixedDepositStatus.PREMATURELY_CLOSED,
                TransactionType.FIXED_DEPOSIT_PREMATURE_CLOSURE, penalizedRate);
    }

    private FixedDepositContract fund(FixedDepositContract contract) {
        BankTransaction funding = transaction(TransactionType.FIXED_DEPOSIT_FUNDING,
                reference("FD-O-", contract.getContractId()), contract.getFundingAccountId(),
                contract.getFdAccountId(), contract.getPrincipal(), contract.getCustomerId(), null);
        BankTransaction result = transactionService.initiate(funding);
        if (result.getTransactionStatus() == TransactionStatus.COMPLETED) {
            contract.recordFunding(result.getTransactionId());
            auditClient.success("transactions", "FIXED_DEPOSIT_OPENED",
                    "Fixed-deposit principal transferred from the funding account to the deposit account on "
                            + contract.getOpenedOn(),
                    auditDetails(contract, result, BigDecimal.ZERO));
        } else {
            contract.recordFundingFailure(result.getTransactionId());
            auditClient.failed("transactions", "FIXED_DEPOSIT_FUNDING_FAILED",
                    "Fixed-deposit principal transfer failed on " + contract.getOpenedOn(),
                    result.getFailureCode(), result.getFailureReason(),
                    auditDetails(contract, result, BigDecimal.ZERO));
        }
        return contractRepository.save(contract);
    }

    private FixedDepositContract close(FixedDepositContract contract, LocalDate valueDate,
                                       FixedDepositStatus closingStatus, TransactionType closingType,
                                       BigDecimal appliedRate) {
        contract.useFundingAccountForPayout();
        BigDecimal interest = interest(contract.getPrincipal(), appliedRate,
                contract.getOpenedOn(), valueDate);
        BankTransaction interestResult = null;
        if (interest.signum() > 0) {
            BankTransaction interestCredit = transaction(TransactionType.FIXED_DEPOSIT_INTEREST_CREDIT,
                    reference("FD-I-", contract.getContractId()), null, contract.getPayoutAccountId(),
                    interest, contract.getCustomerId(), valueDate);
            interestResult = transactionService.initiate(interestCredit);
            if (interestResult.getTransactionStatus() != TransactionStatus.COMPLETED) return contract;
        }
        BankTransaction principal = transaction(closingType, reference("FD-P-", contract.getContractId()),
                contract.getFdAccountId(), contract.getPayoutAccountId(), contract.getPrincipal(),
                contract.getCustomerId(), null);
        BankTransaction principalResult = transactionService.initiate(principal);
        if (principalResult.getTransactionStatus() != TransactionStatus.COMPLETED) return contract;
        contract.close(closingStatus, interest, principalResult.getTransactionId(),
                interestResult == null ? null : interestResult.getTransactionId());
        auditClient.success("transactions", closingStatus == FixedDepositStatus.MATURED
                        ? "FIXED_DEPOSIT_MATURED" : "FIXED_DEPOSIT_PREMATURELY_CLOSED",
                closingStatus == FixedDepositStatus.MATURED
                        ? "Fixed deposit matured on " + valueDate
                                + "; principal and interest were credited to the original funding account"
                        : "Fixed deposit closed early on " + valueDate
                                + "; principal and penalty-adjusted interest were credited to the original funding account",
                closureAuditDetails(contract, principalResult, interest, appliedRate));
        return contractRepository.save(contract);
    }

    private void validateOpening(FixedDepositOpenRequest request, AccountDetailsResponse fd,
                                 AccountDetailsResponse funding, AccountDetailsResponse payout) {
        if (!"FD".equalsIgnoreCase(fd.productTypeCode())) {
            throw new IllegalArgumentException("Selected account is not a fixed-deposit account");
        }
        if (!"ACTIVE".equalsIgnoreCase(fd.status()) || fd.availableBalance().signum() != 0) {
            throw new IllegalArgumentException("Fixed-deposit account must be active and unfunded");
        }
        if (!"ACTIVE".equalsIgnoreCase(funding.status()) || !"ACTIVE".equalsIgnoreCase(payout.status())) {
            throw new IllegalArgumentException("Funding and payout accounts must be active");
        }
        if ("FD".equalsIgnoreCase(funding.productTypeCode())
                || "FD".equalsIgnoreCase(payout.productTypeCode())) {
            throw new IllegalArgumentException("Funding and payout accounts must be transactional accounts");
        }
        if (!funding.accountId().equals(payout.accountId())) {
            throw new IllegalArgumentException("The FD maturity payout account must be the same account that funds the FD");
        }
        if (!fd.customerId().equals(funding.customerId()) || !fd.customerId().equals(payout.customerId())) {
            throw new IllegalArgumentException("FD, funding, and payout accounts must belong to the same customer");
        }
        if (!fd.currencyCode().equalsIgnoreCase(funding.currencyCode())
                || !fd.currencyCode().equalsIgnoreCase(payout.currencyCode())) {
            throw new IllegalArgumentException("FD, funding, and payout accounts must use the same currency");
        }
        if (fd.tenureMonths() == null || fd.tenureMonths() <= 0) {
            throw new IllegalArgumentException("Fixed-deposit product has no valid tenure");
        }
        if (fd.annualInterestRate() == null || fd.annualInterestRate().signum() < 0) {
            throw new IllegalArgumentException("Fixed-deposit product has no valid interest rate");
        }
        if (!"CREDIT_TO_ACCOUNT".equalsIgnoreCase(fd.maturityInstruction())) {
            throw new IllegalArgumentException("Only CREDIT_TO_ACCOUNT maturity is currently supported");
        }
        if (fd.minimumBalance() != null && request.principal().compareTo(fd.minimumBalance()) < 0) {
            throw new IllegalArgumentException("Principal is below the product minimum of " + fd.minimumBalance());
        }
    }

    private BankTransaction transaction(TransactionType type, String reference, String debitAccountId,
                                        String creditAccountId, BigDecimal amount, String customerId,
                                        LocalDate interestPeriodEnd) {
        BankTransaction transaction = new BankTransaction();
        transaction.setTransactionRef(reference);
        transaction.setTransactionType(type);
        transaction.setTransactionStatus(TransactionStatus.INITIATED);
        transaction.setDebitAccountId(debitAccountId);
        transaction.setCreditAccountId(creditAccountId);
        transaction.setAmount(amount);
        AccountDetailsResponse account = accountsClient.getAccount(
                creditAccountId == null ? debitAccountId : creditAccountId);
        transaction.setCurrencyCode(account.currencyCode());
        transaction.setFeeAmount(BigDecimal.ZERO);
        transaction.setInitiatedByCustomerId(customerId);
        transaction.setInitiatedByUserId("SYSTEM");
        transaction.setInterestPeriodEnd(interestPeriodEnd);
        return transaction;
    }

    static BigDecimal interest(BigDecimal principal, BigDecimal annualRate,
                               LocalDate from, LocalDate to) {
        long days = Math.max(0, ChronoUnit.DAYS.between(from, to));
        return principal.multiply(annualRate)
                .multiply(BigDecimal.valueOf(days))
                .divide(new BigDecimal("100"), 12, RoundingMode.HALF_EVEN)
                .divide(DAYS_IN_YEAR, 2, RoundingMode.HALF_EVEN);
    }

    private String reference(String prefix, String contractId) {
        return prefix + contractId.replace("-", "");
    }

    private Map<String, Object> auditDetails(FixedDepositContract contract,
                                             BankTransaction transaction, BigDecimal interest) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("relatedEntityType", "FIXED_DEPOSIT_CONTRACT");
        details.put("relatedEntityId", contract.getContractId());
        details.put("accountId", contract.getFdAccountId());
        details.put("fundingAccountId", contract.getFundingAccountId());
        details.put("payoutAccountId", contract.getPayoutAccountId());
        details.put("transactionId", transaction.getTransactionId());
        details.put("transactionRef", transaction.getTransactionRef());
        details.put("amount", contract.getPrincipal());
        details.put("interestAmount", interest);
        details.put("currencyCode", transaction.getCurrencyCode());
        details.put("openedOn", contract.getOpenedOn());
        details.put("maturityDate", contract.getMaturityDate());
        details.put("newStatus", contract.getStatus().name());
        return details;
    }

    private Map<String, Object> closureAuditDetails(FixedDepositContract contract,
                                                    BankTransaction transaction,
                                                    BigDecimal interest,
                                                    BigDecimal appliedRate) {
        Map<String, Object> details = auditDetails(contract, transaction, interest);
        details.put("contractedAnnualRate", contract.getAnnualInterestRate());
        details.put("appliedAnnualRate", appliedRate);
        if (contract.getStatus() == FixedDepositStatus.PREMATURELY_CLOSED) {
            details.put("prematurePenaltyRate", contract.getAnnualInterestRate().subtract(appliedRate));
        }
        details.put("totalPayout", contract.getPrincipal().add(interest));
        details.put("closedAt", contract.getClosedAt());
        if (contract.getStatus() == FixedDepositStatus.MATURED) {
            details.put("actorId", "SYSTEM");
            details.put("actorType", "SYSTEM");
        }
        return details;
    }
}
