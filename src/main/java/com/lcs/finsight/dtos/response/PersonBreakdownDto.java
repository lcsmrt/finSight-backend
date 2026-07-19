package com.lcs.finsight.dtos.response;

import java.math.BigDecimal;

public class PersonBreakdownDto {
    private final Long userId;
    private final String name;
    private final BigDecimal income;
    private final BigDecimal expense;

    public PersonBreakdownDto(Long userId, String name, BigDecimal income, BigDecimal expense) {
        this.userId = userId;
        this.name = name;
        this.income = income;
        this.expense = expense;
    }

    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public BigDecimal getIncome() { return income; }
    public BigDecimal getExpense() { return expense; }
    public BigDecimal getNet() { return income.subtract(expense); }
}
