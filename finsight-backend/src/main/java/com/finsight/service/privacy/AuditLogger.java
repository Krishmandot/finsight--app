package com.finsight.service.privacy;

import com.finsight.domain.entity.AuditEvent;
import com.finsight.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Records audit events for operational tracing.
 *
 * PRIVACY CONTRACT: description must NEVER contain raw financial data,
 * account numbers, passwords, tokens, or personal data values.
 * Only operational event descriptions are permitted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogger {

    private final AuditEventRepository auditEventRepository;

    public void record(UUID userId, String eventType, String entityType, String entityId, String description) {
        AuditEvent event = AuditEvent.builder()
            .userId(userId)
            .eventType(eventType)
            .entityType(entityType)
            .entityId(entityId)
            .description(description)
            .build();

        auditEventRepository.save(event);

        // Log at INFO level — no data values, only structural identifiers
        log.info("AUDIT: type={} entity={} id={}", eventType, entityType, entityId);
    }
}
