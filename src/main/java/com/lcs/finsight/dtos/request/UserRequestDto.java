package com.lcs.finsight.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class UserRequestDto {

    @NotBlank(message = "User name cannot be blank.")
    private String name;

    @NotBlank(message = "User email cannot be blank.")
    @Email(message = "Invalid email format.")
    private String email;

    @NotBlank(message = "User password cannot be blank.")
    private String password;

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }
}
