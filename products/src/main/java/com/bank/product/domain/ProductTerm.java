package com.bank.product.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name = "PRODUCT_TERM") @Getter @Setter @NoArgsConstructor
public class ProductTerm extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_term_seq") @SequenceGenerator(name = "product_term_seq", sequenceName = "PRODUCT_TERM_SEQ", allocationSize = 1) private Long productTermId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id", unique = true) private Product product;
    private Integer tenureMonths; @Column(precision = 19, scale = 4) private BigDecimal installmentAmount;
    @Column(length = 30) private String installmentFrequency; private Integer lockInPeriod;
    @Column(length = 50) private String maturityInstruction; private Boolean prematureWithdrawalAllowed;
    @Version @Column(nullable = false) private Long versionNo;
}
