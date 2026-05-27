package com.web.labportalbackend.research.service;

import com.web.labportalbackend.research.dto.request.CreateTopicRequest;
import com.web.labportalbackend.research.dto.response.TopicResponse;

import java.util.List;

public interface ResearchTopicService {
    TopicResponse createTopic(CreateTopicRequest request);

    List<TopicResponse> getByLab(Long labId);
}
