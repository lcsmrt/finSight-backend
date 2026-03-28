package com.lcs.finsight.controllers;

import com.lcs.finsight.utils.ApiRoutes;
import com.lcs.finsight.dtos.request.UserRequestDto;
import com.lcs.finsight.dtos.response.UserResponseDto;
import com.lcs.finsight.models.User;
import com.lcs.finsight.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Usuários")
@RestController
@RequestMapping(ApiRoutes.USER)
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Cria um novo usuário")
    @PostMapping
    public ResponseEntity<UserResponseDto> createUser(@RequestBody @Valid UserRequestDto dto) {
        User createdUser = userService.create(dto);
        UserResponseDto response = userService.mapToResponseDTO(createdUser);
        return ResponseEntity.status(201).body(response);
    }

    @Operation(summary = "Atualiza o usuário autenticado")
    @PutMapping
    public ResponseEntity<UserResponseDto> updateUser(
            @RequestBody @Valid UserRequestDto dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        User loggedUser = userService.findByEmail(userDetails.getUsername());
        User updatedUser = userService.update(loggedUser, dto);
        UserResponseDto response = userService.mapToResponseDTO(updatedUser);
        return ResponseEntity.ok(response);
    }
}
