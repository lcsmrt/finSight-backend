package com.lcs.finsight.controllers;

import com.lcs.finsight.dtos.request.InvitationRequestDto;
import com.lcs.finsight.dtos.response.AcceptInvitationResponseDto;
import com.lcs.finsight.dtos.response.InvitationPreviewResponseDto;
import com.lcs.finsight.dtos.response.InvitationResponseDto;
import com.lcs.finsight.models.PlanInvitation;
import com.lcs.finsight.models.User;
import com.lcs.finsight.security.PlanContext;
import com.lcs.finsight.services.PlanInvitationService;
import com.lcs.finsight.services.UserService;
import com.lcs.finsight.utils.ApiRoutes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Plan Invitations")
@RestController
public class PlanInvitationController {

    private final PlanInvitationService invitationService;
    private final UserService userService;

    public PlanInvitationController(PlanInvitationService invitationService, UserService userService) {
        this.invitationService = invitationService;
        this.userService = userService;
    }

    @Operation(summary = "Creates an invitation (email or link) for the plan; owner only")
    @PostMapping(ApiRoutes.PLAN_INVITATION)
    public ResponseEntity<InvitationResponseDto> createInvitation(
            @RequestBody @Valid InvitationRequestDto dto,
            PlanContext ctx) {
        PlanInvitation invitation = invitationService.createInvite(
                ctx, dto.getRole(), dto.getType(), dto.getEmail(), dto.getExpiresAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(new InvitationResponseDto(invitation));
    }

    @Operation(summary = "Lists a plan's invitations (owner only)")
    @GetMapping(ApiRoutes.PLAN_INVITATION)
    public ResponseEntity<List<InvitationResponseDto>> listInvitations(PlanContext ctx) {
        List<InvitationResponseDto> invitations = invitationService.listInvitations(ctx).stream()
                .map(InvitationResponseDto::new)
                .toList();
        return ResponseEntity.ok(invitations);
    }

    @Operation(summary = "Revokes an invitation (owner only)")
    @DeleteMapping(ApiRoutes.PLAN_INVITATION + "/{invitationId}")
    public ResponseEntity<Void> revokeInvitation(@PathVariable Long invitationId, PlanContext ctx) {
        invitationService.revoke(ctx, invitationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Previews an invitation by token before accepting")
    @GetMapping(ApiRoutes.INVITATION + "/{token}")
    public ResponseEntity<InvitationPreviewResponseDto> previewInvitation(@PathVariable String token) {
        return ResponseEntity.ok(new InvitationPreviewResponseDto(invitationService.preview(token)));
    }

    @Operation(summary = "Accepts an invitation by token for the authenticated user")
    @PostMapping(ApiRoutes.INVITATION + "/{token}/accept")
    public ResponseEntity<AcceptInvitationResponseDto> acceptInvitation(
            @PathVariable String token,
            @AuthenticationPrincipal UserDetails userDetails) {
        User actor = userService.findByEmail(userDetails.getUsername());
        PlanInvitationService.AcceptResult result = invitationService.accept(token, actor);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(new AcceptInvitationResponseDto(result.membership()));
    }
}
