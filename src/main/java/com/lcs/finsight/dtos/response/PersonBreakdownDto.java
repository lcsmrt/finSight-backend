package com.lcs.finsight.dtos.response;

import java.math.BigDecimal;

public class PersonBreakdownDto {
    private final Long userId;
    private final String name;
    private BigDecimal income;
    private BigDecimal expense;

    public PersonBreakdownDto(Long userId, String name) {
        this.userId = userId;
        this.name = name;
        this.income = BigDecimal.ZERO;
        this.expense = BigDecimal.ZERO;
    }

    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public BigDecimal getIncome() { return income; }
    public BigDecimal getExpense() { return expense; }
    public BigDecimal getNet() { return income.subtract(expense); }
    public void setIncome(BigDecimal income) { this.income = income; }
    public void setExpense(BigDecimal expense) { this.expense = expense; }
}
