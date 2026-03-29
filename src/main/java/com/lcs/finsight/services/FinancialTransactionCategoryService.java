package com.lcs.finsight.services;

import com.lcs.finsight.dtos.request.FinancialTransactionCategoryFilterDto;
import com.lcs.finsight.dtos.request.FinancialTransactionCategoryRequestDto;
import com.lcs.finsight.exceptions.FinancialTransactionCategoryExceptions;
import com.lcs.finsight.models.FinancialTransactionCategory;
import com.lcs.finsight.models.FinancialTransactionType;
import com.lcs.finsight.models.User;
import com.lcs.finsight.repositories.FinancialTransactionCategoryRepository;
import com.lcs.finsight.specifications.FinancialTransactionCategorySpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class FinancialTransactionCategoryService {

    private final FinancialTransactionCategoryRepository categoryRepository;

    public FinancialTransactionCategoryService(FinancialTransactionCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public FinancialTransactionCategory findById(Long id, User user) {
        FinancialTransactionCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new FinancialTransactionCategoryExceptions.FinancialTransactionCategoryNotFoundException(id));

        if (!category.getUser().getId().equals(user.getId())) {
            throw new FinancialTransactionCategoryExceptions.FinancialTransactionCategoryNotFoundException(id);
        }

        return category;
    }

    private static final Set<String> SORTABLE_FIELDS = Set.of("description", "spendingLimit");

    @Transactional(readOnly = true)
    public List<FinancialTransactionCategory> findAllByUser(User user) {
        return categoryRepository.findAllByUser(user);
    }

    @Transactional(readOnly = true)
    public Page<FinancialTransactionCategory> findAllByUserPaged(FinancialTransactionCategoryFilterDto filter, User user) {
        PageRequest pageable = filter.toPageable(SORTABLE_FIELDS);

        Specification<FinancialTransactionCategory> spec = Specification.allOf(
                FinancialTransactionCategorySpecification.belongsToUser(user),
                FinancialTransactionCategorySpecification.descriptionContains(filter.getDescription()),
                FinancialTransactionCategorySpecification.typeEquals(filter.getType()));

        return categoryRepository.findAll(spec, pageable);
    }

    @Transactional
    public FinancialTransactionCategory create(FinancialTransactionCategoryRequestDto dto, User user) {
        FinancialTransactionCategory category = new FinancialTransactionCategory();

        category.setUser(user);
        category.setType(dto.getType());
        category.setDescription(dto.getDescription());
        category.setSpendingLimit(dto.getType() == FinancialTransactionType.CREDIT ? null : dto.getSpendingLimit());

        return categoryRepository.save(category);
    }

    @Transactional
    public FinancialTransactionCategory update(Long id, FinancialTransactionCategoryRequestDto dto, User user) {
        FinancialTransactionCategory existingCategory = findById(id, user);

        existingCategory.setType(dto.getType());
        existingCategory.setDescription(dto.getDescription());
        existingCategory.setSpendingLimit(dto.getType() == FinancialTransactionType.CREDIT ? null : dto.getSpendingLimit());

        return categoryRepository.save(existingCategory);
    }

    @Transactional
    public void delete(Long id, User user) {
        FinancialTransactionCategory category = findById(id, user);
        categoryRepository.delete(category);
    }
}
