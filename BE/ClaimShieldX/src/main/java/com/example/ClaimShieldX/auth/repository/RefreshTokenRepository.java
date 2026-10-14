package com.example.ClaimShieldX.auth.repository;

import com.example.ClaimShieldX.auth.model.RefreshToken;
import com.example.ClaimShieldX.auth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    void deleteByUser(User user);

    RefreshToken findByUser_IdAndToken(UUID userId, String refreshTokenFromCookie);

    Optional<RefreshToken> findByUser_Id(UUID userId);
}
