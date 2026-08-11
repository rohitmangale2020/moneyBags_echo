package com.bank.product.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name = "PRODUCT") @Getter @Setter @NoArgsConstructor
public class Product extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq")
    @SequenceGenerator(name = "product_seq", sequenceName = "PRODUCT_SEQ", allocationSize = 1)
    @Column(name = "product_id") private Long productId;
    @Column(nullable = false, unique = true, length = 50) private String productCode;
    @Column(nullable = false, length = 150) private String productName;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_type_code") private ProductType productType;
    @Column(length = 500) private String description;
    @Column(precision = 19, scale = 4) private BigDecimal minimumBalance;
    @Column(precision = 19, scale = 4) private BigDecimal maximumBalance;
    @Column(nullable = false, length = 3) private String currency;
    @Column(nullable = false, length = 20) private String status;
    @Version @Column(nullable = false) private Long versionNo;
}
