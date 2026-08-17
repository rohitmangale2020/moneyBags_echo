package com.bank.product.api;

import com.bank.product.domain.Product;
import com.bank.product.domain.ProductFee;
import com.bank.product.domain.ProductRate;
import com.bank.product.domain.ProductTerm;
import com.bank.product.domain.ProductType;
import com.bank.product.repository.*;
import com.training.platform.auditclient.AuditClient;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceAuditTest {
    @Mock private ProductRepository products;
    @Mock private ProductTypeRepository productTypes;
    @Mock private ProductRateRepository rates;
    @Mock private ProductTermRepository terms;
    @Mock private ProductFeeRepository fees;
    @Mock private ProductStatusHistoryRepository statusHistory;
    @Mock private AuditClient auditClient;
    @Mock private ProductRetirementImpactRepository retirementImpact;

    private ProductService service;
    private Product product;
    private ProductRate rate;
    private ProductTerm term;
    private ProductFee fee;

    @BeforeEach
    void setUp() {
        service = new ProductService(products, productTypes, rates, terms, fees, statusHistory, auditClient,retirementImpact);

        ProductType type = new ProductType();
        type.setProductTypeCode("SAVINGS");
        type.setProductTypeName("Savings");
        type.setStatus("ACTIVE");

        product = new Product();
        product.setProductId(38L);
        product.setProductCode("SAVINGS-01");
        product.setProductName("Savings Account");
        product.setProductType(type);
        product.setDescription("Standard savings account");
        product.setMinimumBalance(new BigDecimal("1000.0000"));
        product.setMaximumBalance(new BigDecimal("1000000.0000"));
        product.setCurrency("INR");
        product.setStatus("ACTIVE");

        rate = new ProductRate();
        rate.setProductRateId(101L);
        rate.setProduct(product);
        rate.setInterestRate(new BigDecimal("6.5000"));

        term = new ProductTerm();
        term.setProductTermId(102L);
        term.setProduct(product);

        fee = new ProductFee();
        fee.setProductFeeId(103L);
        fee.setProduct(product);
        fee.setAnnualMaintenanceFee(new BigDecimal("100.0000"));

        when(products.findByProductCode("SAVINGS-01")).thenReturn(Optional.of(product));
        when(productTypes.findById("SAVINGS")).thenReturn(Optional.of(type));
        when(products.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(rates.findByProductProductId(38L)).thenReturn(Optional.of(rate));
        when(terms.findByProductProductId(38L)).thenReturn(Optional.of(term));
        when(fees.findByProductProductId(38L)).thenReturn(Optional.of(fee));
        when(rates.save(any(ProductRate.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(terms.save(any(ProductTerm.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fees.save(any(ProductFee.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditClient.changes(anyMap(), anyMap())).thenAnswer(invocation -> changes(
                invocation.getArgument(0), invocation.getArgument(1)));
    }

    @Test
    void changingOnlyRateWritesOnlyRateAuditEvent() {
        ProductRequest request = new ProductRequest(
                "SAVINGS-01", "Savings Account", "SAVINGS", "Standard savings account",
                new BigDecimal("1000.0000"), new BigDecimal("1000000.0000"), "INR", "ACTIVE",
                new RateRequest(new BigDecimal("7.2500")),
                new TermRequest(null, null, null, null, null, null),
                new FeeRequest(new BigDecimal("100.0000")));

        service.update("SAVINGS-01", request);

        verify(auditClient).success(eq("products"), eq("PRODUCT_RATE_CHANGED"),
                eq("Interest rate changed"), anyMap());
        verify(auditClient, never()).success(eq("products"), eq("PRODUCT_UPDATED"), any(), anyMap());
        verify(auditClient, never()).success(eq("products"), eq("PRODUCT_DETAILS_CHANGED"), any(), anyMap());
        verify(auditClient, never()).success(eq("products"), eq("PRODUCT_TERM_CHANGED"), any(), anyMap());
        verify(auditClient, never()).success(eq("products"), eq("PRODUCT_FEE_CHANGED"), any(), anyMap());
    }

    private Map<String, Object> changes(Map<String, ?> previous, Map<String, ?> current) {
        Set<String> fields = new LinkedHashSet<>();
        fields.addAll(previous.keySet());
        fields.addAll(current.keySet());
        Map<String, Object> oldValues = new LinkedHashMap<>();
        Map<String, Object> newValues = new LinkedHashMap<>();
        for (String field : fields) {
            if (!same(previous.get(field), current.get(field))) {
                oldValues.put(field, previous.get(field));
                newValues.put(field, current.get(field));
            }
        }
        if (newValues.isEmpty()) return Map.of();
        return Map.of(
                "changedFields", String.join(",", newValues.keySet()),
                "oldValuesJson", oldValues.toString(),
                "newValuesJson", newValues.toString());
    }

    private boolean same(Object previous, Object current) {
        if (previous instanceof BigDecimal oldNumber && current instanceof BigDecimal newNumber) {
            return oldNumber.compareTo(newNumber) == 0;
        }
        return Objects.equals(previous, current);
    }
}
