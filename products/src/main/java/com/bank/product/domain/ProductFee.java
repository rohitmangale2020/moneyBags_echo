package com.bank.product.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name = "PRODUCT_FEE") @Getter @Setter @NoArgsConstructor
public class ProductFee extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_fee_seq") @SequenceGenerator(name = "product_fee_seq", sequenceName = "PRODUCT_FEE_SEQ", allocationSize = 1) private Long productFeeId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id", unique = true) private Product product;
    /** Legacy column retained to avoid a schema migration; its value is now the annual fee. */
    @Column(name = "monthly_maintenance_fee", precision = 19, scale = 4)
    private BigDecimal annualMaintenanceFee;
    @Version @Column(nullable = false) private Long versionNo;
}
