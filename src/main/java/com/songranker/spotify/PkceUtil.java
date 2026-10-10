package com.songranker.spotify;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/** Generates PKCE (Proof Key for Code Exchange) verifier/challenge pairs for OAuth. */
final class PkceUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    private PkceUtil() {
    }

    /** A random, URL-safe verifier string (43-128 chars per RFC 7636). */
    static String generateCodeVerifier() {
        // 64 random bytes encode to 86 base64url characters, inside the 43 to 128 that RFC 7636 allows.
        byte[] bytes = new byte[64];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 challenge derived from the verifier, base64url-encoded without padding. */
    static String deriveCodeChallenge(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available on this JVM", e);
        }
    }

    /** Random opaque value to protect the auth redirect against CSRF. */
    static String generateState() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
