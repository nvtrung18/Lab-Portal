package com.web.labportalbackend.face.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import feign.FeignException;
import feign.Request;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

class FaceProcessingContractTest {

    @Test
    void feignInterfaceKeepsFrozenPathsHeadersAndTypedBodies() throws Exception {
        assertEndpoint(
                FaceProcessingFeignClient.class.getMethod(
                        "embed", String.class, String.class, FaceEmbedRequest.class),
                "/v1/face/embed",
                FaceEmbedRequest.class,
                FaceEmbedResponse.class);
        assertEndpoint(
                FaceProcessingFeignClient.class.getMethod(
                        "match", String.class, String.class, FaceMatchRequest.class),
                "/v1/face/match",
                FaceMatchRequest.class,
                FaceMatchResponse.class);
    }

    @Test
    void clientUsesBoundedTimeouts() {
        Request.Options options = new FaceFeignConfiguration().faceRequestOptions();

        assertThat(options.connectTimeoutMillis()).isEqualTo(3_000);
        assertThat(options.readTimeoutMillis()).isEqualTo(3_000);
        assertThat(options.isFollowRedirects()).isTrue();
    }

    @Test
    void clientRequiresTrustedInternalToken() {
        FaceProcessingFeignClient feignClient = mock(FaceProcessingFeignClient.class);

        assertThrows(IllegalStateException.class, () -> new FaceProcessingClient(feignClient, "  "));
    }

    @Test
    void clientForwardsTrustedTokenAndGeneratedRequestId() {
        FaceProcessingFeignClient feignClient = mock(FaceProcessingFeignClient.class);
        FaceEmbedResponse expected = new FaceEmbedResponse(
                "EMBEDDED",
                List.of(0.1, 0.2),
                "test-model",
                new FaceQualityResult(true, null),
                0.91,
                0.88,
                null);
        when(feignClient.embed(eq("trusted-token"), any(), any())).thenReturn(expected);
        FaceProcessingClient client = new FaceProcessingClient(feignClient, "trusted-token");
        FaceEmbedRequest request = new FaceEmbedRequest("image", "image/jpeg", true);

        assertThat(client.embed(request)).isSameAs(expected);

        ArgumentCaptor<String> requestId = ArgumentCaptor.forClass(String.class);
        verify(feignClient).embed(eq("trusted-token"), requestId.capture(), eq(request));
        assertThat(requestId.getValue()).isNotBlank();
    }

    @Test
    void clientSanitizesDownstreamFailureAndPreservesRetryability() {
        FaceProcessingFeignClient feignClient = mock(FaceProcessingFeignClient.class);
        FeignException downstream = mock(FeignException.class);
        when(downstream.status()).thenReturn(503);
        when(feignClient.embed(any(), any(), any())).thenThrow(downstream);
        FaceProcessingClient client = new FaceProcessingClient(feignClient, "trusted-token");

        FaceServiceException failure = assertThrows(FaceServiceException.class,
                () -> client.embed(new FaceEmbedRequest("image", "image/jpeg", false)));

        assertThat(failure.getMessage()).isEqualTo("Face service request failed");
        assertThat(failure.getMessage()).doesNotContain("trusted-token");
        assertThat(failure.retryable()).isTrue();
        assertThat(failure.getCause()).isSameAs(downstream);
    }

    private static void assertEndpoint(
            Method method,
            String path,
            Class<?> requestType,
            Class<?> responseType
    ) {
        assertThat(method.getAnnotation(PostMapping.class).value()).containsExactly(path);
        assertThat(method.getReturnType()).isEqualTo(responseType);

        Annotation[][] annotations = method.getParameterAnnotations();
        assertThat(header(annotations[0]).value()).isEqualTo("X-Internal-Service-Token");
        assertThat(header(annotations[1]).value()).isEqualTo("X-Request-Id");
        assertThat(hasAnnotation(annotations[2], RequestBody.class)).isTrue();
        assertThat(method.getParameterTypes()[2]).isEqualTo(requestType);
    }

    private static RequestHeader header(Annotation[] annotations) {
        return (RequestHeader) java.util.Arrays.stream(annotations)
                .filter(RequestHeader.class::isInstance)
                .findFirst()
                .orElseThrow();
    }

    private static boolean hasAnnotation(Annotation[] annotations, Class<? extends Annotation> type) {
        return java.util.Arrays.stream(annotations).anyMatch(type::isInstance);
    }
}
