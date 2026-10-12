package com.crm.service.impl;

import com.crm.dto.response.AuditLogResponse;
import com.crm.mapper.AuditLogMapper;
import com.crm.model.AuditLog;
import com.crm.model.User;
import com.crm.repository.AuditLogRepository;
import com.crm.repository.UserRepository;
import com.crm.service.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final AuditLogMapper auditLogMapper;

    public AuditServiceImpl(AuditLogRepository auditLogRepository, UserRepository userRepository, AuditLogMapper auditLogMapper) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.auditLogMapper = auditLogMapper;
    }

    @Override
    @Transactional
    public void logAction(Long userId, String entityName, Long entityId, String action, String oldValue, String newValue) {
        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }

        AuditLog log = AuditLog.builder()
                .user(user)
                .entityName(entityName)
                .entityId(entityId)
                .action(action)
                .oldValue(oldValue)
                .newValue(newValue)
                .build();

        auditLogRepository.save(log);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> searchAuditLogs(String entityName, Long userId, String action, Pageable pageable) {
        org.springframework.data.jpa.domain.Specification<AuditLog> spec = (root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (entityName != null && !entityName.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("entityName"), entityName.trim()));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }
            if (action != null && !action.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("action"), action.trim()));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return auditLogRepository.findAll(spec, pageable).map(auditLogMapper::toResponse);
    }
}
