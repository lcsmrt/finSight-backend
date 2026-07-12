package com.lcs.finsight.controllers;

import com.lcs.finsight.dtos.request.DashboardFilterDto;
import com.lcs.finsight.dtos.response.DashboardSummaryDto;
import com.lcs.finsight.security.PlanContext;
import com.lcs.finsight.services.DashboardService;
import com.lcs.finsight.utils.ApiRoutes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Dashboard")
@RestController
@RequestMapping(ApiRoutes.DASHBOARD)
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(summary = "Returns the financial summary for the plan")
    @GetMapping
    public ResponseEntity<DashboardSummaryDto> getSummary(
            @ParameterObject @ModelAttribute @Valid DashboardFilterDto filter,
            PlanContext ctx) {
        return ResponseEntity.ok(dashboardService.getSummary(ctx, filter.getStartDate(), filter.getEndDate()));
    }
}
