package com.training.platform.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** Authentication and authorization events produced by the security service. */
@Entity
@Table(name = "security_audit_log", indexes = {
        @Index(name = "idx_sec_audit_corr", columnList = "correlation_id"),
        @Index(name = "idx_sec_audit_user", columnList = "user_id, created_at"),
        @Index(name = "idx_sec_audit_action", columnList = "action, created_at")
})
public class SecurityAuditLog extends BaseAuditLog {
    @Column(name = "user_id", updatable = false)
    private Long userId;

    @Column(updatable = false, length = 100)
    private String username;

    @Column(name = "client_ip", updatable = false, length = 45)
    private String clientIp;

    @Column(name = "user_agent", updatable = false, length = 500)
    private String userAgent;

    public SecurityAuditLog() { }

    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getClientIp() { return clientIp; }
    public String getUserAgent() { return userAgent; }

    public void setUserId(Long userId) { this.userId = userId; }
    public void setUsername(String username) { this.username = username; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
}
