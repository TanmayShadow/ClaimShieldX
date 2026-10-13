package com.example.ClaimShieldX.auth.service;

import com.example.ClaimShieldX.auth.model.User;
import com.example.ClaimShieldX.auth.model.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class TokenService {
    @Value("${app.auth.jwt-secret}")
    private String jwtSecret;

    @Value("${app.auth.jwt-expiration-seconds}")
    private long jwtExpirationSeconds;

    public String generateToken(UserPrincipal userPrincipal){
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId",userPrincipal.getId());
        claims.put("organizationId",userPrincipal.getOrganization().getId());
        claims.put("organizationName",userPrincipal.getOrganization().getName());

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (jwtExpirationSeconds * 1000));

        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userPrincipal.getUsername())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public long getExpirationPeriod() {
        return jwtExpirationSeconds;
    }

    public String generateTokenFromUser(User user){
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId",user.getId());
        claims.put("organizationId",user.getOrganization().getId());
        claims.put("organizationName",user.getOrganization().getName());

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (jwtExpirationSeconds * 1000));

        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getEmail())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Object getClaim(String token, String claimName){
        var key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get(claimName);
    }
}
