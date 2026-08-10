package com.training.platform.customers.dto;

import com.training.platform.customers.constants.DocumentStatusType;
import com.training.platform.customers.constants.DocumentType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class DocumentResponseDto {
    private Long docId;
    private Long customerId;
    private DocumentType documentType;
    private String documentNumber;
    private String filePath;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private DocumentStatusType status;
    private String verifiedBy;
    private LocalDateTime verifiedOn;
    private String rejectedReason;
    private String remarks;
    private LocalDateTime updatedOn;
    private String updatedBy;
}