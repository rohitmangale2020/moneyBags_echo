package com.training.platform.transactions.config;

import com.training.platform.transactions.entity.LedgerAccount;
import com.training.platform.transactions.entity.LedgerAccountType;
import com.training.platform.transactions.repository.LedgerAccountRepository;
import com.training.platform.transactions.service.LedgerService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class LedgerBootstrapConfiguration {
    @Bean
    CommandLineRunner defaultLedgerAccounts(LedgerAccountRepository repository) {
        return ignored -> {
            createIfMissing(repository, LedgerService.CASH_ON_HAND, "Cash on Hand", LedgerAccountType.ASSET);
            createIfMissing(repository, LedgerService.CUSTOMER_DEPOSITS, "Customer Deposits", LedgerAccountType.LIABILITY);
            createIfMissing(repository, LedgerService.INTERNAL_CLEARING, "Internal Clearing", LedgerAccountType.LIABILITY);
            createIfMissing(repository, LedgerService.INTEREST_EXPENSE, "Deposit Interest Expense", LedgerAccountType.EXPENSE);
            createIfMissing(repository, LedgerService.FEE_INCOME, "Maintenance Fee Income", LedgerAccountType.INCOME);
        };
    }

    private void createIfMissing(LedgerAccountRepository repository, String code, String name, LedgerAccountType type) {
        if (repository.findByCode(code).isPresent()) return;
        LedgerAccount account = new LedgerAccount();
        account.setCode(code);
        account.setName(name);
        account.setAccountType(type);
        repository.save(account);
    }
}
