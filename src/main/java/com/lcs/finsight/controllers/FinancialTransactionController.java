package com.lcs.finsight.controllers;

import com.lcs.finsight.dtos.request.FinancialTransactionFilterDto;
import com.lcs.finsight.dtos.request.FinancialTransactionRequestDto;
import com.lcs.finsight.dtos.response.FinancialTransactionResponseDto;
import com.lcs.finsight.dtos.response.PagedResponseDto;
import com.lcs.finsight.models.User;
import com.lcs.finsight.services.FinancialTransactionService;
import com.lcs.finsight.services.UserService;
import com.lcs.finsight.utils.ApiRoutes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Transações Financeiras")
@RestController
@RequestMapping(ApiRoutes.FINANCIAL_TRANSACTION)
public class FinancialTransactionController {

    private final FinancialTransactionService financialTransactionService;
    private final UserService userService;

    public FinancialTransactionController(FinancialTransactionService financialTransactionService, UserService userService) {
        this.financialTransactionService = financialTransactionService;
        this.userService = userService;
    }

    @Operation(summary = "Busca uma transação pelo ID")
    @GetMapping("/{id}")
    public ResponseEntity<FinancialTransactionResponseDto> getTransaction(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User loggedUser = userService.findByEmail(userDetails.getUsername());
        return ResponseEntity.ok(new FinancialTransactionResponseDto(financialTransactionService.findById(id, loggedUser)));
    }

    @Operation(summary = "Busca todas as transações do usuário logado")
    @GetMapping
    public ResponseEntity<PagedResponseDto<FinancialTransactionResponseDto>> getAllTransactions(
            @ParameterObject @ModelAttribute @Valid FinancialTransactionFilterDto filter,
            @AuthenticationPrincipal UserDetails userDetails) {
        User loggedUser = userService.findByEmail(userDetails.getUsername());
        return ResponseEntity.ok(new PagedResponseDto<>(
                financialTransactionService.findAllByUserPaged(filter, loggedUser).map(FinancialTransactionResponseDto::new)
        ));
    }

    @Operation(summary = "Cria uma nova transação")
    @PostMapping
    public ResponseEntity<FinancialTransactionResponseDto> createTransaction(
            @RequestBody @Valid FinancialTransactionRequestDto dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        User loggedUser = userService.findByEmail(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(new FinancialTransactionResponseDto(financialTransactionService.create(dto, loggedUser)));
    }

    @Operation(summary = "Atualiza uma transação")
    @PutMapping("/{id}")
    public ResponseEntity<FinancialTransactionResponseDto> updateTransaction(
            @PathVariable Long id,
            @RequestBody @Valid FinancialTransactionRequestDto dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        User loggedUser = userService.findByEmail(userDetails.getUsername());
        return ResponseEntity.ok(new FinancialTransactionResponseDto(financialTransactionService.update(id, dto, loggedUser)));
    }

    @Operation(summary = "Deleta uma transação")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User loggedUser = userService.findByEmail(userDetails.getUsername());
        financialTransactionService.delete(id, loggedUser);
        return ResponseEntity.noContent().build();
    }
}
