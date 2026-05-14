package com.web.labportalbackend.research.entity;

import com.web.labportalbackend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "reports", indexes = {
        @Index(name = "idx_reports_task_id", columnList = "task_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_report_task_version", columnNames = {"task_id", "version"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportEntity extends BaseEntity {

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(nullable = false)
    private Integer version;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;
}
