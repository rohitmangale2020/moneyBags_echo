package com.bank.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;

@Getter @Setter @MappedSuperclass @EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {
    @CreatedDate @Column(nullable = false, updatable = false) private LocalDateTime createdDate;
    @LastModifiedDate @Column(nullable = false) private LocalDateTime updatedDate;
    @CreatedBy @Column(nullable = false, updatable = false, length = 100) private String createdBy;
    @LastModifiedBy @Column(nullable = false, length = 100) private String updatedBy;
}
