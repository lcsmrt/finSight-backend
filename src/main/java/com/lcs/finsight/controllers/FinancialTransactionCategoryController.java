package com.lcs.finsight.controllers;

import com.lcs.finsight.dtos.request.FinancialTransactionCategoryFilterDto;
import com.lcs.finsight.dtos.request.FinancialTransactionCategoryRequestDto;
import com.lcs.finsight.dtos.response.FinancialTransactionCategoryResponseDto;
import com.lcs.finsight.dtos.response.PagedResponseDto;
import com.lcs.finsight.security.PlanContext;
import com.lcs.finsight.services.FinancialTransactionCategoryService;
import com.lcs.finsight.utils.ApiRoutes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Financial Transaction Categories")
@RestController
@RequestMapping(ApiRoutes.FINANCIAL_TRANSACTION_CATEGORY)
public class FinancialTransactionCategoryController {

    private final FinancialTransactionCategoryService categoryService;

    public FinancialTransactionCategoryController(FinancialTransactionCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "Fetches a transaction category by ID")
    @GetMapping("/{id}")
    public ResponseEntity<FinancialTransactionCategoryResponseDto> getCategory(
            @PathVariable Long id,
            PlanContext ctx) {
        return ResponseEntity.ok(new FinancialTransactionCategoryResponseDto(categoryService.findById(id, ctx)));
    }

    @Operation(summary = "Fetches all transaction categories for the plan")
    @GetMapping
    public ResponseEntity<PagedResponseDto<FinancialTransactionCategoryResponseDto>> getAllCategories(
            @ParameterObject @ModelAttribute @Valid FinancialTransactionCategoryFilterDto filter,
            PlanContext ctx) {
        return ResponseEntity.ok(new PagedResponseDto<>(
                categoryService.findAllByPlanPaged(filter, ctx).map(FinancialTransactionCategoryResponseDto::new)
        ));
    }

    @Operation(summary = "Creates a new transaction category for the plan")
    @PostMapping
    public ResponseEntity<FinancialTransactionCategoryResponseDto> createCategory(
            @RequestBody @Valid FinancialTransactionCategoryRequestDto dto,
            PlanContext ctx) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new FinancialTransactionCategoryResponseDto(categoryService.create(dto, ctx)));
    }

    @Operation(summary = "Updates a transaction category for the plan")
    @PutMapping("/{id}")
    public ResponseEntity<FinancialTransactionCategoryResponseDto> updateCategory(
            @PathVariable Long id,
            @RequestBody @Valid FinancialTransactionCategoryRequestDto dto,
            PlanContext ctx) {
        return ResponseEntity.ok(new FinancialTransactionCategoryResponseDto(categoryService.update(id, dto, ctx)));
    }

    @Operation(summary = "Deletes a transaction category")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable Long id,
            PlanContext ctx) {
        categoryService.delete(id, ctx);
        return ResponseEntity.noContent().build();
    }
}
