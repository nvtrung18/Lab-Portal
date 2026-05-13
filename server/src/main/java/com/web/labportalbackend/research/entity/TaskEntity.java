package com.web.labportalbackend.research.entity;

import com.web.labportalbackend.common.entity.BaseEntity;
import com.web.labportalbackend.research.enums.TaskStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tasks", indexes = {
        @Index(name = "idx_tasks_milestone_id", columnList = "milestone_id"),
        @Index(name = "idx_tasks_assignee_id", columnList = "assignee_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskEntity extends BaseEntity {

    @Column(name = "milestone_id", nullable = false)
    private Long milestoneId;

    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(nullable = false, length = 200)
    private String title;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status = TaskStatus.TODO;
}
