package com.lcs.finsight.dtos.response;

import com.lcs.finsight.models.InvitationStatus;
import com.lcs.finsight.models.InvitationType;
import com.lcs.finsight.models.PlanInvitation;
import com.lcs.finsight.models.PlanRole;

import java.time.LocalDateTime;

public class InvitationResponseDto {
    private final Long id;
    private final String token;
    private final PlanRole role;
    private final InvitationType type;
    private final String email;
    private final InvitationStatus status;
    private final String link;
    private final LocalDateTime expiresAt;

    public InvitationResponseDto(PlanInvitation invitation) {
        this.id = invitation.getId();
        this.token = invitation.getToken();
        this.role = invitation.getRole();
        this.type = invitation.getType();
        this.email = invitation.getEmail();
        this.expiresAt = invitation.getExpiresAt();
        // Expiry is never persisted as a status (the DB check constraint only allows
        // PENDING/ACCEPTED/REVOKED); it is computed here so listings reflect reality
        // even for an expired invitation that was never previewed/accepted.
        this.status = invitation.getStatus() == InvitationStatus.PENDING
                && expiresAt != null
                && expiresAt.isBefore(LocalDateTime.now())
                ? InvitationStatus.EXPIRED
                : invitation.getStatus();
        this.link = invitation.getType() == InvitationType.LINK
                ? "/invitations/" + invitation.getToken()
                : null;
    }

    public Long getId() {
        return id;
    }

    public String getToken() {
        return token;
    }

    public PlanRole getRole() {
        return role;
    }

    public InvitationType getType() {
        return type;
    }

    public String getEmail() {
        return email;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public String getLink() {
        return link;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
}
