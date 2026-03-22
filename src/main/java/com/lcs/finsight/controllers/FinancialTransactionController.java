package com.lcs.finsight.controllers;

import com.lcs.finsight.dtos.request.FinancialTransactionRequestDto;
import com.lcs.finsight.models.FinancialTransaction;
import com.lcs.finsight.models.User;
import com.lcs.finsight.services.FinancialTransactionService;
import com.lcs.finsight.services.UserService;
import com.lcs.finsight.utils.ApiRoutes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public ResponseEntity<FinancialTransaction> getTransaction(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User loggedUser = userService.findByEmail(userDetails.getUsername());
        return ResponseEntity.ok(financialTransactionService.findById(id, loggedUser));
    }

    @Operation(summary = "Busca todas as transações do usuário logado")
    @GetMapping
    public ResponseEntity<List<FinancialTransaction>> getAllTransactions(@AuthenticationPrincipal UserDetails userDetails) {
        User loggedUser = userService.findByEmail(userDetails.getUsername());
        return ResponseEntity.ok(financialTransactionService.findAllByUser(loggedUser));
    }

    @Operation(summary = "Cria uma nova transação")
    @PostMapping
    public ResponseEntity<FinancialTransaction> createTransaction(
            @RequestBody @Valid FinancialTransactionRequestDto dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        User loggedUser = userService.findByEmail(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(financialTransactionService.create(dto, loggedUser));
    }

    @Operation(summary = "Atualiza uma transação")
    @PutMapping("/{id}")
    public ResponseEntity<FinancialTransaction> updateTransaction(
            @PathVariable Long id,
            @RequestBody @Valid FinancialTransactionRequestDto dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        User loggedUser = userService.findByEmail(userDetails.getUsername());
        return ResponseEntity.ok(financialTransactionService.update(id, dto, loggedUser));
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
