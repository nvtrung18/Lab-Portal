package com.web.labportalbackend.auth.security;

public record GoogleIdentity(
        String subject,
        String email,
        String fullName,
        boolean authoritativeEmail
) {
}
