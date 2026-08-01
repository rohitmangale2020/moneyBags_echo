package com.training.platform.customers.entity;
import com.training.platform.customers.constants.DocumentStatusType;
import com.training.platform.customers.constants.DocumentType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_document")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DocumentEntity {


        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "doc_id")
        private Long docId;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "customer_id", nullable = false)
        private CustomerEntity customer;

        @Column(name = "document_type", nullable = false, length = 50)
        private DocumentType documentType;

        @Column(name = "document_number", length = 100)
        private String documentNumber;

        @Column(name = "file_path", nullable = false, length = 500)
        private String filePath;

        @Column(name = "issue_date")
        private LocalDate issueDate;

        @Column(name = "expiry_date")
        private LocalDate expiryDate;

        @Column(name = "status", nullable = false, length = 20)
        private DocumentStatusType status; // Uploaded, Verified, Rejected, Expired

        @Column(name = "verified_by", length = 100)
        private String verifiedBy;

        @Column(name = "verified_on")
        private LocalDateTime verifiedOn;

        @Column(name = "rejected_reason", length = 500)
        private String rejectedReason;

        @Column(name = "remarks", length = 500)
        private String remarks;

        @Column(name = "updated_on")
        private LocalDateTime updatedOn;

        @Column(name = "updated_by", length = 100)
        private String updatedBy;
    }

