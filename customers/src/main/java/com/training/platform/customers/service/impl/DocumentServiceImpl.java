package com.training.platform.customers.service.impl;

import com.training.platform.customers.dto.DocumentRequest;
import com.training.platform.customers.dto.DocumentResponse;
import com.training.platform.customers.entity.CustomerEntity;
import com.training.platform.customers.entity.DocumentEntity;
import com.training.platform.customers.exception.BadRequestException;
import com.training.platform.customers.exception.ResourceNotFoundException;
import com.training.platform.customers.repository.CustomerRepository;
import com.training.platform.customers.repository.DocumentRepository;
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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final CustomerRepository customerRepository;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Override
    public DocumentResponse uploadDocument(Long customerId, MultipartFile file, DocumentRequest requestDto) {
        CustomerEntity customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id " + customerId));

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Document file is required");
        }

        String savedPath = saveFile(customerId, file);

        DocumentEntity entity = new DocumentEntity();
        entity.setCustomer(customer);
        entity.setDocumentType(requestDto.getDocumentType());
        entity.setDocumentNumber(requestDto.getDocumentNumber());
        entity.setFilePath(savedPath);
        entity.setIssueDate(requestDto.getIssueDate());
        entity.setExpiryDate(requestDto.getExpiryDate());
        entity.setStatus(requestDto.getStatus());
        entity.setVerifiedBy(requestDto.getVerifiedBy());
        entity.setRejectedReason(requestDto.getRejectedReason());
        entity.setRemarks(requestDto.getRemarks());
        entity.setUpdatedBy(requestDto.getUpdatedBy());
        entity.setUpdatedOn(LocalDateTime.now());

        DocumentEntity saved = documentRepository.save(entity);
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

        if (file != null && !file.isEmpty()) {
            String savedPath = saveFile(customerId, file);
            document.setFilePath(savedPath);
        }

        document.setDocumentType(requestDto.getDocumentType());
        document.setDocumentNumber(requestDto.getDocumentNumber());
        document.setIssueDate(requestDto.getIssueDate());
        document.setExpiryDate(requestDto.getExpiryDate());
        document.setStatus(requestDto.getStatus());
        document.setVerifiedBy(requestDto.getVerifiedBy());
        document.setRejectedReason(requestDto.getRejectedReason());
        document.setRemarks(requestDto.getRemarks());
        document.setUpdatedBy(requestDto.getUpdatedBy());
        document.setUpdatedOn(LocalDateTime.now());

        DocumentEntity updated = documentRepository.save(document);
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
}