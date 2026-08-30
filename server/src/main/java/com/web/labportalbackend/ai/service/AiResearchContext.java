package com.web.labportalbackend.ai.service;

import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.enums.MilestoneStatus;
import com.web.labportalbackend.research.enums.ProjectStatus;
import com.web.labportalbackend.research.enums.TaskPriority;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.enums.TaskType;
import java.time.LocalDate;
import java.util.List;

/**
 * Minimal trusted-server-only AI context. It intentionally contains no JPA entities,
 * user profile fields, descriptions, audit fields, or other sensitive research data.
 */
public record AiResearchContext(
        Identity identity,
        Laboratory laboratory,
        Project project,
        List<Group> groups,
        List<Milestone> milestones,
        List<Task> tasks
) {
    public AiResearchContext {
        groups = List.copyOf(groups);
        milestones = List.copyOf(milestones);
        tasks = List.copyOf(tasks);
    }

    public record Identity(Long userId, List<String> roles) {
        public Identity {
            roles = List.copyOf(roles);
        }
    }

    public record Laboratory(Long id, String name) {
    }

    public record Project(Long id, String code, String title, ProjectStatus status,
                          LocalDate startDate, LocalDate endDate) {
    }

    public record Group(Long id, String name, GroupRole role) {
    }

    public record Milestone(Long id, String title, String name, MilestoneStatus status,
                            LocalDate startDate, LocalDate endDate, LocalDate deadline,
                            Integer progressPercent) {
    }

    public record Task(Long id, String title, TaskStatus status, TaskPriority priority,
                       TaskType type, LocalDate dueDate, LocalDate deadline,
                       Integer progressPercent, String blockedReason, boolean overdue) {
    }
}
