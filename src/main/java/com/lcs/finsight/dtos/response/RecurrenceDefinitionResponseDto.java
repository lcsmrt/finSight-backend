package com.lcs.finsight.dtos.response;

import com.lcs.finsight.models.FinancialTransactionType;
import com.lcs.finsight.models.RecurrenceDefinition;
import com.lcs.finsight.models.RecurrenceDefinitionParticipant;
import com.lcs.finsight.models.RecurrenceInterval;
import com.lcs.finsight.models.RecurrenceMode;
import com.lcs.finsight.models.SplitMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public class RecurrenceDefinitionResponseDto {
    private final Long id;
    private final String seriesId;
    private final FinancialTransactionType type;
    private final BigDecimal amount;
    private final String description;
    private final Long categoryId;
    private final RecurrenceMode mode;
    private final RecurrenceInterval interval;
    private final Integer parcelsNumber;
    private final Integer firstParcel;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final SplitMode splitMode;
    private final List<ParticipantTemplateDto> participants;

    public RecurrenceDefinitionResponseDto(RecurrenceDefinition def) {
        this.id = def.getId();
        this.seriesId = def.getSeriesId();
        this.type = def.getType();
        this.amount = def.getAmount();
        this.description = def.getDescription();
        this.categoryId = def.getCategory() != null ? def.getCategory().getId() : null;
        this.mode = def.getMode();
        this.interval = def.getRecurrenceInterval();
        this.parcelsNumber = def.getParcelsNumber();
        this.firstParcel = def.getFirstParcel();
        this.startDate = def.getStartDate();
        this.endDate = def.getEndDate();
        this.splitMode = def.getSplitMode();
        this.participants = def.getParticipants().stream()
                .map(ParticipantTemplateDto::new)
                .collect(Collectors.toList());
    }

    public static class ParticipantTemplateDto {
        private final Long memberId;
        private final BigDecimal shareAmount;

        public ParticipantTemplateDto(RecurrenceDefinitionParticipant participant) {
            this.memberId = participant.getMember().getId();
            this.shareAmount = participant.getShareAmount();
        }

        public Long getMemberId() {
            return memberId;
        }

        public BigDecimal getShareAmount() {
            return shareAmount;
        }
    }

    public Long getId() {
        return id;
    }

    public String getSeriesId() {
        return seriesId;
    }

    public FinancialTransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public RecurrenceMode getMode() {
        return mode;
    }

    public RecurrenceInterval getInterval() {
        return interval;
    }

    public Integer getParcelsNumber() {
        return parcelsNumber;
    }

    public Integer getFirstParcel() {
        return firstParcel;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public SplitMode getSplitMode() {
        return splitMode;
    }

    public List<ParticipantTemplateDto> getParticipants() {
        return participants;
    }
}
