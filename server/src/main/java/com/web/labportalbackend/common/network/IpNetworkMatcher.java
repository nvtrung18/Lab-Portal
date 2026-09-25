package com.web.labportalbackend.common.network;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Validates and matches IP literals and CIDR networks without resolving host names.
 */
public final class IpNetworkMatcher {

    private static final Pattern IP_LITERAL = Pattern.compile("[0-9a-fA-F:.]+");

    private IpNetworkMatcher() {
    }

    public static boolean matches(String candidateIp, String configuredNetwork) {
        Parsed candidate = parse(candidateIp, false);
        Parsed network = parse(configuredNetwork, true);
        if (candidate.address().length != network.address().length) {
            return false;
        }

        byte[] candidateNetwork = mask(candidate.address(), network.prefixLength());
        return Arrays.equals(candidateNetwork, network.address());
    }

    public static String normalize(String value) {
        Parsed parsed = parse(value, true);
        return toHostAddress(parsed.address()) + "/" + parsed.prefixLength();
    }

    public static String normalizeAddress(String value) {
        return toHostAddress(parse(value, false).address());
    }

    private static Parsed parse(String rawValue, boolean allowCidr) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("IP address or CIDR is required");
        }

        String value = rawValue.trim();
        String[] parts = value.split("/", -1);
        if (parts.length > 2 || (parts.length == 2 && !allowCidr)) {
            throw new IllegalArgumentException("Invalid IP address or CIDR: " + rawValue);
        }

        String host = parts[0];
        if (!IP_LITERAL.matcher(host).matches()) {
            throw new IllegalArgumentException("Only IP literals or CIDR values are allowed");
        }

        InetAddress address;
        try {
            address = InetAddress.getByName(host);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Invalid IP address: " + rawValue, exception);
        }

        int bits = address.getAddress().length * 8;
        int prefixLength = parts.length == 2 && !parts[1].isBlank()
                ? parsePrefix(parts[1], bits)
                : bits;
        return new Parsed(mask(address.getAddress(), prefixLength), prefixLength);
    }

    private static int parsePrefix(String rawPrefix, int bits) {
        try {
            int prefixLength = Integer.parseInt(rawPrefix);
            if (prefixLength < 0 || prefixLength > bits) {
                throw new IllegalArgumentException("CIDR prefix is outside the address range");
            }
            return prefixLength;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("CIDR prefix must be numeric", exception);
        }
    }

    private static byte[] mask(byte[] address, int prefixLength) {
        byte[] masked = address.clone();
        int fullBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;
        if (fullBytes < masked.length && remainingBits > 0) {
            int mask = 0xFF << (8 - remainingBits);
            masked[fullBytes] = (byte) (masked[fullBytes] & mask);
            fullBytes++;
        }
        for (int index = fullBytes; index < masked.length; index++) {
            masked[index] = 0;
        }
        return masked;
    }

    private static String toHostAddress(byte[] address) {
        try {
            return InetAddress.getByAddress(address).getHostAddress();
        } catch (UnknownHostException exception) {
            throw new IllegalStateException("Unable to format a parsed IP address", exception);
        }
    }

    private record Parsed(byte[] address, int prefixLength) {
    }
}
