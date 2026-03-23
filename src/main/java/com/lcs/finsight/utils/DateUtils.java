package com.lcs.finsight.utils;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class DateUtils {
    public void checkIfStartDateIsBeforeEndDate(LocalDate startDate, LocalDate endDate) {
        if (endDate == null) return;

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("A data inicial não pode ser maior que a data final.");
        }
    }
}
