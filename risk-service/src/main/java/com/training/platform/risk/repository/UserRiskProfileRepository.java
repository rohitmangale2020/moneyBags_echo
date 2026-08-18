package com.training.platform.risk.repository;

import com.training.platform.risk.entity.UserRiskProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRiskProfileRepository extends JpaRepository<UserRiskProfile, String> { }
