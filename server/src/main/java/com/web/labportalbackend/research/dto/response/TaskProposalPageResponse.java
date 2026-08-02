package com.web.labportalbackend.research.dto.response;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record TaskProposalPageResponse(
        List<TaskProposalListItemResponse> content, int page, int size, long totalElements, int totalPages
) {
    public TaskProposalPageResponse {
        content = content == null ? List.of() : List.copyOf(content);
    }
}
