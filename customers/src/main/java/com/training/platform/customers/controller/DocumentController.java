package com.training.platform.customers.controller;

import com.training.platform.customers.dto.DocumentRequest;
import com.training.platform.customers.dto.DocumentResponse;
import com.training.platform.customers.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/customers/{customerId}/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadDocument(
            @PathVariable Long customerId,
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("data") DocumentRequest requestDto
    ) {
        DocumentResponse response = documentService.uploadDocument(customerId, file, requestDto);
        return ResponseEntity.ok(response);
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