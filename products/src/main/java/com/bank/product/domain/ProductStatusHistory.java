package com.bank.product.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;

@Entity @Table(name = "PRODUCT_STATUS_HISTORY") @EntityListeners(AuditingEntityListener.class) @Getter @Setter @NoArgsConstructor
public class ProductStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "status_history_seq") @SequenceGenerator(name = "status_history_seq", sequenceName = "PRODUCT_STATUS_HISTORY_SEQ", allocationSize = 1) private Long productStatusHistoryId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id") private Product product;
    @Column(nullable = false, length = 20) private String previousStatus;
    @Column(nullable = false, length = 20) private String newStatus;
    @Column(nullable = false, length = 500) private String changeReason;
    @CreatedDate @Column(nullable = false, updatable = false) private LocalDateTime changedDate;
    @CreatedBy @Column(nullable = false, updatable = false, length = 100) private String changedBy;
}
