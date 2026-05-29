package com.web.labportalbackend.admin.audit.service.impl;

import com.web.labportalbackend.admin.audit.dto.AuditLogFilter;
import com.web.labportalbackend.admin.audit.dto.AuditLogPageResponse;
import com.web.labportalbackend.admin.audit.dto.AuditLogResponse;
import com.web.labportalbackend.admin.audit.entity.AuditLog;
import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.enums.AuditModule;
import com.web.labportalbackend.admin.audit.repository.AuditLogRepository;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void log(
            User actor,
            AuditAction action,
            AuditModule module,
            String targetType,
            Long targetId,
            String description
    ) {
        log(actor, action, module, targetType, targetId, description, null);
    }

    @Override
    @Transactional
    public void log(
            User actor,
            AuditAction action,
            AuditModule module,
            String targetType,
            Long targetId,
            String description,
            String metadataJson
    ) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActorId(actor == null ? null : actor.getId());
        auditLog.setActorName(actor == null ? "SYSTEM" : displayName(actor));
        auditLog.setActorRole(actor == null ? null : actorRoles(actor));
        auditLog.setAction(action);
        auditLog.setModule(module);
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId);
        auditLog.setDescription(description);
        auditLog.setMetadataJson(metadataJson);
        auditLogRepository.save(auditLog);
    }

    @Override
    @Transactional
    public void logCurrentUser(
            AuditAction action,
            AuditModule module,
            String targetType,
            Long targetId,
            String description
    ) {
        log(getCurrentUser(), action, module, targetType, targetId, description);
    }

    @Override
    @Transactional(readOnly = true)
    public AuditLogPageResponse getAuditLogs(AuditLogFilter filter, Pageable pageable) {
        Page<AuditLog> page = auditLogRepository.findAll(toSpecification(filter), pageable);
        List<AuditLogResponse> items = page.getContent().stream()
                .map(this::toResponse)
                .toList();
        return new AuditLogPageResponse(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    private Specification<AuditLog> toSpecification(AuditLogFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deleted")));

            if (filter.actorId() != null) {
                predicates.add(cb.equal(root.get("actorId"), filter.actorId()));
            }
            if (filter.action() != null) {
                predicates.add(cb.equal(root.get("action"), filter.action()));
            }
            if (filter.module() != null) {
                predicates.add(cb.equal(root.get("module"), filter.module()));
            }
            if (filter.fromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.fromDate()));
            }
            if (filter.toDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.toDate()));
            }
            if (StringUtils.hasText(filter.keyword())) {
                String keyword = "%" + filter.keyword().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("actorName")), keyword),
                        cb.like(cb.lower(root.get("description")), keyword),
                        cb.like(cb.lower(root.get("targetType")), keyword)
                ));
            }

            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private AuditLogResponse toResponse(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getActorId(),
                auditLog.getActorName(),
                auditLog.getActorRole(),
                auditLog.getAction(),
                auditLog.getModule(),
                auditLog.getTargetType(),
                auditLog.getTargetId(),
                auditLog.getDescription(),
                auditLog.getMetadataJson(),
                auditLog.getCreatedAt()
        );
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new AccessDeniedException("Authentication is required");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user was not found"));
    }

    private String displayName(User user) {
        return StringUtils.hasText(user.getFullName()) ? user.getFullName() : user.getUsername();
    }

    private String actorRoles(User user) {
        return user.getRoles().stream()
                .map(Role::getName)
                .sorted()
                .collect(Collectors.joining(","));
    }
}
