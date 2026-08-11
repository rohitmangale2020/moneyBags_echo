package com.bank.product.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name = "PRODUCT_RATE") @Getter @Setter @NoArgsConstructor
public class ProductRate extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_rate_seq") @SequenceGenerator(name = "product_rate_seq", sequenceName = "PRODUCT_RATE_SEQ", allocationSize = 1) private Long productRateId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id", unique = true) private Product product;
    @Column(nullable = false, precision = 8, scale = 4) private BigDecimal interestRate;
    @Version @Column(nullable = false) private Long versionNo;
}
