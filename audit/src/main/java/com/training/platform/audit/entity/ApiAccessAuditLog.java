package com.training.platform.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** Technical request/response events produced by the API gateway. */
@Entity
@Table(name = "api_access_audit_log", indexes = {
        @Index(name = "idx_api_audit_corr", columnList = "correlation_id"),
        @Index(name = "idx_api_audit_service", columnList = "target_service, created_at"),
        @Index(name = "idx_api_audit_status", columnList = "http_status, created_at"),
        @Index(name = "idx_api_audit_actor", columnList = "actor_id, created_at")
})
public class ApiAccessAuditLog extends BaseAuditLog {
    @Column(updatable = false, length = 100)
    private String username;

    @Column(name = "target_service", nullable = false, updatable = false, length = 50)
    private String targetService;

    @Column(name = "http_method", nullable = false, updatable = false, length = 10)
    private String httpMethod;

    @Column(name = "request_path", nullable = false, updatable = false, length = 500)
    private String requestPath;

    @Column(name = "http_status", nullable = false, updatable = false)
    private Integer httpStatus;

    @Column(name = "client_ip", updatable = false, length = 45)
    private String clientIp;

    @Column(name = "duration_ms", updatable = false)
    private Long durationMs;

    public ApiAccessAuditLog() { }

    public String getUsername() { return username; }
    public String getTargetService() { return targetService; }
    public String getHttpMethod() { return httpMethod; }
    public String getRequestPath() { return requestPath; }
    public Integer getHttpStatus() { return httpStatus; }
    public String getClientIp() { return clientIp; }
    public Long getDurationMs() { return durationMs; }

    public void setUsername(String username) { this.username = username; }
    public void setTargetService(String targetService) { this.targetService = targetService; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }
    public void setRequestPath(String requestPath) { this.requestPath = requestPath; }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
}
