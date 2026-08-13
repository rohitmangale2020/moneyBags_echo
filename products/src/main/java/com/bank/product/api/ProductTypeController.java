package com.bank.product.api;
import com.bank.product.domain.ProductType;
import com.bank.product.repository.ProductTypeRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/v1/product-types") @RequiredArgsConstructor
public class ProductTypeController {
    private final ProductTypeRepository productTypes;
    @GetMapping public List<ProductType> findAll() { return productTypes.findAll(); }
    @PostMapping @PreAuthorize("hasRole('ADMIN')") public ResponseEntity<ProductType> create(@Valid @RequestBody ProductTypeRequest request) {
        if (productTypes.existsById(request.productTypeCode())) throw new IllegalArgumentException("Product type code already exists");
        ProductType type = new ProductType(); type.setProductTypeCode(request.productTypeCode()); type.setProductTypeName(request.productTypeName()); type.setDescription(request.description()); type.setStatus("ACTIVE");
        return ResponseEntity.status(HttpStatus.CREATED).body(productTypes.save(type));
    }
}
