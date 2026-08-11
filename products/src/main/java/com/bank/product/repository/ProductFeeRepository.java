package com.bank.product.repository;
import com.bank.product.domain.ProductFee;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ProductFeeRepository extends JpaRepository<ProductFee, Long> { Optional<ProductFee> findByProductProductId(Long productId); }
