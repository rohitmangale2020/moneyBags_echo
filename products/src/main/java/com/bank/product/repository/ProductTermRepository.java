package com.bank.product.repository;
import com.bank.product.domain.ProductTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ProductTermRepository extends JpaRepository<ProductTerm, Long> { Optional<ProductTerm> findByProductProductId(Long productId); }
