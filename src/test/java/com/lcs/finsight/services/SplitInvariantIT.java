package com.lcs.finsight.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lcs.finsight.models.FinancialTransactionType;
import com.lcs.finsight.models.Plan;
import com.lcs.finsight.models.PlanRole;
import com.lcs.finsight.models.User;
import com.lcs.finsight.support.AbstractIntegrationTest;
import com.lcs.finsight.utils.ApiRoutes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Through-the-API proof of the SPLIT-01 invariant: a split transaction's persisted
 * participations always sum to its {@code amount}, unfiltered dashboard totals don't
 * change because a transaction happens to be split, and a per-user dashboard filter
 * only sees that user's share.
 *
 * <p>Responses are read as raw {@link JsonNode} trees rather than deserialized into the
 * response DTOs, since those DTOs are immutable (no default constructor/setters) and are
 * only ever built server-side from entities, not intended as Jackson deserialization targets.
 */
class SplitInvariantIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private static final LocalDate TODAY = LocalDate.now();

    @Test
    void splitTransactionParticipationsSumToAmount() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);
        User member = fixtures.aUser();
        fixtures.addMember(plan, member, PlanRole.EDITOR);

        String requestBody = splitTransactionRequestBody("100.00", owner, member);

        MvcResult createResult = mockMvc.perform(post(ApiRoutes.FINANCIAL_TRANSACTION, plan.getId())
                        .with(testAuthHelper.asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());

        assertThat(created.get("participants")).hasSize(2);
        assertThat(sumParticipantShares(created)).isEqualByComparingTo("100.00");

        long transactionId = created.get("id").asLong();
        MvcResult getResult = mockMvc.perform(get(ApiRoutes.FINANCIAL_TRANSACTION + "/{id}", plan.getId(), transactionId)
                        .with(testAuthHelper.asUser(owner)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode persisted = objectMapper.readTree(getResult.getResponse().getContentAsString());

        BigDecimal sumFromPersistedFetch = sumParticipantShares(persisted);
        assertThat(sumFromPersistedFetch).isEqualByComparingTo(persisted.get("amount").decimalValue());
        assertThat(sumFromPersistedFetch).isEqualByComparingTo("100.00");
    }

    @Test
    void unfilteredDashboardTotalIsUnaffectedByWhetherATransactionIsSplit() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);
        User member = fixtures.aUser();
        fixtures.addMember(plan, member, PlanRole.EDITOR);

        fixtures.aTransaction(plan, owner, new BigDecimal("100.00"), FinancialTransactionType.DEBIT);

        BigDecimal totalExpensesAfterNonSplit = fetchDashboard(plan, owner, null).get("totalExpenses").decimalValue();
        assertThat(totalExpensesAfterNonSplit).isEqualByComparingTo("100.00");

        String requestBody = splitTransactionRequestBody("150.00", owner, member);

        mockMvc.perform(post(ApiRoutes.FINANCIAL_TRANSACTION, plan.getId())
                        .with(testAuthHelper.asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        BigDecimal totalExpensesAfterSplitAdded = fetchDashboard(plan, owner, null).get("totalExpenses").decimalValue();

        assertThat(totalExpensesAfterSplitAdded)
                .isEqualByComparingTo(totalExpensesAfterNonSplit.add(new BigDecimal("150.00")));
        assertThat(totalExpensesAfterSplitAdded).isEqualByComparingTo("250.00");
    }

    @Test
    void perUserDashboardFilterReturnsOnlyThatUsersShare() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);
        User member = fixtures.aUser();
        fixtures.addMember(plan, member, PlanRole.EDITOR);

        String requestBody = splitTransactionRequestBody("100.00", owner, member);

        mockMvc.perform(post(ApiRoutes.FINANCIAL_TRANSACTION, plan.getId())
                        .with(testAuthHelper.asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        BigDecimal unfilteredTotal = fetchDashboard(plan, owner, null).get("totalExpenses").decimalValue();
        BigDecimal ownerOnlyTotal = fetchDashboard(plan, owner, owner.getId()).get("totalExpenses").decimalValue();
        BigDecimal memberOnlyTotal = fetchDashboard(plan, owner, member.getId()).get("totalExpenses").decimalValue();

        assertThat(unfilteredTotal).isEqualByComparingTo("100.00");
        assertThat(ownerOnlyTotal).isEqualByComparingTo("50.00");
        assertThat(memberOnlyTotal).isEqualByComparingTo("50.00");
        assertThat(ownerOnlyTotal.add(memberOnlyTotal)).isEqualByComparingTo(unfilteredTotal);
    }

    private String splitTransactionRequestBody(String amount, User owner, User member) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "type", "DEBIT",
                "amount", new BigDecimal(amount),
                "description", "Split expense",
                "startDate", TODAY.toString(),
                "splitMode", "EQUAL",
                "participants", new Object[] {
                        Map.of("memberId", owner.getId()),
                        Map.of("memberId", member.getId())
                }));
    }

    private static BigDecimal sumParticipantShares(JsonNode transactionNode) {
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode participant : transactionNode.get("participants")) {
            sum = sum.add(participant.get("shareAmount").decimalValue());
        }
        return sum;
    }

    private JsonNode fetchDashboard(Plan plan, User asUser, Long memberIdFilter) throws Exception {
        MockHttpServletRequestBuilder requestBuilder = get(ApiRoutes.DASHBOARD, plan.getId())
                .with(testAuthHelper.asUser(asUser))
                .param("startDate", TODAY.minusDays(1).toString())
                .param("endDate", TODAY.plusDays(1).toString());
        if (memberIdFilter != null) {
            requestBuilder = requestBuilder.param("memberId", memberIdFilter.toString());
        }

        MvcResult result = mockMvc.perform(requestBuilder)
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
