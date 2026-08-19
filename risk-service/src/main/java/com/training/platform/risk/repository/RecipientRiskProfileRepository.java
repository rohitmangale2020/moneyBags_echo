package com.training.platform.risk.repository;

import com.training.platform.risk.entity.RecipientRiskProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipientRiskProfileRepository extends JpaRepository<RecipientRiskProfile, String> { }
