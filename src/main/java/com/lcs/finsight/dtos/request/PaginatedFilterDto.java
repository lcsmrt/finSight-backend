package com.lcs.finsight.dtos.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Set;

public abstract class PaginatedFilterDto {

    @Min(value = 0, message = "Page number cannot be negative.")
    private int page = 0;

    @Min(value = 1, message = "Page size must be at least 1.")
    @Max(value = 100, message = "Page size must be at most 100.")
    private int size = 10;

    private String sortBy;

    @Pattern(regexp = "asc|desc", flags = Pattern.Flag.CASE_INSENSITIVE, message = "Sort direction must be 'asc' or 'desc'.")
    private String sortDirection;

    protected PaginatedFilterDto(String defaultSortBy, String defaultSortDirection) {
        this.sortBy = defaultSortBy;
        this.sortDirection = defaultSortDirection;
    }

    public PageRequest toPageable(Set<String> allowedSortFields) {
        if (!allowedSortFields.contains(sortBy)) {
            throw new IllegalArgumentException("Invalid sort field: " + sortBy);
        }
        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
        return PageRequest.of(page, size, sort);
    }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }

    public String getSortDirection() { return sortDirection; }
    public void setSortDirection(String sortDirection) { this.sortDirection = sortDirection; }
}
