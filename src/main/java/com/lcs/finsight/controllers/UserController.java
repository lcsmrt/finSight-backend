package com.lcs.finsight.controllers;

import com.lcs.finsight.utils.ApiRoutes;
import com.lcs.finsight.dtos.request.UserRequestDto;
import com.lcs.finsight.dtos.response.UserResponseDto;
import com.lcs.finsight.models.User;
import com.lcs.finsight.security.CurrentUser;
import com.lcs.finsight.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Users")
@RestController
@RequestMapping(ApiRoutes.USER)
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Creates a new user")
    @SecurityRequirements
    @PostMapping
    public ResponseEntity<UserResponseDto> createUser(@RequestBody @Valid UserRequestDto dto) {
        User createdUser = userService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(new UserResponseDto(createdUser));
    }

    @Operation(summary = "Returns the authenticated user's data")
    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> getCurrentUser(@CurrentUser User user) {
        return ResponseEntity.ok(new UserResponseDto(user));
    }

    @Operation(summary = "Updates the authenticated user")
    @PutMapping
    public ResponseEntity<UserResponseDto> updateUser(
            @RequestBody @Valid UserRequestDto dto,
            @CurrentUser User loggedUser) {
        User updatedUser = userService.update(loggedUser, dto);
        return ResponseEntity.ok(new UserResponseDto(updatedUser));
    }
}
