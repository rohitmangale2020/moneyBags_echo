package com.training.platform.transactions.service;

import com.training.platform.transactions.dto.LedgerAccountRequest;
import com.training.platform.transactions.dto.LedgerPostingRequest;
import com.training.platform.transactions.entity.LedgerAccount;
import com.training.platform.transactions.entity.LedgerAccountType;
import com.training.platform.transactions.entity.LedgerEntry;
import com.training.platform.transactions.entity.LedgerEntryType;
import com.training.platform.transactions.entity.TransactionType;
import com.training.platform.transactions.entity.BankTransaction;
import com.training.platform.transactions.repository.LedgerAccountRepository;
import com.training.platform.transactions.repository.LedgerEntryRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Minimal immutable double-entry ledger. Journal headers are intentionally not modelled. */
@Service
@Transactional(readOnly = true)
public class LedgerService {
    public static final String CASH_ON_HAND = "CASH_ON_HAND";
    public static final String CUSTOMER_DEPOSITS = "CUSTOMER_DEPOSITS";
    public static final String INTERNAL_CLEARING = "INTERNAL_CLEARING";

    private final LedgerAccountRepository accountRepository;
    private final LedgerEntryRepository entryRepository;

    public LedgerService(LedgerAccountRepository accountRepository, LedgerEntryRepository entryRepository) {
        this.accountRepository = accountRepository;
        this.entryRepository = entryRepository;
    }

    public List<LedgerAccount> accounts() { return accountRepository.findAll(); }

    public LedgerAccount account(String code) { return findAccount(code); }

    public List<LedgerEntry> entries(String transactionRef, String accountCode) {
        if (transactionRef != null && !transactionRef.isBlank()) {
            return entryRepository.findByTransactionRefOrderByLineNumber(transactionRef);
        }
        if (accountCode != null && !accountCode.isBlank()) {
            return entryRepository.findByLedgerAccountCodeOrderByPostingDateDescLineNumberDesc(normalize(accountCode));
        }
        throw new IllegalArgumentException("Either transactionRef or accountCode is required");
    }

    @Transactional
    public LedgerAccount createAccount(LedgerAccountRequest request) {
        String code = normalize(request.code());
        if (accountRepository.findByCode(code).isPresent()) {
            throw new IllegalArgumentException("Ledger account code already exists: " + code);
        }
        LedgerAccount account = new LedgerAccount();
        account.setCode(code);
        account.setName(request.name().trim());
        account.setAccountType(request.accountType());
        return accountRepository.save(account);
    }

    @Transactional
    public List<LedgerEntry> post(LedgerPostingRequest request) {
        if (entryRepository.existsByTransactionRef(request.transactionRef())) {
            throw new IllegalArgumentException("Ledger entries already exist for transaction reference: " + request.transactionRef());
        }
        List<Posting> postings = request.items().stream().map(item -> new Posting(item.ledgerAccountCode(),
                item.customerAccountId(), item.entryType(), item.amount(), item.description())).toList();
        return postNew(request.transactionRef(), request.postingDate(), request.currencyCode(), request.description(), postings);
    }

    /** Creates the ledger side of a successfully posted banking transaction exactly once. */
    @Transactional
    public List<LedgerEntry> postCompletedTransaction(BankTransaction transaction) {
        if (entryRepository.existsByTransactionRef(transaction.getTransactionRef())) {
            return entryRepository.findByTransactionRefOrderByLineNumber(transaction.getTransactionRef());
        }
        BigDecimal amount = transaction.getAmount();
        String description = transaction.getDescription();
        List<Posting> postings = switch (transaction.getTransactionType()) {
            case DEPOSIT, OPENING_DEPOSIT, FIXED_DEPOSIT_FUNDING -> List.of(
                    new Posting(CASH_ON_HAND, null, LedgerEntryType.DEBIT, amount, description),
                    new Posting(CUSTOMER_DEPOSITS, transaction.getCreditAccountId(), LedgerEntryType.CREDIT, amount, description));
            case WITHDRAWAL, FIXED_DEPOSIT_PREMATURE_CLOSURE -> List.of(
                    new Posting(CUSTOMER_DEPOSITS, transaction.getDebitAccountId(), LedgerEntryType.DEBIT, amount, description),
                    new Posting(CASH_ON_HAND, null, LedgerEntryType.CREDIT, amount, description));
            case TRANSFER -> List.of(
                    new Posting(CUSTOMER_DEPOSITS, transaction.getDebitAccountId(), LedgerEntryType.DEBIT, amount, description),
                    new Posting(INTERNAL_CLEARING, null, LedgerEntryType.CREDIT, amount, description),
                    new Posting(INTERNAL_CLEARING, null, LedgerEntryType.DEBIT, amount, description),
                    new Posting(CUSTOMER_DEPOSITS, transaction.getCreditAccountId(), LedgerEntryType.CREDIT, amount, description));
            default -> throw new IllegalArgumentException("No ledger mapping exists for " + transaction.getTransactionType());
        };
        return postNew(transaction.getTransactionRef(), LocalDate.now(), transaction.getCurrencyCode(), description, postings);
    }

    private List<LedgerEntry> postNew(String transactionRef, LocalDate postingDate, String currencyCode,
                                      String description, List<Posting> postings) {
        if (transactionRef == null || transactionRef.isBlank()) throw new IllegalArgumentException("Transaction reference is required");
        if (postings.size() < 2) throw new IllegalArgumentException("At least two ledger entries are required");
        BigDecimal debits = postings.stream().filter(p -> p.entryType() == LedgerEntryType.DEBIT)
                .map(Posting::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = postings.stream().filter(p -> p.entryType() == LedgerEntryType.CREDIT)
                .map(Posting::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (debits.compareTo(credits) != 0) {
            throw new IllegalArgumentException("Ledger transaction is not balanced: debits must equal credits");
        }

        String normalizedCurrency = normalize(currencyCode);
        List<LedgerEntry> entries = new ArrayList<>();
        for (int index = 0; index < postings.size(); index++) {
            Posting posting = postings.get(index);
            if (posting.amount() == null || posting.amount().signum() <= 0) {
                throw new IllegalArgumentException("Ledger entry amount must be greater than zero");
            }
            LedgerAccount account = findAccount(posting.ledgerAccountCode());
            if (!account.isActive()) throw new IllegalArgumentException("Ledger account is inactive: " + account.getCode());
            LedgerEntry entry = new LedgerEntry();
            entry.setTransactionRef(transactionRef.trim());
            entry.setLineNumber(index + 1);
            entry.setLedgerAccount(account);
            entry.setCustomerAccountId(blankToNull(posting.customerAccountId()));
            entry.setEntryType(posting.entryType());
            entry.setAmount(posting.amount());
            entry.setCurrencyCode(normalizedCurrency);
            entry.setPostingDate(postingDate == null ? LocalDate.now() : postingDate);
            entry.setDescription(blankToNull(posting.description()) == null ? blankToNull(description) : posting.description().trim());
            account.apply(posting.entryType(), posting.amount());
            entries.add(entry);
        }
        accountRepository.saveAll(entries.stream().map(LedgerEntry::getLedgerAccount).distinct().toList());
        return entryRepository.saveAll(entries);
    }

    private LedgerAccount findAccount(String code) {
        String normalizedCode = normalize(code);
        return accountRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new EntityNotFoundException("Ledger account not found: " + normalizedCode));
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Value is required");
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private record Posting(String ledgerAccountCode, String customerAccountId, LedgerEntryType entryType,
                           BigDecimal amount, String description) { }
}
