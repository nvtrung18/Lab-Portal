package com.web.labportalbackend.common.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IpNetworkMatcherTest {

    @Test
    void matchesAnExactIpv4Address() {
        assertTrue(IpNetworkMatcher.matches("203.0.113.42", "203.0.113.42"));
        assertFalse(IpNetworkMatcher.matches("203.0.113.43", "203.0.113.42"));
    }

    @Test
    void matchesAnIpv4CidrRange() {
        assertTrue(IpNetworkMatcher.matches("203.0.113.42", "203.0.113.0/24"));
        assertFalse(IpNetworkMatcher.matches("203.0.114.42", "203.0.113.0/24"));
    }

    @Test
    void matchesIpv6CidrRange() {
        assertTrue(IpNetworkMatcher.matches("2001:db8::42", "2001:db8::/64"));
        assertFalse(IpNetworkMatcher.matches("2001:db9::42", "2001:db8::/64"));
    }

    @Test
    void normalizesAnExactAddressToHostCidr() {
        assertEquals("203.0.113.42/32", IpNetworkMatcher.normalize("203.0.113.42"));
        assertEquals("2001:db8:0:0:0:0:0:42/128", IpNetworkMatcher.normalize("2001:db8::42"));
    }

    @Test
    void rejectsInvalidNetworkValues() {
        assertThrows(IllegalArgumentException.class, () -> IpNetworkMatcher.normalize("lab.example.com"));
        assertThrows(IllegalArgumentException.class, () -> IpNetworkMatcher.normalize("203.0.113.0/33"));
        assertThrows(IllegalArgumentException.class, () -> IpNetworkMatcher.normalize("203.0.113.0/not-a-prefix"));
        assertThrows(IllegalArgumentException.class, () -> IpNetworkMatcher.matches("203.0.113.42/24", "203.0.113.0/24"));
    }
}
