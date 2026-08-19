package com.training.platform.risk.repository;

import com.training.platform.risk.entity.RiskAssessment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, String> {
    List<RiskAssessment> findByTransactionRefOrderByAssessedAtDesc(String transactionRef);
}
