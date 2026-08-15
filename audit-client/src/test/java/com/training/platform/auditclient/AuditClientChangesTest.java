package com.training.platform.auditclient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditClientChangesTest {
    private final AuditClient client = new AuditClient("http://localhost:1", "test-key");
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void reportsOnlyValuesThatActuallyChanged() throws Exception {
        Map<String, Object> previous = new LinkedHashMap<>();
        previous.put("interestRate", new BigDecimal("6.5000"));
        previous.put("monthlyFee", new BigDecimal("100.00"));
        previous.put("status", "ACTIVE");

        Map<String, Object> current = new LinkedHashMap<>();
        current.put("interestRate", new BigDecimal("7.0000"));
        current.put("monthlyFee", new BigDecimal("100.0000"));
        current.put("status", "ACTIVE");

        Map<String, Object> changes = client.changes(previous, current);

        assertThat(changes.get("changedFields")).isEqualTo("interestRate");
        Map<String, Object> oldValues = jsonMap(changes.get("oldValuesJson"));
        Map<String, Object> newValues = jsonMap(changes.get("newValuesJson"));
        assertThat(oldValues).hasSize(1);
        assertThat(oldValues.get("interestRate")).isEqualTo(6.5);
        assertThat(newValues).hasSize(1);
        assertThat(newValues.get("interestRate")).isEqualTo(7.0);
    }

    @Test
    void serializesJavaTimeValuesAndRepresentsDeletedValuesAsNull() throws Exception {
        Map<String, Object> previous = new LinkedHashMap<>();
        previous.put("expiryDate", LocalDate.of(2027, 8, 14));

        Map<String, Object> changes = client.changes(previous, Map.of());

        assertThat(changes.get("changedFields")).isEqualTo("expiryDate");
        Map<String, Object> oldValues = jsonMap(changes.get("oldValuesJson"));
        Map<String, Object> newValues = jsonMap(changes.get("newValuesJson"));
        assertThat(oldValues).hasSize(1);
        assertThat(oldValues.get("expiryDate")).isEqualTo("2027-08-14");
        assertThat(newValues).hasSize(1);
        assertThat(newValues.get("expiryDate")).isNull();
    }

    @Test
    void returnsNoChangeDetailsWhenValuesAreEqual() {
        assertThat(client.changes(Map.of("status", "ACTIVE"), Map.of("status", "ACTIVE"))).isEmpty();
    }

    private Map<String, Object> jsonMap(Object json) throws Exception {
        return objectMapper.readValue(json.toString(), new TypeReference<>() { });
    }
}
