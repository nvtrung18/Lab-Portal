package com.web.labportalbackend.lab.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AssignableLabResponse {
    private Long id;
    private String name;
    private String department;
    private String status;
    private Long managerId;
    private String managerName;
}
