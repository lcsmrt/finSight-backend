package com.lcs.finsight.controllers;

import com.lcs.finsight.dtos.request.FinancialTransactionFilterDto;
import com.lcs.finsight.dtos.request.FinancialTransactionRequestDto;
import com.lcs.finsight.dtos.request.FinancialTransactionSeriesRequestDto;
import com.lcs.finsight.dtos.request.SeriesEditRequestDto;
import com.lcs.finsight.dtos.response.FinancialTransactionImportResponseDto;
import com.lcs.finsight.dtos.response.FinancialTransactionResponseDto;
import com.lcs.finsight.dtos.response.FinancialTransactionSeriesResponseDto;
import com.lcs.finsight.dtos.response.PagedResponseDto;
import com.lcs.finsight.dtos.response.RecurrenceDefinitionResponseDto;
import com.lcs.finsight.models.FinancialTransaction;
import com.lcs.finsight.models.SeriesEditScope;
import com.lcs.finsight.security.PlanContext;
import com.lcs.finsight.services.FinancialTransactionService;
import com.lcs.finsight.utils.ApiRoutes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Financial Transactions")
@RestController
@RequestMapping(ApiRoutes.FINANCIAL_TRANSACTION)
public class FinancialTransactionController {

    private final FinancialTransactionService financialTransactionService;

    public FinancialTransactionController(FinancialTransactionService financialTransactionService) {
        this.financialTransactionService = financialTransactionService;
    }

    @Operation(summary = "Fetches a transaction by ID")
    @GetMapping("/{id}")
    public ResponseEntity<FinancialTransactionResponseDto> getTransaction(
            @PathVariable Long id,
            PlanContext ctx) {
        return ResponseEntity.ok(new FinancialTransactionResponseDto(financialTransactionService.findById(id, ctx)));
    }

    @Operation(summary = "Fetches all transactions for the plan")
    @GetMapping
    public ResponseEntity<PagedResponseDto<FinancialTransactionResponseDto>> getAllTransactions(
            @ParameterObject @ModelAttribute @Valid FinancialTransactionFilterDto filter,
            PlanContext ctx) {
        return ResponseEntity.ok(new PagedResponseDto<>(
                financialTransactionService.findAllByPlanPaged(filter, ctx).map(FinancialTransactionResponseDto::new)
        ));
    }

    @Operation(summary = "Creates a new transaction")
    @PostMapping
    public ResponseEntity<FinancialTransactionResponseDto> createTransaction(
            @RequestBody @Valid FinancialTransactionRequestDto dto,
            PlanContext ctx) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new FinancialTransactionResponseDto(financialTransactionService.create(dto, ctx)));
    }

    @Operation(summary = "Creates a series of recurring transactions")
    @PostMapping("/series")
    public ResponseEntity<FinancialTransactionSeriesResponseDto> createSeries(
            @RequestBody @Valid FinancialTransactionSeriesRequestDto dto,
            PlanContext ctx) {
        List<FinancialTransaction> occurrences = financialTransactionService.createSeries(dto, ctx);
        return ResponseEntity.status(HttpStatus.CREATED).body(new FinancialTransactionSeriesResponseDto(occurrences));
    }

    @Operation(summary = "Updates a transaction")
    @PutMapping("/{id}")
    public ResponseEntity<FinancialTransactionResponseDto> updateTransaction(
            @PathVariable Long id,
            @RequestBody @Valid FinancialTransactionRequestDto dto,
            PlanContext ctx) {
        return ResponseEntity.ok(new FinancialTransactionResponseDto(financialTransactionService.update(id, dto, ctx)));
    }

    @Operation(summary = "Imports transactions from a Nubank CSV file")
    @PostMapping("/import")
    public ResponseEntity<FinancialTransactionImportResponseDto> importCsv(
            @RequestParam("file") MultipartFile file,
            PlanContext ctx) {
        int imported = financialTransactionService.importFromNubankCsv(file, ctx);
        HttpStatus status = imported > 0 ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(new FinancialTransactionImportResponseDto(imported));
    }

    @Operation(summary = "Deletes a transaction")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(
            @PathVariable Long id,
            PlanContext ctx) {
        financialTransactionService.delete(id, ctx);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Deletes a series of recurring transactions (This one / This and following / All)")
    @DeleteMapping("/series/{seriesId}")
    public ResponseEntity<Void> deleteSeries(
            @PathVariable String seriesId,
            @RequestParam(defaultValue = "ALL") SeriesEditScope scope,
            @RequestParam(required = false) Long pivotOccurrenceId,
            PlanContext ctx) {
        financialTransactionService.deleteSeries(seriesId, scope, pivotOccurrenceId, ctx);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Fetches a series' recurrence definition (for edit-form prefill)")
    @GetMapping("/series/{seriesId}")
    public ResponseEntity<RecurrenceDefinitionResponseDto> getSeries(
            @PathVariable String seriesId,
            PlanContext ctx) {
        return ResponseEntity.ok(financialTransactionService.getSeriesDefinition(seriesId, ctx));
    }

    @Operation(summary = "Edits a series of recurring transactions (This one / This and following / All)")
    @PutMapping("/series/{seriesId}")
    public ResponseEntity<FinancialTransactionSeriesResponseDto> editSeries(
            @PathVariable String seriesId,
            @RequestBody @Valid SeriesEditRequestDto dto,
            PlanContext ctx) {
        return ResponseEntity.ok(financialTransactionService.editSeries(seriesId, dto, ctx));
    }
}
