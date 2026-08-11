package com.bank.product.repository;
import com.bank.product.domain.ProductType;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProductTypeRepository extends JpaRepository<ProductType, String> { }
