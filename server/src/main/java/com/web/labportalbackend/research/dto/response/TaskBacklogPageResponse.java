package com.web.labportalbackend.research.dto.response;

import java.util.List;

public record TaskBacklogPageResponse(
        List<TaskResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public TaskBacklogPageResponse {
        content = content == null ? List.of() : List.copyOf(content);
    }
}
