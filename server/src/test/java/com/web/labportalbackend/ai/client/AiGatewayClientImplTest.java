package com.web.labportalbackend.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

class AiGatewayClientImplTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void chatDecodesTypedResponseAndSendsFixedJsonRequestWithTrustedHeaders() throws Exception {
        List<ClientRequest> requests = new ArrayList<>();
        AiGatewayClient client = client(recordingExchange(requests, json(HttpStatus.OK, """
                {"assistantKey":"RESEARCH_ASSISTANT","answer":"Draft ready","promptTokens":12,
                 "completionTokens":34,"metadata":{"model":"test"}}
                """)));

        AiChatResponse response = client.chat(request("{}", "request-123"));

        assertEquals("RESEARCH_ASSISTANT", response.assistantKey());
        assertEquals("Draft ready", response.answer());
        assertEquals(12, response.promptTokens());
        assertEquals(34, response.completionTokens());
        assertEquals("test", response.metadata().get("model").asText());
        assertRequest(requests.getFirst(), "/v1/assistants/chat", "request-123");
    }

    @Test
    void suggestionsDecodeTypedResponseAndOmitBlankRequestId() throws Exception {
        List<ClientRequest> requests = new ArrayList<>();
        AiGatewayClient client = client(recordingExchange(requests, json(HttpStatus.OK, """
                {"assistantKey":"RESEARCH_ASSISTANT","actionType":"CREATE_TASK","schemaVersion":1,
                 "payload":{"title":"Draft"},"confidence":0.82,"explanation":"Suggested task"}
                """)));

        AiSuggestionResponse response = client.suggestions(request("{}", "  "));

        assertEquals("RESEARCH_ASSISTANT", response.assistantKey());
        assertEquals("CREATE_TASK", response.actionType());
        assertEquals(1, response.schemaVersion());
        assertEquals("Draft", response.payload().get("title").asText());
        assertEquals(0.82, response.confidence());
        assertRequest(requests.getFirst(), "/v1/research/suggestions", null);
    }

    @Test
    void retryableFiveHundredResponseRetriesOnceWithTokenOnEveryAttempt() throws Exception {
        List<ClientRequest> requests = new ArrayList<>();
        Deque<Mono<ClientResponse>> responses = new ArrayDeque<>();
        responses.add(Mono.just(json(HttpStatus.BAD_GATEWAY,
                "{\"errorCode\":\"AI_TIMEOUT\",\"message\":\"remote detail\",\"retryable\":true,"
                        + "\"requestId\":\"retry-request\"}")));
        responses.add(Mono.just(json(HttpStatus.OK, """
                {"assistantKey":"LAB_ASSISTANT","answer":"Recovered","promptTokens":1,
                 "completionTokens":2,"metadata":{}}
                """)));
        AiGatewayClient client = client(request -> {
            requests.add(request);
            return responses.removeFirst();
        });

        AiChatResponse response = client.chat(request("{}", "retry-request"));

        assertEquals("Recovered", response.answer());
        assertEquals(2, requests.size());
        requests.forEach(request -> assertRequest(request, "/v1/assistants/chat", "retry-request"));
    }

    @Test
    void eligibleNetworkFailureRetriesAtMostOnceAndExposesOnlySafeFailureData() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AiGatewayClient client = client(request -> {
            attempts.incrementAndGet();
            return Mono.error(new IOException("network diagnostic that must not escape"));
        });

        AiGatewayException exception = assertThrows(AiGatewayException.class,
                () -> client.chat(request("{}", null)));

        assertEquals(2, attempts.get());
        assertEquals(AiGatewayFailureCategory.NETWORK, exception.failure().category());
        assertNull(exception.failure().statusCode());
        assertNull(exception.failure().errorCode());
        assertThat(exception.getMessage()).doesNotContain("network diagnostic");
    }

    @Test
    void timeoutUsesConfiguredPerAttemptDeadlineAndRetriesOnceWithoutSleeping() throws Exception {
        assertEquals(Duration.ofSeconds(5), AiGatewayClientImpl.ATTEMPT_TIMEOUT);
        AtomicInteger attempts = new AtomicInteger();
        AiGatewayClient client = client(request -> {
            attempts.incrementAndGet();
            return Mono.never();
        }, Duration.ZERO);

        AiGatewayException exception = assertThrows(AiGatewayException.class,
                () -> client.suggestions(request("{}", null)));

        assertEquals(2, attempts.get());
        assertEquals(AiGatewayFailureCategory.TIMEOUT, exception.failure().category());
    }

    @Test
    void fourHundredNeverRetriesEvenWhenErrorBodyClaimsRetryable() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AiGatewayClient client = client(request -> {
            attempts.incrementAndGet();
            return Mono.just(json(HttpStatus.BAD_REQUEST,
                    "{\"errorCode\":\"INVALID\",\"message\":\"private detail\",\"retryable\":true,"
                            + "\"requestId\":\"error-request\"}"));
        });

        AiGatewayException exception = assertThrows(AiGatewayException.class,
                () -> client.chat(request("{}", null)));

        assertEquals(1, attempts.get());
        assertEquals(AiGatewayFailureCategory.REMOTE, exception.failure().category());
        assertEquals(400, exception.failure().statusCode());
        assertEquals("INVALID", exception.failure().errorCode());
        assertThat(exception.getMessage()).doesNotContain("private detail");
    }

    @Test
    void redirectStatusNeverRetries() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AiGatewayClient client = client(request -> {
            attempts.incrementAndGet();
            return Mono.just(json(HttpStatus.FOUND,
                    "{\"errorCode\":\"REDIRECT\",\"message\":\"detail\",\"retryable\":true,"
                            + "\"requestId\":\"redirect-request\"}"));
        });

        AiGatewayException exception = assertThrows(AiGatewayException.class,
                () -> client.suggestions(request("{}", null)));

        assertEquals(1, attempts.get());
        assertEquals(AiGatewayFailureCategory.REMOTE, exception.failure().category());
        assertEquals(302, exception.failure().statusCode());
    }

    @Test
    void nonRetryableMalformedAndEmptyServerErrorsNeverRetry() throws Exception {
        assertOneAttemptFailure("{\"errorCode\":\"NO_RETRY\",\"message\":\"detail\",\"retryable\":false,"
                        + "\"requestId\":\"failure-request\"}",
                AiGatewayFailureCategory.REMOTE);
        assertOneAttemptFailure("not-json", AiGatewayFailureCategory.PROTOCOL);
        assertOneAttemptFailure("", AiGatewayFailureCategory.PROTOCOL);
    }

    @Test
    void malformedSuccessResponseFailsAsProtocolError() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AiGatewayClient client = client(request -> {
            attempts.incrementAndGet();
            return Mono.just(json(HttpStatus.OK, "{\"assistantKey\":\"LAB_ASSISTANT\"}"));
        });

        AiGatewayException exception = assertThrows(AiGatewayException.class,
                () -> client.chat(request("{}", null)));

        assertEquals(1, attempts.get());
        assertEquals(AiGatewayFailureCategory.PROTOCOL, exception.failure().category());
    }

    @Test
    void configurationRejectsUnsafeBaseUrlsAndBlankTokensBeforeRequests() {
        assertThrows(IllegalArgumentException.class,
                () -> new AiGatewayConfiguration("relative/path", "token"));
        assertThrows(IllegalArgumentException.class,
                () -> new AiGatewayConfiguration("ftp://ai.example.invalid", "token"));
        assertThrows(IllegalArgumentException.class,
                () -> new AiGatewayConfiguration("https://user@ai.example.invalid", "token"));
        assertThrows(IllegalArgumentException.class,
                () -> new AiGatewayConfiguration("https://ai.example.invalid?query=true", "token"));
        assertThrows(IllegalArgumentException.class,
                () -> new AiGatewayConfiguration("https://ai.example.invalid", " "));
    }

    @Test
    void requestIdsUseSharedCorrelationFormat() throws Exception {
        assertEquals("request-123", request("{}", " request-123 ").requestId());

        for (String invalid : List.of("bad request id", "-invalid-prefix", "a".repeat(129))) {
            assertThrows(IllegalArgumentException.class, () -> request("{}", invalid));
        }
    }

    private void assertOneAttemptFailure(String body, AiGatewayFailureCategory expectedCategory) throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AiGatewayClient client = client(request -> {
            attempts.incrementAndGet();
            return Mono.just(json(HttpStatus.INTERNAL_SERVER_ERROR, body));
        });

        AiGatewayException exception = assertThrows(AiGatewayException.class,
                () -> client.chat(request("{}", null)));

        assertEquals(1, attempts.get());
        assertEquals(expectedCategory, exception.failure().category());
    }

    private static AiGatewayClient client(ExchangeFunction exchangeFunction) {
        return client(exchangeFunction, Duration.ofSeconds(5));
    }

    private static AiGatewayClient client(ExchangeFunction exchangeFunction, Duration timeout) {
        return new AiGatewayClientImpl(new AiGatewayConfiguration("https://ai.example.invalid", "test-token"),
                OBJECT_MAPPER, exchangeFunction, timeout);
    }

    private static ExchangeFunction recordingExchange(List<ClientRequest> requests, ClientResponse response) {
        return request -> {
            requests.add(request);
            return Mono.just(response);
        };
    }

    private static ClientResponse json(HttpStatus status, String body) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    private static AiGatewayRequest request(String payload, String requestId) throws Exception {
        JsonNode body = OBJECT_MAPPER.readTree(payload);
        return new AiGatewayRequest(body, requestId);
    }

    private static void assertRequest(ClientRequest request, String expectedPath, String expectedRequestId) {
        assertEquals(HttpMethod.POST, request.method());
        assertEquals(expectedPath, request.url().getPath());
        assertEquals(MediaType.APPLICATION_JSON, request.headers().getContentType());
        assertEquals("test-token", request.headers().getFirst("X-Internal-Service-Token"));
        assertEquals(expectedRequestId, request.headers().getFirst("X-Request-Id"));
    }
}
