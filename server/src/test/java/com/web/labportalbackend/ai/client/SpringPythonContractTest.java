package com.web.labportalbackend.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiToolId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

class SpringPythonContractTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final JsonNode CONTRACT = loadContract();

    @Test
    void sharedManifestMatchesSpringAssistantAndToolCatalogs() {
        Set<String> assistantKeys = Set.of(AiAssistantKey.values()).stream()
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());
        Set<String> toolIds = Set.of(AiToolId.values()).stream()
                .map(AiToolId::value)
                .collect(Collectors.toUnmodifiableSet());

        assertEquals(assistantKeys, stringSet(CONTRACT.path("assistantKeys")));
        assertEquals(toolIds, stringSet(CONTRACT.path("toolIds")));
        assertEquals(CONTRACT.path("assistantKeys").size(), assistantKeys.size());
        assertEquals(CONTRACT.path("toolIds").size(), toolIds.size());
    }

    @Test
    void sharedRequestFixtureUsesTheExactSpringPayloadShape() throws Exception {
        JsonNode requestContract = CONTRACT.path("assistantRequest");
        JsonNode fixture = requestContract.path("example");
        AiGatewayRequest request = new AiGatewayRequest(fixture, " contract-request-123 ");
        Set<String> expectedFields = new HashSet<>(stringSet(requestContract.path("requiredFields")));
        expectedFields.addAll(stringSet(requestContract.path("optionalFields")));

        assertEquals(expectedFields, fieldSet(request.payload()));
        assertEquals(fixture, OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsBytes(request.payload())));
        assertEquals("contract-request-123", request.requestId());
        assertEquals(Set.of("resourceType", "resourceId"), fieldSet(
                request.payload().path("authorizedContext").path("resources").get(0)));
        stringSet(requestContract.path("forbiddenAuthorityFields"))
                .forEach(field -> assertFalse(request.payload().has(field)));
    }

    @Test
    void gatewayUsesSharedHeaderNamesAndActiveSpringRoutes() throws Exception {
        JsonNode headers = CONTRACT.path("headers");
        JsonNode chatError = errorCase("AI_MODEL_NOT_READY");
        List<ClientRequest> chatRequests = new ArrayList<>();
        AiGatewayClient chatClient = client(request -> {
            chatRequests.add(request);
            return Mono.just(errorResponse(chatError));
        });

        assertThrows(AiGatewayException.class, () -> chatClient.chat(contractRequest()));

        assertEquals(2, chatRequests.size());
        chatRequests.forEach(request -> assertOutgoingRequest(
                request, CONTRACT.path("routes").path("chat").path("path").asText(), headers));

        JsonNode suggestionError = errorCase("AI_SERVICE_NOT_READY");
        List<ClientRequest> suggestionRequests = new ArrayList<>();
        AiGatewayClient suggestionClient = client(request -> {
            suggestionRequests.add(request);
            return Mono.just(errorResponse(suggestionError));
        });

        assertThrows(AiGatewayException.class, () -> suggestionClient.suggestions(contractRequest()));

        assertEquals(1, suggestionRequests.size());
        assertOutgoingRequest(suggestionRequests.getFirst(),
                CONTRACT.path("routes").path("legacySuggestions").path("path").asText(), headers);
    }

    @Test
    void currentPythonErrorsDecodeWithRequestIdAndExistingRetryPolicy() throws Exception {
        for (JsonNode errorCase : CONTRACT.path("errorEnvelope").path("cases")) {
            AtomicInteger attempts = new AtomicInteger();
            AiGatewayClient client = client(request -> {
                attempts.incrementAndGet();
                return Mono.just(errorResponse(errorCase));
            });

            AiGatewayException exception = assertThrows(AiGatewayException.class,
                    () -> client.chat(contractRequest()));
            JsonNode body = errorCase.path("body");
            int statusCode = errorCase.path("statusCode").asInt();
            boolean retryable = statusCode >= 500 && statusCode <= 599 && body.path("retryable").asBoolean();

            assertEquals(stringSet(CONTRACT.path("errorEnvelope").path("requiredFields")), fieldSet(body));
            assertEquals(AiGatewayFailureCategory.REMOTE, exception.failure().category());
            assertEquals(statusCode, exception.failure().statusCode());
            assertEquals(body.path("errorCode").asText(), exception.failure().errorCode());
            assertEquals(body.path("requestId").asText(), exception.failure().requestId());
            assertEquals(retryable, exception.retryable());
            assertEquals(retryable ? 2 : 1, attempts.get());
            assertThat(exception.getMessage()).doesNotContain(body.path("message").asText());
        }
    }

    @Test
    void pythonGeneratedRequestIdIsAcceptedAndRetained() throws Exception {
        JsonNode generatedIdError = errorCase("AI_INTERNAL_AUTH_FAILED").deepCopy();
        String generatedRequestId = "0123456789abcdef0123456789abcdef";
        ((ObjectNode) generatedIdError.path("body")).put("requestId", generatedRequestId);
        AiGatewayClient client = client(request -> Mono.just(errorResponse(generatedIdError)));

        AiGatewayException exception = assertThrows(AiGatewayException.class,
                () -> client.chat(contractRequest()));

        assertEquals(generatedRequestId, exception.failure().requestId());
    }

    @Test
    void incompleteErrorEnvelopeFailsAsProtocolWithoutRetry() throws Exception {
        JsonNode caseWithoutRequestId = errorCase("AI_INTERNAL_AUTH_FAILED").deepCopy();
        ((ObjectNode) caseWithoutRequestId.path("body")).remove("requestId");
        AtomicInteger attempts = new AtomicInteger();
        AiGatewayClient client = client(request -> {
            attempts.incrementAndGet();
            return Mono.just(errorResponse(caseWithoutRequestId));
        });

        AiGatewayException exception = assertThrows(AiGatewayException.class,
                () -> client.chat(contractRequest()));

        assertEquals(AiGatewayFailureCategory.PROTOCOL, exception.failure().category());
        assertNull(exception.failure().requestId());
        assertEquals(1, attempts.get());
    }

    @Test
    void sharedRuntimeAndToolStatesCannotBeMistakenForReadinessOrApproval() {
        JsonNode runtime = CONTRACT.path("runtimeStates");
        JsonNode artifactReady = runtime.path("artifactReady");
        JsonNode tool = CONTRACT.path("toolValidation");
        JsonNode authority = CONTRACT.path("authority");

        assertEquals(200, runtime.path("health").path("statusCode").asInt());
        assertEquals(503, runtime.path("ready").path("statusCode").asInt());
        assertFalse(runtime.path("ready").path("expectedFields").path("ready").asBoolean());
        assertEquals("NOT_LOADED",
                runtime.path("modelInfo").path("expectedFields").path("status").asText());
        assertEquals(200, artifactReady.path("ready").path("statusCode").asInt());
        assertEquals("READY", artifactReady.path("ready").path("expectedFields").path("status").asText());
        assertEquals("APPROVED",
                artifactReady.path("modelInfo").path("expectedFields").path("artifactState").asText());
        assertEquals("AI_SERVICE_NOT_READY", CONTRACT.path("artifactReadyPostErrors").path("chat").asText());
        assertEquals("REQUIRES_SPRING_AUTHORIZATION",
                tool.path("expected").path("executionEligibility").asText());
        assertFalse(tool.path("expected").path("executable").asBoolean());
        assertEquals("SPRING", authority.path("businessOwner").asText());
        assertFalse(authority.path("assistantKeyAuthorizes").asBoolean());
        assertFalse(authority.path("pythonAuthorizesResources").asBoolean());
        assertFalse(authority.path("pythonExecutesTools").asBoolean());
    }

    private static AiGatewayClient client(ExchangeFunction exchangeFunction) {
        return new AiGatewayClientImpl(new AiGatewayConfiguration("https://ai.example.invalid", "test-token"),
                OBJECT_MAPPER, exchangeFunction, Duration.ofSeconds(5));
    }

    private static AiGatewayRequest contractRequest() {
        return new AiGatewayRequest(CONTRACT.path("assistantRequest").path("example"), "contract-request-123");
    }

    private static ClientResponse errorResponse(JsonNode errorCase) {
        return ClientResponse.create(HttpStatusCode.valueOf(errorCase.path("statusCode").asInt()))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(errorCase.path("body").toString())
                .build();
    }

    private static JsonNode errorCase(String errorCode) {
        for (JsonNode errorCase : CONTRACT.path("errorEnvelope").path("cases")) {
            if (errorCode.equals(errorCase.path("body").path("errorCode").asText())) {
                return errorCase;
            }
        }
        throw new IllegalArgumentException("Unknown contract error code");
    }

    private static void assertOutgoingRequest(ClientRequest request, String expectedPath, JsonNode headers) {
        String tokenHeader = headers.path("internalServiceToken").asText();
        String requestIdHeader = headers.path("requestId").asText();
        String authorizationHeader = headers.path("userAuthorization").asText();

        assertEquals(HttpMethod.POST, request.method());
        assertEquals(expectedPath, request.url().getPath());
        assertEquals(MediaType.APPLICATION_JSON, request.headers().getContentType());
        assertEquals("test-token", request.headers().getFirst(tokenHeader));
        assertEquals("contract-request-123", request.headers().getFirst(requestIdHeader));
        assertNull(request.headers().getFirst(authorizationHeader));
    }

    private static Set<String> stringSet(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> fieldSet(JsonNode object) {
        Set<String> fields = new HashSet<>();
        object.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private static JsonNode loadContract() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve("contracts").resolve("ai-runtime-contract.json");
            if (Files.isRegularFile(candidate)) {
                try {
                    return OBJECT_MAPPER.readTree(candidate.toFile());
                } catch (IOException exception) {
                    throw new IllegalStateException("AI runtime contract fixture is unreadable", exception);
                }
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("AI runtime contract fixture is missing");
    }
}
