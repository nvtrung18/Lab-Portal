package com.web.labportalbackend.ai.client;

public interface AiGatewayClient {

    AiChatResponse chat(AiGatewayRequest request);

    AiSuggestionResponse suggestions(AiGatewayRequest request);
}
