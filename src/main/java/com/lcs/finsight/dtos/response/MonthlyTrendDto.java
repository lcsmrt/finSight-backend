package com.lcs.finsight.dtos.response;

import java.math.BigDecimal;

public class MonthlyTrendDto {
    private final int year;
    private final int month;
    private BigDecimal income;
    private BigDecimal expenses;

    public MonthlyTrendDto(int year, int month) {
        this.year = year;
        this.month = month;
        this.income = BigDecimal.ZERO;
        this.expenses = BigDecimal.ZERO;
    }

    public int getYear() { return year; }
    public int getMonth() { return month; }
    public BigDecimal getIncome() { return income; }
    public BigDecimal getExpenses() { return expenses; }
    public void setIncome(BigDecimal income) { this.income = income; }
    public void setExpenses(BigDecimal expenses) { this.expenses = expenses; }
}
