package com.bank.product.api;
import com.training.platform.auditclient.AuditClient;
import com.bank.product.domain.ProductType;
import com.bank.product.repository.ProductTypeRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/v1/product-types") @RequiredArgsConstructor
public class ProductTypeController {
    private final ProductTypeRepository productTypes;
    private final AuditClient auditClient;
    @GetMapping public List<ProductType> findAll() { return productTypes.findAll(); }
    @PostMapping @PreAuthorize("hasRole('ADMIN')") public ResponseEntity<ProductType> create(@Valid @RequestBody ProductTypeRequest request) {
        if (productTypes.existsById(request.productTypeCode())) throw new IllegalArgumentException("Product type code already exists");
        ProductType type = new ProductType(); type.setProductTypeCode(request.productTypeCode()); type.setProductTypeName(request.productTypeName()); type.setDescription(request.description()); type.setStatus("ACTIVE");
        ProductType saved = productTypes.save(type);
        Map<String, Object> currentValues = new LinkedHashMap<>();
        currentValues.put("productTypeCode", saved.getProductTypeCode());
        currentValues.put("productTypeName", saved.getProductTypeName());
        currentValues.put("description", saved.getDescription());
        currentValues.put("status", saved.getStatus());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("componentType", "PRODUCT_TYPE");
        details.put("componentId", saved.getProductTypeCode());
        details.put("newStatus", saved.getStatus());
        details.put("changeSummary", "Product type created");
        Map<String, Object> changes = auditClient.changes(Map.of(), currentValues);
        if (changes != null) details.putAll(changes);
        auditClient.success("products", "PRODUCT_TYPE_CREATED", "Product type created", details);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}
