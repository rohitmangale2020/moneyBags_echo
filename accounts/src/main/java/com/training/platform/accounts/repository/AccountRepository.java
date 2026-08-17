package com.training.platform.accounts.repository;

import com.training.platform.accounts.entity.Account;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, String>, JpaSpecificationExecutor<Account> {
    List<Account> findAllByOrderByCreatedAtDesc();
    Page<Account> findAllByOrderByCreatedAtDesc(Pageable pageable);
    boolean existsByAccountNumber(String accountNumber);
    Optional<Account> findByAccountNumber(String accountNumber);
    List<Account> findByCustomerId(String customerId);

    @Query("select a from Account a where a.status = com.training.platform.accounts.entity.AccountStatus.ACTIVE "
            + "and a.productTypeCode in ('SAVINGS', 'CURRENT') order by a.accountId")
    List<Account> findActiveAnnualFeeAccounts();

    @Query("select a from Account a where a.status = com.training.platform.accounts.entity.AccountStatus.ACTIVE "
            + "and a.productTypeCode in ('SAVINGS', 'SALARY') and a.annualInterestRate > 0 "
            + "and a.nextInterestPayoutDate <= :asOf order by a.nextInterestPayoutDate, a.accountId")
    List<Account> findInterestDue(@Param("asOf") LocalDate asOf);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.accountId in :accountIds order by a.accountId")
    List<Account> findAllByIdForUpdate(@Param("accountIds") Collection<String> accountIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.accountId = :accountId")
    Optional<Account> findByIdForUpdate(@Param("accountId") String accountId);
}
