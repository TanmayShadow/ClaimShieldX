package com.example.ClaimShieldX.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.util.Date;

/** Forges tokens for the cases a real login can never produce: expired, wrongly signed, or without a subject. */
public final class TestTokens {
    private static final String OTHER_SECRET = "a-completely-different-signing-key-0123456789-abcdef";

    private TestTokens() {
    }

    public static String expired(String secret, String subject) {
        Date issued = new Date(System.currentTimeMillis() - 3_600_000);
        return Jwts.builder().setSubject(subject).setIssuedAt(issued).setExpiration(new Date(issued.getTime() + 1_800_000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes()), SignatureAlgorithm.HS256).compact();
    }

    public static String valid(String secret, String subject) {
        return Jwts.builder().setSubject(subject).setIssuedAt(new Date()).setExpiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes()), SignatureAlgorithm.HS256).compact();
    }

    public static String signedWithAnotherKey(String subject) {
        return valid(OTHER_SECRET, subject);
    }

    public static String withoutSubject(String secret) {
        return Jwts.builder().setIssuedAt(new Date()).setExpiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes()), SignatureAlgorithm.HS256).compact();
    }
}
