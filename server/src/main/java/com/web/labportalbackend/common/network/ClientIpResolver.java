package com.web.labportalbackend.common.network;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the client public address while only trusting forwarded headers from configured proxies.
 */
@Component
public class ClientIpResolver {

    private final List<String> trustedProxyCidrs;

    public ClientIpResolver(@Value("${app.network.trusted-proxy-cidrs:}") String trustedProxyCidrs) {
        this.trustedProxyCidrs = Arrays.stream(trustedProxyCidrs.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = normalizeAddress(request.getRemoteAddr());
        if (!isTrustedProxy(remoteAddress)) {
            return remoteAddress;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String forwardedClient = resolveForwardedClient(forwardedFor, remoteAddress);
            if (forwardedClient != null) {
                return forwardedClient;
            }
        }

        String realIp = request.getHeader("X-Real-IP");
        return realIp == null || realIp.isBlank() ? remoteAddress : normalizeAddress(realIp);
    }

    private boolean isTrustedProxy(String remoteAddress) {
        return trustedProxyCidrs.stream()
                .anyMatch(proxyCidr -> {
                    try {
                        return IpNetworkMatcher.matches(remoteAddress, proxyCidr);
                    } catch (IllegalArgumentException ignored) {
                        return false;
                    }
                });
    }

    private String normalizeAddress(String rawAddress) {
        if (rawAddress == null || rawAddress.isBlank()) {
            throw new IllegalStateException("Client IP address is unavailable");
        }
        return IpNetworkMatcher.normalizeAddress(rawAddress);
    }

    private String resolveForwardedClient(String forwardedFor, String remoteAddress) {
        String[] addresses = forwardedFor.split(",", -1);
        for (int index = addresses.length - 1; index >= 0; index--) {
            String address = addresses[index].trim();
            if (address.isBlank()) {
                continue;
            }
            String normalized = normalizeAddress(address);
            if (!isTrustedProxy(normalized)) {
                return normalized;
            }
        }
        return remoteAddress;
    }
}
