package com.crm.service.impl;

import com.crm.dto.request.FollowUpCreateRequest;
import com.crm.dto.request.FollowUpUpdateRequest;
import com.crm.dto.response.FollowUpResponse;
import com.crm.exception.ForbiddenException;
import com.crm.exception.ResourceNotFoundException;
import com.crm.mapper.FollowUpMapper;
import com.crm.model.FollowUp;
import com.crm.model.Lead;
import com.crm.model.User;
import com.crm.repository.FollowUpRepository;
import com.crm.repository.LeadRepository;
import com.crm.repository.UserRepository;
import com.crm.service.AuditService;
import com.crm.service.FollowUpService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import com.crm.service.LeadAssignmentService;

@Service
@RequiredArgsConstructor
public class FollowUpServiceImpl implements FollowUpService {

    private final FollowUpRepository followUpRepository;
    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final LeadAssignmentService leadAssignmentService;
    private final FollowUpMapper followUpMapper;
    private final AuditService auditService;

    @Override
    @Transactional
    public FollowUpResponse createFollowUp(FollowUpCreateRequest request, Long userId) {
        return createFollowUp(request, userId, false);
    }

    @Override
    @Transactional
    public FollowUpResponse createFollowUp(FollowUpCreateRequest request, Long userId, boolean isAdmin) {
        Lead lead = leadRepository.findById(request.getLeadId())
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found with id: " + request.getLeadId()));

        if (!leadAssignmentService.isUserAllowedToAccessLead(request.getLeadId(), userId, isAdmin)) {
            throw new ForbiddenException("Access denied: You are not authorized to create follow-ups for this lead");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        FollowUp followUp = FollowUp.builder()
                .lead(lead)
                .user(user)
                .scheduledTime(request.getScheduledTime())
                .notes(request.getNotes())
                .status("PENDING")
                .build();

        FollowUp saved = followUpRepository.save(followUp);

        auditService.logAction(userId, "FollowUp", saved.getId(), "CREATE", null,
                "Scheduled for: " + saved.getScheduledTime() + " on Lead: " + lead.getName());

        return followUpMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public FollowUpResponse updateFollowUp(Long id, FollowUpUpdateRequest request, Long userId, boolean isAdmin) {
        FollowUp followUp = followUpRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Follow-up not found with id: " + id));

        if (!isAdmin && !followUp.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You can only modify your own follow-ups");
        }

        if (request.getScheduledTime() != null) {
            followUp.setScheduledTime(request.getScheduledTime());
        }
        if (request.getNotes() != null) {
            followUp.setNotes(request.getNotes());
        }
        if (request.getStatus() != null) {
            followUp.setStatus(request.getStatus().toUpperCase());
            if ("COMPLETED".equalsIgnoreCase(request.getStatus())) {
                followUp.setCompletedAt(LocalDateTime.now());
            }
        }

        FollowUp updated = followUpRepository.save(followUp);
        return followUpMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public FollowUpResponse toggleStatus(Long id, String status, Long userId, boolean isAdmin) {
        FollowUp followUp = followUpRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Follow-up not found with id: " + id));

        if (!isAdmin && !followUp.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You can only modify your own follow-ups");
        }

        followUp.setStatus(status.toUpperCase());
        if ("COMPLETED".equalsIgnoreCase(status)) {
            followUp.setCompletedAt(LocalDateTime.now());
        }

        FollowUp updated = followUpRepository.save(followUp);
        return followUpMapper.toResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowUp> getFollowUpsByLead(Long leadId) {
        return followUpRepository.findByLeadIdOrderByScheduledTimeDesc(leadId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FollowUpResponse> searchFollowUps(Long userId, String status, LocalDateTime start, LocalDateTime end, Pageable pageable) {
        org.springframework.data.jpa.domain.Specification<FollowUp> spec = (root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }
            if (status != null && !status.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }
            if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledTime"), start));
            }
            if (end != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("scheduledTime"), end));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return followUpRepository.findAll(spec, pageable).map(followUpMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowUpResponse> getOverdueFollowUps(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<FollowUp> list = (userId != null)
                ? followUpRepository.findByUserIdAndStatusAndScheduledTimeLessThanOrderByScheduledTimeAsc(userId, "PENDING", now)
                : followUpRepository.findByStatusAndScheduledTimeLessThanOrderByScheduledTimeAsc("PENDING", now);
        return list.stream()
                .map(followUpMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowUpResponse> getTodayFollowUps(Long userId) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);
        List<FollowUp> list = (userId != null)
                ? followUpRepository.findByUserIdAndStatusAndScheduledTimeBetweenOrderByScheduledTimeAsc(userId, "PENDING", startOfDay, endOfDay)
                : followUpRepository.findByStatusAndScheduledTimeBetweenOrderByScheduledTimeAsc("PENDING", startOfDay, endOfDay);
        return list.stream()
                .map(followUpMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowUpResponse> getUpcomingFollowUps(Long userId) {
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);
        List<FollowUp> list = (userId != null)
                ? followUpRepository.findByUserIdAndStatusAndScheduledTimeGreaterThanOrderByScheduledTimeAsc(userId, "PENDING", endOfDay)
                : followUpRepository.findByStatusAndScheduledTimeGreaterThanOrderByScheduledTimeAsc("PENDING", endOfDay);
        return list.stream()
                .map(followUpMapper::toResponse)
                .toList();
    }
}
