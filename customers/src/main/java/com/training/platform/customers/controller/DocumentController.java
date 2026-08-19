package com.training.platform.customers.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.training.platform.customers.dto.DocumentRequest;
import com.training.platform.customers.dto.DocumentResponse;
import com.training.platform.customers.exception.BadRequestException;
import com.training.platform.customers.service.DocumentService;
import jakarta.validation.Valid;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/customers/{customerId}/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadDocument(
            @PathVariable Long customerId,
            @RequestPart("file") MultipartFile file,
            @RequestPart("data") byte[] data
    ) {
        try {
            DocumentRequest requestDto =
                    objectMapper.readValue(data, DocumentRequest.class);
            validate(requestDto);

            DocumentResponse response =
                    documentService.uploadDocument(customerId, file, requestDto);

            return ResponseEntity.ok(response);

        } catch (IOException exception) {
            throw new BadRequestException("Invalid JSON in the data field");
        }
    }

    private void validate(DocumentRequest requestDto) {
        Set<ConstraintViolation<DocumentRequest>> violations = validator.validate(requestDto);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    @GetMapping
    public ResponseEntity<List<DocumentResponse>> getDocumentsByCustomerId(
            @PathVariable Long customerId
    ) {
        return ResponseEntity.ok(documentService.getDocumentsByCustomerId(customerId));
    }

    @GetMapping("/{docId}")
    public ResponseEntity<DocumentResponse> getDocumentById(
            @PathVariable Long customerId,
            @PathVariable Long docId
    ) {
        return ResponseEntity.ok(documentService.getDocumentById(customerId, docId));
    }

    @PutMapping(value = "/{docId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> updateDocument(
            @PathVariable Long customerId,
            @PathVariable Long docId,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @Valid @RequestPart("data") DocumentRequest requestDto
    ) {
        return ResponseEntity.ok(documentService.updateDocument(customerId, docId, file, requestDto));
    }

}
