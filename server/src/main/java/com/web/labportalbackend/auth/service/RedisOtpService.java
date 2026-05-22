package com.web.labportalbackend.auth.service;

import java.time.Duration;

public interface RedisOtpService {
    void saveOtp(String key, String code, Duration ttl);

    String getOtp(String key);

    void delete(String key);

    void saveVerifiedToken(String tokenKey, String email, Duration ttl);

    String getVerifiedEmail(String tokenKey);
}
