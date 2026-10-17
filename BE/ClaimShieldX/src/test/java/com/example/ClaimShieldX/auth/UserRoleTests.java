package com.example.ClaimShieldX.auth;

import com.example.ClaimShieldX.auth.dto.RegisterUserRequest;
import com.example.ClaimShieldX.auth.model.User;
import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.auth.model.UserRole;
import com.example.ClaimShieldX.auth.service.CustomUserDetailsService;
import com.example.ClaimShieldX.organization.Organization;
import com.example.ClaimShieldX.support.DbFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Not @Transactional on purpose: these verify committed database behaviour and clean up after themselves. */
@SpringBootTest
@ActiveProfiles("test")
class UserRoleTests {
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private CustomUserDetailsService userDetailsService;

    private DbFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new DbFixtures(jdbcTemplate);
    }

    @AfterEach
    void cleanUp() {
        fixtures.cleanUp();
    }

    private String role(UUID userId) {
        return jdbcTemplate.queryForObject("select role from users where id = ?", String.class, userId);
    }

    @Test
    void newUsersDefaultToInvestigator() {
        UUID user = fixtures.user(fixtures.organization());

        assertThat(role(user)).isEqualTo("INVESTIGATOR");
        assertThat(new User().getRole()).isEqualTo(UserRole.INVESTIGATOR);
    }

    @Test
    void registrationAlwaysCreatesAnInvestigator() {
        UUID organization = fixtures.organization();
        String organizationName = jdbcTemplate.queryForObject("select name from organization where id = ?", String.class, organization);
        String email = UUID.randomUUID() + "@role-test.local";

        userDetailsService.registerUser(new RegisterUserRequest(email, "role-test-pass", organizationName));

        assertThat(jdbcTemplate.queryForObject("select role from users where email = ?", String.class, email)).isEqualTo("INVESTIGATOR");
    }

    @Test
    void databaseOnlyAllowsKnownRoles() {
        UUID user = fixtures.user(fixtures.organization());

        for (String bad : new String[]{"SYSTEM_ADMIN", "HACKER", "investigator", ""}) {
            assertThatThrownBy(() -> jdbcTemplate.update("update users set role = ? where id = ?", bad, user))
                    .as(bad).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_users_role");
        }
        jdbcTemplate.update("update users set role = 'ORG_ADMIN' where id = ?", user);
        assertThat(role(user)).isEqualTo("ORG_ADMIN");
    }

    @Test
    void principalCarriesTheDatabaseRoleAsASpringAuthority() {
        UUID organization = fixtures.organization();
        UUID user = fixtures.user(organization);
        String email = jdbcTemplate.queryForObject("select email from users where id = ?", String.class, user);

        UserPrincipal investigator = (UserPrincipal) userDetailsService.loadUserByUsername(email);
        assertThat(investigator.getRole()).isEqualTo(UserRole.INVESTIGATOR);
        assertThat(investigator.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_INVESTIGATOR");

        jdbcTemplate.update("update users set role = 'ORG_ADMIN' where id = ?", user);
        UserPrincipal admin = (UserPrincipal) userDetailsService.loadUserByUsername(email);
        assertThat(admin.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ORG_ADMIN");
    }

    @Test
    void everyRoleMapsToExactlyOneAuthority() {
        for (UserRole role : UserRole.values()) {
            UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "u@test.local", "x", new Organization(), role);
            assertThat(principal.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_" + role.name());
        }
    }
}
