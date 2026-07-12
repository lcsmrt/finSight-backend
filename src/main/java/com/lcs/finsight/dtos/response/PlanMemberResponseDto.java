package com.lcs.finsight.dtos.response;

import com.lcs.finsight.models.PlanMembership;
import com.lcs.finsight.models.PlanRole;
import com.lcs.finsight.models.User;

public class PlanMemberResponseDto {
    private final Long userId;
    private final String name;
    private final String email;
    private final PlanRole role;

    public PlanMemberResponseDto(PlanMembership membership) {
        User user = membership.getUser();
        this.userId = user.getId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.role = membership.getRole();
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public PlanRole getRole() {
        return role;
    }
}
