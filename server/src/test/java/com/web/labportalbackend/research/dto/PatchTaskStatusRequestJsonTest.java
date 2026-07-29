package com.web.labportalbackend.research.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.research.dto.request.PatchTaskStatusRequest;
import com.web.labportalbackend.research.enums.TaskStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PatchTaskStatusRequestJsonTest {

    @Autowired
    private ObjectMapper objectMapper;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void exactFieldsBindAndNullOrOmittedReasonRemainValid() throws Exception {
        PatchTaskStatusRequest explicit = objectMapper.readValue(
                "{\"status\":\"IN_PROGRESS\",\"blockedReason\":null}", PatchTaskStatusRequest.class);
        PatchTaskStatusRequest omitted = objectMapper.readValue(
                "{\"status\":\"IN_PROGRESS\"}", PatchTaskStatusRequest.class);

        assertEquals(TaskStatus.IN_PROGRESS, explicit.getStatus());
        assertNull(explicit.getBlockedReason());
        assertNull(omitted.getBlockedReason());
        assertTrue(validator.validate(explicit).isEmpty());
        assertTrue(validator.validate(omitted).isEmpty());
    }

    @Test
    void unknownAndCaseVariantPropertiesAreCapturedInWireOrder() throws Exception {
        PatchTaskStatusRequest request = objectMapper.readValue(
                "{\"progressPercent\":10,\"Status\":\"TODO\",\"metadata\":{},\"status\":\"TODO\"}",
                PatchTaskStatusRequest.class);

        assertEquals(List.of("progressPercent", "Status", "metadata"), List.copyOf(request.getUnknownFields()));
        assertEquals(TaskStatus.TODO, request.getStatus());
    }

    @Test
    void sizeAndControlRulesAllowTabLfCrAndFormatButRejectOtherControl() throws Exception {
        PatchTaskStatusRequest accepted = objectMapper.readValue(
                "{\"status\":\"BLOCKED\",\"blockedReason\":\"  visible\\t\\n\\r\\u200b\\u202e  \"}",
                PatchTaskStatusRequest.class);
        PatchTaskStatusRequest tooLong = objectMapper.readValue(
                "{\"status\":\"BLOCKED\",\"blockedReason\":\"" + "x".repeat(4001) + "\"}",
                PatchTaskStatusRequest.class);
        PatchTaskStatusRequest forbiddenControl = objectMapper.readValue(
                "{\"status\":\"BLOCKED\",\"blockedReason\":\"visible\\u0000\"}",
                PatchTaskStatusRequest.class);

        assertTrue(validator.validate(accepted).isEmpty());
        assertFalse(validator.validate(tooLong).isEmpty());
        assertFalse(validator.validate(forbiddenControl).isEmpty());
    }

    @Test
    void missingStatusFailsValidation() throws Exception {
        PatchTaskStatusRequest request = objectMapper.readValue("{}", PatchTaskStatusRequest.class);
        assertFalse(validator.validate(request).isEmpty());
    }
}
