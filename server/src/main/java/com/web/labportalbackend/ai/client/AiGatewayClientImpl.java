package com.web.labportalbackend.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

@Component
public final class AiGatewayClientImpl implements AiGatewayClient {

    static final Duration ATTEMPT_TIMEOUT = Duration.ofSeconds(5);
    private static final String CHAT_PATH = "/v1/assistants/chat";
    private static final String SUGGESTIONS_PATH = "/v1/research/suggestions";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Service-Token";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String internalServiceToken;
    private final Duration attemptTimeout;

    @Autowired
    public AiGatewayClientImpl(WebClient.Builder webClientBuilder, ObjectMapper objectMapper,
                               @Value("${ai.gateway.base-url}") String baseUrl,
                               @Value("${ai.gateway.internal-service-token}") String internalServiceToken) {
        this(new AiGatewayConfiguration(baseUrl, internalServiceToken), objectMapper, webClientBuilder, ATTEMPT_TIMEOUT);
    }

    AiGatewayClientImpl(AiGatewayConfiguration configuration, ObjectMapper objectMapper,
                        ExchangeFunction exchangeFunction, Duration attemptTimeout) {
        this(configuration, objectMapper,
                WebClient.builder().baseUrl(configuration.baseUrl()).exchangeFunction(exchangeFunction), attemptTimeout);
    }

    private AiGatewayClientImpl(AiGatewayConfiguration configuration, ObjectMapper objectMapper,
                                WebClient.Builder webClientBuilder, Duration attemptTimeout) {
        if (objectMapper == null || webClientBuilder == null || attemptTimeout == null || attemptTimeout.isNegative()) {
            throw new IllegalArgumentException("AI gateway client dependencies are invalid");
        }
        this.webClient = webClientBuilder.baseUrl(configuration.baseUrl()).build();
        this.objectMapper = objectMapper;
        this.internalServiceToken = configuration.internalServiceToken();
        this.attemptTimeout = attemptTimeout;
    }

    @Override
    public AiChatResponse chat(AiGatewayRequest request) {
        return invoke(request, CHAT_PATH, this::decodeChat);
    }

    @Override
    public AiSuggestionResponse suggestions(AiGatewayRequest request) {
        return invoke(request, SUGGESTIONS_PATH, this::decodeSuggestion);
    }

    private <T> T invoke(AiGatewayRequest request, String path, Function<JsonNode, T> decoder) {
        if (request == null) {
            throw new IllegalArgumentException("AI gateway request is required");
        }
        return execute(request, path, decoder)
                .onErrorResume(AiGatewayException.class, failure -> failure.retryable()
                        ? execute(request, path, decoder)
                        : Mono.error(failure))
                .block();
    }

    private <T> Mono<T> execute(AiGatewayRequest request, String path, Function<JsonNode, T> decoder) {
        return webClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .header(INTERNAL_TOKEN_HEADER, internalServiceToken)
                .headers(headers -> addRequestId(headers, request.requestId()))
                .bodyValue(request.payload())
                .exchangeToMono(response -> decodeResponse(response, decoder))
                .timeout(attemptTimeout)
                .onErrorMap(throwable -> !(throwable instanceof AiGatewayException), this::transportFailure);
    }

    private <T> Mono<T> decodeResponse(ClientResponse response, Function<JsonNode, T> decoder) {
        HttpStatusCode status = response.statusCode();
        return response.bodyToMono(String.class).defaultIfEmpty("")
                .flatMap(body -> status.is2xxSuccessful() ? decodeSuccess(body, decoder) : decodeFailure(status, body));
    }

    private <T> Mono<T> decodeSuccess(String body, Function<JsonNode, T> decoder) {
        try {
            return Mono.just(decoder.apply(readJson(body)));
        } catch (AiGatewayException exception) {
            return Mono.error(exception);
        } catch (RuntimeException exception) {
            return Mono.error(protocolFailure(null));
        }
    }

    private <T> Mono<T> decodeFailure(HttpStatusCode status, String body) {
        try {
            AiGatewayErrorResponse error = decodeError(readJson(body));
            boolean retryable = status.is5xxServerError() && error.retryable();
            return Mono.error(new AiGatewayException(new AiGatewayFailure(AiGatewayFailureCategory.REMOTE,
                    status.value(), error.errorCode(), error.requestId()), retryable));
        } catch (AiGatewayException exception) {
            return Mono.error(exception);
        } catch (RuntimeException exception) {
            return Mono.error(protocolFailure(status.value()));
        }
    }

    private JsonNode readJson(String body) {
        if (body == null || body.isBlank()) {
            throw protocolFailure(null);
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.isObject()) {
                throw protocolFailure(null);
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw protocolFailure(null);
        }
    }

    private AiChatResponse decodeChat(JsonNode root) {
        return new AiChatResponse(requiredText(root, "assistantKey"), requiredText(root, "answer"),
                requiredNonNegativeInt(root, "promptTokens"), requiredNonNegativeInt(root, "completionTokens"),
                objectFields(requiredObject(root, "metadata")));
    }

    private AiSuggestionResponse decodeSuggestion(JsonNode root) {
        JsonNode confidence = root.get("confidence");
        if (confidence == null || !confidence.isNumber() || !Double.isFinite(confidence.doubleValue())
                || confidence.doubleValue() < 0 || confidence.doubleValue() > 1) {
            throw protocolFailure(null);
        }
        return new AiSuggestionResponse(requiredText(root, "assistantKey"), requiredText(root, "actionType"),
                requiredPositiveInt(root, "schemaVersion"), requiredObject(root, "payload"), confidence.doubleValue(),
                requiredText(root, "explanation"));
    }

    private AiGatewayErrorResponse decodeError(JsonNode root) {
        JsonNode retryable = root.get("retryable");
        if (retryable == null || !retryable.isBoolean()) {
            throw protocolFailure(null);
        }
        return new AiGatewayErrorResponse(requiredText(root, "errorCode"), requiredText(root, "message"),
                retryable.booleanValue(), requiredText(root, "requestId"));
    }

    private String requiredText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual() || node.textValue().isBlank()) {
            throw protocolFailure(null);
        }
        return node.textValue();
    }

    private int requiredNonNegativeInt(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.canConvertToInt() || !node.isIntegralNumber() || node.intValue() < 0) {
            throw protocolFailure(null);
        }
        return node.intValue();
    }

    private int requiredPositiveInt(JsonNode root, String field) {
        int value = requiredNonNegativeInt(root, field);
        if (value == 0) {
            throw protocolFailure(null);
        }
        return value;
    }

    private JsonNode requiredObject(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isObject()) {
            throw protocolFailure(null);
        }
        return node;
    }

    private Map<String, JsonNode> objectFields(JsonNode root) {
        Map<String, JsonNode> fields = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> iterator = root.fields();
        iterator.forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue().deepCopy()));
        return fields;
    }

    private void addRequestId(HttpHeaders headers, String requestId) {
        if (requestId != null) {
            headers.add(REQUEST_ID_HEADER, requestId);
        }
    }

    private AiGatewayException transportFailure(Throwable throwable) {
        if (throwable instanceof TimeoutException) {
            return new AiGatewayException(new AiGatewayFailure(AiGatewayFailureCategory.TIMEOUT, null, null), true);
        }
        if (throwable instanceof WebClientRequestException || throwable instanceof IOException
                || throwable.getCause() instanceof IOException) {
            return new AiGatewayException(new AiGatewayFailure(AiGatewayFailureCategory.NETWORK, null, null), true);
        }
        return protocolFailure(null);
    }

    private AiGatewayException protocolFailure(Integer statusCode) {
        return new AiGatewayException(new AiGatewayFailure(AiGatewayFailureCategory.PROTOCOL, statusCode, null), false);
    }
}
