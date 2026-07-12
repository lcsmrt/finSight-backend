package com.lcs.finsight.security;

import com.lcs.finsight.models.Plan;
import com.lcs.finsight.models.PlanRole;
import com.lcs.finsight.models.User;

/**
 * Immutable per-request holder for the active plan, the acting user and their role
 * within that plan. Produced by {@link PlanContextArgumentResolver} from the
 * {@code {planId}} path variable and declared as a controller method parameter.
 */
public final class PlanContext {

    private final Plan plan;
    private final User user;
    private final PlanRole role;

    public PlanContext(Plan plan, User user, PlanRole role) {
        this.plan = plan;
        this.user = user;
        this.role = role;
    }

    public Plan getPlan() {
        return plan;
    }

    public User getUser() {
        return user;
    }

    public PlanRole getRole() {
        return role;
    }
}
