package com.lcs.finsight.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lcs.finsight.models.FinancialTransaction;
import com.lcs.finsight.models.Plan;
import com.lcs.finsight.models.User;
import com.lcs.finsight.repositories.FinancialTransactionRepository;
import com.lcs.finsight.support.AbstractIntegrationTest;
import com.lcs.finsight.utils.ApiRoutes;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Proves that {@code POST /financial-transaction/import} (Nubank CSV import,
 * {@link com.lcs.finsight.services.FinancialTransactionService#importFromNubankCsv}) dedups on
 * {@code externalId} (the CSV's third column): re-importing the exact same file a second time
 * must not create duplicate rows, since
 * {@link FinancialTransactionRepository#findExistingExternalIds} is consulted before each row is
 * persisted.
 *
 * <p>CSV format expected by the parser (see {@code importFromNubankCsv}): a header line (skipped),
 * then {@code date,amount,externalId,description} per row, split on {@code ","} with a limit of 4
 * (so a description may itself contain commas), date as {@code dd/MM/yyyy}, amount signed (negative
 * -&gt; DEBIT, non-negative -&gt; CREDIT, stored as its absolute value).
 */
class CsvImportIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    private static final String CSV = """
            date,amount,id,description
            15/07/2026,-50.00,nu-ext-1,Uber trip
            16/07/2026,1200.00,nu-ext-2,Salary payment
            17/07/2026,-89.90,nu-ext-3,Restaurant, dinner with friends
            """;

    @Test
    void reimportingTheSameCsvDoesNotDuplicateRowsByExternalId() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);

        JsonNode firstImport = importCsv(plan, owner, CSV);
        assertThat(firstImport.get("imported").asInt()).isEqualTo(3);

        List<FinancialTransaction> afterFirstImport = financialTransactionRepository.findAllByPlan(plan);
        assertThat(afterFirstImport).hasSize(3);
        assertThat(afterFirstImport)
                .extracting(FinancialTransaction::getExternalId)
                .containsExactlyInAnyOrder("nu-ext-1", "nu-ext-2", "nu-ext-3");

        JsonNode secondImport = importCsv(plan, owner, CSV);
        assertThat(secondImport.get("imported").asInt()).isEqualTo(0);

        List<FinancialTransaction> afterSecondImport = financialTransactionRepository.findAllByPlan(plan);
        assertThat(afterSecondImport).hasSize(3);
        assertThat(afterSecondImport)
                .extracting(FinancialTransaction::getExternalId)
                .containsExactlyInAnyOrder("nu-ext-1", "nu-ext-2", "nu-ext-3");

        MvcResult listResult = mockMvc.perform(get(ApiRoutes.FINANCIAL_TRANSACTION, plan.getId())
                        .with(testAuthHelper.asUser(owner)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode listBody = objectMapper.readTree(listResult.getResponse().getContentAsString());
        assertThat(listBody.get("totalElements").asLong()).isEqualTo(3);
    }

    @Test
    void reimportingWithOneNewExternalIdOnlyAddsTheNewRow() throws Exception {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);

        importCsv(plan, owner, CSV);

        String csvWithOneNewRow = CSV + "18/07/2026,-15.00,nu-ext-4,Pharmacy\n";
        JsonNode secondImport = importCsv(plan, owner, csvWithOneNewRow);

        assertThat(secondImport.get("imported").asInt()).isEqualTo(1);

        List<FinancialTransaction> persisted = financialTransactionRepository.findAllByPlan(plan);
        assertThat(persisted).hasSize(4);
        assertThat(persisted)
                .extracting(FinancialTransaction::getExternalId)
                .containsExactlyInAnyOrder("nu-ext-1", "nu-ext-2", "nu-ext-3", "nu-ext-4");
    }

    private JsonNode importCsv(Plan plan, User asUser, String csvContent) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "nubank.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        MvcResult result = mockMvc.perform(multipart(ApiRoutes.FINANCIAL_TRANSACTION + "/import", plan.getId())
                        .file(file)
                        .with(testAuthHelper.asUser(asUser)))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

        // Import is 201 when it creates rows and 200 when every row was a duplicate (nothing
        // created) — assert the status honors both cases, keyed off the reported count.
        int expectedStatus = body.get("imported").asInt() > 0 ? 201 : 200;
        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        return body;
    }
}
