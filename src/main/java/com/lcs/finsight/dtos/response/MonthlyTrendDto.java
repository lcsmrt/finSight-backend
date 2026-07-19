package com.lcs.finsight.dtos.response;

import java.math.BigDecimal;

public class MonthlyTrendDto {
    private final int year;
    private final int month;
    private final BigDecimal income;
    private final BigDecimal expenses;

    public MonthlyTrendDto(int year, int month, BigDecimal income, BigDecimal expenses) {
        this.year = year;
        this.month = month;
        this.income = income;
        this.expenses = expenses;
    }

    public int getYear() { return year; }
    public int getMonth() { return month; }
    public BigDecimal getIncome() { return income; }
    public BigDecimal getExpenses() { return expenses; }
}
