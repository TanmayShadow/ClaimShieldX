package com.example.ClaimShieldX.auth.service;

import com.example.ClaimShieldX.auth.dto.LoginRequest;
import com.example.ClaimShieldX.auth.dto.TokenResponse;
import com.example.ClaimShieldX.auth.model.User;
import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.exceptions.RefreshTokenExpiredException;
import com.example.ClaimShieldX.exceptions.RefreshTokenInvalidException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        String refreshToken = refreshTokenService.issue(userPrincipal.getId());
        return new TokenResponse(jwt,refreshToken);
    }

    /** Needs only the refresh token: it identifies the user, so an expired access token is no obstacle. */
    @Transactional(noRollbackFor = RefreshTokenExpiredException.class)
    public TokenResponse refresh(String presentedRefreshToken){
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(presentedRefreshToken);
        User user;
        try {
            user = customUserDetailsService.getUserFromId(rotation.userId());
        } catch (UsernameNotFoundException e) {
            throw new RefreshTokenInvalidException("Refresh token is not valid");
        }
        return new TokenResponse(tokenService.generateTokenFromUser(user), rotation.newToken());
    }

    public void logout(String presentedRefreshToken){
        refreshTokenService.revoke(presentedRefreshToken);
    }
}
