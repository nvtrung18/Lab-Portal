package com.web.labportalbackend.common.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    @Test
    void ignoresForwardedHeadersWhenRequestDoesNotComeFromTrustedProxy() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.42");
        request.addHeader("X-Forwarded-For", "198.51.100.8");

        assertEquals("203.0.113.42", resolver.resolve(request));
    }

    @Test
    void resolvesTheFirstForwardedAddressFromAConfiguredProxy() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.20.30.40");
        request.addHeader("X-Forwarded-For", "198.51.100.8, 10.20.30.40");

        assertEquals("198.51.100.8", resolver.resolve(request));
    }

    @Test
    void resolvesTheFirstUntrustedAddressFromTheRightOfAProxyChain() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.20.30.40");
        request.addHeader("X-Forwarded-For", "198.51.100.9, 203.0.113.8, 10.20.30.40");

        assertEquals("203.0.113.8", resolver.resolve(request));
    }

    @Test
    void rejectsAnInvalidForwardedAddress() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.20.30.40");
        request.addHeader("X-Forwarded-For", "not-an-ip");

        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(request));
    }
}
