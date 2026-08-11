package com.bank.product.repository;
import com.bank.product.domain.ProductRate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ProductRateRepository extends JpaRepository<ProductRate, Long> { Optional<ProductRate> findByProductProductId(Long productId); }
