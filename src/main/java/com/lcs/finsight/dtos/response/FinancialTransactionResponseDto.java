package com.lcs.finsight.dtos.response;

import com.lcs.finsight.models.FinancialTransaction;
import com.lcs.finsight.models.FinancialTransactionType;
import com.lcs.finsight.models.SplitMode;
import com.lcs.finsight.models.TransactionItem;
import com.lcs.finsight.models.TransactionParticipant;
import com.lcs.finsight.models.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class FinancialTransactionResponseDto {
    private final Long id;
    private final FinancialTransactionCategoryResponseDto category;
    private final FinancialTransactionType type;
    private final BigDecimal amount;
    private final String description;
    private final String seriesId;
    private final String frequency;
    private final Integer parcelsNumber;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final CreatedByDto createdBy;
    private final SplitMode splitMode;
    private final List<ParticipantDto> participants;
    private final List<ItemDto> items;

    public FinancialTransactionResponseDto(FinancialTransaction transaction) {
        this.id = transaction.getId();
        this.category = transaction.getCategory() != null
                ? new FinancialTransactionCategoryResponseDto(transaction.getCategory())
                : null;
        this.type = transaction.getType();
        this.amount = transaction.getAmount();
        this.description = transaction.getDescription();
        this.seriesId = transaction.getSeriesId();
        this.frequency = transaction.getFrequency();
        this.parcelsNumber = transaction.getParcelsNumber();
        this.startDate = transaction.getStartDate();
        this.endDate = transaction.getEndDate();
        this.createdBy = transaction.getCreatedBy() != null
                ? new CreatedByDto(transaction.getCreatedBy())
                : null;
        this.splitMode = transaction.getSplitMode();
        this.participants = transaction.getParticipants().stream()
                .map(ParticipantDto::new)
                .toList();
        this.items = transaction.getItems().stream()
                .map(ItemDto::new)
                .toList();
    }

    public static class CreatedByDto {
        private final Long id;
        private final String name;

        public CreatedByDto(User user) {
            this.id = user.getId();
            this.name = user.getName();
        }

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    public static class ParticipantDto {
        private final Long id;
        private final String name;
        private final BigDecimal shareAmount;

        public ParticipantDto(TransactionParticipant participant) {
            this.id = participant.getMember().getId();
            this.name = participant.getMember().getName();
            this.shareAmount = participant.getShareAmount();
        }

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public BigDecimal getShareAmount() {
            return shareAmount;
        }
    }

    public static class ItemDto {
        private final Long id;
        private final String description;
        private final BigDecimal amount;
        private final Integer quantity;
        private final FinancialTransactionCategoryResponseDto category;

        public ItemDto(TransactionItem item) {
            this.id = item.getId();
            this.description = item.getDescription();
            this.amount = item.getAmount();
            this.quantity = item.getQuantity();
            this.category = item.getCategory() != null
                    ? new FinancialTransactionCategoryResponseDto(item.getCategory())
                    : null;
        }

        public Long getId() {
            return id;
        }

        public String getDescription() {
            return description;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public FinancialTransactionCategoryResponseDto getCategory() {
            return category;
        }
    }

    public CreatedByDto getCreatedBy() {
        return createdBy;
    }

    public SplitMode getSplitMode() {
        return splitMode;
    }

    public List<ParticipantDto> getParticipants() {
        return participants;
    }

    public List<ItemDto> getItems() {
        return items;
    }

    public Long getId() {
        return id;
    }

    public FinancialTransactionCategoryResponseDto getCategory() {
        return category;
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

    public String getSeriesId() {
        return seriesId;
    }

    public String getFrequency() {
        return frequency;
    }

    public Integer getParcelsNumber() {
        return parcelsNumber;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }
}
