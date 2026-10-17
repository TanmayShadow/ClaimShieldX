package com.example.ClaimShieldX.support;

import java.util.UUID;

/** A signed-in user in a fresh organization: the user exists in the database and the token is valid for it. */
public record TestActors(UUID organizationId, UUID userId, String email, String token) {
    public static TestActors create(DbFixtures fixtures, String jwtSecret, String role) {
        UUID organization = fixtures.organization();
        return inOrganization(fixtures, jwtSecret, organization, role);
    }

    public static TestActors inOrganization(DbFixtures fixtures, String jwtSecret, UUID organization, String role) {
        UUID user = fixtures.user(organization, role);
        String email = user + "@test.local";
        return new TestActors(organization, user, email, TestTokens.valid(jwtSecret, email));
    }

    public String bearer() {
        return "Bearer " + token;
    }
}
