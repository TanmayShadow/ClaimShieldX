package com.example.ClaimShieldX.auth.service;

import com.example.ClaimShieldX.auth.model.RefreshToken;
import com.example.ClaimShieldX.auth.repository.RefreshTokenRepository;
import com.example.ClaimShieldX.common.utils.TokenHasher;
import com.example.ClaimShieldX.exceptions.RefreshTokenExpiredException;
import com.example.ClaimShieldX.exceptions.RefreshTokenInvalidException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Each user has one refresh token (a new login replaces it). Tokens are random, 256 bits, and only their
 * SHA-256 hash is stored. Every use rotates the token, so a token works exactly once.
 */
@Service
public class RefreshTokenService {
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Rotation(UUID userId, String newToken) {
    }

    private final RefreshTokenRepository refreshTokenRepository;
    private final long ttlSeconds;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               @Value("${app.auth.refresh-expiration-seconds:604800}") long ttlSeconds){
        this.refreshTokenRepository = refreshTokenRepository;
        this.ttlSeconds = ttlSeconds;
    }

    public long getTtlSeconds(){
        return ttlSeconds;
    }

    @Transactional
    public String issue(UUID userId){
        String token = newToken();
        refreshTokenRepository.upsert(UUID.randomUUID(), userId, TokenHasher.sha256Hex(token), Instant.now().plusSeconds(ttlSeconds));
        return token;
    }

    // noRollbackFor: removing an expired token must survive the exception that reports it.
    @Transactional(noRollbackFor = RefreshTokenExpiredException.class)
    public Rotation rotate(String presentedToken){
        if(presentedToken == null || presentedToken.isBlank()){
            throw new RefreshTokenInvalidException("No refresh token was supplied");
        }
        String oldHash = TokenHasher.sha256Hex(presentedToken);
        RefreshToken existing = refreshTokenRepository.findByToken(oldHash)
                .orElseThrow(() -> new RefreshTokenInvalidException("Refresh token is not valid"));
        Instant now = Instant.now();
        if(!existing.getExpiryDate().isAfter(now)){
            refreshTokenRepository.deleteByTokenHash(oldHash);
            throw new RefreshTokenExpiredException("Refresh token has expired");
        }

        UUID userId = existing.getUser().getId();
        String newToken = newToken();
        int updated = refreshTokenRepository.rotate(oldHash, TokenHasher.sha256Hex(newToken), now.plusSeconds(ttlSeconds), now);
        if(updated == 0){
            // Another request rotated or revoked this token between our read and our update.
            throw new RefreshTokenInvalidException("Refresh token is not valid");
        }
        return new Rotation(userId, newToken);
    }

    /** Idempotent: revoking an unknown or already revoked token is not an error. */
    @Transactional
    public void revoke(String presentedToken){
        if(presentedToken != null && !presentedToken.isBlank()){
            refreshTokenRepository.deleteByTokenHash(TokenHasher.sha256Hex(presentedToken));
        }
    }

    private String newToken(){
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
