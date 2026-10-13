package com.example.ClaimShieldX.auth.service;

import com.example.ClaimShieldX.auth.dto.LoginRequest;
import com.example.ClaimShieldX.auth.dto.TokenResponse;
import com.example.ClaimShieldX.auth.model.RefreshToken;
import com.example.ClaimShieldX.auth.model.UserPrincipal;
import org.apache.http.auth.AuthenticationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final CustomUserDetailsService customUserDetailsService;

    public AuthService(AuthenticationManager authenticationManager, TokenService tokenService, RefreshTokenService refreshTokenService, CustomUserDetailsService customUserDetailsService){
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.customUserDetailsService = customUserDetailsService;
    }

    public TokenResponse authenticate(LoginRequest loginRequest){
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.email(),
                        loginRequest.password()
                )
        );

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        if(userPrincipal == null)
            throw new BadCredentialsException("Invalid email or password");
        String jwt = tokenService.generateToken(userPrincipal);
        String refreshToken = refreshTokenService.createRefreshToken(userPrincipal.getId()).getToken();
        return new TokenResponse(jwt,refreshToken);
    }

    public TokenResponse refreshToken(String bearerToken, String refreshTokenFromCookie){
        UUID userId = UUID.fromString(tokenService.getClaim(bearerToken.substring(7),"userId").toString());
        String refreshToken = refreshTokenService.rotateToken(userId,refreshTokenFromCookie);
        String accessToken = tokenService.generateTokenFromUser(customUserDetailsService.getUserFromId(userId));
        return new TokenResponse(accessToken,refreshToken);
    }
}
