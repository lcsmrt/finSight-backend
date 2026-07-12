package com.lcs.finsight.dtos.response;

import com.lcs.finsight.models.PlanInvitation;
import com.lcs.finsight.models.PlanRole;

public class InvitationPreviewDto {
    private final String planName;
    private final PlanRole role;
    private final String invitedByName;

    public InvitationPreviewDto(PlanInvitation invitation) {
        this.planName = invitation.getPlan().getName();
        this.role = invitation.getRole();
        this.invitedByName = invitation.getInvitedBy().getName();
    }

    public String getPlanName() {
        return planName;
    }

    public PlanRole getRole() {
        return role;
    }

    public String getInvitedByName() {
        return invitedByName;
    }
}
