package com.crm.service.impl;

import com.crm.dto.response.CallResponse;
import com.crm.dto.response.DashboardSummaryResponse;
import com.crm.dto.response.FollowUpResponse;
import com.crm.mapper.CallMapper;
import com.crm.mapper.FollowUpMapper;
import com.crm.model.Call;
import com.crm.model.FollowUp;
import com.crm.repository.*;
import com.crm.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final LeadRepository leadRepository;
    private final LeadAssignmentRepository leadAssignmentRepository;
    private final CallRepository callRepository;
    private final FollowUpRepository followUpRepository;
    private final SalesRepository salesRepository;
    private final CallMapper callMapper;
    private final FollowUpMapper followUpMapper;

    @Override
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getAdminDashboardSummary() {
        long totalUsers = userRepository.count();
        List<com.crm.model.User> activeUserList = userRepository.findByStatus("ACTIVE");
        long activeUsers = activeUserList.size();
        long activeAgents = activeUserList.stream()
                .filter(u -> u.getRole() != null && ("ROLE_USER".equalsIgnoreCase(u.getRole().getName()) || "USER".equalsIgnoreCase(u.getRole().getName())))
                .count();
        if (activeAgents == 0 && activeUsers > 0) {
            activeAgents = activeUsers;
        }
        long totalProjects = projectRepository.count();
        long activeProjects = projectRepository.countByStatus("ACTIVE");
        long totalLeads = leadRepository.count();
        long assignedLeads = leadRepository.countAssignedLeads();
        long unassignedLeads = totalLeads - assignedLeads;

        long totalCalls = callRepository.count();
        long connectedCalls = callRepository.countTotalConnectedCalls();

        Map<String, Long> callStatusMap = new HashMap<>();
        for (Object[] row : callRepository.countCallsByStatusGroup()) {
            if (row != null && row[0] != null) {
                callStatusMap.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }
        long missedCalls = callStatusMap.getOrDefault("MISSED", 0L);
        long noAnswerCalls = callStatusMap.getOrDefault("NO_ANSWER", 0L);
        long busyCalls = callStatusMap.getOrDefault("BUSY", 0L);

        // Calls logged today (org-wide)
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);
        long callsToday = callRepository.countByCreatedAtBetween(startOfDay, endOfDay);

        long followUpsPending = followUpRepository.countByStatus("PENDING");
        long overdueFollowUps = followUpRepository.countByStatusAndScheduledTimeLessThan("PENDING", LocalDateTime.now());
        long convertedLeads = salesRepository.count();
        BigDecimal totalRevenue = salesRepository.sumTotalDealValue();

        Map<String, Long> statusMap = new HashMap<>();
        statusMap.put("NEW", 0L);
        statusMap.put("CONTACTED", 0L);
        statusMap.put("IN_PROGRESS", 0L);
        statusMap.put("FOLLOW_UP", 0L);
        statusMap.put("CONVERTED", 0L);
        statusMap.put("CLOSED", 0L);
        for (Object[] row : leadRepository.countLeadsByStatusGroup()) {
            if (row != null && row[0] != null) {
                statusMap.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }

        Map<String, Long> outcomeMap = new HashMap<>();
        outcomeMap.put("INTERESTED", 0L);
        outcomeMap.put("NOT_INTERESTED", 0L);
        outcomeMap.put("FOLLOW_UP", 0L);
        outcomeMap.put("WRONG_NUMBER", 0L);
        outcomeMap.put("JUNK", 0L);
        outcomeMap.put("CONVERTED", convertedLeads);
        for (Object[] row : leadRepository.countLeadsByOutcomeGroup()) {
            if (row != null && row[0] != null) {
                outcomeMap.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }
        long interestedLeads = outcomeMap.getOrDefault("INTERESTED", 0L);

        List<CallResponse> recentCalls = callRepository.findRecentCallsWithLeadAndUser(PageRequest.of(0, 5)).stream()
                .map(callMapper::toResponse)
                .toList();

        return DashboardSummaryResponse.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .activeAgents(activeAgents)
                .totalProjects(totalProjects)
                .activeProjects(activeProjects)
                .totalLeads(totalLeads)
                .assignedLeads(assignedLeads)
                .unassignedLeads(unassignedLeads)
                .totalCalls(totalCalls)
                .callsToday(callsToday)
                .connectedCalls(connectedCalls)
                .missedCalls(missedCalls)
                .noAnswerCalls(noAnswerCalls)
                .busyCalls(busyCalls)
                .interestedLeads(interestedLeads)
                .followUpsPending(followUpsPending)
                .overdueFollowUps(overdueFollowUps)
                .convertedLeads(convertedLeads)
                .totalRevenue(totalRevenue)
                .leadsByStatus(statusMap)
                .leadsByOutcome(outcomeMap)
                .recentCalls(recentCalls)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getUserDashboardSummary(Long userId) {
        long myAssignedLeads = leadAssignmentRepository.countByUserIdAndIsActiveTrue(userId);

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);

        long myCallsToday = callRepository.countByUserIdAndCreatedAtBetween(userId, startOfDay, endOfDay);
        long myConnectedCallsToday = callRepository.countConnectedCallsForUserToday(userId, startOfDay, endOfDay);

        long myPendingFollowUpsToday = followUpRepository.countByUserIdAndStatusAndScheduledTimeBetween(userId, "PENDING", startOfDay, endOfDay);
        long myOverdueFollowUps = followUpRepository.countByUserIdAndStatusAndScheduledTimeLessThan(userId, "PENDING", LocalDateTime.now());

        long interestedLeads = leadRepository.countOutcomeForUser(userId, "INTERESTED");
        long myConversions = salesRepository.countByUserId(userId);

        List<FollowUp> upcomingList = (userId != null)
                ? followUpRepository.findByUserIdAndStatusAndScheduledTimeGreaterThanOrderByScheduledTimeAsc(userId, "PENDING", endOfDay)
                : followUpRepository.findByStatusAndScheduledTimeGreaterThanOrderByScheduledTimeAsc("PENDING", endOfDay);
        List<FollowUpResponse> upcomingFollowUps = upcomingList.stream()
                .limit(5)
                .map(followUpMapper::toResponse)
                .toList();

        List<Call> recentCallsList = (userId != null)
                ? callRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 5))
                : callRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 5));
        List<CallResponse> recentCalls = recentCallsList.stream()
                .map(callMapper::toResponse)
                .toList();

        return DashboardSummaryResponse.builder()
                .myAssignedLeads(myAssignedLeads)
                .myCallsToday(myCallsToday)
                .myConnectedCallsToday(myConnectedCallsToday)
                .myPendingFollowUpsToday(myPendingFollowUpsToday)
                .myOverdueFollowUps(myOverdueFollowUps)
                .interestedLeads(interestedLeads)
                .myConversions(myConversions)
                .totalRevenue(salesRepository.sumDealValueByUserId(userId))
                .upcomingFollowUps(upcomingFollowUps)
                .recentCalls(recentCalls)
                .build();
    }
}
