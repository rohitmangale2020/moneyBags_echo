package com.training.platform.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** User lifecycle and role changes produced by the users service. */
@Entity
@Table(name = "user_audit_log", indexes = {
        @Index(name = "idx_user_audit_corr", columnList = "correlation_id"),
        @Index(name = "idx_user_audit_target", columnList = "target_user_id, created_at"),
        @Index(name = "idx_user_audit_action", columnList = "action, created_at")
})
public class UserAuditLog extends BaseAuditLog {
    @Column(name = "target_user_id", updatable = false)
    private Long targetUserId;

    @Column(name = "previous_status", updatable = false, length = 30)
    private String previousStatus;

    @Column(name = "new_status", updatable = false, length = 30)
    private String newStatus;

    @Column(name = "previous_role", updatable = false, length = 50)
    private String previousRole;

    @Column(name = "new_role", updatable = false, length = 50)
    private String newRole;

    public UserAuditLog() { }

    public Long getTargetUserId() { return targetUserId; }
    public String getPreviousStatus() { return previousStatus; }
    public String getNewStatus() { return newStatus; }
    public String getPreviousRole() { return previousRole; }
    public String getNewRole() { return newRole; }

    public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }
    public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
    public void setPreviousRole(String previousRole) { this.previousRole = previousRole; }
    public void setNewRole(String newRole) { this.newRole = newRole; }
}
