package com.lcs.finsight.controllers;

import com.lcs.finsight.dtos.request.PlanRequestDto;
import com.lcs.finsight.dtos.request.TransferOwnershipRequestDto;
import com.lcs.finsight.dtos.request.UpdateMemberRoleRequestDto;
import com.lcs.finsight.dtos.response.PlanMemberResponseDto;
import com.lcs.finsight.dtos.response.PlanResponseDto;
import com.lcs.finsight.models.PlanMembership;
import com.lcs.finsight.models.User;
import com.lcs.finsight.security.CurrentUser;
import com.lcs.finsight.services.PlanService;
import com.lcs.finsight.utils.ApiRoutes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Plans")
@RestController
@RequestMapping(ApiRoutes.PLAN)
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @Operation(summary = "Creates a new plan owned by the authenticated user")
    @PostMapping
    public ResponseEntity<PlanResponseDto> createPlan(
            @RequestBody @Valid PlanRequestDto dto,
            @CurrentUser User loggedUser) {
        PlanMembership membership = planService.createPlan(dto.getName(), loggedUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(new PlanResponseDto(membership));
    }

    @Operation(summary = "Fetches all plans the authenticated user belongs to")
    @GetMapping
    public ResponseEntity<List<PlanResponseDto>> getPlans(@CurrentUser User loggedUser) {
        List<PlanResponseDto> plans = planService.findMembershipsForUser(loggedUser).stream()
                .map(PlanResponseDto::new)
                .toList();
        return ResponseEntity.ok(plans);
    }

    @Operation(summary = "Fetches a plan the authenticated user belongs to")
    @GetMapping("/{id}")
    public ResponseEntity<PlanResponseDto> getPlan(
            @PathVariable Long id,
            @CurrentUser User loggedUser) {
        return ResponseEntity.ok(new PlanResponseDto(planService.getMembership(id, loggedUser)));
    }

    @Operation(summary = "Fetches the members of a plan the authenticated user belongs to")
    @GetMapping("/{id}/members")
    public ResponseEntity<List<PlanMemberResponseDto>> getMembers(
            @PathVariable Long id,
            @CurrentUser User loggedUser) {
        List<PlanMemberResponseDto> members = planService.getMembers(id, loggedUser).stream()
                .map(PlanMemberResponseDto::new)
                .toList();
        return ResponseEntity.ok(members);
    }

    @Operation(summary = "Renames a plan (owner only)")
    @PutMapping("/{id}")
    public ResponseEntity<PlanResponseDto> renamePlan(
            @PathVariable Long id,
            @RequestBody @Valid PlanRequestDto dto,
            @CurrentUser User loggedUser) {
        return ResponseEntity.ok(new PlanResponseDto(planService.renamePlan(id, dto.getName(), loggedUser)));
    }

    @Operation(summary = "Deletes (soft-delete) a plan (owner only)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlan(
            @PathVariable Long id,
            @CurrentUser User loggedUser) {
        planService.deletePlan(id, loggedUser);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Leaves a plan (non-owner only)")
    @PostMapping("/{id}/leave")
    public ResponseEntity<Void> leavePlan(
            @PathVariable Long id,
            @CurrentUser User loggedUser) {
        planService.leavePlan(id, loggedUser);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Transfers plan ownership to another member (owner only)")
    @PostMapping("/{id}/transfer")
    public ResponseEntity<Void> transferOwnership(
            @PathVariable Long id,
            @RequestBody @Valid TransferOwnershipRequestDto dto,
            @CurrentUser User loggedUser) {
        planService.transferOwnership(id, dto.getNewOwnerUserId(), dto.getPreviousOwnerRole(), loggedUser);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Changes the role of a plan member (owner only)")
    @PutMapping("/{id}/members/{userId}")
    public ResponseEntity<PlanMemberResponseDto> updateMemberRole(
            @PathVariable Long id,
            @PathVariable Long userId,
            @RequestBody @Valid UpdateMemberRoleRequestDto dto,
            @CurrentUser User loggedUser) {
        return ResponseEntity.ok(new PlanMemberResponseDto(
                planService.changeMemberRole(id, userId, dto.getRole(), loggedUser)));
    }

    @Operation(summary = "Removes a member from a plan (owner only)")
    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long id,
            @PathVariable Long userId,
            @CurrentUser User loggedUser) {
        planService.removeMember(id, userId, loggedUser);
        return ResponseEntity.noContent().build();
    }
}
