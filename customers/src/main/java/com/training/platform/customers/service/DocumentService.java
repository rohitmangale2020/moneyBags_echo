package com.training.platform.customers.service;

import com.training.platform.customers.dto.DocumentRequest;
import com.training.platform.customers.dto.DocumentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {
    DocumentResponse uploadDocument(Long customerId, MultipartFile file, DocumentRequest requestDto);
    List<DocumentResponse> getDocumentsByCustomerId(Long customerId);
    DocumentResponse getDocumentById(Long customerId, Long docId);
    DocumentResponse updateDocument(Long customerId, Long docId, MultipartFile file, DocumentRequest requestDto);
    void deleteDocument(Long customerId, Long docId);
}
