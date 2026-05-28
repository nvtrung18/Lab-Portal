package com.web.labportalbackend.research.dto.response;

import com.web.labportalbackend.research.dto.StudentAttendanceDTO;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class ProjectStatsOverviewResponse {

    private Long projectId;

    private String projectTitle;

    private String scope;

    private String scopeLabel;

    private Long scopeGroupId;

    private Overview overview;

    private Map<String, Long> taskByStatus;

    private List<MilestoneProgress> milestoneProgress;

    private List<StudentAttendanceDTO> attendanceByStudent;

    private List<GroupProgress> groupProgress;

    @Getter
    @Builder
    public static class Overview {
        private long memberCount;
        private long milestoneCount;
        private long completedMilestoneCount;
        private long taskCount;
        private long completedTaskCount;
        private long overdueTaskCount;
        private double taskCompletionRate;
        private long reportCount;
        private long approvedReportCount;
        private long productCount;
        private double averageEvaluationScore;
        private long attendanceCount;
        private double attendanceRate;
    }

    @Getter
    @Builder
    public static class MilestoneProgress {
        private Long milestoneId;
        private String title;
        private double progressPercent;
        private String status;
    }

    @Getter
    @Builder
    public static class GroupProgress {
        private Long groupId;
        private String groupName;
        private long memberCount;
        private double taskCompletionRate;
        private long reportCount;
        private long productCount;
        private double averageEvaluationScore;
    }
}
