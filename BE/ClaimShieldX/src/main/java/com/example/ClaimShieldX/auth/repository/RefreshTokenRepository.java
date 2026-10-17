package com.example.ClaimShieldX.auth.repository;

import com.example.ClaimShieldX.auth.model.RefreshToken;
import com.example.ClaimShieldX.auth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** The {@code token} column holds a SHA-256 hash of the refresh token, never the token itself. */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    void deleteByUser(User user);

    Optional<RefreshToken> findByUser_Id(UUID userId);

    Optional<RefreshToken> findByToken(String tokenHash);

    // One statement, so two simultaneous first logins cannot trip over the unique user_id index.
    @Modifying
    @Query(value = "INSERT INTO refresh_token(id, user_id, token, expiry_date) VALUES (:id, :userId, :tokenHash, :expiry) "
            + "ON CONFLICT (user_id) DO UPDATE SET token = EXCLUDED.token, expiry_date = EXCLUDED.expiry_date", nativeQuery = true)
    void upsert(@Param("id") UUID id, @Param("userId") UUID userId, @Param("tokenHash") String tokenHash, @Param("expiry") Instant expiry);

    // Compare-and-swap: of two simultaneous refreshes with the same token, exactly one updates a row.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.token = :newHash, t.expiryDate = :expiry where t.token = :oldHash and t.expiryDate > :now")
    int rotate(@Param("oldHash") String oldHash, @Param("newHash") String newHash, @Param("expiry") Instant expiry, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken t where t.token = :tokenHash")
    int deleteByTokenHash(@Param("tokenHash") String tokenHash);
}
