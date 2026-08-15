package com.training.platform.audit.service;

import com.training.platform.audit.entity.AuditActorType;
import com.training.platform.audit.entity.AuditOutcome;
import com.training.platform.audit.entity.TransactionAuditLog;
import com.training.platform.audit.repository.TransactionAuditLogRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionAuditLogServiceTest {
    @Test
    void repeatedDeliveryReturnsStoredRowWithoutUpdatingIt() {
        TransactionAuditLogRepository repository = mock(TransactionAuditLogRepository.class);
        TransactionAuditLogService service = new TransactionAuditLogService(repository);
        TransactionAuditLog stored = auditLog("audit-1");
        TransactionAuditLog retry = auditLog("audit-1");
        when(repository.findById("audit-1")).thenReturn(Optional.of(stored));

        TransactionAuditLog result = service.record(retry);

        assertThat(result).isSameAs(stored);
        verify(repository, never()).save(retry);
    }

    private TransactionAuditLog auditLog(String auditId) {
        TransactionAuditLog log = new TransactionAuditLog();
        log.setAuditId(auditId);
        log.setCorrelationId("corr-1");
        log.setAction("TRANSACTION_COMPLETED");
        log.setActorType(AuditActorType.USER);
        log.setOutcome(AuditOutcome.SUCCESS);
        return log;
    }
}
