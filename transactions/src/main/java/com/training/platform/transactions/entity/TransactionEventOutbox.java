package com.training.platform.transactions.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transaction_event_outbox")
/** Queues transaction-domain events for reliable asynchronous publication. */
public class TransactionEventOutbox {
    @Id @Column(name = "event_id", length = 36) private String eventId;
    @Column(name = "aggregate_id", nullable = false, length = 36) private String aggregateId;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false, length = 100) private TransactionEventType eventType;
    @Lob @Column(name = "payload_json", nullable = false) private String payloadJson;
    @Column(name = "occurred_at", nullable = false) private LocalDateTime occurredAt;
    @Column(name = "published_at") private LocalDateTime publishedAt;
    @Column(name = "retry_count", nullable = false) private Integer retryCount = 0;

    protected TransactionEventOutbox() { }
    @jakarta.persistence.PrePersist void beforeInsert() { if (eventId == null) eventId = UUID.randomUUID().toString(); if (occurredAt == null) occurredAt = LocalDateTime.now(); }
}
