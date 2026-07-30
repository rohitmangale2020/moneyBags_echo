package com.training.platform.transactions.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "transaction_channel_detail")
public class TransactionChannelDetail {
    @Id @Column(name = "channel_detail_id", length = 36) private String channelDetailId;
    @ManyToOne(optional = false) @JoinColumn(name = "transaction_id", nullable = false) private BankTransaction transaction;
    @Column(name = "channel_reference", length = 100) private String channelReference;
    @Column(name = "device_id", length = 100) private String deviceId;
    @Column(name = "terminal_id", length = 100) private String terminalId;
    @Column(name = "ip_address", length = 45) private String ipAddress;
    @Column(length = 250) private String location;
    @Lob @Column(name = "metadata_json") private String metadataJson;

    protected TransactionChannelDetail() { }
    @jakarta.persistence.PrePersist void beforeInsert() { if (channelDetailId == null) channelDetailId = UUID.randomUUID().toString(); }
}
