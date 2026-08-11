package com.training.platform.customers.dto;

import com.training.platform.customers.constants.DocumentType;
import com.training.platform.customers.constants.DocumentStatusType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class DocumentRequest {
    @NotNull(message = "Document type is required")
    private DocumentType documentType;

    @NotBlank(message = "Document number is required")
    @Size(max = 100, message = "Document number must not exceed 100 characters")
    private String documentNumber;

    @PastOrPresent(message = "Issue date cannot be in the future")
    private LocalDate issueDate;
    private LocalDate expiryDate;

    @NotNull(message = "Document status is required")
    private DocumentStatusType status;

    @Size(max = 100, message = "Verified by must not exceed 100 characters")
    private String verifiedBy;

    @Size(max = 500, message = "Rejected reason must not exceed 500 characters")
    private String rejectedReason;

    @Size(max = 500, message = "Remarks must not exceed 500 characters")
    private String remarks;

    @Size(max = 100, message = "Updated by must not exceed 100 characters")
    private String updatedBy;
}
