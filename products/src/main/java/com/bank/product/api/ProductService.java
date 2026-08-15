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
import com.bank.product.repository.ProductRetirementImpactRepository;
import com.bank.product.repository.ProductTermRepository;
import com.bank.product.repository.ProductStatusHistoryRepository;
import com.bank.product.repository.ProductTypeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Value;
import java.math.BigDecimal;
import java.util.Comparator;
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
    private final ProductRetirementImpactRepository retirementImpact;

    @Value("${app.retirement-risk.medium-customer-count:11}") private long mediumRiskCustomerCount;
    @Value("${app.retirement-risk.high-customer-count:101}") private long highRiskCustomerCount;
    @Value("${app.retirement-risk.high-balance:1000000}") private BigDecimal highRiskBalance;

    public ProductResponse create(ProductRequest request) {
        if (products.findByProductCode(request.productCode()).isPresent()) throw new IllegalArgumentException("Product code already exists");
        if (!"ACTIVE".equals(request.status())) throw new IllegalArgumentException("New products must be created with ACTIVE status");
        Product product = new Product(); apply(product, request); product = products.save(product); saveConfiguration(product, request);
        recordHistory(product, "NEW", "ACTIVE", "Product created");
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
        if (!product.getProductCode().equals(request.productCode())) throw new IllegalArgumentException("Product code cannot be changed after creation");
        if (!product.getStatus().equals(request.status())) throw new IllegalArgumentException("Product status can only be changed by retiring the product");
        apply(product, request); product = products.save(product); saveConfiguration(product, request);
        recordHistory(product, product.getStatus(), product.getStatus(), "Product details updated");
        return toResponse(product);
    }
    public ProductResponse updateStatus(String productCode, StatusRequest request) {
        Product product = getByCode(productCode);
        if ("RETIRED".equals(product.getStatus())) throw new IllegalArgumentException("A retired product cannot be changed");
        if ("RETIRED".equals(request.status())) throw new IllegalArgumentException("Use the retirement flow so customer impact is assessed and recorded");
        String previousStatus = product.getStatus(); product.setStatus(request.status()); product = products.save(product);
        recordHistory(product, previousStatus, request.status(), request.reason());
        return toResponse(product);
    }
    public void retire(String productCode, String migrationProductCode) {
        Product product = getByCode(productCode);
        if ("RETIRED".equals(product.getStatus())) throw new IllegalArgumentException("Product is already retired");
        ProductRetirementImpactResponse impact = retirementImpact(product);
        Product migrationProduct = migrationProduct(product, impact, migrationProductCode);
        int migratedAccountCount = migrationProduct == null ? 0 : retirementImpact.migrateRelevantAccounts(
                String.valueOf(product.getProductId()), String.valueOf(migrationProduct.getProductId()));
        String previousStatus = product.getStatus();
        product.setStatus("RETIRED");
        product = products.save(product);
        recordHistory(product, previousStatus, "RETIRED", retirementAuditReason(impact, migrationProduct, migratedAccountCount));
    }

    @Transactional(readOnly = true)
    public ProductRetirementImpactResponse retirementImpact(String productCode) {
        return retirementImpact(getByCode(productCode));
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

    private ProductRetirementImpactResponse retirementImpact(Product product) {
        ProductRetirementImpactRepository.ImpactTotals totals = retirementImpact.findTotals(String.valueOf(product.getProductId()));
        Map<String, Long> accountsByStatus = retirementImpact.countByStatus(String.valueOf(product.getProductId()));
        return new ProductRetirementImpactResponse(
                product.getProductCode(), totals.accountCount(), totals.customerCount(), accountsByStatus,
                totals.frozenCount(), totals.totalBalance(), riskLevel(totals), recommendations(product));
    }

    private String riskLevel(ProductRetirementImpactRepository.ImpactTotals totals) {
        if (totals.accountCount() == 0) return "NO_IMPACT";
        if (totals.frozenCount() > 0 || totals.customerCount() >= highRiskCustomerCount
                || totals.totalBalance().compareTo(highRiskBalance) >= 0) return "HIGH";
        if (totals.customerCount() >= mediumRiskCustomerCount) return "MEDIUM";
        return "LOW";
    }

    private List<ProductMigrationRecommendation> recommendations(Product retiringProduct) {
        return products.findByStatus("ACTIVE").stream()
                .filter(candidate -> !candidate.getProductId().equals(retiringProduct.getProductId()))
                .filter(candidate -> candidate.getCurrency().equals(retiringProduct.getCurrency()))
                .filter(candidate -> candidate.getProductType().getProductTypeCode().equals(retiringProduct.getProductType().getProductTypeCode()))
                .map(candidate -> recommendation(retiringProduct, candidate))
                .sorted(Comparator.comparingInt(ProductMigrationRecommendation::compatibilityScore).reversed()
                        .thenComparing(ProductMigrationRecommendation::productName))
                .toList();
    }

    private ProductMigrationRecommendation recommendation(Product retiring, Product candidate) {
        int score = 75;
        if (balanceDifference(retiring, candidate).compareTo(BigDecimal.ZERO) == 0) score += 15;
        ProductRate oldRate = rates.findByProductProductId(retiring.getProductId()).orElse(null);
        ProductRate newRate = rates.findByProductProductId(candidate.getProductId()).orElse(null);
        if (decimalDifference(oldRate == null ? null : oldRate.getInterestRate(), newRate == null ? null : newRate.getInterestRate()).compareTo(BigDecimal.ONE) <= 0) score += 10;
        String reason = "Same product type; " + balanceReason(retiring, candidate)
                + "; compatible active product";
        return new ProductMigrationRecommendation(candidate.getProductId(), candidate.getProductCode(), candidate.getProductName(), score, reason);
    }

    private BigDecimal balanceDifference(Product left, Product right) {
        return decimalDifference(left.getMinimumBalance(), right.getMinimumBalance());
    }

    private BigDecimal decimalDifference(BigDecimal left, BigDecimal right) {
        return (left == null ? BigDecimal.ZERO : left).subtract(right == null ? BigDecimal.ZERO : right).abs();
    }

    private String balanceReason(Product retiring, Product candidate) {
        BigDecimal retiringMinimum = retiring.getMinimumBalance() == null ? BigDecimal.ZERO : retiring.getMinimumBalance();
        BigDecimal candidateMinimum = candidate.getMinimumBalance() == null ? BigDecimal.ZERO : candidate.getMinimumBalance();
        int comparison = candidateMinimum.compareTo(retiringMinimum);
        if (comparison == 0) return "same minimum balance";
        return comparison < 0 ? "lower minimum balance" : "higher minimum balance";
    }

    private Product migrationProduct(Product retiringProduct, ProductRetirementImpactResponse impact, String migrationProductCode) {
        if (impact.affectedAccountCount() == 0) return null;
        if (migrationProductCode == null || migrationProductCode.isBlank())
            throw new IllegalArgumentException("Select an active product of the same type before retiring this product");
        Product migrationProduct = getByCode(migrationProductCode);
        if (migrationProduct.getProductId().equals(retiringProduct.getProductId()))
            throw new IllegalArgumentException("The retiring product cannot be its own migration target");
        if (!"ACTIVE".equals(migrationProduct.getStatus()))
            throw new IllegalArgumentException("The selected migration product is not active");
        if (!migrationProduct.getCurrency().equals(retiringProduct.getCurrency()))
            throw new IllegalArgumentException("The selected migration product must use the same currency");
        if (!migrationProduct.getProductType().getProductTypeCode().equals(retiringProduct.getProductType().getProductTypeCode()))
            throw new IllegalArgumentException("The selected migration product must have the same product type");
        return migrationProduct;
    }

    private String retirementAuditReason(ProductRetirementImpactResponse impact, Product migrationProduct, int migratedAccountCount) {
        String suggestedProduct = migrationProduct == null ? "none" : migrationProduct.getProductCode();
        return "Retirement impact: " + impact.riskLevel() + "; customers=" + impact.affectedCustomerCount()
                + "; accounts=" + impact.affectedAccountCount() + "; frozen=" + impact.frozenAccountCount()
                + "; balance=" + impact.totalAvailableBalance() + "; migrated=" + migratedAccountCount + "; target=" + suggestedProduct;
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
    private ProductResponse toResponse(Product p) {
        ProductRate rate = rates.findByProductProductId(p.getProductId()).orElse(null); ProductTerm term = terms.findByProductProductId(p.getProductId()).orElse(null); ProductFee fee = fees.findByProductProductId(p.getProductId()).orElse(null);
        RateRequest rateResponse = rate == null ? null : new RateRequest(rate.getInterestRate());
        TermRequest termResponse = term == null ? null : new TermRequest(term.getTenureMonths(), term.getInstallmentAmount(), term.getInstallmentFrequency(), term.getLockInPeriod(), term.getMaturityInstruction(), term.getPrematureWithdrawalAllowed());
        FeeRequest feeResponse = fee == null ? null : new FeeRequest(fee.getMonthlyMaintenanceFee());
        return new ProductResponse(p.getProductId(), p.getProductCode(), p.getProductName(), p.getProductType().getProductTypeCode(), p.getProductType().getProductTypeName(), p.getProductType().getDescription(), p.getDescription(), p.getMinimumBalance(), p.getMaximumBalance(), p.getCurrency(), p.getStatus(), p.getVersionNo(), p.getCreatedDate(), p.getUpdatedDate(), p.getCreatedBy(), p.getUpdatedBy(), rateResponse, termResponse, feeResponse);
    }
}
