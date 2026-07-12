package com.lcs.finsight.services;

import com.lcs.finsight.exceptions.PlanExceptions;
import com.lcs.finsight.models.InvitationStatus;
import com.lcs.finsight.models.InvitationType;
import com.lcs.finsight.models.PlanInvitation;
import com.lcs.finsight.models.PlanMembership;
import com.lcs.finsight.models.PlanRole;
import com.lcs.finsight.models.User;
import com.lcs.finsight.repositories.PlanInvitationRepository;
import com.lcs.finsight.repositories.PlanMembershipRepository;
import com.lcs.finsight.security.PlanAuthorization;
import com.lcs.finsight.security.PlanContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PlanInvitationService {

    private final PlanInvitationRepository invitationRepository;
    private final PlanMembershipRepository membershipRepository;
    private final PlanAuthorization planAuthorization;

    public PlanInvitationService(
            PlanInvitationRepository invitationRepository,
            PlanMembershipRepository membershipRepository,
            PlanAuthorization planAuthorization
    ) {
        this.invitationRepository = invitationRepository;
        this.membershipRepository = membershipRepository;
        this.planAuthorization = planAuthorization;
    }

    /** Only the plan owner may invite. Builds and persists a PENDING invitation. */
    @Transactional
    public PlanInvitation createInvite(
            PlanContext ctx, PlanRole role, InvitationType type, String email, LocalDateTime expiresAt) {
        planAuthorization.requireOwner(ctx.getRole());

        PlanInvitation invitation = new PlanInvitation();
        invitation.setPlan(ctx.getPlan());
        invitation.setRole(role);
        invitation.setType(type);
        invitation.setEmail(type == InvitationType.EMAIL ? email : null);
        invitation.setToken(UUID.randomUUID().toString());
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setInvitedBy(ctx.getUser());
        invitation.setExpiresAt(type == InvitationType.LINK ? expiresAt : null);

        return invitationRepository.save(invitation);
    }

    /** Reads an invitation by token for display before accepting. */
    @Transactional(readOnly = true)
    public PlanInvitation preview(String token) {
        return loadValidInvitation(token);
    }

    /**
     * Accepts an invitation for the acting user. Idempotent: if the user is already a
     * member the existing membership is returned without duplicating it. EMAIL invites
     * are single-use (marked ACCEPTED); LINK invites stay PENDING (reusable).
     */
    @Transactional
    public PlanMembership accept(String token, User actor) {
        PlanInvitation invitation = loadValidInvitation(token);

        if (membershipRepository.existsByPlanAndUser(invitation.getPlan(), actor)) {
            if (invitation.getType() == InvitationType.EMAIL) {
                invitation.setStatus(InvitationStatus.ACCEPTED);
            }
            return membershipRepository.findByPlanAndUser(invitation.getPlan(), actor)
                    .orElseThrow(() -> new PlanExceptions.NotAMemberException(invitation.getPlan().getId()));
        }

        PlanMembership membership = new PlanMembership();
        membership.setPlan(invitation.getPlan());
        membership.setUser(actor);
        membership.setRole(invitation.getRole());
        PlanMembership savedMembership = membershipRepository.save(membership);

        if (invitation.getType() == InvitationType.EMAIL) {
            invitation.setStatus(InvitationStatus.ACCEPTED);
        }

        return savedMembership;
    }

    /** Only the plan owner may list the active plan's invitations. */
    @Transactional(readOnly = true)
    public List<PlanInvitation> listInvitations(PlanContext ctx) {
        planAuthorization.requireOwner(ctx.getRole());
        return invitationRepository.findAllByPlan(ctx.getPlan());
    }

    /** Only the plan owner may revoke an invitation belonging to the active plan. */
    @Transactional
    public void revoke(PlanContext ctx, Long invitationId) {
        planAuthorization.requireOwner(ctx.getRole());

        PlanInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new PlanExceptions.InvitationNotFoundException(String.valueOf(invitationId)));

        if (!invitation.getPlan().getId().equals(ctx.getPlan().getId())) {
            throw new PlanExceptions.InvitationNotFoundException(String.valueOf(invitationId));
        }

        invitation.setStatus(InvitationStatus.REVOKED);
    }

    /**
     * Loads by token, rejecting missing / revoked / already-consumed EMAIL / expired
     * invitations. Expiry is checked lazily against {@code expiresAt} rather than
     * persisted as a status flip: the {@code plan_invitations_status_check} DB
     * constraint only allows PENDING/ACCEPTED/REVOKED, so writing EXPIRED would require
     * a schema migration out of scope here.
     */
    private PlanInvitation loadValidInvitation(String token) {
        PlanInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new PlanExceptions.InvitationNotFoundException(token));

        if (invitation.getStatus() == InvitationStatus.REVOKED) {
            throw new PlanExceptions.InvitationInvalidException("This invitation has been revoked.");
        }
        if (invitation.getType() == InvitationType.EMAIL
                && invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw new PlanExceptions.InvitationInvalidException("This invitation has already been used.");
        }
        if (invitation.getExpiresAt() != null && invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new PlanExceptions.InvitationExpiredException("This invitation has expired.");
        }

        return invitation;
    }
}
