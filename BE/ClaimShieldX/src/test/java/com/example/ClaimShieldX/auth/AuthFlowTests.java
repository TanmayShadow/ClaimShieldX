package com.example.ClaimShieldX.auth;

import com.example.ClaimShieldX.common.utils.TokenHasher;
import com.example.ClaimShieldX.support.AuthClient;
import com.example.ClaimShieldX.support.AuthClient.Tokens;
import com.example.ClaimShieldX.support.DbFixtures;
import com.example.ClaimShieldX.support.TestTokens;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Not @Transactional on purpose: refresh tokens are committed so concurrent requests really race. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowTests {
    private static final String PASSWORD = "Test-pass-1234";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Value("${app.auth.jwt-secret}") private String jwtSecret;

    private DbFixtures fixtures;
    private AuthClient client;
    private String organizationName;

    @BeforeEach
    void setUp() {
        fixtures = new DbFixtures(jdbcTemplate);
        client = new AuthClient(mockMvc);
        UUID organization = fixtures.organization();
        organizationName = jdbcTemplate.queryForObject("select name from organization where id = ?", String.class, organization);
    }

    @AfterEach
    void cleanUp() {
        fixtures.cleanUp();
    }

    private String email() {
        return UUID.randomUUID() + "@auth-test.local";
    }

    private Tokens registerAndLogin() throws Exception {
        return client.registerAndLogin(email(), PASSWORD, organizationName);
    }

    private MvcResult refreshWithCookie(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refreshToken", token))).andReturn();
    }

    private int refreshTokenRows(String email) {
        return jdbcTemplate.queryForObject("select count(*) from refresh_token where user_id = (select id from users where email = ?)", Integer.class, email);
    }

    // ---------------------------------------------------------------- registration

    @Test
    void registrationCreatesAnInvestigator() throws Exception {
        String email = email();

        mockMvc.perform(post("/api/v1/users/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"12345678\",\"organizationName\":\"%s\"}".formatted(email, organizationName)))
                .andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject("select role from users where email = ?", String.class, email)).isEqualTo("INVESTIGATOR");
    }

    @Test
    void registrationRequiresAtLeastEightCharacters() throws Exception {
        mockMvc.perform(post("/api/v1/users/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"1234567\",\"organizationName\":\"%s\"}".formatted(email(), organizationName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("password")));
    }

    @Test
    void registrationRejectsPasswordsBcryptCannotHandle() throws Exception {
        String tooManyCharacters = "x".repeat(73);
        String tooManyBytes = "₹".repeat(30);

        for (String password : new String[]{tooManyCharacters, tooManyBytes}) {
            mockMvc.perform(post("/api/v1/users/register").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"%s\",\"password\":\"%s\",\"organizationName\":\"%s\"}".formatted(email(), password, organizationName)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    void registrationWithAnUnknownOrganizationIsNotFoundNotAConflict() throws Exception {
        mockMvc.perform(post("/api/v1/users/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"organizationName\":\"No Such Org %s\"}".formatted(email(), PASSWORD, UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"));
    }

    @Test
    void registeringTheSameEmailTwiceIsAConflict() throws Exception {
        String email = email();
        client.registerAndLogin(email, PASSWORD, organizationName);

        mockMvc.perform(post("/api/v1/users/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"organizationName\":\"%s\"}".formatted(email, PASSWORD, organizationName)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void registrationRejectsAnInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/v1/users/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"%s\",\"organizationName\":\"%s\"}".formatted(PASSWORD, organizationName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")));
    }

    // ---------------------------------------------------------------- login

    @Test
    void loginReturnsBothTokensAndAHardenedRefreshCookie() throws Exception {
        Tokens tokens = registerAndLogin();

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).hasSizeGreaterThanOrEqualTo(40);
        assertThat(tokens.setCookie()).startsWith("refreshToken=" + tokens.refreshToken())
                .contains("Path=/api/v1/auth", "Max-Age=604800", "HttpOnly", "Secure", "SameSite=Strict");
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameAnswer() throws Exception {
        String email = email();
        client.registerAndLogin(email, PASSWORD, organizationName);

        MvcResult wrongPassword = client.loginRaw(email, "Wrong-pass-1234");
        MvcResult unknownEmail = client.loginRaw(email(), PASSWORD);

        for (MvcResult result : List.of(wrongPassword, unknownEmail)) {
            assertThat(result.getResponse().getStatus()).isEqualTo(401);
            assertThat(com.jayway.jsonpath.JsonPath.<String>read(result.getResponse().getContentAsString(), "$.code")).isEqualTo("INVALID_CREDENTIALS");
            assertThat(com.jayway.jsonpath.JsonPath.<String>read(result.getResponse().getContentAsString(), "$.message")).isEqualTo("Invalid email or password");
        }
    }

    @Test
    void loginDoesNotEnforceThePasswordPolicy() throws Exception {
        String email = email();
        client.registerAndLogin(email, PASSWORD, organizationName);

        MvcResult shortPassword = client.loginRaw(email, "abc");

        assertThat(shortPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(shortPassword.getResponse().getContentAsString()).contains("INVALID_CREDENTIALS");
    }

    @Test
    void loginRejectsABlankPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"  \"}".formatted(email())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void aMultiByteLoginPasswordLongerThanBcryptReadsIsAWrongPasswordNotAServerError() throws Exception {
        String email = email();
        client.registerAndLogin(email, PASSWORD, organizationName);

        MvcResult result = client.loginRaw(email, "₹".repeat(60));

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    // ---------------------------------------------------------------- refresh

    @Test
    void refreshRotatesTheTokenAndIssuesAWorkingAccessToken() throws Exception {
        Tokens first = registerAndLogin();

        MvcResult refreshed = refreshWithCookie(first.refreshToken());
        assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);
        Tokens second = AuthClient.tokens(refreshed);

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(second.accessToken()).isNotBlank();
        assertThat(second.setCookie()).startsWith("refreshToken=" + second.refreshToken()).contains("Path=/api/v1/auth", "HttpOnly");
        mockMvc.perform(post("/api/v1/claims").header("Authorization", "Bearer " + second.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anOldRefreshTokenStopsWorkingAfterItIsUsed() throws Exception {
        Tokens first = registerAndLogin();
        refreshWithCookie(first.refreshToken());

        MvcResult reuse = refreshWithCookie(first.refreshToken());

        assertThat(reuse.getResponse().getStatus()).isEqualTo(401);
        assertThat(reuse.getResponse().getContentAsString()).contains("REFRESH_TOKEN_INVALID");
    }

    @Test
    void refreshAcceptsTheTokenInTheBodyForClientsWithoutCookies() throws Exception {
        Tokens tokens = registerAndLogin();

        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(tokens.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(emptyOrNullString())));
    }

    @Test
    void theCookieWinsOverTheBody() throws Exception {
        Tokens tokens = registerAndLogin();

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refreshToken", tokens.refreshToken()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"garbage\"}"))
                .andExpect(status().isOk());
    }

    // The B1 bug: refresh needed the old access token and crashed when it had expired, which is exactly when it is used.
    @Test
    void refreshWorksWhenTheAccessTokenHasExpired() throws Exception {
        String email = email();
        Tokens tokens = client.registerAndLogin(email, PASSWORD, organizationName);
        String expiredAccessToken = TestTokens.expired(jwtSecret, email);

        mockMvc.perform(post("/api/v1/auth/refresh").header("Authorization", "Bearer " + expiredAccessToken)
                        .cookie(new Cookie("refreshToken", tokens.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(emptyOrNullString())));
    }

    @Test
    void refreshWorksEvenWhenTheAccessTokenIsGarbage() throws Exception {
        Tokens tokens = registerAndLogin();

        mockMvc.perform(post("/api/v1/auth/refresh").header("Authorization", "Bearer not.a.jwt")
                        .cookie(new Cookie("refreshToken", tokens.refreshToken())))
                .andExpect(status().isOk());
    }

    @Test
    void refreshWithoutAnyTokenOrWithAGarbageTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refreshToken", "definitely-not-a-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void anExpiredRefreshTokenIsReportedAsExpiredAndRemoved() throws Exception {
        String email = email();
        Tokens tokens = client.registerAndLogin(email, PASSWORD, organizationName);
        jdbcTemplate.update("update refresh_token set expiry_date = now() - interval '1 minute' where user_id = (select id from users where email = ?)", email);

        MvcResult result = refreshWithCookie(tokens.refreshToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentAsString()).contains("REFRESH_TOKEN_EXPIRED");
        assertThat(refreshTokenRows(email)).as("the expired token must be deleted even though the request failed").isZero();
    }

    @Test
    void onlyAHashOfTheRefreshTokenIsStored() throws Exception {
        String email = email();
        Tokens tokens = client.registerAndLogin(email, PASSWORD, organizationName);

        String stored = jdbcTemplate.queryForObject("select token from refresh_token where user_id = (select id from users where email = ?)", String.class, email);

        assertThat(stored).isNotEqualTo(tokens.refreshToken()).isEqualTo(TokenHasher.sha256Hex(tokens.refreshToken())).hasSize(64);
    }

    @Test
    void loggingInAgainReplacesThePreviousSession() throws Exception {
        String email = email();
        Tokens first = client.registerAndLogin(email, PASSWORD, organizationName);
        Tokens second = client.login(email, PASSWORD);

        assertThat(refreshWithCookie(first.refreshToken()).getResponse().getStatus()).isEqualTo(401);
        assertThat(refreshWithCookie(second.refreshToken()).getResponse().getStatus()).isEqualTo(200);
        assertThat(refreshTokenRows(email)).isEqualTo(1);
    }

    @Test
    void simultaneousRefreshesWithTheSameTokenYieldExactlyOneSuccess() throws Exception {
        int parallel = 8;
        Tokens tokens = registerAndLogin();
        ExecutorService pool = Executors.newFixedThreadPool(parallel);
        try {
            CountDownLatch ready = new CountDownLatch(parallel);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> outcomes = new ArrayList<>();
            for (int i = 0; i < parallel; i++) {
                outcomes.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return refreshWithCookie(tokens.refreshToken()).getResponse().getStatus();
                }));
            }
            ready.await();
            go.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> outcome : outcomes) {
                statuses.add(outcome.get(30, TimeUnit.SECONDS));
            }

            assertThat(statuses).filteredOn(code -> code == 200).hasSize(1);
            assertThat(statuses).filteredOn(code -> code == 401).hasSize(parallel - 1);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------------------------------------------------------------- logout

    @Test
    void logoutRevokesTheTokenAndClearsTheCookie() throws Exception {
        Tokens tokens = registerAndLogin();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("refreshToken", tokens.refreshToken())))
                .andExpect(status().isNoContent()).andReturn();

        assertThat(result.getResponse().getHeader("Set-Cookie")).startsWith("refreshToken=;").contains("Max-Age=0", "Path=/api/v1/auth");
        assertThat(refreshWithCookie(tokens.refreshToken()).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void logoutIsIdempotentAndRevealsNothingAboutTheToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("refreshToken", "never-existed"))).andExpect(status().isNoContent());
    }

    @Test
    void logoutWorksWithAnExpiredAccessToken() throws Exception {
        String email = email();
        Tokens tokens = client.registerAndLogin(email, PASSWORD, organizationName);

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + TestTokens.expired(jwtSecret, email))
                        .cookie(new Cookie("refreshToken", tokens.refreshToken())))
                .andExpect(status().isNoContent());
        assertThat(refreshTokenRows(email)).isZero();
    }
}
