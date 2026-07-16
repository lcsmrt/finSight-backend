package com.lcs.finsight.dtos.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class DashboardFilterDto {

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    private Long memberId;

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
}
