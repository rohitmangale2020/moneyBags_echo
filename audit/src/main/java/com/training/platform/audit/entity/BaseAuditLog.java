package com.training.platform.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import java.time.LocalDateTime;
import java.util.UUID;

/** Common, append-only information stored by every service-specific audit table. */
@MappedSuperclass
public abstract class BaseAuditLog {
    @Id
    @Column(name = "audit_id", nullable = false, updatable = false, length = 36)
    private String auditId;

    @Column(name = "correlation_id", nullable = false, updatable = false, length = 36)
    private String correlationId;

    @Column(nullable = false, updatable = false, length = 100)
    private String action;

    @Column(name = "actor_id", updatable = false, length = 100)
    private String actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, updatable = false, length = 20)
    private AuditActorType actorType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private AuditOutcome outcome;

    @Column(updatable = false, length = 500)
    private String description;

    @Column(name = "error_code", updatable = false, length = 50)
    private String errorCode;

    @Column(name = "error_message", updatable = false, length = 1000)
    private String errorMessage;

    @Column(name = "changed_fields", updatable = false, length = 500)
    private String changedFields;

    @Lob
    @Column(name = "old_values_json", updatable = false)
    private String oldValuesJson;

    @Lob
    @Column(name = "new_values_json", updatable = false)
    private String newValuesJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected BaseAuditLog() { }

    @PrePersist
    protected void beforeInsert() {
        if (auditId == null) {
            auditId = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public String getAuditId() { return auditId; }
    public String getCorrelationId() { return correlationId; }
    public String getAction() { return action; }
    public String getActorId() { return actorId; }
    public AuditActorType getActorType() { return actorType; }
    public AuditOutcome getOutcome() { return outcome; }
    public String getDescription() { return description; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public String getChangedFields() { return changedFields; }
    public String getOldValuesJson() { return oldValuesJson; }
    public String getNewValuesJson() { return newValuesJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setAuditId(String auditId) { this.auditId = auditId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public void setAction(String action) { this.action = action; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public void setActorType(AuditActorType actorType) { this.actorType = actorType; }
    public void setOutcome(AuditOutcome outcome) { this.outcome = outcome; }
    public void setDescription(String description) { this.description = description; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setChangedFields(String changedFields) { this.changedFields = changedFields; }
    public void setOldValuesJson(String oldValuesJson) { this.oldValuesJson = oldValuesJson; }
    public void setNewValuesJson(String newValuesJson) { this.newValuesJson = newValuesJson; }
}
