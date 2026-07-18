package com.lcs.finsight.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Through-the-API proof of the PUT full-replace contract described in the transaction update
 * memory note: {@code PUT /financial-transaction/{id}} is a full replace, not a patch. A caller
 * must send the complete desired state (participants, items included); the server discards any
 * child rows (participants/items) not present in the new request rather than merging them in.
 *
 * <p>Also exercises the validation guards that are actually implemented in
 * {@link com.lcs.finsight.services.FinancialTransactionService} and its DTOs — not every guard
 * one might expect exists; see the {@code negativeTransactionAmount...} test below for a
 * documented gap discovered while writing this suite.
 */
class TransactionCrudIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private static final LocalDate TODAY = LocalDate.now();

    @Test
    void putWithFewerItemsReplacesAllPreviousItemsLeavingNoLeftoverRows() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);

        JsonNode created = createTransaction(plan, owner, requestBody(
                "DEBIT", "100.00", "Groceries", owner,
                List.of(Map.of("description", "Item A", "amount", new BigDecimal("40.00")),
                        Map.of("description", "Item B", "amount", new BigDecimal("60.00")))));
        assertThat(created.get("items")).hasSize(2);
        long transactionId = created.get("id").asLong();

        // Full-replace PUT: only one item this time, and it's a brand-new one, not a subset of
        // the original two.
        mockMvc.perform(put(ApiRoutes.FINANCIAL_TRANSACTION + "/{id}", plan.getId(), transactionId)
                        .with(testAuthHelper.asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(
                                "DEBIT", "100.00", "Groceries", owner,
                                List.of(Map.of("description", "Item C", "amount", new BigDecimal("70.00"))))))
                .andExpect(status().isOk());

        JsonNode persisted = fetchTransaction(plan, owner, transactionId);

        // Exactly one item survives — the two originals were fully discarded, not merged with
        // the new one (which would leave 3).
        assertThat(persisted.get("items")).hasSize(1);
        assertThat(persisted.get("items").get(0).get("description").asText()).isEqualTo("Item C");
        assertThat(persisted.get("items").get(0).get("amount").decimalValue())
                .isEqualByComparingTo("70.00");

        List<String> itemDescriptions = StreamSupport.stream(persisted.get("items").spliterator(), false)
                .map(item -> item.get("description").asText())
                .toList();
        assertThat(itemDescriptions).doesNotContain("Item A", "Item B");
    }

    @Test
    void putWithFewerParticipantsReplacesAllPreviousParticipantsLeavingNoLeftoverRows() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);
        User member = fixtures.aUser();
        fixtures.addMember(plan, member, PlanRole.EDITOR);

        JsonNode created = createTransaction(plan, owner, splitRequestBody(
                "100.00", owner, List.of(owner, member)));
        assertThat(created.get("participants")).hasSize(2);
        long transactionId = created.get("id").asLong();

        // Full-replace PUT: drop the member, keep only the owner as participant.
        mockMvc.perform(put(ApiRoutes.FINANCIAL_TRANSACTION + "/{id}", plan.getId(), transactionId)
                        .with(testAuthHelper.asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(splitRequestBody("100.00", owner, List.of(owner))))
                .andExpect(status().isOk());

        JsonNode persisted = fetchTransaction(plan, owner, transactionId);

        assertThat(persisted.get("participants")).hasSize(1);
        assertThat(persisted.get("participants").get(0).get("id").asLong()).isEqualTo(owner.getId());
        assertThat(persisted.get("participants").get(0).get("shareAmount").decimalValue())
                .isEqualByComparingTo("100.00");
    }

    @Test
    void putWithItemsOverflowingTransactionAmountIsRejected() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);

        JsonNode created = createTransaction(plan, owner, requestBody(
                "DEBIT", "100.00", "Groceries", owner, List.of()));
        long transactionId = created.get("id").asLong();

        // Single item worth more than the transaction's own amount: FinancialTransactionService
        // .applyItems throws IllegalArgumentException("Items total cannot exceed the transaction
        // amount."), mapped to 400 by GlobalExceptionHandler's IllegalArgumentException handler.
        mockMvc.perform(put(ApiRoutes.FINANCIAL_TRANSACTION + "/{id}", plan.getId(), transactionId)
                        .with(testAuthHelper.asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(
                                "DEBIT", "100.00", "Groceries", owner,
                                List.of(Map.of("description", "Too much", "amount", new BigDecimal("150.00"))))))
                .andExpect(status().isBadRequest());

        // The rejected update must not have mutated the persisted transaction.
        JsonNode persisted = fetchTransaction(plan, owner, transactionId);
        assertThat(persisted.get("items")).isEmpty();
    }

    @Test
    void putWithBlankDescriptionIsRejected() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);

        JsonNode created = createTransaction(plan, owner, requestBody(
                "DEBIT", "100.00", "Groceries", owner, List.of()));
        long transactionId = created.get("id").asLong();

        // description carries @NotBlank on FinancialTransactionRequestDto: a blank value fails
        // bean validation before the service is even invoked (MethodArgumentNotValidException -> 400).
        mockMvc.perform(put(ApiRoutes.FINANCIAL_TRANSACTION + "/{id}", plan.getId(), transactionId)
                        .with(testAuthHelper.asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("DEBIT", "100.00", "   ", owner, List.of())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putWithItemAmountNegativeOrZeroIsRejected() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);

        JsonNode created = createTransaction(plan, owner, requestBody(
                "DEBIT", "100.00", "Groceries", owner, List.of()));
        long transactionId = created.get("id").asLong();

        // ItemInputDto.amount carries @Positive: a negative item amount fails bean validation.
        // (FinancialTransactionService.applyItems also redundantly re-checks this server-side.)
        mockMvc.perform(put(ApiRoutes.FINANCIAL_TRANSACTION + "/{id}", plan.getId(), transactionId)
                        .with(testAuthHelper.asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(
                                "DEBIT", "100.00", "Groceries", owner,
                                List.of(Map.of("description", "Refund-ish", "amount", new BigDecimal("-10.00"))))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void negativeTransactionAmountIsCurrentlyAcceptedNotRejected() throws Exception {
        // DISCOVERED GAP: unlike ItemInputDto.amount (which carries @Positive), the top-level
        // FinancialTransactionRequestDto.amount only carries @NotNull -- no @Positive, and
        // FinancialTransactionService.create/update never checks it against zero either. A
        // negative transaction amount is therefore accepted end-to-end today, as this test
        // documents (not a call to weaken the assertion -- this is the real, current behavior).
        // Flagged as a candidate follow-up fix, not fixed here per task scope.
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);

        JsonNode created = createTransaction(plan, owner, requestBody(
                "DEBIT", "100.00", "Groceries", owner, List.of()));
        long transactionId = created.get("id").asLong();

        mockMvc.perform(put(ApiRoutes.FINANCIAL_TRANSACTION + "/{id}", plan.getId(), transactionId)
                        .with(testAuthHelper.asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("DEBIT", "-50.00", "Groceries", owner, List.of())))
                .andExpect(status().isOk());

        JsonNode persisted = fetchTransaction(plan, owner, transactionId);
        assertThat(persisted.get("amount").decimalValue()).isEqualByComparingTo("-50.00");
    }

    @Test
    void putWithCategoryTypeMismatchIsRejected() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);

        long creditCategoryId = createCategory(plan, owner, "CREDIT", "Salary");

        JsonNode created = createTransaction(plan, owner, requestBody(
                "DEBIT", "100.00", "Groceries", owner, List.of()));
        long transactionId = created.get("id").asLong();

        // FinancialTransactionService.update throws IllegalArgumentException("Category does not
        // match the transaction type.") when category.type != dto.type -> 400.
        mockMvc.perform(put(ApiRoutes.FINANCIAL_TRANSACTION + "/{id}", plan.getId(), transactionId)
                        .with(testAuthHelper.asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type", "DEBIT",
                                "categoryId", creditCategoryId,
                                "amount", new BigDecimal("100.00"),
                                "description", "Groceries",
                                "startDate", TODAY.toString(),
                                "splitMode", "EQUAL",
                                "participants", new Object[] {Map.of("memberId", owner.getId())},
                                "items", List.of()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putWithCategoryFromAnotherPlanIsRejectedAsNotFound() throws Exception {
        // Real behavior: FinancialTransactionCategoryService.findById treats a category that
        // belongs to a *different* plan the same as a category that doesn't exist at all, and
        // throws FinancialTransactionCategoryNotFoundException, which GlobalExceptionHandler maps
        // to 404 -- not 400. This follows the same "cross-plan reference looks like not-found,
        // never forbidden" convention documented in PlanAuthorizationMatrixIT/AuthenticationIT,
        // so it's asserted here as the real (and intentional) 404, not the 400 one might guess.
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);

        User otherOwner = fixtures.aUser();
        Plan otherPlan = fixtures.aPlan(otherOwner);
        long foreignCategoryId = createCategory(otherPlan, otherOwner, "DEBIT", "Foreign category");

        JsonNode created = createTransaction(plan, owner, requestBody(
                "DEBIT", "100.00", "Groceries", owner, List.of()));
        long transactionId = created.get("id").asLong();

        mockMvc.perform(put(ApiRoutes.FINANCIAL_TRANSACTION + "/{id}", plan.getId(), transactionId)
                        .with(testAuthHelper.asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type", "DEBIT",
                                "categoryId", foreignCategoryId,
                                "amount", new BigDecimal("100.00"),
                                "description", "Groceries",
                                "startDate", TODAY.toString(),
                                "splitMode", "EQUAL",
                                "participants", new Object[] {Map.of("memberId", owner.getId())},
                                "items", List.of()))))
                .andExpect(status().isNotFound());
    }

    // --- helpers ------------------------------------------------------------------------------

    private JsonNode createTransaction(Plan plan, User asUser, String body) throws Exception {
        MvcResult result = mockMvc.perform(post(ApiRoutes.FINANCIAL_TRANSACTION, plan.getId())
                        .with(testAuthHelper.asUser(asUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode fetchTransaction(Plan plan, User asUser, long transactionId) throws Exception {
        MvcResult result = mockMvc.perform(get(ApiRoutes.FINANCIAL_TRANSACTION + "/{id}", plan.getId(), transactionId)
                        .with(testAuthHelper.asUser(asUser)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private long createCategory(Plan plan, User asUser, String type, String description) throws Exception {
        MvcResult result = mockMvc.perform(post(ApiRoutes.FINANCIAL_TRANSACTION_CATEGORY, plan.getId())
                        .with(testAuthHelper.asUser(asUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type", type,
                                "description", description))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String requestBody(
            String type, String amount, String description, User soleParticipant, List<Map<String, Object>> items)
            throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "type", type,
                "amount", new BigDecimal(amount),
                "description", description,
                "startDate", TODAY.toString(),
                "splitMode", "EQUAL",
                "participants", new Object[] {Map.of("memberId", soleParticipant.getId())},
                "items", items));
    }

    private String splitRequestBody(String amount, User owner, List<User> participants) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "type", "DEBIT",
                "amount", new BigDecimal(amount),
                "description", "Split expense",
                "startDate", TODAY.toString(),
                "splitMode", "EQUAL",
                "participants", participants.stream()
                        .map(user -> Map.of("memberId", user.getId()))
                        .toList(),
                "items", List.of()));
    }
}
