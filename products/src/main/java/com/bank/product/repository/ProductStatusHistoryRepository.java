package com.bank.product.repository;
import com.bank.product.domain.ProductStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ProductStatusHistoryRepository extends JpaRepository<ProductStatusHistory, Long> { List<ProductStatusHistory> findByProductProductIdOrderByChangedDateDesc(Long productId); }
