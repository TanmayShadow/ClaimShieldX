package com.example.ClaimShieldX.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Registers and logs in through the real endpoints, the way a client would. */
public class AuthClient {
    public record Tokens(String accessToken, String refreshToken, String setCookie) {
    }

    private final MockMvc mockMvc;

    public AuthClient(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    public MvcResult register(String email, String password, String organizationName) throws Exception {
        return mockMvc.perform(post("/api/v1/users/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\",\"organizationName\":\"%s\"}".formatted(email, password, organizationName))).andReturn();
    }

    public MvcResult loginRaw(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password))).andReturn();
    }

    public Tokens login(String email, String password) throws Exception {
        MvcResult result = loginRaw(email, password);
        if (result.getResponse().getStatus() != 200) {
            throw new AssertionError("Login failed with HTTP " + result.getResponse().getStatus() + ": " + result.getResponse().getContentAsString());
        }
        return tokens(result);
    }

    public static Tokens tokens(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return new Tokens(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"),
                result.getResponse().getHeader("Set-Cookie"));
    }

    public Tokens registerAndLogin(String email, String password, String organizationName) throws Exception {
        MvcResult registered = register(email, password, organizationName);
        if (registered.getResponse().getStatus() != 201) {
            throw new AssertionError("Registration failed with HTTP " + registered.getResponse().getStatus() + ": " + registered.getResponse().getContentAsString());
        }
        return login(email, password);
    }
}
