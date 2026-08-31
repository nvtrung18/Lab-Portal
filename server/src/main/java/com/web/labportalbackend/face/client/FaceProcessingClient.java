package com.web.labportalbackend.face.client;

import feign.FeignException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FaceProcessingClient {

    private final FaceProcessingFeignClient feignClient;
    private final String internalServiceToken;

    public FaceProcessingClient(
            FaceProcessingFeignClient feignClient,
            @Value("${face.service.internal-service-token}") String internalServiceToken
    ) {
        if (internalServiceToken == null || internalServiceToken.isBlank()) {
            throw new IllegalStateException("Face internal service token is required");
        }
        this.feignClient = feignClient;
        this.internalServiceToken = internalServiceToken;
    }

    public FaceEmbedResponse embed(FaceEmbedRequest request) {
        try {
            return feignClient.embed(internalServiceToken, UUID.randomUUID().toString(), request);
        } catch (FeignException exception) {
            throw new FaceServiceException("Face service request failed", exception);
        }
    }

    public FaceMatchResponse match(FaceMatchRequest request) {
        try {
            return feignClient.match(internalServiceToken, UUID.randomUUID().toString(), request);
        } catch (FeignException exception) {
            throw new FaceServiceException("Face service request failed", exception);
        }
    }
}
