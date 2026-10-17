package com.example.ClaimShieldX.auth;

import com.example.ClaimShieldX.support.AuthClient;
import com.example.ClaimShieldX.support.AuthClient.Tokens;
import com.example.ClaimShieldX.support.DbFixtures;
import com.example.ClaimShieldX.support.TestTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Not @Transactional on purpose: users are created through the real endpoints and cleaned up afterwards. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AuthErrorHandlingTests.TestAuthEndpoints.class)
class AuthErrorHandlingTests {
    private static final String PASSWORD = "Test-pass-1234";
    private static final String ANY = "/api/v1/test-auth/any";
    private static final String ADMIN = "/api/v1/test-auth/admin";
    private static final String INVESTIGATOR = "/api/v1/test-auth/investigator";

    /** Stands in for the endpoints B3 will add, to prove role rules and their error mapping work end to end. */
    @RestController
    @RequestMapping("/api/v1/test-auth")
    public static class TestAuthEndpoints {
        @GetMapping("/any")
        @PreAuthorize("isAuthenticated()")
        public String any() {
            return "ok";
        }

        @GetMapping("/admin")
        @PreAuthorize("hasRole('ORG_ADMIN')")
        public String admin() {
            return "ok";
        }

        @GetMapping("/investigator")
        @PreAuthorize("hasAnyRole('INVESTIGATOR', 'ORG_ADMIN')")
        public String investigator() {
            return "ok";
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Value("${app.auth.jwt-secret}") private String jwtSecret;

    private DbFixtures fixtures;
    private AuthClient client;
    private String organizationName;
    private String email;
    private Tokens tokens;

    @BeforeEach
    void setUp() throws Exception {
        fixtures = new DbFixtures(jdbcTemplate);
        client = new AuthClient(mockMvc);
        UUID organization = fixtures.organization();
        organizationName = jdbcTemplate.queryForObject("select name from organization where id = ?", String.class, organization);
        email = UUID.randomUUID() + "@auth-error.local";
        tokens = client.registerAndLogin(email, PASSWORD, organizationName);
    }

    @AfterEach
    void cleanUp() {
        fixtures.cleanUp();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    // ---------------------------------------------------------------- authentication failures

    @Test
    void noTokenOnAProtectedEndpointIsA401WithAJsonBody() throws Exception {
        mockMvc.perform(get(ANY))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message", not(emptyOrNullString())))
                .andExpect(jsonPath("$.timestamp", not(emptyOrNullString())));
    }

    @Test
    void anExpiredTokenIsToldApartFromAnInvalidOne() throws Exception {
        mockMvc.perform(get(ANY).header("Authorization", bearer(TestTokens.expired(jwtSecret, email))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void malformedWronglySignedAndSubjectlessTokensAreInvalid() throws Exception {
        String[] badTokens = {
                "not.a.jwt",
                "garbage",
                TestTokens.signedWithAnotherKey(email),
                TestTokens.withoutSubject(jwtSecret),
                tokens.accessToken() + "tampered"
        };
        for (String bad : badTokens) {
            mockMvc.perform(get(ANY).header("Authorization", bearer(bad)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        }
    }

    @Test
    void aValidSignatureForAUserWhoNoLongerExistsIsInvalid() throws Exception {
        mockMvc.perform(get(ANY).header("Authorization", bearer(TestTokens.valid(jwtSecret, "ghost-" + UUID.randomUUID() + "@nowhere.local"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        jdbcTemplate.update("delete from users where email = ?", email);
        mockMvc.perform(get(ANY).header("Authorization", bearer(tokens.accessToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void aBareBearerKeywordOrAnotherSchemeIsNotAUsableToken() throws Exception {
        mockMvc.perform(get(ANY).header("Authorization", "Bearer"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", anyOf(is("UNAUTHENTICATED"), is("TOKEN_INVALID"))));
        mockMvc.perform(get(ANY).header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void aValidTokenReachesTheEndpoint() throws Exception {
        mockMvc.perform(get(ANY).header("Authorization", bearer(tokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void theBearerKeywordIsCaseInsensitive() throws Exception {
        mockMvc.perform(get(ANY).header("Authorization", "bearer " + tokens.accessToken()))
                .andExpect(status().isOk());
    }

    // The B1 bug: a stale token made even the public endpoints answer an empty 403.
    @Test
    void aBadOrExpiredTokenNeverBlocksAPublicEndpoint() throws Exception {
        String[] badTokens = {TestTokens.expired(jwtSecret, email), "not.a.jwt", TestTokens.signedWithAnotherKey(email)};
        for (String bad : badTokens) {
            mockMvc.perform(post("/api/v1/auth/login").header("Authorization", bearer(bad)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken", not(emptyOrNullString())));

            mockMvc.perform(post("/api/v1/users/register").header("Authorization", bearer(bad)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"%s\",\"password\":\"%s\",\"organizationName\":\"%s\"}".formatted(UUID.randomUUID() + "@public.local", PASSWORD, organizationName)))
                    .andExpect(status().isCreated());
        }
    }

    // ---------------------------------------------------------------- authorization

    @Test
    void aSignedInUserWithoutTheRoleGetsA403JsonBodyNotA500() throws Exception {
        mockMvc.perform(get(ADMIN).header("Authorization", bearer(tokens.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void eachRoleReachesExactlyTheEndpointsItIsAllowed() throws Exception {
        String auth = bearer(tokens.accessToken());

        mockMvc.perform(get(INVESTIGATOR).header("Authorization", auth)).andExpect(status().isOk());
        mockMvc.perform(get(ADMIN).header("Authorization", auth)).andExpect(status().isForbidden());

        jdbcTemplate.update("update users set role = 'ORG_ADMIN' where email = ?", email);

        mockMvc.perform(get(INVESTIGATOR).header("Authorization", auth)).andExpect(status().isOk());
        mockMvc.perform(get(ADMIN).header("Authorization", auth)).andExpect(status().isOk());
    }

    @Test
    void aRoleChangeTakesEffectOnTheNextRequestWithTheSameToken() throws Exception {
        String auth = bearer(tokens.accessToken());
        mockMvc.perform(get(ADMIN).header("Authorization", auth)).andExpect(status().isForbidden());

        jdbcTemplate.update("update users set role = 'ORG_ADMIN' where email = ?", email);

        mockMvc.perform(get(ADMIN).header("Authorization", auth)).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- other error mapping

    @Test
    void anUnknownPathIsA404ForASignedInUserAndA401ForAnAnonymousOne() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist").header("Authorization", bearer(tokens.accessToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void anUnsupportedContentTypeIsA415NotA500() throws Exception {
        mockMvc.perform(post("/api/v1/claims").header("Authorization", bearer(tokens.accessToken()))
                        .contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void errorBodiesNeverEchoTheTokenOrInternals() throws Exception {
        String secretLooking = TestTokens.signedWithAnotherKey(email);

        mockMvc.perform(get(ANY).header("Authorization", bearer(secretLooking)))
                .andExpect(content().string(not(containsString(secretLooking))))
                .andExpect(content().string(not(containsString("io.jsonwebtoken"))))
                .andExpect(content().string(not(containsString("Exception"))));
    }
}
