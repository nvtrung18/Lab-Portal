package com.web.labportalbackend.research.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.research.dto.request.PatchResearchTaskRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class PatchResearchTaskRequestJsonTest {

    @Autowired
    private ObjectMapper objectMapper;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void omittedTitleIsNotPresentAndExplicitNullTitleIsPresent() throws Exception {
        PatchResearchTaskRequest omitted = objectMapper.readValue("{\"description\":\"Details\"}",
                PatchResearchTaskRequest.class);
        PatchResearchTaskRequest explicitNull = objectMapper.readValue("{\"title\":null}",
                PatchResearchTaskRequest.class);

        assertFalse(omitted.isTitlePresent());
        assertTrue(explicitNull.isTitlePresent());
        assertNull(explicitNull.getTitle());
        assertTrue(validator.validate(omitted).isEmpty());
        assertTrue(validator.validate(explicitNull).isEmpty());
    }

    @Test
    void blankTitleIsPresenceAwareAndDeferredToServiceValidation() throws Exception {
        PatchResearchTaskRequest request = objectMapper.readValue("{\"title\":\"   \"}",
                PatchResearchTaskRequest.class);

        assertTrue(request.isTitlePresent());
        assertEquals("   ", request.getTitle());
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void titleAndDescriptionSizeLimitsUseFrozenContract() throws Exception {
        PatchResearchTaskRequest valid = objectMapper.readValue(
                objectMapper.writeValueAsString(java.util.Map.of(
                        "title", "t".repeat(200),
                        "description", "d".repeat(4000))),
                PatchResearchTaskRequest.class);
        PatchResearchTaskRequest invalid = objectMapper.readValue(
                objectMapper.writeValueAsString(java.util.Map.of(
                        "title", "t".repeat(201),
                        "description", "d".repeat(4001))),
                PatchResearchTaskRequest.class);

        assertTrue(validator.validate(valid).isEmpty());
        assertEquals(2, validator.validate(invalid).size());
    }

    @Test
    void capturesUnknownFieldsInStableOrderAlongsideRecognizedFields() throws Exception {
        PatchResearchTaskRequest request = objectMapper.readValue(
                "{\"title\":\"Task\",\"status\":\"DONE\",\"projectId\":1,\"titel\":\"typo\"}",
                PatchResearchTaskRequest.class);

        assertTrue(request.isTitlePresent());
        assertEquals(List.of("status", "projectId", "titel"), List.copyOf(request.getUnknownFields()));
    }

    @Test
    void internalPresencePropertiesAreRejectedAsUnknown() throws Exception {
        PatchResearchTaskRequest request = objectMapper.readValue(
                "{\"titlePresent\":true,\"unknownFields\":[]}",
                PatchResearchTaskRequest.class);

        assertEquals(List.of("titlePresent", "unknownFields"), List.copyOf(request.getUnknownFields()));
        assertFalse(request.hasAnyRecognizedField());
    }

    @Test
    void caseVariantsAndNullUnknownValuesRemainUnknownWithApplicationMapper() throws Exception {
        PatchResearchTaskRequest request = objectMapper.readValue(
                "{\"Status\":null,\"GroupId\":100,\"titlePresent\":null}",
                PatchResearchTaskRequest.class);

        assertEquals(List.of("Status", "GroupId", "titlePresent"), List.copyOf(request.getUnknownFields()));
        assertFalse(request.hasAnyRecognizedField());
    }

    @Test
    void emptyObjectHasNoRecognizedFieldsAndNoUnknownFields() throws Exception {
        PatchResearchTaskRequest request = objectMapper.readValue("{}", PatchResearchTaskRequest.class);

        assertFalse(request.hasAnyRecognizedField());
        assertTrue(request.getUnknownFields().isEmpty());
    }
}
