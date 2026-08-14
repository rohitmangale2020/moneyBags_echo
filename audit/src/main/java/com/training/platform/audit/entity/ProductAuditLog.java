package com.training.platform.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** Product, rate, fee and term events produced by the products service. */
@Entity
@Table(name = "product_audit_log", indexes = {
        @Index(name = "idx_prod_audit_corr", columnList = "correlation_id"),
        @Index(name = "idx_prod_audit_product", columnList = "product_id, created_at"),
        @Index(name = "idx_prod_audit_action", columnList = "action, created_at")
})
public class ProductAuditLog extends BaseAuditLog {
    @Column(name = "product_id", updatable = false)
    private Long productId;

    @Column(name = "component_type", updatable = false, length = 30)
    private String componentType;

    @Column(name = "component_id", updatable = false, length = 100)
    private String componentId;

    @Column(name = "previous_status", updatable = false, length = 20)
    private String previousStatus;

    @Column(name = "new_status", updatable = false, length = 20)
    private String newStatus;

    @Column(name = "change_summary", updatable = false, length = 1000)
    private String changeSummary;

    public ProductAuditLog() { }

    public Long getProductId() { return productId; }
    public String getComponentType() { return componentType; }
    public String getComponentId() { return componentId; }
    public String getPreviousStatus() { return previousStatus; }
    public String getNewStatus() { return newStatus; }
    public String getChangeSummary() { return changeSummary; }

    public void setProductId(Long productId) { this.productId = productId; }
    public void setComponentType(String componentType) { this.componentType = componentType; }
    public void setComponentId(String componentId) { this.componentId = componentId; }
    public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
    public void setChangeSummary(String changeSummary) { this.changeSummary = changeSummary; }
}
