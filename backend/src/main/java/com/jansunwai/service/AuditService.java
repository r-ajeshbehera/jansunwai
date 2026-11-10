package com.jansunwai.service;

import com.jansunwai.entity.AuditLog;
import com.jansunwai.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Who did what, when. Stores IDs and action names only, never complaint text or personal data. */
@Service
public class AuditService {
    private final AuditLogRepository logs;

    public AuditService(AuditLogRepository logs) {
        this.logs = logs;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void log(Long actorId, String action, String entityType, Long entityId, String details) {
        logs.save(new AuditLog(actorId, action, entityType, entityId, details));
    }
}
