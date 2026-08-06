package com.web.labportalbackend.ai.service;

import com.web.labportalbackend.ai.client.AiSuggestionResponse;

public interface AiSuggestionPayloadValidator {

    void validate(AiSuggestionResponse response);
}
