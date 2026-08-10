package com.training.platform.customers.dto;

import com.training.platform.customers.constants.DocumentType;
import com.training.platform.customers.constants.DocumentStatusType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class DocumentRequest {
    private DocumentType documentType;
    private String documentNumber;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private DocumentStatusType status;
    private String verifiedBy;
    private String rejectedReason;
    private String remarks;
    private String updatedBy;
}