package com.web.labportalbackend.admin.dashboard.dto;

public record AdminDashboardStatsResponse(
        UserStats users,
        LabStats labs,
        OperationStats operations,
        ResearchStats research
) {

    public record UserStats(
            long total,
            long active,
            long banned,
            long pendingVerification,
            long students,
            long managers,
            long unassignedManagers
    ) {
    }

    public record LabStats(
            long total,
            long active,
            long inactive,
            long withoutManager
    ) {
    }

    public record OperationStats(
            long pendingApplications,
            long todaySlots,
            long todayBookings,
            long pendingComplaints,
            long pendingCleaningTasks
    ) {
    }

    public record ResearchStats(
            long activeProjects,
            long activeGroups,
            long pendingReports,
            long submittedProducts,
            double averageEvaluationScore
    ) {
    }
}
