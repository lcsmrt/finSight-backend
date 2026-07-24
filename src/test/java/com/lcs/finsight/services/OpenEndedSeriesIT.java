package com.lcs.finsight.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lcs.finsight.models.Plan;
import com.lcs.finsight.models.RecurrenceDefinition;
import com.lcs.finsight.models.User;
import com.lcs.finsight.repositories.RecurrenceDefinitionRepository;
import com.lcs.finsight.support.AbstractIntegrationTest;
import com.lcs.finsight.utils.ApiRoutes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Through-the-API proof of T5 (Recurrence Model v2, RMV2-04/05/07): a RECURRING series can be
 * created with no {@code endDate} (open-ended), materializes a rolling 12-month look-ahead from
 * {@code startDate}, and stamps {@code RecurrenceDefinition.generatedThrough} with the last
 * materialized occurrence's date. Bounded RECURRING (endDate present) and the INSTALLMENT
 * requires-parcels guard are asserted unchanged, as regression coverage for the same code path.
 *
 * <p>{@code generatedThrough} is not (yet) exposed on {@link
 * com.lcs.finsight.dtos.response.RecurrenceDefinitionResponseDto}, so it is asserted by autowiring
 * {@link RecurrenceDefinitionRepository} directly and re-fetching the persisted definition — the
 * same escape hatch used by {@code InvitationLifecycleIT}/{@code CsvImportIT} for state the API
 * does not surface.
 */
class OpenEndedSeriesIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecurrenceDefinitionRepository recurrenceDefinitionRepository;

    private static final int OPEN_ENDED_HORIZON_MONTHS = 12;

    @Test
    void openEndedRecurringSeriesMaterializesTwelveMonthHorizonAndSetsGeneratedThrough() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);
        LocalDate start = LocalDate.now();
        LocalDate expectedLast = start.plusMonths(OPEN_ENDED_HORIZON_MONTHS);
        int expectedCount = OPEN_ENDED_HORIZON_MONTHS + 1; // start..start+12mo inclusive, monthly

        JsonNode created = createSeries(plan, owner, recurringBody("Streaming", "20.00", start, null));

        assertThat(created.get("count").asInt()).isEqualTo(expectedCount);
        JsonNode occurrences = created.get("occurrences");
        assertThat(occurrences).hasSize(expectedCount);
        assertThat(occurrences.get(0).get("startDate").asText()).isEqualTo(start.toString());
        assertThat(occurrences.get(expectedCount - 1).get("startDate").asText()).isEqualTo(expectedLast.toString());

        String seriesId = created.get("seriesId").asText();
        JsonNode definitionDto = fetchSeriesDefinition(plan, owner, seriesId);
        assertThat(definitionDto.get("mode").asText()).isEqualTo("RECURRING");
        assertThat(definitionDto.get("endDate").isNull()).isTrue();

        RecurrenceDefinition definition = recurrenceDefinitionRepository.findByPlanAndSeriesId(plan, seriesId)
                .orElseThrow();
        assertThat(definition.getEndDate()).isNull();
        assertThat(definition.getGeneratedThrough()).isEqualTo(expectedLast);
    }

    @Test
    void boundedRecurringSeriesBehaviorIsUnchangedAndGeneratedThroughTracksLastOccurrence() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 4, 1);

        JsonNode created = createSeries(plan, owner, recurringBody("Gym", "50.00", start, end));

        assertThat(created.get("count").asInt()).isEqualTo(4);
        JsonNode occurrences = created.get("occurrences");
        for (int i = 0; i < 4; i++) {
            assertThat(occurrences.get(i).get("startDate").asText()).isEqualTo(start.plusMonths(i).toString());
        }

        String seriesId = created.get("seriesId").asText();
        JsonNode definitionDto = fetchSeriesDefinition(plan, owner, seriesId);
        assertThat(definitionDto.get("endDate").asText()).isEqualTo(end.toString());

        RecurrenceDefinition definition = recurrenceDefinitionRepository.findByPlanAndSeriesId(plan, seriesId)
                .orElseThrow();
        assertThat(definition.getGeneratedThrough()).isEqualTo(end);
    }

    @Test
    void installmentSeriesStillRequiresParcelsNumber() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "DEBIT");
        body.put("amount", new BigDecimal("300.00"));
        body.put("description", "Laptop");
        body.put("mode", "INSTALLMENT");
        body.put("startDate", LocalDate.now().toString());
        body.put("splitMode", "EQUAL");
        // parcelsNumber intentionally omitted

        MvcResult result = mockMvc.perform(post(ApiRoutes.FINANCIAL_TRANSACTION + "/series", plan.getId())
                        .with(testAuthHelper.asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("Parcels number is required");
    }

    @Test
    void openEndedSeriesFutureOccurrencesAppearInDashboardLookAhead() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);
        LocalDate start = LocalDate.now();
        LocalDate sixMonthsOut = start.plusMonths(6);

        createSeries(plan, owner, recurringBody("Streaming", "20.00", start, null));

        // A narrow window around the 6-month-out occurrence: only that single future row should fall
        // inside it, proving the occurrence was already materialized (not merely promised) at create time.
        JsonNode dashboard = fetchDashboard(plan, owner, sixMonthsOut.minusDays(1), sixMonthsOut.plusDays(1));
        assertThat(dashboard.get("totalExpenses").decimalValue()).isEqualByComparingTo("20.00");
    }

    private Map<String, Object> recurringBody(String description, String amount, LocalDate start, LocalDate end) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "DEBIT");
        body.put("amount", new BigDecimal(amount));
        body.put("description", description);
        body.put("mode", "RECURRING");
        body.put("startDate", start.toString());
        body.put("interval", "MONTHLY");
        if (end != null) {
            body.put("endDate", end.toString());
        }
        body.put("splitMode", "EQUAL");
        return body;
    }

    private JsonNode createSeries(Plan plan, User asUser, Map<String, Object> body) throws Exception {
        MvcResult result = mockMvc.perform(post(ApiRoutes.FINANCIAL_TRANSACTION + "/series", plan.getId())
                        .with(testAuthHelper.asUser(asUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode fetchSeriesDefinition(Plan plan, User asUser, String seriesId) throws Exception {
        MvcResult result = mockMvc.perform(get(ApiRoutes.FINANCIAL_TRANSACTION + "/series/{seriesId}", plan.getId(), seriesId)
                        .with(testAuthHelper.asUser(asUser)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
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
}
