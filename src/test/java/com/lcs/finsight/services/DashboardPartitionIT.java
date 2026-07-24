package com.lcs.finsight.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lcs.finsight.models.FinancialTransaction;
import com.lcs.finsight.models.FinancialTransactionCategory;
import com.lcs.finsight.models.FinancialTransactionType;
import com.lcs.finsight.models.Plan;
import com.lcs.finsight.models.RecurrenceDefinition;
import com.lcs.finsight.models.RecurrenceDefinitionParticipant;
import com.lcs.finsight.models.RecurrenceInterval;
import com.lcs.finsight.models.RecurrenceMode;
import com.lcs.finsight.models.SplitMode;
import com.lcs.finsight.models.User;
import com.lcs.finsight.repositories.FinancialTransactionCategoryRepository;
import com.lcs.finsight.repositories.FinancialTransactionRepository;
import com.lcs.finsight.repositories.RecurrenceDefinitionRepository;
import com.lcs.finsight.support.AbstractIntegrationTest;
import com.lcs.finsight.utils.ApiRoutes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    @Autowired
    private RecurrenceDefinitionRepository recurrenceDefinitionRepository;

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    @Autowired
    private FinancialTransactionCategoryRepository financialTransactionCategoryRepository;

    private static final LocalDate TODAY = LocalDate.now();

    @Test
    void itemizedTransactionAttributesItemsToOwnCategoryAndRemainderToParent() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);

        long groceriesId = createCategory(plan, owner, "DEBIT", "Groceries").get("id").asLong();
        long foodId = createCategory(plan, owner, "DEBIT", "Food").get("id").asLong();
        long cleaningId = createCategory(plan, owner, "DEBIT", "Cleaning").get("id").asLong();

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

        assertThat(breakdown).hasSize(1);
        assertThat(categorySpent(breakdown, "Utilities")).isEqualByComparingTo("75.00");
        assertThat(dashboard.get("totalExpenses").decimalValue()).isEqualByComparingTo("75.00");
    }

    @Test
    void topLineTotalsAreIdenticalRegardlessOfItemization() throws Exception {
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

    /**
     * Proves the D1 on-read trigger (RMV2-06/07, see {@code design.md}): a stale open-ended
     * (mode = RECURRING, endDate = null) series is topped up as a side effect of the dashboard
     * GET itself, its newly materialized rows are readable in that same response, the watermark
     * advances, and — critically — none of this perturbs the AD-007 partition invariant already
     * proven above for an unrelated itemized transaction in the same plan. The recurring series
     * uses its own category so its contribution is trivially separable from the itemized one.
     *
     * <p>The definition's own dates are anchored on day-of-month 1 (independent of whatever day
     * "today" happens to be) so the expected occurrence count is exact calendar-month arithmetic,
     * regardless of month-length clamping around the real, injected {@code Clock}'s current date.
     */
    @Test
    void openEndedSeriesTopUpDuringDashboardReadPreservesPartitionInvariant() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);

        long groceriesId = createCategory(plan, owner, "DEBIT", "Groceries").get("id").asLong();
        long foodId = createCategory(plan, owner, "DEBIT", "Food").get("id").asLong();
        long cleaningId = createCategory(plan, owner, "DEBIT", "Cleaning").get("id").asLong();
        long subscriptionsId = createCategory(plan, owner, "DEBIT", "Subscriptions").get("id").asLong();

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

        FinancialTransactionCategory subscriptionsCategory = financialTransactionCategoryRepository
                .findByIdAndPlan(subscriptionsId, plan).orElseThrow();

        LocalDate anchorMonth = TODAY.withDayOfMonth(1);
        LocalDate start = anchorMonth.minusMonths(6);
        LocalDate staleGeneratedThrough = anchorMonth.minusMonths(2);
        LocalDate expectedLastGenerated = anchorMonth.plusMonths(12);

        RecurrenceDefinition definition = anOpenEndedDefinition(
                plan, owner, subscriptionsCategory, start, "Streaming", new BigDecimal("30.00"));
        definition.setGeneratedThrough(staleGeneratedThrough);
        recurrenceDefinitionRepository.save(definition);

        assertThat(financialTransactionRepository.findAllByPlanAndSeriesId(plan, definition.getSeriesId())).isEmpty();

        JsonNode dashboard = fetchDashboard(plan, owner, start, TODAY.plusMonths(13));

        List<FinancialTransaction> occurrences = financialTransactionRepository
                .findAllByPlanAndSeriesId(plan, definition.getSeriesId());
        // Top-up only materializes forward from the (stale) watermark — it never backfills the
        // months between the series' start and that watermark, since those are assumed already
        // persisted from the original creation flow (not seeded here, so none exist beforehand).
        long expectedOccurrences = ChronoUnit.MONTHS.between(staleGeneratedThrough, expectedLastGenerated);
        assertThat(occurrences).hasSize((int) expectedOccurrences);

        RecurrenceDefinition reloaded = recurrenceDefinitionRepository.findById(definition.getId()).orElseThrow();
        assertThat(reloaded.getGeneratedThrough()).isEqualTo(expectedLastGenerated);

        JsonNode breakdown = dashboard.get("categoryBreakdown");
        assertThat(categorySpent(breakdown, "Groceries")).isEqualByComparingTo("20.00");
        assertThat(categorySpent(breakdown, "Food")).isEqualByComparingTo("90.00");
        assertThat(categorySpent(breakdown, "Cleaning")).isEqualByComparingTo("40.00");

        BigDecimal partitionedSum = categorySpent(breakdown, "Groceries")
                .add(categorySpent(breakdown, "Food"))
                .add(categorySpent(breakdown, "Cleaning"));
        assertThat(partitionedSum).isEqualByComparingTo("150.00");

        BigDecimal expectedSubscriptionsSpent = new BigDecimal("30.00").multiply(BigDecimal.valueOf(expectedOccurrences));
        assertThat(categorySpent(breakdown, "Subscriptions")).isEqualByComparingTo(expectedSubscriptionsSpent);

        BigDecimal expectedTotalExpenses = new BigDecimal("150.00").add(expectedSubscriptionsSpent);
        assertThat(dashboard.get("totalExpenses").decimalValue()).isEqualByComparingTo(expectedTotalExpenses);
    }

    private RecurrenceDefinition anOpenEndedDefinition(Plan plan, User owner, FinancialTransactionCategory category,
                                                         LocalDate start, String description, BigDecimal amount) {
        RecurrenceDefinition definition = new RecurrenceDefinition();
        definition.setPlan(plan);
        definition.setCreatedBy(owner);
        definition.setCategory(category);
        definition.setSeriesId(UUID.randomUUID().toString());
        definition.setType(FinancialTransactionType.DEBIT);
        definition.setAmount(amount);
        definition.setDescription(description);
        definition.setMode(RecurrenceMode.RECURRING);
        definition.setRecurrenceInterval(RecurrenceInterval.MONTHLY);
        definition.setStartDate(start);
        definition.setEndDate(null);
        definition.setSplitMode(SplitMode.EQUAL);

        RecurrenceDefinitionParticipant participant = new RecurrenceDefinitionParticipant();
        participant.setDefinition(definition);
        participant.setMember(owner);
        participant.setShareAmount(amount);
        definition.getParticipants().add(participant);

        return recurrenceDefinitionRepository.save(definition);
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
        return fetchDashboard(plan, asUser, TODAY.minusDays(1), TODAY.plusDays(1));
    }

    private JsonNode fetchDashboard(Plan plan, User asUser, LocalDate startDate, LocalDate endDate) throws Exception {
        MvcResult result = mockMvc.perform(get(ApiRoutes.DASHBOARD, plan.getId())
                        .with(testAuthHelper.asUser(asUser))
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
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
