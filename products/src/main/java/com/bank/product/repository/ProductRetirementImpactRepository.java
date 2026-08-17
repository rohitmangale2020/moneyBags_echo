package com.bank.product.repository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

/**
 * Reads the shared ACCOUNT table.  ACCOUNT.PRODUCT_ID stores the numeric product
 * identifier as text, so callers must pass the product identifier as a string.
 */
@Repository
@RequiredArgsConstructor
public class ProductRetirementImpactRepository {
    private static final String RELEVANT_ACCOUNTS = "('ACTIVE', 'INACTIVE', 'DORMANT', 'FROZEN')";
    private final JdbcTemplate jdbcTemplate;

    public ImpactTotals findTotals(String productId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) AS account_count,
                       COUNT(DISTINCT customer_id) AS customer_count,
                       NVL(SUM(available_balance), 0) AS total_balance,
                       SUM(CASE WHEN status = 'FROZEN' THEN 1 ELSE 0 END) AS frozen_count
                  FROM ACCOUNT
                 WHERE product_id = ?
                   AND status IN %s
                """.formatted(RELEVANT_ACCOUNTS), (rs, rowNum) -> new ImpactTotals(
                        rs.getLong("account_count"),
                        rs.getLong("customer_count"),
                        rs.getBigDecimal("total_balance"),
                        rs.getLong("frozen_count")), productId);
    }

    public Map<String, Long> countByStatus(String productId) {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT status, COUNT(*) AS account_count
                  FROM ACCOUNT
                 WHERE product_id = ?
                   AND status IN %s
                 GROUP BY status
                 ORDER BY status
                """.formatted(RELEVANT_ACCOUNTS), (RowCallbackHandler) rs ->
                result.put(rs.getString("status"), rs.getLong("account_count")), productId);
        return result;
    }

    public int migrateRelevantAccounts(String retiringProductId, String migrationProductId) {
        return jdbcTemplate.update("""
                UPDATE ACCOUNT
                   SET product_id = ?, updated_at = SYSTIMESTAMP
                 WHERE product_id = ?
                   AND status IN %s
                """.formatted(RELEVANT_ACCOUNTS), migrationProductId, retiringProductId);
    }

    public record ImpactTotals(long accountCount, long customerCount, BigDecimal totalBalance, long frozenCount) { }
}
