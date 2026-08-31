package com.web.labportalbackend.face.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "face-processing",
        url = "${face.service.base-url}",
        configuration = FaceFeignConfiguration.class
)
public interface FaceProcessingFeignClient {

    @PostMapping("/v1/face/embed")
    FaceEmbedResponse embed(
            @RequestHeader("X-Internal-Service-Token") String internalServiceToken,
            @RequestHeader("X-Request-Id") String requestId,
            @RequestBody FaceEmbedRequest request
    );
}
