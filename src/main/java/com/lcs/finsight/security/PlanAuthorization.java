package com.lcs.finsight.security;

import com.lcs.finsight.exceptions.PlanExceptions;
import com.lcs.finsight.models.PlanRole;
import com.lcs.finsight.models.User;
import org.springframework.stereotype.Component;

/**
 * Layer 2 authorization: role x row-ownership rules. Pure logic, no dependencies —
 * every method throws a {@link PlanExceptions} on denial (403-mapped) and returns
 * silently when allowed.
 */
@Component
public class PlanAuthorization {

    /** Any member except VIEWER may create transactions. */
    public void requireCanCreateTransaction(PlanRole role) {
        if (role == PlanRole.VIEWER) {
            throw new PlanExceptions.InsufficientPlanRoleException(
                    "Viewers cannot create transactions.");
        }
    }

    /**
     * OWNER/EDITOR may modify any transaction; CONTRIBUTOR only their own;
     * VIEWER none.
     */
    public void requireCanModifyTransaction(PlanRole role, User rowOwner, User actor) {
        switch (role) {
            case OWNER, EDITOR -> {
                // allowed
            }
            case CONTRIBUTOR -> {
                if (rowOwner == null || actor == null
                        || !rowOwner.getId().equals(actor.getId())) {
                    throw new PlanExceptions.CannotModifyOthersTransactionException();
                }
            }
            case VIEWER -> throw new PlanExceptions.InsufficientPlanRoleException(
                    "Viewers cannot modify transactions.");
        }
    }

    /** Only OWNER may manage categories. */
    public void requireCanManageCategories(PlanRole role) {
        if (role != PlanRole.OWNER) {
            throw new PlanExceptions.InsufficientPlanRoleException(
                    "Only the plan owner can manage categories.");
        }
    }

    /** Only OWNER may perform owner-level actions. */
    public void requireOwner(PlanRole role) {
        if (role != PlanRole.OWNER) {
            throw new PlanExceptions.InsufficientPlanRoleException(
                    "Only the plan owner can perform this action.");
        }
    }
}
