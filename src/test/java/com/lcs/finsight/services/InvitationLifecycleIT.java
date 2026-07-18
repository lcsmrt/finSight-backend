package com.lcs.finsight.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lcs.finsight.models.Plan;
import com.lcs.finsight.models.PlanRole;
import com.lcs.finsight.models.User;
import com.lcs.finsight.repositories.PlanMembershipRepository;
import com.lcs.finsight.support.AbstractIntegrationTest;
import com.lcs.finsight.utils.ApiRoutes;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Drives {@link com.lcs.finsight.controllers.PlanInvitationController} end-to-end through the
 * real API (create -> accept -> revoke) to pin down the actual lifecycle semantics of
 * {@link com.lcs.finsight.services.PlanInvitationService}, which differ by
 * {@code InvitationType}:
 *
 * <ul>
 *   <li>LINK invites never flip to ACCEPTED (they stay PENDING/reusable), so a second accept
 *       by an already-a-member user is genuinely idempotent: 200 OK, no duplicate membership.
 *   <li>EMAIL invites are single-use: the first accept flips status to ACCEPTED, so a second
 *       accept attempt on the same token is rejected up-front by the token-loading guard with
 *       400 BAD_REQUEST ("This invitation has already been used") rather than silently
 *       succeeding. This is a real behavioral asymmetry in the codebase, not a test artifact.
 *   <li>Expiry (LINK-only; EMAIL invites never carry an expiresAt) is enforced lazily against
 *       {@code expiresAt} and mapped by {@link com.lcs.finsight.exceptions.GlobalExceptionHandler}
 *       to 410 GONE, matching the task's assumption.
 *   <li>Revoke is mapped to 400 BAD_REQUEST via {@code InvitationInvalidException} ("This
 *       invitation has been revoked."), not 403/410 as one might otherwise assume.
 * </ul>
 */
class InvitationLifecycleIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlanMembershipRepository planMembershipRepository;

    // --- double-accept idempotency (LINK invites: genuinely idempotent, no error, no dup) ---

    @Test
    void acceptingALinkInvitationTwiceIsIdempotentAndDoesNotDuplicateMembership() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);
        User invitee = fixtures.aUser();

        String token = createInvitation(plan, owner, PlanRole.EDITOR, "LINK", null, null);

        MvcResult first = acceptAs(token, invitee).andExpect(status().isOk()).andReturn();
        JsonNode firstBody = objectMapper.readTree(first.getResponse().getContentAsString());
        assertThat(firstBody.get("planId").asLong()).isEqualTo(plan.getId());
        assertThat(firstBody.get("role").asText()).isEqualTo("EDITOR");
        assertThat(planMembershipRepository.findAllByPlan(plan)).hasSize(2); // owner + invitee

        // Second accept of the same token by the same (now-member) user: no error, no duplicate.
        MvcResult second = acceptAs(token, invitee).andExpect(status().isOk()).andReturn();
        JsonNode secondBody = objectMapper.readTree(second.getResponse().getContentAsString());
        assertThat(secondBody.get("planId").asLong()).isEqualTo(plan.getId());
        assertThat(secondBody.get("role").asText()).isEqualTo("EDITOR");

        assertThat(planMembershipRepository.findAllByPlan(plan)).hasSize(2); // still no duplicate row
    }

    // --- EMAIL invites: real behavior is single-use, not idempotent, documented explicitly ---

    @Test
    void acceptingAnEmailInvitationTwiceRejectsTheSecondAttemptAsAlreadyUsed() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);
        User invitee = fixtures.aUser();

        String token = createInvitation(plan, owner, PlanRole.VIEWER, "EMAIL", invitee.getEmail(), null);

        acceptAs(token, invitee).andExpect(status().isOk());
        assertThat(planMembershipRepository.findAllByPlan(plan)).hasSize(2);

        // Real behavior: EMAIL invites flip to ACCEPTED on first use, so the token-loading
        // guard rejects a repeat accept up front with 400, not a silent no-op 200.
        acceptAs(token, invitee)
                .andExpect(status().isBadRequest())
                .andExpect(result -> {
                    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
                    assertThat(body.get("message").asText()).isEqualTo("This invitation has already been used.");
                });

        // No duplicate membership was created by the rejected second attempt.
        assertThat(planMembershipRepository.findAllByPlan(plan)).hasSize(2);
    }

    // --- expiry: LINK-only, enforced lazily, mapped to 410 GONE ---

    @Test
    void acceptingAnExpiredLinkInvitationReturns410Gone() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);
        User invitee = fixtures.aUser();

        String token = createInvitation(
                plan, owner, PlanRole.CONTRIBUTOR, "LINK", null, LocalDateTime.now().minusDays(1));

        acceptAs(token, invitee)
                .andExpect(status().isGone())
                .andExpect(result -> {
                    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
                    assertThat(body.get("message").asText()).isEqualTo("This invitation has expired.");
                });

        assertThat(planMembershipRepository.findAllByPlan(plan)).hasSize(1); // owner only, no membership created
    }

    // --- revoke: owner-only action; subsequent accept attempts are rejected with 400 ---

    @Test
    void acceptingARevokedInvitationIsRejectedWith400() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);
        User invitee = fixtures.aUser();

        MvcResult created = mockMvc.perform(post(ApiRoutes.PLAN_INVITATION, plan.getId())
                        .with(testAuthHelper.asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invitationBody(PlanRole.EDITOR, "LINK", null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode createdBody = objectMapper.readTree(created.getResponse().getContentAsString());
        String token = createdBody.get("token").asText();
        long invitationId = createdBody.get("id").asLong();

        mockMvc.perform(delete(ApiRoutes.PLAN_INVITATION + "/{invitationId}", plan.getId(), invitationId)
                        .with(testAuthHelper.asUser(owner)))
                .andExpect(status().isNoContent());

        // Real behavior: revoke is mapped to InvitationInvalidException -> 400 BAD_REQUEST,
        // not 403 Forbidden or 410 Gone.
        acceptAs(token, invitee)
                .andExpect(status().isBadRequest())
                .andExpect(result -> {
                    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
                    assertThat(body.get("message").asText()).isEqualTo("This invitation has been revoked.");
                });

        assertThat(planMembershipRepository.findAllByPlan(plan)).hasSize(1); // owner only, no membership created
    }

    // --- helpers -----------------------------------------------------------------------------

    private String createInvitation(
            Plan plan, User owner, PlanRole role, String type, String email, LocalDateTime expiresAt)
            throws Exception {
        MvcResult result = mockMvc.perform(post(ApiRoutes.PLAN_INVITATION, plan.getId())
                        .with(testAuthHelper.asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invitationBody(role, type, email, expiresAt))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("token").asText();
    }

    private Map<String, Object> invitationBody(PlanRole role, String type, String email, LocalDateTime expiresAt) {
        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("role", role.name());
        body.put("type", type);
        if (email != null) {
            body.put("email", email);
        }
        if (expiresAt != null) {
            body.put("expiresAt", expiresAt.toString());
        }
        return body;
    }

    private org.springframework.test.web.servlet.ResultActions acceptAs(String token, User actor) throws Exception {
        return mockMvc.perform(post(ApiRoutes.INVITATION + "/{token}/accept", token)
                .with(testAuthHelper.asUser(actor)));
    }
}
