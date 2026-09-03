package com.web.labportalbackend.auth.security;

public interface GoogleIdentityVerifier {
    GoogleIdentity verify(String credential);
}
