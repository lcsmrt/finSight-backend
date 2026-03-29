package com.lcs.finsight.controllers;

import com.lcs.finsight.dtos.request.LoginRequestDto;
import com.lcs.finsight.dtos.response.AuthenticationResponseDto;
import com.lcs.finsight.dtos.response.UserResponseDto;
import com.lcs.finsight.models.User;
import com.lcs.finsight.repositories.UserRepository;
import com.lcs.finsight.services.AuthenticationService;
import com.lcs.finsight.services.UserService;
import com.lcs.finsight.utils.ApiRoutes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentication")
@RestController
@RequestMapping(ApiRoutes.AUTH)
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final UserService userService;

    public AuthenticationController(AuthenticationService authenticationService, UserRepository userRepository, UserService userService) {
        this.authenticationService = authenticationService;
        this.userService = userService;
    }

    @Operation(summary = "Authenticates a user")
    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponseDto> login(@RequestBody @Valid LoginRequestDto dto) {
        String token = authenticationService.authenticate(dto);

        return  ResponseEntity.ok(new AuthenticationResponseDto(token));
    }

    @Operation(summary = "Returns the authenticated user's data")
    @GetMapping("/profile")
    public ResponseEntity<UserResponseDto> getUser(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.findByEmail(userDetails.getUsername());
        UserResponseDto response = userService.mapToResponseDTO(user);

        return ResponseEntity.ok(response);
    }
}
