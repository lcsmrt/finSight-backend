package com.lcs.finsight.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lcs.finsight.models.Plan;
import com.lcs.finsight.models.PlanRole;
import com.lcs.finsight.models.User;
import com.lcs.finsight.support.AbstractIntegrationTest;
import com.lcs.finsight.utils.ApiRoutes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * Through-the-API proof of the series-edit domain (SEDIT-01..12): installment k/N labeling and
 * recurring generation on {@code POST /series}, all three edit scopes (This one / This and
 * following / All) on {@code PUT /series/{seriesId}}, the D10 guard (a parcel-count change is
 * only permitted at scope ALL), and the {@code MAX_OCCURRENCES} (120) cap on both create and edit.
 *
 * <p>Occurrences are verified independently after every write by re-fetching each row by ID
 * (never trusting only the write's own echoed response), the same discipline as
 * {@link SplitInvariantIT}. Response DTOs are immutable (no setters), so responses are parsed as
 * raw {@link JsonNode} trees rather than deserialized, matching sibling ITs in this package.
 */
class SeriesEditIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void installmentSeriesGeneratesPositionAnchoredLabelsThroughApi() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);
        LocalDate start = LocalDate.of(2026, 1, 15);

        JsonNode created = createSeries(plan, owner, installmentBody(
                "Laptop", "300.00", start, 6, null));

        assertThat(created.get("count").asInt()).isEqualTo(6);
        String seriesId = created.get("seriesId").asText();
        assertThat(seriesId).isNotBlank();

        JsonNode occurrences = created.get("occurrences");
        assertThat(occurrences).hasSize(6);
        for (int i = 0; i < 6; i++) {
            JsonNode occ = occurrences.get(i);
            assertThat(occ.get("description").asText()).isEqualTo("Laptop (" + (i + 1) + "/6)");
            assertThat(occ.get("startDate").asText()).isEqualTo(start.plusMonths(i).toString());
            assertThat(occ.get("amount").decimalValue()).isEqualByComparingTo("300.00");
            assertThat(occ.get("seriesId").asText()).isEqualTo(seriesId);
            assertThat(occ.get("parcelsNumber").asInt()).isEqualTo(6);
            assertThat(occ.get("frequency").isNull()).isTrue();
        }

        // Independent round trip: re-fetch the first and last occurrence by ID, don't trust only
        // the create response.
        JsonNode firstPersisted = fetchOccurrence(plan, owner, occurrences.get(0).get("id").asLong());
        JsonNode lastPersisted = fetchOccurrence(plan, owner, occurrences.get(5).get("id").asLong());
        assertThat(firstPersisted.get("description").asText()).isEqualTo("Laptop (1/6)");
        assertThat(lastPersisted.get("description").asText()).isEqualTo("Laptop (6/6)");

        JsonNode definition = fetchSeriesDefinition(plan, owner, seriesId);
        assertThat(definition.get("mode").asText()).isEqualTo("INSTALLMENT");
        assertThat(definition.get("description").asText()).isEqualTo("Laptop");
        assertThat(definition.get("parcelsNumber").asInt()).isEqualTo(6);
        assertThat(definition.get("firstParcel").asInt()).isEqualTo(1);
    }

    @Test
    void installmentSeriesStartingMidwayLabelsFromCurrentParcel() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);
        LocalDate start = LocalDate.of(2026, 1, 1);

        // An in-progress installment: total 10 parcels, but generation starts at parcel 4 (as if
        // parcels 1-3 predate the system). k/N must be position-anchored to the *total*, not the
        // count of generated rows.
        JsonNode created = createSeries(plan, owner, installmentBody(
                "Insurance", "120.00", start, 10, 4));

        assertThat(created.get("count").asInt()).isEqualTo(7);
        JsonNode occurrences = created.get("occurrences");
        assertThat(occurrences.get(0).get("description").asText()).isEqualTo("Insurance (4/10)");
        assertThat(occurrences.get(0).get("startDate").asText()).isEqualTo("2026-01-01");
        assertThat(occurrences.get(6).get("description").asText()).isEqualTo("Insurance (10/10)");
        assertThat(occurrences.get(6).get("startDate").asText()).isEqualTo("2026-07-01");

        String seriesId = created.get("seriesId").asText();
        JsonNode definition = fetchSeriesDefinition(plan, owner, seriesId);
        assertThat(definition.get("firstParcel").asInt()).isEqualTo(4);
        assertThat(definition.get("parcelsNumber").asInt()).isEqualTo(10);
    }

    @Test
    void recurringSeriesGeneratesMonthlyOccurrencesThroughApi() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 4, 1);

        JsonNode created = createSeries(plan, owner, recurringBody("Gym", "50.00", start, end));

        assertThat(created.get("count").asInt()).isEqualTo(4);
        JsonNode occurrences = created.get("occurrences");
        for (int i = 0; i < 4; i++) {
            JsonNode occ = occurrences.get(i);
            assertThat(occ.get("description").asText()).isEqualTo("Gym");
            assertThat(occ.get("startDate").asText()).isEqualTo(start.plusMonths(i).toString());
            assertThat(occ.get("frequency").asText()).isEqualTo("MONTHLY");
            assertThat(occ.get("parcelsNumber").isNull()).isTrue();
        }

        String seriesId = created.get("seriesId").asText();
        JsonNode definition = fetchSeriesDefinition(plan, owner, seriesId);
        assertThat(definition.get("mode").asText()).isEqualTo("RECURRING");
        assertThat(definition.get("endDate").asText()).isEqualTo(end.toString());
    }

    @Test
    void editThisOneUpdatesOnlyPivotOccurrencePreservingSeriesMembership() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);
        LocalDate start = LocalDate.of(2026, 1, 1);

        JsonNode created = createSeries(plan, owner, installmentBody("Rent", "300.00", start, 6, null));
        String seriesId = created.get("seriesId").asText();
        JsonNode occurrences = created.get("occurrences");
        long parcel3Id = occurrences.get(2).get("id").asLong();

        Map<String, Object> editBody = editBody(
                "Fixed Rent", "999.00", start, 6, "THIS_ONE", parcel3Id);
        JsonNode editResponse = editSeries(plan, owner, seriesId, editBody, status().isOk());

        assertThat(editResponse.get("count").asInt()).isEqualTo(1);
        assertThat(editResponse.get("occurrences").get(0).get("id").asLong()).isEqualTo(parcel3Id);

        // Only parcel 3 changed. It keeps its k/N label, seriesId and parcelsNumber (never
        // silently detached from the series) but picks up the new amount/description.
        JsonNode parcel3 = fetchOccurrence(plan, owner, parcel3Id);
        assertThat(parcel3.get("description").asText()).isEqualTo("Fixed Rent (3/6)");
        assertThat(parcel3.get("amount").decimalValue()).isEqualByComparingTo("999.00");
        assertThat(parcel3.get("seriesId").asText()).isEqualTo(seriesId);
        assertThat(parcel3.get("parcelsNumber").asInt()).isEqualTo(6);

        // Every other occurrence, before and after the pivot, is untouched.
        for (int i = 0; i < 6; i++) {
            if (i == 2) {
                continue;
            }
            long id = occurrences.get(i).get("id").asLong();
            JsonNode occ = fetchOccurrence(plan, owner, id);
            assertThat(occ.get("description").asText()).isEqualTo("Rent (" + (i + 1) + "/6)");
            assertThat(occ.get("amount").decimalValue()).isEqualByComparingTo("300.00");
        }

        // The definition itself is untouched by a THIS_ONE edit.
        JsonNode definition = fetchSeriesDefinition(plan, owner, seriesId);
        assertThat(definition.get("description").asText()).isEqualTo("Rent");
        assertThat(definition.get("amount").decimalValue()).isEqualByComparingTo("300.00");
    }

    @Test
    void editThisAndFollowingRewritesFromPivotForwardLeavingEarlierUntouched() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);
        LocalDate start = LocalDate.of(2026, 1, 1);

        JsonNode created = createSeries(plan, owner, installmentBody("Rent", "300.00", start, 6, null));
        String seriesId = created.get("seriesId").asText();
        JsonNode occurrences = created.get("occurrences");
        long parcel3Id = occurrences.get(2).get("id").asLong();

        Map<String, Object> editBody = editBody(
                "New Rent", "350.00", start, 6, "THIS_AND_FOLLOWING", parcel3Id);
        JsonNode editResponse = editSeries(plan, owner, seriesId, editBody, status().isOk());

        // Exactly parcels 3-6 (4 rows) are in scope; nothing created or deleted since the count
        // didn't change.
        assertThat(editResponse.get("count").asInt()).isEqualTo(4);

        // Parcels 1-2 (strictly before the pivot's date) are untouched.
        for (int i = 0; i < 2; i++) {
            long id = occurrences.get(i).get("id").asLong();
            JsonNode occ = fetchOccurrence(plan, owner, id);
            assertThat(occ.get("description").asText()).isEqualTo("Rent (" + (i + 1) + "/6)");
            assertThat(occ.get("amount").decimalValue()).isEqualByComparingTo("300.00");
        }

        // Parcels 3-6 (on/after the pivot's date) are rewritten, keeping position-anchored labels.
        for (int i = 2; i < 6; i++) {
            long id = occurrences.get(i).get("id").asLong();
            JsonNode occ = fetchOccurrence(plan, owner, id);
            assertThat(occ.get("description").asText()).isEqualTo("New Rent (" + (i + 1) + "/6)");
            assertThat(occ.get("amount").decimalValue()).isEqualByComparingTo("350.00");
        }

        // The definition's forward template is updated (used for any future regeneration).
        JsonNode definition = fetchSeriesDefinition(plan, owner, seriesId);
        assertThat(definition.get("description").asText()).isEqualTo("New Rent");
        assertThat(definition.get("amount").decimalValue()).isEqualByComparingTo("350.00");
    }

    @Test
    void editAllRewritesEveryOccurrenceIncludingPastAndPreservesSplitInvariant() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);
        User member = fixtures.aUser();
        fixtures.addMember(plan, member, PlanRole.EDITOR);
        LocalDate start = LocalDate.of(2026, 1, 1);

        Map<String, Object> createBody = installmentBody("Rent", "300.00", start, 6, null);
        createBody.put("participants", List.of(
                Map.of("memberId", owner.getId()), Map.of("memberId", member.getId())));
        JsonNode created = createSeries(plan, owner, createBody);
        String seriesId = created.get("seriesId").asText();
        JsonNode occurrences = created.get("occurrences");

        // ALL scope needs no pivot: every occurrence, including ones dated in the past relative
        // to any pivot, is rewritten.
        Map<String, Object> editBody = editBody("All New Rent", "402.00", start, 6, "ALL", null);
        editBody.put("participants", List.of(
                Map.of("memberId", owner.getId()), Map.of("memberId", member.getId())));
        JsonNode editResponse = editSeries(plan, owner, seriesId, editBody, status().isOk());

        assertThat(editResponse.get("count").asInt()).isEqualTo(6);

        for (int i = 0; i < 6; i++) {
            long id = occurrences.get(i).get("id").asLong();
            JsonNode occ = fetchOccurrence(plan, owner, id);
            assertThat(occ.get("description").asText()).isEqualTo("All New Rent (" + (i + 1) + "/6)");
            assertThat(occ.get("amount").decimalValue()).isEqualByComparingTo("402.00");

            // SPLIT-01: participations sum exactly to the new amount on every rewritten row.
            BigDecimal sum = BigDecimal.ZERO;
            for (JsonNode participant : occ.get("participants")) {
                sum = sum.add(participant.get("shareAmount").decimalValue());
            }
            assertThat(sum).isEqualByComparingTo("402.00");
            assertThat(occ.get("participants")).hasSize(2);
        }
    }

    @Test
    void parcelsNumberChangeAtThisOneScopeRejectedByD10Guard() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);
        LocalDate start = LocalDate.of(2026, 1, 1);

        JsonNode created = createSeries(plan, owner, installmentBody("Rent", "300.00", start, 6, null));
        String seriesId = created.get("seriesId").asText();
        long parcel1Id = created.get("occurrences").get(0).get("id").asLong();

        // D10: a total-parcel-count change is only permitted at scope ALL. Attempting it at
        // THIS_ONE (a narrower scope) must be rejected with 400, leaving the series unmodified.
        Map<String, Object> editBody = editBody("Rent", "300.00", start, 8, "THIS_ONE", parcel1Id);
        editSeries(plan, owner, seriesId, editBody, status().isBadRequest());

        JsonNode definition = fetchSeriesDefinition(plan, owner, seriesId);
        assertThat(definition.get("parcelsNumber").asInt()).isEqualTo(6);
    }

    @Test
    void parcelsNumberChangeAtThisAndFollowingScopeRejectedByD10Guard() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);
        LocalDate start = LocalDate.of(2026, 1, 1);

        JsonNode created = createSeries(plan, owner, installmentBody("Rent", "300.00", start, 6, null));
        String seriesId = created.get("seriesId").asText();
        long parcel3Id = created.get("occurrences").get(2).get("id").asLong();

        // Same guard, exercised from the other narrower scope (THIS_AND_FOLLOWING), confirming the
        // D10 condition is "scope != ALL", not just "scope == THIS_ONE".
        Map<String, Object> editBody = editBody("Rent", "300.00", start, 9, "THIS_AND_FOLLOWING", parcel3Id);
        editSeries(plan, owner, seriesId, editBody, status().isBadRequest());

        JsonNode definition = fetchSeriesDefinition(plan, owner, seriesId);
        assertThat(definition.get("parcelsNumber").asInt()).isEqualTo(6);
    }

    @Test
    void parcelsNumberChangeAtAllScopeIsAllowedAndRelabelsEntireSeries() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);
        LocalDate start = LocalDate.of(2026, 1, 1);

        JsonNode created = createSeries(plan, owner, installmentBody("Rent", "300.00", start, 6, null));
        String seriesId = created.get("seriesId").asText();

        // At ALL scope the count change is allowed: existing rows relabel to the new total and
        // trailing rows are generated (SEDIT-11) -- never a mixed 4/6 next to 7/9 series.
        Map<String, Object> editBody = editBody("Rent", "300.00", start, 9, "ALL", null);
        JsonNode editResponse = editSeries(plan, owner, seriesId, editBody, status().isOk());

        assertThat(editResponse.get("count").asInt()).isEqualTo(9);
        JsonNode occurrences = editResponse.get("occurrences");
        for (int i = 0; i < 9; i++) {
            assertThat(occurrences.get(i).get("description").asText())
                    .isEqualTo("Rent (" + (i + 1) + "/9)");
            assertThat(occurrences.get(i).get("parcelsNumber").asInt()).isEqualTo(9);
        }

        JsonNode definition = fetchSeriesDefinition(plan, owner, seriesId);
        assertThat(definition.get("parcelsNumber").asInt()).isEqualTo(9);
    }

    @Test
    void maxOccurrencesOverflowOnCreateIsRejected() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);

        // 2020-01-01 .. 2030-01-01 inclusive is 121 monthly occurrences, one over the 120 cap.
        Map<String, Object> body = recurringBody(
                "Forever", "10.00", LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1));

        MvcResult result = mockMvc.perform(post(ApiRoutes.FINANCIAL_TRANSACTION + "/series", plan.getId())
                        .with(testAuthHelper.asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("too many occurrences");
    }

    @Test
    void maxOccurrencesOverflowOnEditIsRejected() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate originalEnd = LocalDate.of(2026, 3, 1);

        JsonNode created = createSeries(plan, owner, recurringBody("Gym", "50.00", start, originalEnd));
        String seriesId = created.get("seriesId").asText();

        // Extending the end date under ALL scope so the regenerated target list would exceed the
        // 120 cap must be rejected, and (being @Transactional) must leave the definition untouched.
        LocalDate overflowEnd = start.plusMonths(130);
        Map<String, Object> editBody = new LinkedHashMap<>();
        editBody.put("type", "DEBIT");
        editBody.put("amount", new BigDecimal("50.00"));
        editBody.put("description", "Gym");
        editBody.put("mode", "RECURRING");
        editBody.put("startDate", start.toString());
        editBody.put("interval", "MONTHLY");
        editBody.put("endDate", overflowEnd.toString());
        editBody.put("splitMode", "EQUAL");
        editBody.put("scope", "ALL");

        MvcResult result = mockMvc.perform(put(ApiRoutes.FINANCIAL_TRANSACTION + "/series/{seriesId}", plan.getId(), seriesId)
                        .with(testAuthHelper.asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editBody)))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("too many occurrences");

        JsonNode definition = fetchSeriesDefinition(plan, owner, seriesId);
        assertThat(definition.get("endDate").asText()).isEqualTo(originalEnd.toString());
    }

    // --- helpers ------------------------------------------------------------------------------

    private Map<String, Object> installmentBody(
            String description, String amount, LocalDate start, int parcelsNumber, Integer currentParcel) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "DEBIT");
        body.put("amount", new BigDecimal(amount));
        body.put("description", description);
        body.put("mode", "INSTALLMENT");
        body.put("startDate", start.toString());
        body.put("parcelsNumber", parcelsNumber);
        if (currentParcel != null) {
            body.put("currentParcel", currentParcel);
        }
        body.put("splitMode", "EQUAL");
        return body;
    }

    private Map<String, Object> recurringBody(String description, String amount, LocalDate start, LocalDate end) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "DEBIT");
        body.put("amount", new BigDecimal(amount));
        body.put("description", description);
        body.put("mode", "RECURRING");
        body.put("startDate", start.toString());
        body.put("interval", "MONTHLY");
        body.put("endDate", end.toString());
        body.put("splitMode", "EQUAL");
        return body;
    }

    private Map<String, Object> editBody(
            String description, String amount, LocalDate start, int parcelsNumber, String scope, Long pivotOccurrenceId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "DEBIT");
        body.put("amount", new BigDecimal(amount));
        body.put("description", description);
        body.put("mode", "INSTALLMENT");
        body.put("startDate", start.toString());
        body.put("parcelsNumber", parcelsNumber);
        body.put("splitMode", "EQUAL");
        body.put("scope", scope);
        if (pivotOccurrenceId != null) {
            body.put("pivotOccurrenceId", pivotOccurrenceId);
        }
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

    private JsonNode editSeries(
            Plan plan, User asUser, String seriesId, Map<String, Object> body,
            ResultMatcher expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(put(ApiRoutes.FINANCIAL_TRANSACTION + "/series/{seriesId}", plan.getId(), seriesId)
                        .with(testAuthHelper.asUser(asUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(expectedStatus)
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode fetchOccurrence(Plan plan, User asUser, long id) throws Exception {
        MvcResult result = mockMvc.perform(get(ApiRoutes.FINANCIAL_TRANSACTION + "/{id}", plan.getId(), id)
                        .with(testAuthHelper.asUser(asUser)))
                .andExpect(status().isOk())
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
}
