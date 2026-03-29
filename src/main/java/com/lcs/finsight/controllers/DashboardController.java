package com.lcs.finsight.controllers;

import com.lcs.finsight.dtos.request.DashboardFilterDto;
import com.lcs.finsight.dtos.response.DashboardSummaryDto;
import com.lcs.finsight.models.User;
import com.lcs.finsight.services.DashboardService;
import com.lcs.finsight.services.UserService;
import com.lcs.finsight.utils.ApiRoutes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Dashboard")
@RestController
@RequestMapping(ApiRoutes.DASHBOARD)
public class DashboardController {

    private final DashboardService dashboardService;
    private final UserService userService;

    public DashboardController(DashboardService dashboardService, UserService userService) {
        this.dashboardService = dashboardService;
        this.userService = userService;
    }

    @Operation(summary = "Returns the financial summary for the authenticated user")
    @GetMapping
    public ResponseEntity<DashboardSummaryDto> getSummary(
            @ParameterObject @ModelAttribute @Valid DashboardFilterDto filter,
            @AuthenticationPrincipal UserDetails userDetails) {
        User loggedUser = userService.findByEmail(userDetails.getUsername());
        return ResponseEntity.ok(dashboardService.getSummary(loggedUser, filter.getStartDate(), filter.getEndDate()));
    }
}
