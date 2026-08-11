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

    public static TransactionEventOutbox create(String aggregateId, TransactionEventType eventType,
                                                 String payloadJson) {
        TransactionEventOutbox event = new TransactionEventOutbox();
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.payloadJson = payloadJson;
        return event;
    }

    @jakarta.persistence.PrePersist void beforeInsert() { if (eventId == null) eventId = UUID.randomUUID().toString(); if (occurredAt == null) occurredAt = LocalDateTime.now(); }

    public String getEventId() { return eventId; }
    public String getAggregateId() { return aggregateId; }
    public TransactionEventType getEventType() { return eventType; }
    public String getPayloadJson() { return payloadJson; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public Integer getRetryCount() { return retryCount; }
}
