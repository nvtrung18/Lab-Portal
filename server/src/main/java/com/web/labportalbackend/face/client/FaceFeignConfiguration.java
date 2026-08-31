package com.web.labportalbackend.face.client;

import feign.Request;
import feign.Response;
import feign.RetryableException;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;

public class FaceFeignConfiguration {

    @Bean
    Request.Options faceRequestOptions() {
        return new Request.Options(3, TimeUnit.SECONDS, 3, TimeUnit.SECONDS, true);
    }

    @Bean
    Retryer faceRetryer() {
        return new Retryer.Default(100, 300, 2);
    }

    @Bean
    ErrorDecoder faceErrorDecoder() {
        ErrorDecoder defaultDecoder = new ErrorDecoder.Default();
        return (methodKey, response) -> retryableServerFailure(response, defaultDecoder, methodKey);
    }

    private Exception retryableServerFailure(
            Response response,
            ErrorDecoder defaultDecoder,
            String methodKey
    ) {
        if (response.status() >= 500) {
            return new RetryableException(
                    response.status(),
                    "Face service server failure",
                    response.request().httpMethod(),
                    null,
                    (Long) null,
                    response.request());
        }
        return defaultDecoder.decode(methodKey, response);
    }
}
