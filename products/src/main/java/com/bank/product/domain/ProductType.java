package com.bank.product.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "PRODUCT_TYPE_MASTER") @Getter @Setter @NoArgsConstructor
public class ProductType extends AuditableEntity {
    @Id @Column(length = 30) private String productTypeCode;
    @Column(nullable = false, length = 100) private String productTypeName;
    @Column(length = 500) private String description;
    @Column(nullable = false, length = 20) private String status;
}
