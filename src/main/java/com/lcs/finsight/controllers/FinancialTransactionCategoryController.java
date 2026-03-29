package com.lcs.finsight.controllers;

import com.lcs.finsight.dtos.request.FinancialTransactionCategoryFilterDto;
import com.lcs.finsight.dtos.request.FinancialTransactionCategoryRequestDto;
import com.lcs.finsight.dtos.response.FinancialTransactionCategoryResponseDto;
import com.lcs.finsight.dtos.response.PagedResponseDto;
import com.lcs.finsight.models.User;
import com.lcs.finsight.services.FinancialTransactionCategoryService;
import com.lcs.finsight.services.UserService;
import com.lcs.finsight.utils.ApiRoutes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Financial Transaction Categories")
@RestController
@RequestMapping(ApiRoutes.FINANCIAL_TRANSACTION_CATEGORY)
public class FinancialTransactionCategoryController {

    private final FinancialTransactionCategoryService categoryService;
    private final UserService userService;

    public FinancialTransactionCategoryController(FinancialTransactionCategoryService categoryService, UserService userService) {
        this.categoryService = categoryService;
        this.userService = userService;
    }

    @Operation(summary = "Fetches a transaction category by ID")
    @GetMapping("/{id}")
    public ResponseEntity<FinancialTransactionCategoryResponseDto> getCategory(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User loggedUser = userService.findByEmail(userDetails.getUsername());
        return ResponseEntity.ok(new FinancialTransactionCategoryResponseDto(categoryService.findById(id, loggedUser)));
    }

    @Operation(summary = "Fetches all transaction categories for the authenticated user")
    @GetMapping
    public ResponseEntity<PagedResponseDto<FinancialTransactionCategoryResponseDto>> getAllCategories(
            @ParameterObject @ModelAttribute @Valid FinancialTransactionCategoryFilterDto filter,
            @AuthenticationPrincipal UserDetails userDetails) {
        User loggedUser = userService.findByEmail(userDetails.getUsername());
        return ResponseEntity.ok(new PagedResponseDto<>(
                categoryService.findAllByUserPaged(filter, loggedUser).map(FinancialTransactionCategoryResponseDto::new)
        ));
    }

    @Operation(summary = "Creates a new transaction category for the authenticated user")
    @PostMapping
    public ResponseEntity<FinancialTransactionCategoryResponseDto> createCategory(
            @RequestBody @Valid FinancialTransactionCategoryRequestDto dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        User loggedUser = userService.findByEmail(userDetails.getUsername());
        return ResponseEntity.status(201).body(new FinancialTransactionCategoryResponseDto(categoryService.create(dto, loggedUser)));
    }

    @Operation(summary = "Updates a transaction category for the authenticated user")
    @PutMapping("/{id}")
    public ResponseEntity<FinancialTransactionCategoryResponseDto> updateCategory(
            @PathVariable Long id,
            @RequestBody @Valid FinancialTransactionCategoryRequestDto dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        User loggedUser = userService.findByEmail(userDetails.getUsername());
        return ResponseEntity.ok(new FinancialTransactionCategoryResponseDto(categoryService.update(id, dto, loggedUser)));
    }

    @Operation(summary = "Deletes a transaction category")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User loggedUser = userService.findByEmail(userDetails.getUsername());
        categoryService.delete(id, loggedUser);
        return ResponseEntity.noContent().build();
    }
}
