package com.bank.product.api;

import com.training.platform.auditclient.AuditClient;
import com.bank.product.domain.Product;
import com.bank.product.domain.ProductFee;
import com.bank.product.domain.ProductRate;
import com.bank.product.domain.ProductTerm;
import com.bank.product.domain.ProductStatusHistory;
import com.bank.product.domain.ProductType;
import com.bank.product.repository.ProductFeeRepository;
import com.bank.product.repository.ProductRepository;
import com.bank.product.repository.ProductRateRepository;
import com.bank.product.repository.ProductTermRepository;
import com.bank.product.repository.ProductStatusHistoryRepository;
import com.bank.product.repository.ProductTypeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service @RequiredArgsConstructor @Transactional
public class ProductService {
    private final ProductRepository products;
    private final ProductTypeRepository productTypes;
    private final ProductRateRepository rates;
    private final ProductTermRepository terms;
    private final ProductFeeRepository fees;
    private final ProductStatusHistoryRepository statusHistory;
    private final AuditClient auditClient;

    public ProductResponse create(ProductRequest request) {
        if (products.findByProductCode(request.productCode()).isPresent()) throw new IllegalArgumentException("Product code already exists");
        if (!"ACTIVE".equals(request.status())) throw new IllegalArgumentException("New products must be created with ACTIVE status");
        Product product = new Product(); apply(product, request); product = products.save(product); saveConfiguration(product, request);
        recordHistory(product, "NEW", "ACTIVE", "Product created");
        auditChange(product, "PRODUCT_CREATED", "Product created", "PRODUCT", product.getProductId(),
                Map.of(), productValues(product));
        auditChange(product, "PRODUCT_RATE_CONFIGURED", "Interest rate configured", "PRODUCT_RATE",
                rateFor(product).getProductRateId(), Map.of(), rateValues(rateFor(product)));
        auditChange(product, "PRODUCT_TERM_CONFIGURED", "Product term configured", "PRODUCT_TERM",
                termFor(product).getProductTermId(), Map.of(), termValues(termFor(product)));
        auditChange(product, "PRODUCT_FEE_CONFIGURED", "Maintenance fee configured", "PRODUCT_FEE",
                feeFor(product).getProductFeeId(), Map.of(), feeValues(feeFor(product)));
        return toResponse(product);
    }
    @Transactional(readOnly = true) public List<ProductResponse> findAll(Authentication authentication) {
        boolean staff = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_EMPLOYEE"));
        return (staff ? products.findAll() : products.findByStatus("ACTIVE")).stream().map(this::toResponse).toList();
    }
    @Transactional(readOnly = true) public ProductResponse findById(Long id, Authentication authentication) {
        Product product = get(id);
        return visibleProduct(product, authentication);
    }
    @Transactional(readOnly = true) public ProductResponse findByCode(String productCode, Authentication authentication) {
        Product product = getByCode(productCode);
        return visibleProduct(product, authentication);
    }
    private ProductResponse visibleProduct(Product product, Authentication authentication) {
        boolean staff = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_EMPLOYEE"));
        if (!staff && !"ACTIVE".equals(product.getStatus())) throw new EntityNotFoundException("Product was not found");
        return toResponse(product);
    }
    public ProductResponse update(String productCode, ProductRequest request) {
        Product product = getByCode(productCode);
        Map<String, Object> previousProduct = productValues(product);
        ProductRate previousRateEntity = rateFor(product);
        ProductTerm previousTermEntity = termFor(product);
        ProductFee previousFeeEntity = feeFor(product);
        Map<String, Object> previousRate = rateValues(previousRateEntity);
        Map<String, Object> previousTerm = termValues(previousTermEntity);
        Map<String, Object> previousFee = feeValues(previousFeeEntity);
        if (!product.getProductCode().equals(request.productCode())) throw new IllegalArgumentException("Product code cannot be changed after creation");
        if (!product.getStatus().equals(request.status())) throw new IllegalArgumentException("Product status can only be changed by retiring the product");
        apply(product, request); product = products.save(product); saveConfiguration(product, request);
        ProductRate currentRate = rateFor(product);
        ProductTerm currentTerm = termFor(product);
        ProductFee currentFee = feeFor(product);
        auditChange(product, "PRODUCT_DETAILS_CHANGED", "Product details changed", "PRODUCT",
                product.getProductId(), previousProduct, productValues(product));
        auditChange(product, "PRODUCT_RATE_CHANGED", "Interest rate changed", "PRODUCT_RATE",
                currentRate.getProductRateId(), previousRate, rateValues(currentRate));
        auditChange(product, "PRODUCT_TERM_CHANGED", "Product term changed", "PRODUCT_TERM",
                currentTerm.getProductTermId(), previousTerm, termValues(currentTerm));
        auditChange(product, "PRODUCT_FEE_CHANGED", "Maintenance fee changed", "PRODUCT_FEE",
                currentFee.getProductFeeId(), previousFee, feeValues(currentFee));
        return toResponse(product);
    }
    public ProductResponse updateStatus(String productCode, StatusRequest request) {
        Product product = getByCode(productCode);
        if ("RETIRED".equals(product.getStatus())) throw new IllegalArgumentException("A retired product cannot be changed");
        String previousStatus = product.getStatus(); product.setStatus(request.status()); product = products.save(product);
        recordHistory(product, previousStatus, request.status(), request.reason());
        Map<String, Object> details = productAuditDetails(product, "PRODUCT", product.getProductId());
        details.put("previousStatus", previousStatus);
        details.put("newStatus", request.status());
        details.put("changeSummary", request.reason());
        putChanges(details, Map.of("status", previousStatus), Map.of("status", request.status()));
        auditClient.success("products", "PRODUCT_STATUS_CHANGED", "Product status changed", details);
        return toResponse(product);
    }
    public void retire(String productCode) {
        Product product = getByCode(productCode);
        if ("RETIRED".equals(product.getStatus())) throw new IllegalArgumentException("Product is already retired");
        String previousStatus = product.getStatus();
        product.setStatus("RETIRED");
        product = products.save(product);
        recordHistory(product, previousStatus, "RETIRED", "Product retired");
        Map<String, Object> details = productAuditDetails(product, "PRODUCT", product.getProductId());
        details.put("previousStatus", previousStatus);
        details.put("newStatus", "RETIRED");
        details.put("changeSummary", "Status changed from " + previousStatus + " to RETIRED");
        putChanges(details, Map.of("status", previousStatus), Map.of("status", "RETIRED"));
        auditClient.success("products", "PRODUCT_RETIRED", "Product retired", details);
    }

    private Product get(Long id) { return products.findById(id).orElseThrow(() -> new EntityNotFoundException("Product " + id + " was not found")); }
    private Product getByCode(String productCode) { return products.findByProductCode(productCode).orElseThrow(() -> new EntityNotFoundException("Product code " + productCode + " was not found")); }
    @Transactional(readOnly = true) public List<ProductStatusHistoryResponse> statusHistory(String productCode) {
        return statusHistory.findByProductProductIdOrderByChangedDateDesc(getByCode(productCode).getProductId()).stream().map(history -> new ProductStatusHistoryResponse(history.getPreviousStatus(), history.getNewStatus(), history.getChangeReason(), history.getChangedDate(), history.getChangedBy())).toList();
    }
    private void recordHistory(Product product, String previousStatus, String newStatus, String reason) {
        ProductStatusHistory history = new ProductStatusHistory();
        history.setProduct(product); history.setPreviousStatus(previousStatus); history.setNewStatus(newStatus);
        history.setChangeReason(reason); statusHistory.save(history);
    }
    private void apply(Product p, ProductRequest r) {
        if (r.minimumBalance() != null && r.maximumBalance() != null && r.minimumBalance().compareTo(r.maximumBalance()) > 0) throw new IllegalArgumentException("Minimum balance cannot exceed maximum balance");
        ProductType type = productTypes.findById(r.productTypeCode()).orElseThrow(() -> new EntityNotFoundException("Product type " + r.productTypeCode() + " was not found"));
        if (!"ACTIVE".equals(type.getStatus())) throw new IllegalArgumentException("Selected product type is retired");
        validateConfiguration(r);
        p.setProductCode(r.productCode()); p.setProductName(r.productName()); p.setProductType(type); p.setDescription(r.description());
        p.setMinimumBalance("CREDIT_CARD".equals(r.productTypeCode()) ? null : r.minimumBalance());
        p.setMaximumBalance("CREDIT_CARD".equals(r.productTypeCode()) ? null : r.maximumBalance()); p.setCurrency("INR"); p.setStatus(r.status());
    }
    private void validateConfiguration(ProductRequest request) {
        String type = request.productTypeCode();
        if (("FD".equals(type) || "RD".equals(type)) && (request.term().tenureMonths() == null || request.term().tenureMonths() <= 0)) throw new IllegalArgumentException("Tenure in months is required for FD and RD products");
        if ("RD".equals(type) && (request.term().installmentAmount() == null || request.term().installmentFrequency() == null || request.term().installmentFrequency().isBlank())) throw new IllegalArgumentException("Installment amount and frequency are required for RD products");
    }
    private void saveConfiguration(Product product, ProductRequest request) {
        ProductRate rate = rates.findByProductProductId(product.getProductId()).orElseGet(ProductRate::new); rate.setProduct(product); rate.setInterestRate(request.rate().interestRate()); rates.save(rate);
        boolean hasTerm = "FD".equals(request.productTypeCode()) || "RD".equals(request.productTypeCode());
        boolean isRd = "RD".equals(request.productTypeCode());
        ProductTerm term = terms.findByProductProductId(product.getProductId()).orElseGet(ProductTerm::new); term.setProduct(product);
        term.setTenureMonths(hasTerm ? request.term().tenureMonths() : null); term.setInstallmentAmount(isRd ? request.term().installmentAmount() : null); term.setInstallmentFrequency(isRd ? request.term().installmentFrequency() : null);
        term.setLockInPeriod(hasTerm ? request.term().lockInPeriod() : null); term.setMaturityInstruction(hasTerm ? request.term().maturityInstruction() : null); term.setPrematureWithdrawalAllowed(hasTerm ? request.term().prematureWithdrawalAllowed() : null); terms.save(term);
        ProductFee fee = fees.findByProductProductId(product.getProductId()).orElseGet(ProductFee::new); fee.setProduct(product); fee.setMonthlyMaintenanceFee(request.fee().monthlyMaintenanceFee()); fees.save(fee);
    }

    private ProductRate rateFor(Product product) {
        return rates.findByProductProductId(product.getProductId())
                .orElseThrow(() -> new EntityNotFoundException("Rate for product " + product.getProductId() + " was not found"));
    }

    private ProductTerm termFor(Product product) {
        return terms.findByProductProductId(product.getProductId())
                .orElseThrow(() -> new EntityNotFoundException("Term for product " + product.getProductId() + " was not found"));
    }

    private ProductFee feeFor(Product product) {
        return fees.findByProductProductId(product.getProductId())
                .orElseThrow(() -> new EntityNotFoundException("Fee for product " + product.getProductId() + " was not found"));
    }

    private Map<String, Object> productValues(Product product) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("productCode", product.getProductCode());
        values.put("productName", product.getProductName());
        values.put("productTypeCode", product.getProductType().getProductTypeCode());
        values.put("description", product.getDescription());
        values.put("minimumBalance", product.getMinimumBalance());
        values.put("maximumBalance", product.getMaximumBalance());
        values.put("currency", product.getCurrency());
        values.put("status", product.getStatus());
        return values;
    }

    private Map<String, Object> rateValues(ProductRate rate) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("interestRate", rate.getInterestRate());
        return values;
    }

    private Map<String, Object> termValues(ProductTerm term) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tenureMonths", term.getTenureMonths());
        values.put("installmentAmount", term.getInstallmentAmount());
        values.put("installmentFrequency", term.getInstallmentFrequency());
        values.put("lockInPeriod", term.getLockInPeriod());
        values.put("maturityInstruction", term.getMaturityInstruction());
        values.put("prematureWithdrawalAllowed", term.getPrematureWithdrawalAllowed());
        return values;
    }

    private Map<String, Object> feeValues(ProductFee fee) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("monthlyMaintenanceFee", fee.getMonthlyMaintenanceFee());
        return values;
    }

    private void auditChange(Product product, String action, String description, String componentType,
                             Object componentId, Map<String, ?> previousValues, Map<String, ?> newValues) {
        Map<String, Object> changes = auditClient.changes(previousValues, newValues);
        if (changes == null || changes.isEmpty()) return;
        Map<String, Object> details = productAuditDetails(product, componentType, componentId);
        details.put("previousStatus", previousValues.get("status"));
        details.put("newStatus", newValues.get("status"));
        details.put("changeSummary", description + ": " + changes.get("changedFields"));
        details.putAll(changes);
        auditClient.success("products", action, description, details);
    }

    private Map<String, Object> productAuditDetails(Product product, String componentType, Object componentId) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("productId", product.getProductId());
        details.put("componentType", componentType);
        details.put("componentId", componentId == null ? product.getProductId().toString() : componentId.toString());
        return details;
    }

    private void putChanges(Map<String, Object> details, Map<String, ?> previousValues, Map<String, ?> newValues) {
        Map<String, Object> changes = auditClient.changes(previousValues, newValues);
        if (changes != null) details.putAll(changes);
    }
    private ProductResponse toResponse(Product p) {
        ProductRate rate = rates.findByProductProductId(p.getProductId()).orElse(null); ProductTerm term = terms.findByProductProductId(p.getProductId()).orElse(null); ProductFee fee = fees.findByProductProductId(p.getProductId()).orElse(null);
        RateRequest rateResponse = rate == null ? null : new RateRequest(rate.getInterestRate());
        TermRequest termResponse = term == null ? null : new TermRequest(term.getTenureMonths(), term.getInstallmentAmount(), term.getInstallmentFrequency(), term.getLockInPeriod(), term.getMaturityInstruction(), term.getPrematureWithdrawalAllowed());
        FeeRequest feeResponse = fee == null ? null : new FeeRequest(fee.getMonthlyMaintenanceFee());
        return new ProductResponse(p.getProductId(), p.getProductCode(), p.getProductName(), p.getProductType().getProductTypeCode(), p.getProductType().getProductTypeName(), p.getProductType().getDescription(), p.getDescription(), p.getMinimumBalance(), p.getMaximumBalance(), p.getCurrency(), p.getStatus(), p.getVersionNo(), p.getCreatedDate(), p.getUpdatedDate(), p.getCreatedBy(), p.getUpdatedBy(), rateResponse, termResponse, feeResponse);
    }
}
