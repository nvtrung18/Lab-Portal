package com.web.labportalbackend.auth.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Locale;

@Component
public class GoogleIdentityVerifierImpl implements GoogleIdentityVerifier {

    private final String clientId;
    private final GoogleIdTokenVerifier verifier;

    public GoogleIdentityVerifierImpl(@Value("${app.auth.google.client-id:}") String clientId) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(this.clientId.isEmpty() ? List.of() : List.of(this.clientId))
                .build();
    }

    @Override
    public GoogleIdentity verify(String credential) {
        if (clientId.isEmpty()) {
            throw new IllegalStateException("Google Sign-In chưa được cấu hình.");
        }

        try {
            GoogleIdToken idToken = verifier.verify(credential);
            if (idToken == null) {
                throw invalidCredential();
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String subject = requiredClaim(payload.getSubject());
            String email = requiredClaim(payload.getEmail()).trim().toLowerCase(Locale.ROOT);
            if (subject.length() > 255 || email.length() > 100) {
                throw invalidCredential();
            }
            boolean emailVerified = Boolean.TRUE.equals(payload.getEmailVerified());
            if (!emailVerified) {
                throw invalidCredential();
            }

            String hostedDomain = payload.getHostedDomain();
            boolean authoritativeEmail = email.endsWith("@gmail.com")
                    || (hostedDomain != null && !hostedDomain.isBlank());
            String fullName = payload.get("name") instanceof String name ? normalizeFullName(name) : null;
            return new GoogleIdentity(subject, email, fullName,
                    authoritativeEmail);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException exception) {
            throw invalidCredential();
        }
    }

    private String requiredClaim(String value) {
        if (value == null || value.isBlank()) {
            throw invalidCredential();
        }
        return value;
    }

    private String normalizeFullName(String value) {
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() <= 100 ? normalized : normalized.substring(0, 100);
    }

    private BadCredentialsException invalidCredential() {
        return new BadCredentialsException("Google credential is invalid or expired");
    }
}
