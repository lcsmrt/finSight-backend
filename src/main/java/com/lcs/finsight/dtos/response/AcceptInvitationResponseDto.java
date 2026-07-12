package com.lcs.finsight.dtos.response;

import com.lcs.finsight.models.PlanMembership;
import com.lcs.finsight.models.PlanRole;

public class AcceptInvitationResponseDto {
    private final Long planId;
    private final PlanRole role;

    public AcceptInvitationResponseDto(PlanMembership membership) {
        this.planId = membership.getPlan().getId();
        this.role = membership.getRole();
    }

    public Long getPlanId() {
        return planId;
    }

    public PlanRole getRole() {
        return role;
    }
}
