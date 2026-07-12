package com.lcs.finsight.dtos.request;

import com.lcs.finsight.models.InvitationType;
import com.lcs.finsight.models.PlanRole;
import jakarta.validation.constraints.NotNull;

public class InvitationRequestDto {

    @NotNull(message = "Role cannot be null.")
    private PlanRole role;

    @NotNull(message = "Type cannot be null.")
    private InvitationType type;

    private String email;

    public PlanRole getRole() {
        return role;
    }

    public InvitationType getType() {
        return type;
    }

    public String getEmail() {
        return email;
    }
}
