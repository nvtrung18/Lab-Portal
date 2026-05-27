package com.web.labportalbackend.auth.service.impl;

import com.web.labportalbackend.auth.service.RedisOtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisOtpServiceImpl implements RedisOtpService {

    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void saveOtp(String key, String code, Duration ttl) {
        redisTemplate.opsForValue().set(key, passwordEncoder.encode(code), ttl);
    }

    @Override
    public String getOtp(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    @Override
    public void saveVerifiedToken(String tokenKey, String email, Duration ttl) {
        redisTemplate.opsForValue().set(tokenKey, email, ttl);
    }

    @Override
    public String getVerifiedEmail(String tokenKey) {
        return redisTemplate.opsForValue().get(tokenKey);
    }
}
