package com.lcs.finsight.dtos.request;

import jakarta.validation.constraints.NotBlank;

public class LoginRequestDto {

    @NotBlank(message = "User email cannot be blank.")
    private String email;

    @NotBlank(message = "User password cannot be blank.")
    private String password;

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }
}
