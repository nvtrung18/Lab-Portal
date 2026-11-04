package com.web.labportalbackend.ai.client;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.reactive.function.client.WebClient;

/** Real HTTP through Postman, with no model or application database involved. */
@EnabledIfEnvironmentVariable(named = "LAB_PORTAL_AI_SIMULATION", matches = "true")
class AiGatewayPostmanSimulationTest {
    private static final String BASE = "http://127.0.0.1:4501/ai";
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final AiGatewayClient client = new AiGatewayClientImpl(WebClient.builder(), mapper,
            BASE, "simulation-only-token", Duration.ofMillis(500));

    @ParameterizedTest
    @CsvSource({"401,1,REMOTE", "422,1,REMOTE", "429,1,REMOTE", "502,1,PROTOCOL", "524,1,PROTOCOL", "503,2,REMOTE", "504,1,REMOTE", "504-retryable,2,REMOTE",
                "html,1,PROTOCOL", "reset,1,PROTOCOL", "timeout,2,TIMEOUT"})
    void failuresNeverBecomeAnswersAndAttemptsAreBounded(String scenario, int attempts,
                                                         AiGatewayFailureCategory category) throws Exception {
        select(scenario);
        AiGatewayException failure = assertThrows(AiGatewayException.class, () -> client.chat(request()));
        assertEquals(category, failure.failure().category());
        assertMetrics(attempts);
    }

    @Test
    void authorizedSnapshotAcceptsZeroTokensAndSendsTrustedCorrelationHeaders() throws Exception {
        select("success");
        var response = client.chat(request());
        assertEquals("Authorized fixture", response.answer());
        assertEquals(0, response.promptTokens());
        assertEquals("AUTHORIZED_SNAPSHOT", response.metadata().get("grounding").textValue());
        assertMetrics(1);
    }

    @Test
    void directSocketCloseIsNetworkFailureRatherThanTheSimulationProxy502() throws Exception {
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/assistants/chat", exchange -> {
            attempts.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.close();
        });
        server.start();
        try {
            var direct = new AiGatewayClientImpl(WebClient.builder(), mapper,
                    "http://127.0.0.1:" + server.getAddress().getPort(), "simulation-only-token", Duration.ofSeconds(2));
            var failure = assertThrows(AiGatewayException.class, () -> direct.chat(request()));
            assertEquals(AiGatewayFailureCategory.NETWORK, failure.failure().category());
            assertEquals(2, attempts.get());
        } finally { server.stop(0); }
    }

    private AiGatewayRequest request() throws Exception {
        return new AiGatewayRequest(mapper.readTree("{\"assistantKey\":\"LAB_ASSISTANT\",\"input\":\"Test\",\"authorizedContext\":{}}"),
                "simulation-request");
    }
    private void select(String scenario) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(BASE + "/__test/scenario"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"scenario\":\"" + scenario + "\"}")).build();
        assertEquals(200, http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    private void assertMetrics(int attempts) throws Exception {
        var metrics = http.send(HttpRequest.newBuilder(URI.create(BASE + "/__test/metrics")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        var body = mapper.readTree(metrics.body());
        assertEquals(attempts, body.path("attempts").intValue());
        assertTrue(body.path("headersValid").booleanValue());
    }
}
