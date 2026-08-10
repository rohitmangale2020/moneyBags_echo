package com.training.platform.customers.service;

import com.training.platform.customers.dto.DocumentRequestDto;
import com.training.platform.customers.dto.DocumentResponseDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {
    DocumentResponseDto uploadDocument(Long customerId, MultipartFile file, DocumentRequestDto requestDto);
    List<DocumentResponseDto> getDocumentsByCustomerId(Long customerId);
    DocumentResponseDto getDocumentById(Long customerId, Long docId);
    DocumentResponseDto updateDocument(Long customerId, Long docId, MultipartFile file, DocumentRequestDto requestDto);
}