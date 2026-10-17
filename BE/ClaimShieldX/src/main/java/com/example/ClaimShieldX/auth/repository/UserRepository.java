package com.example.ClaimShieldX.auth.repository;

import com.example.ClaimShieldX.auth.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

// The organization is fetched with the user: token generation reads its name outside any transaction.
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    @EntityGraph(attributePaths = "organization")
    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = "organization")
    Optional<User> findWithOrganizationById(UUID id);
}
