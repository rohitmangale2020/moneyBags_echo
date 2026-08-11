package com.bank.product.api;

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
import java.util.List;

@Service @RequiredArgsConstructor @Transactional
public class ProductService {
    private final ProductRepository products;
    private final ProductTypeRepository productTypes;
    private final ProductRateRepository rates;
    private final ProductTermRepository terms;
    private final ProductFeeRepository fees;
    private final ProductStatusHistoryRepository statusHistory;

    public ProductResponse create(ProductRequest request) {
        if (products.findByProductCode(request.productCode()).isPresent()) throw new IllegalArgumentException("Product code already exists");
        Product product = new Product(); apply(product, request); product = products.save(product); saveConfiguration(product, request); return toResponse(product);
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
        if (!product.getProductCode().equals(request.productCode())) throw new IllegalArgumentException("Product code cannot be changed after creation");
        apply(product, request); product = products.save(product); saveConfiguration(product, request); return toResponse(product);
    }
    public ProductResponse updateStatus(String productCode, StatusRequest request) {
        Product product = getByCode(productCode);
        if ("RETIRED".equals(product.getStatus())) throw new IllegalArgumentException("A retired product cannot be reactivated or deactivated");
        String previousStatus = product.getStatus(); product.setStatus(request.status()); product = products.save(product);
        ProductStatusHistory history = new ProductStatusHistory(); history.setProduct(product); history.setPreviousStatus(previousStatus); history.setNewStatus(request.status()); history.setChangeReason(request.reason()); statusHistory.save(history);
        return toResponse(product);
    }
    public void retire(String productCode) { Product product = getByCode(productCode); product.setStatus("RETIRED"); products.save(product); }

    private Product get(Long id) { return products.findById(id).orElseThrow(() -> new EntityNotFoundException("Product " + id + " was not found")); }
    private Product getByCode(String productCode) { return products.findByProductCode(productCode).orElseThrow(() -> new EntityNotFoundException("Product code " + productCode + " was not found")); }
    @Transactional(readOnly = true) public List<ProductStatusHistoryResponse> statusHistory(String productCode) {
        return statusHistory.findByProductProductIdOrderByChangedDateDesc(getByCode(productCode).getProductId()).stream().map(history -> new ProductStatusHistoryResponse(history.getPreviousStatus(), history.getNewStatus(), history.getChangeReason(), history.getChangedDate(), history.getChangedBy())).toList();
    }
    private void apply(Product p, ProductRequest r) {
        if (r.minimumBalance() != null && r.maximumBalance() != null && r.minimumBalance().compareTo(r.maximumBalance()) > 0) throw new IllegalArgumentException("Minimum balance cannot exceed maximum balance");
        ProductType type = productTypes.findById(r.productTypeCode()).orElseThrow(() -> new EntityNotFoundException("Product type " + r.productTypeCode() + " was not found"));
        if (!"ACTIVE".equals(type.getStatus())) throw new IllegalArgumentException("Selected product type is inactive");
        validateConfiguration(r);
        p.setProductCode(r.productCode()); p.setProductName(r.productName()); p.setProductType(type); p.setDescription(r.description());
        p.setMinimumBalance("CREDIT_CARD".equals(r.productTypeCode()) ? null : r.minimumBalance());
        p.setMaximumBalance("CREDIT_CARD".equals(r.productTypeCode()) ? null : r.maximumBalance()); p.setCurrency(r.currency()); p.setStatus(r.status());
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
    private ProductResponse toResponse(Product p) {
        ProductRate rate = rates.findByProductProductId(p.getProductId()).orElse(null); ProductTerm term = terms.findByProductProductId(p.getProductId()).orElse(null); ProductFee fee = fees.findByProductProductId(p.getProductId()).orElse(null);
        RateRequest rateResponse = rate == null ? null : new RateRequest(rate.getInterestRate());
        TermRequest termResponse = term == null ? null : new TermRequest(term.getTenureMonths(), term.getInstallmentAmount(), term.getInstallmentFrequency(), term.getLockInPeriod(), term.getMaturityInstruction(), term.getPrematureWithdrawalAllowed());
        FeeRequest feeResponse = fee == null ? null : new FeeRequest(fee.getMonthlyMaintenanceFee());
        return new ProductResponse(p.getProductId(), p.getProductCode(), p.getProductName(), p.getProductType().getProductTypeCode(), p.getProductType().getProductTypeName(), p.getProductType().getDescription(), p.getDescription(), p.getMinimumBalance(), p.getMaximumBalance(), p.getCurrency(), p.getStatus(), p.getVersionNo(), p.getCreatedDate(), p.getUpdatedDate(), p.getCreatedBy(), p.getUpdatedBy(), rateResponse, termResponse, feeResponse);
    }
}
