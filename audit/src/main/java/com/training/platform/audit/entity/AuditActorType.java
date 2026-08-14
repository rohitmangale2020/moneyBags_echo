package com.training.platform.audit.entity;

/** Identifies the kind of principal responsible for an audited action. */
public enum AuditActorType {
    USER,
    CUSTOMER,
    SERVICE,
    SYSTEM,
    ANONYMOUS
}
