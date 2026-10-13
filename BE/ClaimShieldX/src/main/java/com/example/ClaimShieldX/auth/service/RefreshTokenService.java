package com.example.ClaimShieldX.auth.service;

import com.example.ClaimShieldX.auth.model.RefreshToken;
import com.example.ClaimShieldX.auth.repository.RefreshTokenRepository;
import com.example.ClaimShieldX.auth.repository.UserRepository;
import com.example.ClaimShieldX.exceptions.RefreshTokenExpiredException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;


    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository){
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
    }

    public RefreshToken createRefreshToken(UUID userId){
        long EXPIRATION_TIME = 604800000;

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(userRepository.findById(userId).get());
        refreshToken.setExpiryDate(Instant.now().plusMillis(EXPIRATION_TIME)); // 7 Days
        refreshToken.setToken(UUID.randomUUID().toString());

        return refreshTokenRepository.save(refreshToken);
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(token);
            throw new RefreshTokenExpiredException("Refresh token was expired. Please sign in again.");
        }
        return token;
    }

    @Transactional
    public void deleteByUserId(UUID userId) {
        refreshTokenRepository.deleteByUser(userRepository.findById(userId).get());
    }

    public String rotateToken(UUID userId, String refreshTokenFromCookie){
        RefreshToken refreshToken = refreshTokenRepository.findByUser_IdAndToken(userId,refreshTokenFromCookie);
        if(refreshToken==null || verifyExpiration(refreshToken)==null)
            throw new RefreshTokenExpiredException("Refresh token was expired. Please sign in again.");
        return refreshToken.getToken();
    }

}
