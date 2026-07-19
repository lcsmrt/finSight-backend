package com.lcs.finsight.controllers;

import com.lcs.finsight.dtos.request.LoginRequestDto;
import com.lcs.finsight.dtos.response.AuthenticationResponseDto;
import com.lcs.finsight.services.AuthenticationService;
import com.lcs.finsight.utils.ApiRoutes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentication")
@RestController
@RequestMapping(ApiRoutes.AUTH)
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    public AuthenticationController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Operation(summary = "Authenticates a user")
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponseDto> login(@RequestBody @Valid LoginRequestDto dto) {
        String token = authenticationService.authenticate(dto);

        return ResponseEntity.ok(new AuthenticationResponseDto(token));
    }
}
