package com.training.platform.transactions.repository;

import com.training.platform.transactions.entity.FixedDepositContract;
import com.training.platform.transactions.entity.FixedDepositStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FixedDepositContractRepository extends JpaRepository<FixedDepositContract, String> {
    Optional<FixedDepositContract> findByFdAccountId(String fdAccountId);
    List<FixedDepositContract> findByStatusAndMaturityDateLessThanEqualOrderByMaturityDateAsc(
            FixedDepositStatus status, LocalDate maturityDate);

    @Query("select f from FixedDepositContract f where f.status in :statuses "
            + "and (f.fundingAccountId = :accountId or f.payoutAccountId = :accountId) "
            + "order by f.maturityDate")
    List<FixedDepositContract> findDependencies(@Param("accountId") String accountId,
                                                @Param("statuses") List<FixedDepositStatus> statuses);
}
