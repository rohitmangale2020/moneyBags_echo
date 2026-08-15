package com.training.platform.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** Customer, KYC, document, address and nominee events produced by the customers service. */
@Entity
@Table(name = "customer_audit_log", indexes = {
        @Index(name = "idx_cust_audit_corr", columnList = "correlation_id"),
        @Index(name = "idx_cust_audit_customer", columnList = "customer_id, created_at"),
        @Index(name = "idx_cust_audit_action", columnList = "action, created_at")
})
public class CustomerAuditLog extends BaseAuditLog {
    @Column(name = "customer_id", updatable = false)
    private Long customerId;

    @Column(name = "related_entity_type", updatable = false, length = 30)
    private String relatedEntityType;

    @Column(name = "related_entity_id", updatable = false, length = 100)
    private String relatedEntityId;

    @Column(name = "previous_status", updatable = false, length = 30)
    private String previousStatus;

    @Column(name = "new_status", updatable = false, length = 30)
    private String newStatus;

    public CustomerAuditLog() { }

    public Long getCustomerId() { return customerId; }
    public String getRelatedEntityType() { return relatedEntityType; }
    public String getRelatedEntityId() { return relatedEntityId; }
    public String getPreviousStatus() { return previousStatus; }
    public String getNewStatus() { return newStatus; }

    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public void setRelatedEntityType(String relatedEntityType) { this.relatedEntityType = relatedEntityType; }
    public void setRelatedEntityId(String relatedEntityId) { this.relatedEntityId = relatedEntityId; }
    public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
}
