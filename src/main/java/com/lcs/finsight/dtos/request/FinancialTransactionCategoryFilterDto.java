package com.lcs.finsight.dtos.request;

public class FinancialTransactionCategoryFilterDto extends PaginatedFilterDto {

    private String description;

    public FinancialTransactionCategoryFilterDto() {
        super("description", "asc");
    }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
