package com.training.platform.customers.service.impl;

import com.training.platform.auditclient.AuditClient;
import com.training.platform.customers.dto.DocumentRequest;
import com.training.platform.customers.dto.DocumentResponse;
import com.training.platform.customers.constants.DocumentStatusType;
import com.training.platform.customers.constants.DocumentType;
import com.training.platform.customers.entity.CustomerEntity;
import com.training.platform.customers.entity.DocumentEntity;
import com.training.platform.customers.exception.BadRequestException;
import com.training.platform.customers.exception.ResourceNotFoundException;
import com.training.platform.customers.repository.CustomerRepository;
import com.training.platform.customers.repository.DocumentRepository;
import com.training.platform.customers.security.CurrentUser;
import com.training.platform.customers.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final CustomerRepository customerRepository;
    private final AuditClient auditClient;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Override
    public DocumentResponse uploadDocument(Long customerId, MultipartFile file, DocumentRequest requestDto) {
        CustomerEntity customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id " + customerId));

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Document file is required");
        }
        normalizeAndValidate(requestDto);

        String savedPath = saveFile(customerId, file);

        DocumentEntity entity = new DocumentEntity();
        entity.setCustomer(customer);
        entity.setDocumentType(requestDto.getDocumentType());
        entity.setDocumentNumber(requestDto.getDocumentNumber());
        entity.setFilePath(savedPath);
        entity.setIssueDate(requestDto.getIssueDate());
        entity.setExpiryDate(requestDto.getExpiryDate());
        entity.setStatus(requestDto.getStatus());
        entity.setVerifiedBy(requestDto.getStatus().name().equalsIgnoreCase("VERIFIED") ? CurrentUser.id() : null);
        entity.setRejectedReason(requestDto.getRejectedReason());
        entity.setRemarks(requestDto.getRemarks());
        entity.setUpdatedBy(CurrentUser.id());
        entity.setUpdatedOn(LocalDateTime.now());

        DocumentEntity saved = documentRepository.save(entity);
        auditDocumentChange(customerId, saved, "DOCUMENT_UPLOADED", "Customer document uploaded",
                Map.of(), documentValues(saved));
        return toResponse(saved);
    }

    @Override
    public List<DocumentResponse> getDocumentsByCustomerId(Long customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer not found with id " + customerId);
        }

        return documentRepository.findByCustomerCustomerId(customerId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public DocumentResponse getDocumentById(Long customerId, Long docId) {
        DocumentEntity document = documentRepository.findByDocIdAndCustomerCustomerId(docId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document not found with id " + docId + " for customer " + customerId));
        return toResponse(document);
    }

    @Override
    public DocumentResponse updateDocument(Long customerId, Long docId, MultipartFile file, DocumentRequest requestDto) {
        DocumentEntity document = documentRepository.findByDocIdAndCustomerCustomerId(docId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document not found with id " + docId + " for customer " + customerId));
        Map<String, Object> previousValues = documentValues(document);
        String previousFilePath = document.getFilePath();

        if (file != null && !file.isEmpty()) {
            String savedPath = saveFile(customerId, file);
            document.setFilePath(savedPath);
        }
        normalizeAndValidate(requestDto);

        document.setDocumentType(requestDto.getDocumentType());
        document.setDocumentNumber(requestDto.getDocumentNumber());
        document.setIssueDate(requestDto.getIssueDate());
        document.setExpiryDate(requestDto.getExpiryDate());
        document.setStatus(requestDto.getStatus());
        document.setVerifiedBy(requestDto.getStatus().name().equalsIgnoreCase("VERIFIED") ? CurrentUser.id() : null);
        document.setRejectedReason(requestDto.getRejectedReason());
        document.setRemarks(requestDto.getRemarks());
        document.setUpdatedBy(CurrentUser.id());
        document.setUpdatedOn(LocalDateTime.now());

        DocumentEntity updated = documentRepository.save(document);
        Map<String, Object> currentValues = documentValues(updated);
        if (!Objects.equals(previousFilePath, updated.getFilePath())) {
            previousValues.put("documentFile", "[REDACTED]");
            currentValues.put("documentFile", "[REPLACED - REDACTED]");
        }
        auditDocumentChange(customerId, updated, "DOCUMENT_UPDATED", "Document fields changed",
                previousValues, currentValues);
        return toResponse(updated);
    }

    private String saveFile(Long customerId, MultipartFile file) {
        try {
            Path baseDir = Paths.get(uploadDir, String.valueOf(customerId));
            Files.createDirectories(baseDir);

            String originalName = file.getOriginalFilename();
            String cleanFileName = originalName == null
                    ? "document"
                    : Paths.get(originalName).getFileName().toString();

            String uniqueName = UUID.randomUUID() + "_" + cleanFileName;
            Path targetLocation = baseDir.resolve(uniqueName);

            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            return targetLocation.toString().replace("\\", "/");
        } catch (IOException e) {
            throw new BadRequestException("Failed to store document file: " + e.getMessage());
        }
    }

    private void normalizeAndValidate(DocumentRequest request) {
        DocumentType type = request.getDocumentType();
        String number = request.getDocumentNumber() == null ? "" : request.getDocumentNumber().trim().toUpperCase();
        boolean numberRequired = type != DocumentType.PHOTO
                && type != DocumentType.SIGNATURE
                && type != DocumentType.SALARY_SLIP;
        if (numberRequired && number.isBlank()) {
            throw new BadRequestException("Document number is required for " + type);
        }
        if (type == DocumentType.PAN && !number.matches("[A-Z]{5}[0-9]{4}[A-Z]")) {
            throw new BadRequestException("PAN must use the format AAAAA0000A");
        }
        if (type == DocumentType.AADHAAR && !number.matches("[2-9][0-9]{11}")) {
            throw new BadRequestException("Aadhaar must be a valid 12-digit number");
        }
        request.setDocumentNumber(numberRequired ? number : null);

        boolean hasDates = type == DocumentType.PASSPORT || type == DocumentType.DRIVING_LICENSE;
        if (!hasDates) {
            request.setIssueDate(null);
            request.setExpiryDate(null);
        } else if (request.getExpiryDate() == null) {
            throw new BadRequestException("Expiry date is required for " + type);
        } else if (!request.getExpiryDate().isAfter(java.time.LocalDate.now())) {
            throw new BadRequestException("Expiry date must not be in the past");
        } else if (request.getIssueDate() != null && request.getExpiryDate().isBefore(request.getIssueDate())) {
            throw new BadRequestException("Expiry date cannot be earlier than issue date");
        }
        if (request.getStatus() == null) request.setStatus(DocumentStatusType.UPLOADED);
    }

    private DocumentResponse toResponse(DocumentEntity entity) {
        DocumentResponse dto = new DocumentResponse();
        dto.setDocId(entity.getDocId());

        if (entity.getCustomer() != null) {
            dto.setCustomerId(entity.getCustomer().getCustomerId());
        }

        dto.setDocumentType(entity.getDocumentType());
        dto.setDocumentNumber(entity.getDocumentNumber());
        dto.setFilePath(entity.getFilePath());
        dto.setIssueDate(entity.getIssueDate());
        dto.setExpiryDate(entity.getExpiryDate());
        dto.setStatus(entity.getStatus());
        dto.setVerifiedBy(entity.getVerifiedBy());
        dto.setVerifiedOn(entity.getVerifiedOn());
        dto.setRejectedReason(entity.getRejectedReason());
        dto.setRemarks(entity.getRemarks());
        dto.setUpdatedOn(entity.getUpdatedOn());
        dto.setUpdatedBy(entity.getUpdatedBy());

        return dto;
    }

    private Map<String, Object> documentValues(DocumentEntity document) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("documentType", document.getDocumentType() == null ? null : document.getDocumentType().name());
        values.put("documentNumber", maskDocumentNumber(document.getDocumentNumber()));
        values.put("issueDate", document.getIssueDate());
        values.put("expiryDate", document.getExpiryDate());
        values.put("status", document.getStatus() == null ? null : document.getStatus().name());
        values.put("verifiedBy", document.getVerifiedBy());
        values.put("verifiedOn", document.getVerifiedOn());
        values.put("rejectedReason", document.getRejectedReason());
        values.put("remarks", document.getRemarks());
        return values;
    }

    private String maskDocumentNumber(String number) {
        if (number == null || number.isBlank()) return null;
        String lastFour = number.length() <= 4 ? number : number.substring(number.length() - 4);
        return "****" + lastFour;
    }

    private void auditDocumentChange(Long customerId, DocumentEntity document, String action,
                                     String description, Map<String, ?> previousValues,
                                     Map<String, ?> newValues) {
        Map<String, Object> changes = auditClient.changes(previousValues, newValues);
        if (changes != null && changes.isEmpty()) return;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("customerId", customerId);
        details.put("relatedEntityType", "DOCUMENT");
        details.put("relatedEntityId", document.getDocId().toString());
        details.put("previousStatus", previousValues.get("status"));
        details.put("newStatus", newValues.get("status"));
        if (changes != null) {
            details.putAll(changes);
            if (!previousValues.isEmpty()) description += ": " + changes.get("changedFields");
        }
        auditClient.success("customers", action, description, details);
    }
}
