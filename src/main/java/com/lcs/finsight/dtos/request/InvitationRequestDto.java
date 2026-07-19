package com.lcs.finsight.dtos.request;

import com.lcs.finsight.models.InvitationType;
import com.lcs.finsight.models.PlanRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class InvitationRequestDto {

    @NotNull(message = "Role cannot be null.")
    private PlanRole role;

    @NotNull(message = "Type cannot be null.")
    private InvitationType type;

    @Email(message = "Email must be valid.")
    private String email;

    private LocalDateTime expiresAt;

    public PlanRole getRole() {
        return role;
    }

    public InvitationType getType() {
        return type;
    }

    public String getEmail() {
        return email;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
}
