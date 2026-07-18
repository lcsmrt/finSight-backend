package com.lcs.finsight.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lcs.finsight.models.FinancialTransactionType;
import com.lcs.finsight.models.Plan;
import com.lcs.finsight.models.User;
import com.lcs.finsight.support.AbstractIntegrationTest;
import com.lcs.finsight.utils.ApiRoutes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Through-the-API proof of the AD-007 Partition Invariant: {@code spent[C] = A[C] - B[C] + I[C]},
 * where A is a category's whole-transaction sums, B is the categorized-item amounts subtracted
 * out of the parent category, and I is those same amounts re-attributed to the item's own
 * category. Uncategorized items and any un-itemized remainder stay with the parent category.
 *
 * <p>Responses are read as raw {@link JsonNode} trees rather than deserialized into the response
 * DTOs, since those DTOs are immutable (no default constructor/setters) and are only ever built
 * server-side from entities (see {@code SplitInvariantIT} for the same pattern).
 */
class DashboardPartitionIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private static final LocalDate TODAY = LocalDate.now();

    @Test
    void itemizedTransactionAttributesItemsToOwnCategoryAndRemainderToParent() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);

        long groceriesId = createCategory(plan, owner, "DEBIT", "Groceries").get("id").asLong();
        long foodId = createCategory(plan, owner, "DEBIT", "Food").get("id").asLong();
        long cleaningId = createCategory(plan, owner, "DEBIT", "Cleaning").get("id").asLong();

        // amount=150; items: Food(90, categorized) + Cleaning(40, categorized) + Misc(10, uncategorized).
        // Un-itemized slack = 150 - 140 = 10. Groceries keeps the uncategorized item's 10 plus the
        // 10 slack => 20. Food and Cleaning each keep exactly their own item amount.
        Map<String, Object> body = Map.of(
                "type", "DEBIT",
                "amount", new BigDecimal("150.00"),
                "description", "Grocery run",
                "startDate", TODAY.toString(),
                "categoryId", groceriesId,
                "items", List.of(
                        Map.of("description", "Food shopping", "amount", new BigDecimal("90.00"), "categoryId", foodId),
                        Map.of("description", "Cleaning supplies", "amount", new BigDecimal("40.00"), "categoryId", cleaningId),
                        Map.of("description", "Misc", "amount", new BigDecimal("10.00"))));

        createTransaction(plan, owner, body);

        JsonNode dashboard = fetchDashboard(plan, owner);
        JsonNode breakdown = dashboard.get("categoryBreakdown");

        assertThat(categorySpent(breakdown, "Groceries")).isEqualByComparingTo("20.00");
        assertThat(categorySpent(breakdown, "Food")).isEqualByComparingTo("90.00");
        assertThat(categorySpent(breakdown, "Cleaning")).isEqualByComparingTo("40.00");

        // The partition never loses or fabricates money: the three buckets sum back to the
        // transaction's own amount.
        BigDecimal partitionedSum = categorySpent(breakdown, "Groceries")
                .add(categorySpent(breakdown, "Food"))
                .add(categorySpent(breakdown, "Cleaning"));
        assertThat(partitionedSum).isEqualByComparingTo("150.00");

        assertThat(dashboard.get("totalExpenses").decimalValue()).isEqualByComparingTo("150.00");
    }

    @Test
    void nonItemizedTransactionBreakdownIsUnchangedFromPreItemsBehavior() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);

        long utilitiesId = createCategory(plan, owner, "DEBIT", "Utilities").get("id").asLong();

        Map<String, Object> body = Map.of(
                "type", "DEBIT",
                "amount", new BigDecimal("75.00"),
                "description", "Electric bill",
                "startDate", TODAY.toString(),
                "categoryId", utilitiesId);

        createTransaction(plan, owner, body);

        JsonNode dashboard = fetchDashboard(plan, owner);
        JsonNode breakdown = dashboard.get("categoryBreakdown");

        // No items at all: spent[C] collapses to A[C] - 0 + 0 = A[C], the raw transaction amount,
        // exactly as it behaved before items existed.
        assertThat(breakdown).hasSize(1);
        assertThat(categorySpent(breakdown, "Utilities")).isEqualByComparingTo("75.00");
        assertThat(dashboard.get("totalExpenses").decimalValue()).isEqualByComparingTo("75.00");
    }

    @Test
    void topLineTotalsAreIdenticalRegardlessOfItemization() throws Exception {
        // Plan A: a plain income + a plain expense transaction, no items at all.
        User ownerA = fixtures.aUser();
        Plan planA = fixtures.aPlan(ownerA);

        createTransaction(planA, ownerA, Map.of(
                "type", "CREDIT",
                "amount", new BigDecimal("500.00"),
                "description", "Salary",
                "startDate", TODAY.toString()));
        createTransaction(planA, ownerA, Map.of(
                "type", "DEBIT",
                "amount", new BigDecimal("200.00"),
                "description", "Rent",
                "startDate", TODAY.toString()));

        // Plan B: the same amounts and types, but fully itemized.
        User ownerB = fixtures.aUser();
        Plan planB = fixtures.aPlan(ownerB);

        long bonusId = createCategory(planB, ownerB, "CREDIT", "Bonus").get("id").asLong();
        long foodId = createCategory(planB, ownerB, "DEBIT", "Food").get("id").asLong();

        createTransaction(planB, ownerB, Map.of(
                "type", "CREDIT",
                "amount", new BigDecimal("500.00"),
                "description", "Salary",
                "startDate", TODAY.toString(),
                "items", List.of(
                        Map.of("description", "Year-end bonus", "amount", new BigDecimal("100.00"), "categoryId", bonusId))));
        createTransaction(planB, ownerB, Map.of(
                "type", "DEBIT",
                "amount", new BigDecimal("200.00"),
                "description", "Rent",
                "startDate", TODAY.toString(),
                "items", List.of(
                        Map.of("description", "Groceries slice", "amount", new BigDecimal("50.00"), "categoryId", foodId))));

        JsonNode dashboardA = fetchDashboard(planA, ownerA);
        JsonNode dashboardB = fetchDashboard(planB, ownerB);

        assertThat(dashboardB.get("totalIncome").decimalValue())
                .isEqualByComparingTo(dashboardA.get("totalIncome").decimalValue());
        assertThat(dashboardB.get("totalExpenses").decimalValue())
                .isEqualByComparingTo(dashboardA.get("totalExpenses").decimalValue());
        assertThat(dashboardB.get("netBalance").decimalValue())
                .isEqualByComparingTo(dashboardA.get("netBalance").decimalValue());

        assertThat(dashboardA.get("totalIncome").decimalValue()).isEqualByComparingTo("500.00");
        assertThat(dashboardA.get("totalExpenses").decimalValue()).isEqualByComparingTo("200.00");
        assertThat(dashboardA.get("netBalance").decimalValue()).isEqualByComparingTo("300.00");
    }

    private JsonNode createCategory(Plan plan, User asUser, String type, String description) throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "type", type,
                "description", description));

        MvcResult result = mockMvc.perform(post(ApiRoutes.FINANCIAL_TRANSACTION_CATEGORY, plan.getId())
                        .with(testAuthHelper.asUser(asUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode createTransaction(Plan plan, User asUser, Map<String, Object> body) throws Exception {
        String requestBody = objectMapper.writeValueAsString(body);

        MvcResult result = mockMvc.perform(post(ApiRoutes.FINANCIAL_TRANSACTION, plan.getId())
                        .with(testAuthHelper.asUser(asUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode fetchDashboard(Plan plan, User asUser) throws Exception {
        MvcResult result = mockMvc.perform(get(ApiRoutes.DASHBOARD, plan.getId())
                        .with(testAuthHelper.asUser(asUser))
                        .param("startDate", TODAY.minusDays(1).toString())
                        .param("endDate", TODAY.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static BigDecimal categorySpent(JsonNode breakdown, String categoryName) {
        for (JsonNode category : breakdown) {
            if (category.get("categoryName").asText().equals(categoryName)) {
                return category.get("spent").decimalValue();
            }
        }
        throw new AssertionError("No category named " + categoryName + " in breakdown: " + breakdown);
    }
}
