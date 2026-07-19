package com.lcs.finsight.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lcs.finsight.support.AbstractIntegrationTest;
import com.lcs.finsight.utils.ApiRoutes;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Drives the real registration ({@code POST /user}) and login ({@code POST /auth/login})
 * endpoints end-to-end through the actual security filter chain — no {@code Fixtures.aUser()}
 * shortcuts and no {@code @WithMockUser} — to prove the auth slice actually issues and honors
 * JWTs, and that the two-layer plan authorization model (see {@link PlanAuthorizationMatrixIT})
 * holds when the caller mints their token through the real endpoints instead of
 * {@code testAuthHelper}.
 */
class AuthenticationIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private String registerAndLogin(String email) throws Exception {
        register(email);
        return login(email, "password123");
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post(ApiRoutes.USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Test User",
                                "email", email,
                                "password", "password123"))))
                .andExpect(status().isCreated());
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post(ApiRoutes.AUTH + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("token").asText();
    }

    @Test
    void registerThenLoginIssuesTokenThatGrantsAuthenticatedAccess() throws Exception {
        String email = "alice-" + System.nanoTime() + "@test.com";
        register(email);

        String token = login(email, "password123");
        assertThat(token).isNotBlank();

        mockMvc.perform(get(ApiRoutes.USER + "/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    JsonNode profile = objectMapper.readTree(result.getResponse().getContentAsString());
                    assertThat(profile.get("email").asText()).isEqualTo(email);
                });
    }

    @Test
    void registerWithoutTokenRequiresNoAuthentication() throws Exception {
        // POST /users is permitAll in SecurityConfig: registration itself must not require a
        // bearer token, and the response carries the new user, not a token (login mints that).
        String email = "bob-" + System.nanoTime() + "@test.com";
        MvcResult result = mockMvc.perform(post(ApiRoutes.USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Bob",
                                "email", email,
                                "password", "password123"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(created.get("email").asText()).isEqualTo(email);
        assertThat(created.has("token")).isFalse();
    }

    @Test
    void loginWithCorrectCredentialsReturnsValidJwt() throws Exception {
        String email = "carol-" + System.nanoTime() + "@test.com";
        register(email);

        String token = login(email, "password123");
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    private MvcResult attemptLogin(String email, String password) throws Exception {
        return mockMvc.perform(post(ApiRoutes.AUTH + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password))))
                .andReturn();
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        String email = "dave-" + System.nanoTime() + "@test.com";
        register(email);

        mockMvc.perform(post(ApiRoutes.AUTH + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "not-the-right-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginForUnknownEmailIsRejected() throws Exception {
        // A login for an email with no matching user must be rejected with 401 (not 500):
        // CustomUserDetailsService throws Spring Security's own UsernameNotFoundException, which
        // DaoAuthenticationProvider's hideUserNotFoundExceptions converts to a BadCredentialsException,
        // mapped to 401 by GlobalExceptionHandler.
        mockMvc.perform(post(ApiRoutes.AUTH + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "nobody-" + System.nanoTime() + "@test.com",
                                "password", "password123"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownEmailAndWrongPasswordAreIndistinguishable() throws Exception {
        // Anti-enumeration: an unknown email and a wrong password for a known email must return the
        // exact same status and body, so a caller can't tell whether an email is registered.
        String knownEmail = "heidi-" + System.nanoTime() + "@test.com";
        register(knownEmail);

        MvcResult wrongPassword = attemptLogin(knownEmail, "not-the-right-password");
        MvcResult unknownEmail = attemptLogin("nobody-" + System.nanoTime() + "@test.com", "password123");

        assertThat(unknownEmail.getResponse().getStatus())
                .isEqualTo(wrongPassword.getResponse().getStatus());

        JsonNode wrongPasswordBody = objectMapper.readTree(wrongPassword.getResponse().getContentAsString());
        JsonNode unknownEmailBody = objectMapper.readTree(unknownEmail.getResponse().getContentAsString());
        assertThat(unknownEmailBody.get("message").asText())
                .isEqualTo(wrongPasswordBody.get("message").asText());
    }

    @Test
    void registeredUserCanAccessTheirOwnAutoProvisionedPlan() throws Exception {
        String email = "erin-" + System.nanoTime() + "@test.com";
        String token = registerAndLogin(email);

        // UserService.create() auto-provisions a default plan for every new user; the freshly
        // minted token must be able to see it via the real plans endpoint.
        mockMvc.perform(get(ApiRoutes.PLAN)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    JsonNode plans = objectMapper.readTree(result.getResponse().getContentAsString());
                    assertThat(plans.isArray()).isTrue();
                    assertThat(plans).hasSize(1);
                });
    }

    @Test
    void tokenForUserACannotAccessUserBsNonSharedPlan() throws Exception {
        String emailA = "frank-" + System.nanoTime() + "@test.com";
        String emailB = "grace-" + System.nanoTime() + "@test.com";
        String tokenA = registerAndLogin(emailA);
        String tokenB = registerAndLogin(emailB);

        Long planIdA = fetchOwnPlanId(tokenA);

        // Non-member access must 404, never 403 — consistent with the two-layer plan
        // authorization model verified against real tokens in PlanAuthorizationMatrixIT.
        mockMvc.perform(get(ApiRoutes.PLAN + "/{id}", planIdA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(ApiRoutes.PLAN + "/{id}", planIdA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    @Test
    void requestWithoutTokenIsRejectedForProtectedEndpoint() throws Exception {
        // SecurityConfig registers no explicit AuthenticationEntryPoint, so Spring Security falls
        // back to Http403ForbiddenEntryPoint for an unauthenticated request to a protected route.
        mockMvc.perform(get(ApiRoutes.PLAN))
                .andExpect(status().isForbidden());
    }

    private Long fetchOwnPlanId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get(ApiRoutes.PLAN)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode plans = objectMapper.readTree(result.getResponse().getContentAsString());
        return plans.get(0).get("id").asLong();
    }
}
