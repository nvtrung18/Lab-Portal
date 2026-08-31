package com.web.labportalbackend.admin.operations.service;

import com.web.labportalbackend.admin.operations.dto.AiActionOperationalResponse;
import com.web.labportalbackend.admin.operations.dto.AiUsageOperationalResponse;
import com.web.labportalbackend.admin.operations.dto.FaceCheckinOperationalResponse;
import com.web.labportalbackend.admin.operations.dto.OperationalLogPageResponse;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiResourceType;
import com.web.labportalbackend.face.enums.FaceCheckinResult;
import java.time.Instant;
import org.springframework.data.domain.Pageable;

public interface OperationalLogService {

    OperationalLogPageResponse<AiUsageOperationalResponse> getAiUsage(
            Long userId, String module, Long labId, Instant from, Instant to, Pageable pageable);

    OperationalLogPageResponse<AiActionOperationalResponse> getAiActions(
            Long userId, AiAssistantKey assistantKey, AiResourceType resourceType, Long resourceId,
            Instant from, Instant to, Pageable pageable);

    OperationalLogPageResponse<FaceCheckinOperationalResponse> getFaceCheckins(
            Long userId, Long labId, Long bookingId, FaceCheckinResult result,
            Instant from, Instant to, Pageable pageable);
}
