package com.lcs.finsight.dtos.request;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class ParticipantInputDto {

    @NotNull(message = "Participant member id cannot be null.")
    private Long memberId;

    // Only used when the transaction's splitMode is EXACT.
    private BigDecimal shareAmount;

    public Long getMemberId() {
        return memberId;
    }

    public BigDecimal getShareAmount() {
        return shareAmount;
    }
}
